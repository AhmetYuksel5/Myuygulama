package com.ahmety.uygulama.feature.vocab

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ahmety.uygulama.core.model.VocabDecision
import com.ahmety.uygulama.core.model.VocabStatus
import com.ahmety.uygulama.core.model.startOfDay
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * Ana ekrandaki tekrar kartı.
 *
 * Tekrar uygulamayı açmayı bekliyordu; açılmayan gün tekrar da olmuyordu.
 * Kart sıradaki kelimeyi ana ekranda gösteriyor: düşün, anlamına bak,
 * "Bildim" ya da "Bilemedim" de. Karar uygulamadakiyle aynı yoldan
 * kaydediliyor, tekrar programı tek.
 *
 * Kartın gösterdiği kelime ve anlamı widget'ın kendi durumunda tutuluyor;
 * her düğme kararı kaydedip sıradaki kelimeyi oraya yazıyor.
 */
class VocabWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        refresh(context, id)
        provideContent {
            GlanceTheme {
                Content(context, currentState())
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Content(context: Context, state: Preferences) {
        val word = state[WORD].orEmpty()
        val meaning = state[MEANING].orEmpty()
        val count = state[COUNT] ?: 0
        val revealed = state[REVEALED] == true
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(20.dp)
                .padding(14.dp),
        ) {
            Text(
                text = if (count > 0) "Bugün $count kelime" else "Kelime tekrarı",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
            if (word.isBlank()) {
                Spacer(GlanceModifier.padding(top = 6.dp))
                Text(
                    text = "Bugünlük tekrar bitti.",
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp),
                )
                return@Column
            }
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .let { if (open != null) it.clickable(actionStartActivity(open)) else it }
                    .padding(top = 4.dp, bottom = 8.dp),
            ) {
                Text(
                    text = word,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                if (revealed) {
                    Text(
                        text = meaning.ifBlank { "Anlamı henüz yok; uygulamada kartını aç." },
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 15.sp),
                        maxLines = 3,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!revealed) {
                    Button(text = "Anlamı", onClick = actionRunCallback<RevealAction>())
                    Spacer(GlanceModifier.width(8.dp))
                }
                Button(
                    text = "Bildim",
                    onClick = actionRunCallback<AnswerAction>(actionParametersOf(KNEW to true)),
                )
                Spacer(GlanceModifier.width(8.dp))
                Button(
                    text = "Bilemedim",
                    onClick = actionRunCallback<AnswerAction>(actionParametersOf(KNEW to false)),
                )
            }
        }
    }

    companion object {
        internal val WORD = stringPreferencesKey("word")
        internal val MEANING = stringPreferencesKey("meaning")
        internal val COUNT = intPreferencesKey("count")
        internal val REVEALED = booleanPreferencesKey("revealed")
        internal val KNEW = ActionParameters.Key<Boolean>("knew")

        /** Sıradaki kelimeyi bulup kartın durumuna yazar. */
        internal suspend fun refresh(context: Context, id: GlanceId) {
            val repository = EntryPointAccessors
                .fromApplication(context.applicationContext, VocabWidgetEntryPoint::class.java)
                .repository()
            val due = dueWords(repository)
            val next = due.firstOrNull()
            updateAppWidgetState(context, id) { prefs ->
                prefs[WORD] = next?.first.orEmpty()
                prefs[MEANING] = next?.second.orEmpty()
                prefs[COUNT] = due.size
                prefs[REVEALED] = false
            }
        }

        /**
         * Bugün vadesi gelen kelimeler, en çok bekleyenden başlayarak.
         * Uygulamanın tekrar bölmesiyle aynı kural: yalnız çalışılmış ve
         * vadesi gelmiş kelimeler.
         */
        private suspend fun dueWords(repository: VocabRepository): List<Pair<String, String>> {
            val now = repository.nowMillis()
            val endOfToday = startOfDay(now, repository.zoneOffsetMillis(now)) + DAY_MILLIS
            val meanings = repository.observeBookWords().first().associate { it.word to it.meaning }
            return repository.observeProgress().first()
                .filter { entry ->
                    // Eski kayıtlarda "emin değilim" de var; o da öğreniliyor demek.
                    (entry.status == VocabStatus.LEARNING.name || entry.status == "UNSURE") &&
                        entry.lastReviewedAt != null &&
                        (entry.dueAt ?: Long.MAX_VALUE) <= endOfToday &&
                        entry.word in meanings
                }
                .sortedBy { it.dueAt }
                .map { it.word to meanings[it.word].orEmpty() }
        }

        private const val DAY_MILLIS = 86_400_000L
    }
}

class VocabWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VocabWidget()
}

class RevealAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { it[VocabWidget.REVEALED] = true }
        VocabWidget().update(context, glanceId)
    }
}

/**
 * Bildim / Bilemedim. Uygulamadaki kartla aynı anlam: bilemedim, anlamını
 * açıp hatırlayamamak demek (FSRS'de "unuttum").
 */
class AnswerAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val knew = parameters[VocabWidget.KNEW] ?: true
        val state = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val word = state[VocabWidget.WORD].orEmpty()
        if (word.isNotBlank()) {
            EntryPointAccessors
                .fromApplication(context.applicationContext, VocabWidgetEntryPoint::class.java)
                .repository()
                .applyDecision(word, VocabDecision.STUDIED, revealed = !knew)
        }
        VocabWidget.refresh(context, glanceId)
        VocabWidget().update(context, glanceId)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface VocabWidgetEntryPoint {
    fun repository(): VocabRepository
}
