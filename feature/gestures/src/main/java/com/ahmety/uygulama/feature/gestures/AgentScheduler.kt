package com.ahmety.uygulama.feature.gestures

import android.app.Activity
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Zamanlanmış bir ajan görevi. [repeat]: "none", "daily", "weekdays" ya da "mon,wed,fri". */
data class AgentJob(val id: Long, val task: String, val atMillis: Long, val repeat: String) {
    val time: LocalTime get() = LocalDateTime.ofInstant(Instant.ofEpochMilli(atMillis), ZoneId.systemDefault()).toLocalTime()
}

/**
 * Ekran ajanının zamanlayıcısı.
 *
 * Kullanıcı ayrı bir ekranda saat kurmuyor: ajana "yarın 9'da şunu yap"
 * deyince model işi o an yapmak yerine "schedule" döndürüyor, görev buraya
 * yazılıyor. Saat gelince Android alarmı Merkez'i uyandırıyor, ekran yanıyor,
 * kaydırma kilidi kalkıyor ve görev başlıyor. Alarmlar telefon yeniden
 * başlayınca siliniyor; açılışta ve güncellemeden sonra yeniden kuruluyor.
 */
object AgentScheduler {

    private const val PREFS = "merkez_ajan_zamanlayici"
    private const val KEY_JOBS = "jobs"
    internal const val EXTRA_ID = "is"
    internal const val EXTRA_TASK = "gorev"
    private const val CHANNEL = "ajan_zamanlayici"

    private val TR = Locale.forLanguageTag("tr")

    fun list(context: Context): List<AgentJob> = runCatching {
        val array = JSONArray(prefs(context).getString(KEY_JOBS, "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            AgentJob(o.getLong("id"), o.getString("task"), o.getLong("at"), o.optString("repeat", "none"))
        }.sortedBy { it.atMillis }
    }.getOrDefault(emptyList())

    private fun save(context: Context, jobs: List<AgentJob>) {
        val array = JSONArray()
        jobs.forEach {
            array.put(JSONObject().put("id", it.id).put("task", it.task).put("at", it.atMillis).put("repeat", it.repeat))
        }
        prefs(context).edit().putString(KEY_JOBS, array.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Görevi kaydeder ve alarmı kurar; kurulan iş döner. */
    internal fun add(context: Context, task: String, at: LocalDateTime, repeat: String): AgentJob {
        val clean = normalizeRepeat(repeat)
        var millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Tekrarlı işte verilen gün uymuyorsa ilk uygun güne kaydır.
        if (clean != "none") {
            // Tekrarlı işte ilk çalışma: verilen andan (ya da şimdiden) sonraki, kurala uyan ilk gün.
            val from = at.minusMinutes(1).let { if (it.isBefore(LocalDateTime.now())) LocalDateTime.now() else it }
            millis = nextAfter(at.toLocalTime(), clean, from)
        }
        val job = AgentJob(System.currentTimeMillis(), task.trim(), millis, clean)
        save(context, list(context) + job)
        arm(context, job)
        return job
    }

    fun remove(context: Context, id: Long) {
        val job = list(context).firstOrNull { it.id == id } ?: return
        alarms(context)?.cancel(pending(context, job))
        save(context, list(context).filter { it.id != id })
    }

    /** Açılışta, güncellemeden sonra ve servis bağlanınca: bütün alarmları yeniden kur. */
    fun rearmAll(context: Context) {
        val now = System.currentTimeMillis()
        val kept = list(context).mapNotNull { job ->
            when {
                job.atMillis > now -> job
                // Kaçırılmış tek seferlik iş: kısa gecikmede yine çalışsın, uzunda düşsün.
                job.repeat == "none" -> if (now - job.atMillis < MISS_GRACE_MS) job.copy(atMillis = now + 5_000) else null
                else -> job.copy(atMillis = nextAfter(job.time, job.repeat, LocalDateTime.now()))
            }
        }
        save(context, kept)
        kept.forEach { arm(context, it) }
    }

    /** Alarm çaldı: tekrarlı işi bir sonrakine kur, sonra ekranı uyandırıp görevi başlat. */
    internal fun fire(context: Context, id: Long) {
        val job = list(context).firstOrNull { it.id == id } ?: return
        val rest = list(context).filter { it.id != id }
        if (job.repeat == "none") {
            save(context, rest)
        } else {
            val next = job.copy(atMillis = nextAfter(job.time, job.repeat, LocalDateTime.now()))
            save(context, rest + next)
            arm(context, next)
        }
        val wake = Intent(context, AgentWakeActivity::class.java)
            .putExtra(EXTRA_TASK, job.task)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        runCatching { context.startActivity(wake) }.onFailure {
            notify(context, "Zamanlanmış görev başlatılamadı", "${job.task.take(80)} · Merkez'i açıp tekrar dene.")
        }
    }

    /** Ekran açık ve kilit kalkmışken görevi ajana verir. */
    internal fun runNow(context: Context, task: String) {
        val service = QuickCursorService.current()
        if (service == null) {
            notify(context, "Zamanlanmış görev çalışamadı", "Erişilebilirlik kapalı: ${task.take(80)}")
            return
        }
        Handler(Looper.getMainLooper()).post { ScreenAgent.startScheduled(service, task) }
    }

    private fun arm(context: Context, job: AgentJob) {
        val manager = alarms(context) ?: return
        val pi = pending(context, job)
        runCatching {
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, job.atMillis, pi)
            } else {
                // Tam saat izni yoksa birkaç dakika sapabilir ama yine çalışır.
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, job.atMillis, pi)
            }
        }
    }

    fun exactAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms(context)?.canScheduleExactAlarms() == true

    private fun pending(context: Context, job: AgentJob): PendingIntent = PendingIntent.getBroadcast(
        context,
        (job.id % Int.MAX_VALUE).toInt(),
        Intent(context, AgentAlarmReceiver::class.java).putExtra(EXTRA_ID, job.id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun alarms(context: Context) = context.getSystemService(AlarmManager::class.java)

    internal fun normalizeRepeat(raw: String): String {
        val r = raw.trim().lowercase(Locale.ROOT)
        if (r.isEmpty() || r == "none" || r == "once") return "none"
        if (r == "daily" || r == "weekdays") return r
        val days = r.split(',', ' ').mapNotNull { DAY_KEYS[it.trim().take(3)] }.distinct()
        return if (days.isEmpty()) "none" else days.joinToString(",") { it.name.lowercase(Locale.ROOT).take(3) }
    }

    private fun matches(repeat: String, day: DayOfWeek): Boolean = when (repeat) {
        "daily" -> true
        "weekdays" -> day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY
        else -> repeat.split(',').any { DAY_KEYS[it] == day }
    }

    /** [time] saatinin [after]'dan sonraki, kurala uyan ilk gerçekleşmesi. */
    internal fun nextAfter(time: LocalTime, repeat: String, after: LocalDateTime): Long {
        var candidate = after.toLocalDate().atTime(time)
        if (!candidate.isAfter(after)) candidate = candidate.plusDays(1)
        repeat(8) {
            if (matches(repeat, candidate.dayOfWeek)) {
                return candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            candidate = candidate.plusDays(1)
        }
        return candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    /** Kullanıcıya gösterilen: "27 Kasım Perşembe 09:00 · her gün". */
    fun describe(job: AgentJob): String {
        val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(job.atMillis), ZoneId.systemDefault())
        val day = at.format(DateTimeFormatter.ofPattern("d MMMM EEEE HH:mm", TR))
        val repeat = when (job.repeat) {
            "none" -> ""
            "daily" -> " · her gün"
            "weekdays" -> " · hafta içi her gün"
            else -> " · her " + job.repeat.split(',').mapNotNull { DAY_KEYS[it] }
                .joinToString(", ") { it.getDisplayName(java.time.format.TextStyle.FULL, TR) }
        }
        return day + repeat
    }

    internal fun notify(context: Context, title: String, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Ekran ajanı zamanlayıcısı", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val notification = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify((System.currentTimeMillis() % 100_000).toInt() + 9000, notification) }
    }

    private val DAY_KEYS = mapOf(
        "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY, "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY,
        "sun" to DayOfWeek.SUNDAY,
        "pzt" to DayOfWeek.MONDAY, "sal" to DayOfWeek.TUESDAY, "çar" to DayOfWeek.WEDNESDAY,
        "per" to DayOfWeek.THURSDAY, "cum" to DayOfWeek.FRIDAY, "cmt" to DayOfWeek.SATURDAY,
        "paz" to DayOfWeek.SUNDAY,
    )

    private const val MISS_GRACE_MS = 15 * 60_000L
}

/** Zamanlanan saat geldi. */
class AgentAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AgentScheduler.EXTRA_ID, -1L)
        if (id >= 0) AgentScheduler.fire(context, id)
    }
}

/** Telefon açılınca ve Merkez güncellenince: alarmlar silinmiş olabiliyor, yeniden kuruluyor. */
class AgentRearmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AgentScheduler.rearmAll(context)
    }
}

/**
 * Ekranı yakan ve kaydırma kilidini kaldıran görünmez ekran. Kilit
 * kalkınca görevi ajana verip kapanıyor. Şifreli kilitte Android kilidi
 * uygulamaya açtırmıyor; o zaman kullanıcı açınca görev başlıyor.
 */
class AgentWakeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val task = intent.getStringExtra(AgentScheduler.EXTRA_TASK).orEmpty()
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isKeyguardLocked) {
            go(task)
            return
        }
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = go(task)
                override fun onDismissCancelled() {
                    AgentScheduler.notify(applicationContext, "Zamanlanmış görev bekliyor", "Kilit açılmadı: ${task.take(80)}")
                    finish()
                }
                override fun onDismissError() = go(task)
            },
        )
    }

    private fun go(task: String) {
        val app = applicationContext
        finish()
        // Ekran kapanıp kilit tamamen kalksın, sonra ajan başlasın.
        Handler(Looper.getMainLooper()).postDelayed({ AgentScheduler.runNow(app, task) }, 900L)
    }
}
