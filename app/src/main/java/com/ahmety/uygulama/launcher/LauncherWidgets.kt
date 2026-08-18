package com.ahmety.uygulama.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Ana ekranda widget barındırma.
 *
 * Android'de widget göstermek için uygulamanın bir **AppWidgetHost** olması
 * gerekiyor: önce bir kimlik ayrılır, sistem seçicisi o kimliğe bir sağlayıcı
 * bağlar, sonra sağlayıcının görünümü barındırılır. Seçiciyi sistem açtığı
 * için varsayılan başlatıcı olmasak da çalışıyor.
 */
private const val HOST_ID = 0x4D45524B // "MERK"

class LauncherWidgetHost(context: Context) : AppWidgetHost(context, HOST_ID)

@Composable
fun WidgetsPage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { LauncherStore(context) }
    val manager = remember { AppWidgetManager.getInstance(context) }
    val host = remember { LauncherWidgetHost(context.applicationContext) }

    var widgetIds by remember { mutableStateOf(store.widgetIds) }
    var pendingConfigureId by remember { mutableStateOf<Int?>(null) }

    // Host yalnızca ekran açıkken dinlemeli; aksi hâlde widget'lar güncellenmez
    // ya da arka planda boşuna kaynak tüketir.
    DisposableEffect(host) {
        runCatching { host.startListening() }
        onDispose { runCatching { host.stopListening() } }
    }

    fun persist(ids: List<Int>) {
        widgetIds = ids
        store.widgetIds = ids
    }

    val configureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val id = pendingConfigureId
        pendingConfigureId = null
        if (id == null) return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            persist(widgetIds + id)
        } else {
            // Kullanıcı kurulumdan vazgeçti: ayrılan kimliği geri ver.
            runCatching { host.deleteAppWidgetId(id) }
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val id = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        if (result.resultCode != Activity.RESULT_OK || id == -1) {
            if (id != -1) runCatching { host.deleteAppWidgetId(id) }
            return@rememberLauncherForActivityResult
        }
        val info = manager.getAppWidgetInfo(id)
        if (info?.configure != null) {
            // Sağlayıcının kendi kurulum ekranı var; önce onu göster.
            pendingConfigureId = id
            val configure = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = info.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            }
            runCatching { configureLauncher.launch(configure) }
                .onFailure {
                    pendingConfigureId = null
                    persist(widgetIds + id)
                }
        } else {
            persist(widgetIds + id)
        }
    }

    fun addWidget() {
        val id = runCatching { host.allocateAppWidgetId() }.getOrNull()
        if (id == null) {
            Toast.makeText(context, "Widget kimliği alınamadı", Toast.LENGTH_SHORT).show()
            return
        }
        val pick = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            // Boş listeler: özel kısayol türleri eklemiyoruz.
            putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_INFO, ArrayList<Parcelable>())
            putParcelableArrayListExtra(AppWidgetManager.EXTRA_CUSTOM_EXTRAS, ArrayList<Parcelable>())
        }
        runCatching { pickLauncher.launch(pick) }.onFailure {
            runCatching { host.deleteAppWidgetId(id) }
            Toast.makeText(context, "Widget seçici açılamadı", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { addWidget() }) { Text("Widget ekle") }
        }

        if (widgetIds.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Bu sayfaya widget ekleyebilirsin. \"Widget ekle\" ile " +
                        "telefonundaki widget'lardan birini seç.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        widgetIds.forEach { id ->
            HostedWidget(
                host = host,
                manager = manager,
                appWidgetId = id,
                onRemove = {
                    runCatching { host.deleteAppWidgetId(id) }
                    persist(widgetIds - id)
                },
            )
        }
    }
}

@Composable
private fun HostedWidget(
    host: LauncherWidgetHost,
    manager: AppWidgetManager,
    appWidgetId: Int,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val info = remember(appWidgetId) { manager.getAppWidgetInfo(appWidgetId) }

    if (info == null) {
        // Sağlayıcı kaldırılmış olabilir.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Bu widget artık yok.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRemove) { Text("Kaldır") }
        }
        return
    }

    val heightDp = (info.minHeight / context.resources.displayMetrics.density)
        .toInt()
        .coerceIn(80, 400)

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), RoundedCornerShape(16.dp)),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    val view: AppWidgetHostView = host.createView(viewContext, appWidgetId, info)
                    view.setAppWidget(appWidgetId, info)
                    val options = Bundle().apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, info.minWidth)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, info.minHeight)
                    }
                    runCatching { view.updateAppWidgetOptions(options) }
                    view
                },
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onRemove) { Text("Kaldır") }
        }
    }
}
