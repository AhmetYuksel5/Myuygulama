package com.ahmety.uygulama.feature.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.ToggleableStateKey
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import com.ahmety.uygulama.core.database.prefs.TaskViewPrefs
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

internal data class WidgetTask(
    val uuid: String,
    val title: String,
    val completed: Boolean,
)

internal data class TodayWidgetData(
    val habitsDone: Int = 0,
    val habitsDue: Int = 0,
    val openTasks: List<WidgetTask> = emptyList(),
    val completedTasks: List<WidgetTask> = emptyList(),
    val remainingOpenCount: Int = 0,
)

/** Uygulamayı belirli bir hedefe açan intent; MainActivity ekstradan okuyor. */
internal fun launchIntent(context: Context, target: String): Intent? =
    context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
        putExtra("merkez_hedef", target)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

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

        val open = entryPoint.taskRepository().observeDueThrough(today).first()
        val hideCompleted = TaskViewPrefs(context).hideCompleted
        val completed = if (hideCompleted) {
            emptyList()
        } else {
            entryPoint.taskRepository().observeCompletedOn(today).first()
        }

        return TodayWidgetData(
            habitsDone = doneHabits,
            habitsDue = dueHabits.size,
            openTasks = open.take(MAX_OPEN).map { WidgetTask(it.uuid, it.title, false) },
            completedTasks = completed.take(MAX_DONE).map { WidgetTask(it.uuid, it.title, true) },
            remainingOpenCount = (open.size - MAX_OPEN).coerceAtLeast(0),
        )
    }

    private companion object {
        const val MAX_OPEN = 6
        const val MAX_DONE = 3
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

internal val taskUuidKey = ActionParameters.Key<String>("taskUuid")

/**
 * Widget'taki kutucuğa dokununca görevi tamamlar/geri alır ve widget'ı tazeler.
 * Uygulamayı açmaya gerek kalmıyor; asıl istenen kullanım bu.
 */
class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val uuid = parameters[taskUuidKey] ?: return
        val checked = parameters[ToggleableStateKey] ?: return
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        )
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toEpochDays()
        entryPoint.taskRepository().setCompleted(uuid, checked, today)
        TodayWidget().update(context, glanceId)
    }
}

@Composable
private fun TodayWidgetContent(context: Context, data: TodayWidgetData) {
    val openTasksIntent = launchIntent(context, "gorevler")
    // ＋ artık uygulamayı açmıyor; ana ekranın üstünde küçük bir kutu açıyor.
    val addTaskIntent = Intent(context, QuickAddActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    GlanceTheme {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Başlık bölgesi görevlere götürür; + işareti doğrudan ekleme açar.
                Row(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .let { m -> openTasksIntent?.let { m.clickable(actionStartActivity(it)) } ?: m },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Bugün",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = GlanceTheme.colors.onSurface,
                        ),
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    if (data.habitsDue > 0) {
                        Text(
                            text = "${data.habitsDone}/${data.habitsDue} alışkanlık",
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = GlanceTheme.colors.onSurfaceVariant,
                            ),
                        )
                    }
                }
                Box(
                    modifier = GlanceModifier
                        .padding(start = 10.dp)
                        .clickable(actionStartActivity(addTaskIntent)),
                ) {
                    Text(
                        text = "＋",
                        style = TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.primary,
                        ),
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            if (data.openTasks.isEmpty() && data.completedTasks.isEmpty()) {
                Text(
                    text = "Bugün için görev yok. ＋ ile ekleyebilirsin.",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                )
            }

            data.openTasks.forEach { task -> TaskCheckRow(task) }

            if (data.remainingOpenCount > 0) {
                Text(
                    text = "+${data.remainingOpenCount} görev daha",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                    modifier = GlanceModifier.padding(start = 4.dp, top = 2.dp),
                )
            }

            data.completedTasks.forEach { task -> TaskCheckRow(task) }
        }
    }
}

@Composable
private fun TaskCheckRow(task: WidgetTask) {
    CheckBox(
        checked = task.completed,
        onCheckedChange = actionRunCallback<ToggleTaskAction>(
            actionParametersOf(taskUuidKey to task.uuid),
        ),
        text = task.title,
        maxLines = 1,
        style = if (task.completed) {
            TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
                fontSize = 14.sp,
            )
        } else {
            TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp)
        },
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp),
    )
}
