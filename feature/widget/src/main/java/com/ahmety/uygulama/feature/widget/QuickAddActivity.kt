package com.ahmety.uygulama.feature.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.ahmety.uygulama.core.designsystem.MerkezTheme
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Widget'taki ＋ bunu açar: ana ekranın üstünde beliren küçük bir kutu.
 * Uygulamaya geçilmiyor, görev burada yazılıp kaydediliyor ve widget hemen
 * tazeleniyor — görev anında listede görünüyor.
 *
 * Görev bugüne tarihlenerek ekleniyor; aksi hâlde "Bugün" widget'ında
 * görünmezdi ve kullanıcı eklediği şeyi kaybolmuş sanardı.
 */
class QuickAddActivity : ComponentActivity() {

    private var saving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MerkezTheme {
                QuickAddDialog(
                    onCancel = { finish() },
                    onSave = { title -> saveTask(title) },
                )
            }
        }
    }

    private fun saveTask(title: String) {
        // Çift dokunuş iki görev eklemesin.
        if (saving) return
        val trimmed = title.trim()
        if (trimmed.isEmpty()) {
            finish()
            return
        }
        saving = true
        lifecycleScope.launch {
            runCatching {
                // Ekran kapanınca lifecycleScope iptal ediliyor; yazmayı
                // iptal edilemez yapmazsak kullanıcı "Ekle"ye basıp dışarı
                // dokunduğunda görev kaydedilmeden kayboluyordu.
                withContext(NonCancellable) {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        applicationContext,
                        WidgetEntryPoint::class.java,
                    )
                    val repository = entryPoint.taskRepository()
                    val listUuid = repository.ensureDefaultList()
                    repository.createTask(
                        listUuid = listUuid,
                        title = trimmed,
                        dueDate = Clock.System.todayIn(TimeZone.currentSystemDefault()).toEpochDays(),
                    )
                    // Kaydeder kaydetmez widget'ı tazele: kullanıcı eklediğini
                    // aynı ekranda, beklemeden görsün. (Ekrandaki her kopyayı
                    // ayrı ayrı güncelliyoruz — widget birden çok kez eklenmiş olabilir.)
                    val widget = TodayWidget()
                    GlanceAppWidgetManager(applicationContext)
                        .getGlanceIds(TodayWidget::class.java)
                        .forEach { glanceId -> widget.update(applicationContext, glanceId) }
                }
            }
            finish()
        }
    }
}

@androidx.compose.runtime.Composable
private fun QuickAddDialog(
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Bugüne görev ekle", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Görev") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel) { Text("Vazgeç") }
                Button(
                    onClick = { onSave(text) },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text("Ekle")
                }
            }
        }
    }
}
