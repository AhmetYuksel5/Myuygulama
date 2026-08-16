package com.ahmety.uygulama.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.appwidget.action.actionStartActivity
import androidx.compose.ui.unit.dp
import com.ahmety.uygulama.core.database.repository.HabitRepository
import com.ahmety.uygulama.core.database.repository.TaskRepository
import com.ahmety.uygulama.core.model.HabitStreaks
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Glance widget'ları Hilt ile doğrudan enjekte edilemiyor (sistem tarafından
 * oluşturuluyorlar), bu yüzden bağımlılıkları uygulama bileşeninden
 * bir giriş noktasıyla alıyoruz.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetEntryPoint {
    fun habitRepository(): HabitRepository
    fun taskRepository(): TaskRepository
}

internal data class TodayWidgetData(
    val habitsDone: Int = 0,
    val habitsDue: Int = 0,
    val openTasks: List<String> = emptyList(),
    val openTaskCount: Int = 0,
)

class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadData(context)
        provideContent { TodayWidgetContent(context, data) }
    }

    private suspend fun loadData(context: Context): TodayWidgetData {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        )
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toEpochDays()

        val habits = entryPoint.habitRepository().observeHabits().first()
        val checks = entryPoint.habitRepository().observeChecksBetween(today, today).first()
        val dueHabits = habits.filter { HabitStreaks.isDue(it.schedule, today) }
        val doneHabits = dueHabits.count { habit ->
            val count = checks.firstOrNull { it.habitUuid == habit.uuid }?.count ?: 0
            count >= habit.targetPerDay
        }

        val tasks = entryPoint.taskRepository().observeDueThrough(today).first()

        return TodayWidgetData(
            habitsDone = doneHabits,
            habitsDue = dueHabits.size,
            openTasks = tasks.take(4).map { it.title },
            openTaskCount = tasks.size,
        )
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

@Composable
private fun TodayWidgetContent(context: Context, data: TodayWidgetData) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)

    GlanceTheme {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
                .let { if (launchIntent != null) it.clickable(actionStartActivity(launchIntent)) else it },
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Bugün",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.onSurface,
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(
                    text = habitSummary(data),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                )
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            if (data.openTasks.isEmpty()) {
                Text(
                    text = "Açık görev yok.",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                )
            } else {
                data.openTasks.forEach { title ->
                    Text(
                        text = "• $title",
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurface),
                        modifier = GlanceModifier.padding(vertical = 1.dp),
                    )
                }
                val remaining = data.openTaskCount - data.openTasks.size
                if (remaining > 0) {
                    Text(
                        text = "+$remaining görev daha",
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                    )
                }
            }
        }
    }
}

private fun habitSummary(data: TodayWidgetData): String =
    if (data.habitsDue == 0) "" else "${data.habitsDone}/${data.habitsDue} alışkanlık"
