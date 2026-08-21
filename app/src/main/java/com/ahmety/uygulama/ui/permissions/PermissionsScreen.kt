package com.ahmety.uygulama.ui.permissions

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.ahmety.uygulama.core.designsystem.MerkezIcons
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * İzinleri tek tek, ne işe yaradığı yazılı olarak isteyen ekran.
 * Ayarlar'dan her zaman açılabilir; ilk kurulumda da karşımıza çıkar.
 */
@Composable
fun PermissionsScreen(
    onContinue: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Özel izinler Ayarlar ekranında verildiği için, uygulamaya dönüldüğünde
    // durumları yeniden hesaplamamız gerekiyor.
    var refreshKey by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refreshKey++ }

    val specs = remember { permissionSpecs() }
    val statuses = remember(refreshKey) { specs.associate { it.id to it.isGranted(context) } }
    val missingCount = statuses.count { !it.value }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("İzinler", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = if (missingCount == 0) {
                    "Tüm izinler verildi."
                } else {
                    "$missingCount izin bekliyor. Her biri olmadan da uygulama açılır, " +
                        "sadece ilgili özellik çalışmaz."
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(specs, key = { it.id }) { spec ->
                PermissionCard(
                    spec = spec,
                    granted = statuses[spec.id] == true,
                    onRequest = {
                        when (spec.kind) {
                            PermissionKind.RUNTIME ->
                                runtimeLauncher.launch(spec.manifestPermissions.toTypedArray())

                            PermissionKind.SPECIAL -> {
                                val intent = spec.settingsIntent?.invoke(context) ?: return@PermissionCard
                                try {
                                    context.startActivity(intent)
                                } catch (_: ActivityNotFoundException) {
                                    // Bazı üreticiler bu ayar ekranını kaldırıyor;
                                    // o durumda uygulama ayarlarına düşüyoruz.
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.fromParts("package", context.packageName, null),
                                        ),
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }

        if (onContinue != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onContinue) {
                    Text(if (missingCount == 0) "Devam et" else "Şimdilik geç")
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    spec: PermissionSpec,
    granted: Boolean,
    onRequest: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (granted) {
                    Icon(
                        imageVector = MerkezIcons.CheckCircle,
                        contentDescription = "Verildi",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Text(text = spec.title, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = spec.rationale,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (!granted) {
                Button(
                    onClick = onRequest,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Text(if (spec.kind == PermissionKind.RUNTIME) "İzin ver" else "Ayarları aç")
                }
            }
        }
    }
}
