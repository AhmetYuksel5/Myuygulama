package com.ahmety.uygulama.feature.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val colorArgb: Int,
    val visible: Boolean,
    val writable: Boolean,
)

data class CalendarEvent(
    val eventId: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val calendarId: Long,
    val colorArgb: Int,
    val location: String?,
)

/**
 * Takvimi Google Calendar API'sinden değil, **cihazın takvim sağlayıcısından**
 * okuyup yazıyoruz.
 *
 * Telefonundaki Google hesabı takvimi zaten buraya senkronluyor. Biz buraya
 * yazdığımızda Google'ın kendi senkron adaptörü bulut tarafına taşıyor.
 * Sonuç: OAuth ekranı yok, API kotası yok, jeton yenileme yok, çevrimdışı çalışır
 * ve iki yönlü senkron bedavaya gelir.
 */
@Singleton
class DeviceCalendarRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun hasReadPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CALENDAR,
    ) == PackageManager.PERMISSION_GRANTED

    fun hasWritePermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.WRITE_CALENDAR,
    ) == PackageManager.PERMISSION_GRANTED

    suspend fun calendars(): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) return@withContext emptyList()

        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )

        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC",
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            DeviceCalendar(
                                id = cursor.getLong(0),
                                displayName = cursor.getString(1).orEmpty(),
                                accountName = cursor.getString(2).orEmpty(),
                                colorArgb = cursor.getInt(3),
                                visible = cursor.getInt(4) == 1,
                                writable = cursor.getInt(5) >=
                                    CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                            ),
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    /**
     * Belirtilen aralıktaki etkinlikler.
     *
     * `Instances` tablosunu kullanıyoruz, `Events`'i değil: tekrarlayan
     * etkinlikleri sağlayıcı bizim için tek tek açıyor, tekrar kurallarını
     * elle çözmemiz gerekmiyor.
     */
    suspend fun events(startMillis: Long, endMillis: Long): List<CalendarEvent> =
        withContext(Dispatchers.IO) {
            if (!hasReadPermission()) return@withContext emptyList()

            val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(uriBuilder, startMillis)
            ContentUris.appendId(uriBuilder, endMillis)

            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.DISPLAY_COLOR,
                CalendarContract.Instances.EVENT_LOCATION,
            )

            runCatching {
                context.contentResolver.query(
                    uriBuilder.build(),
                    projection,
                    // Gizlenmiş takvimler ve iptal edilmiş etkinlikler listeye girmesin.
                    "${CalendarContract.Instances.VISIBLE} = 1 AND " +
                        "${CalendarContract.Instances.STATUS} != " +
                        "${CalendarContract.Instances.STATUS_CANCELED}",
                    null,
                    "${CalendarContract.Instances.BEGIN} ASC",
                )?.use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(
                                CalendarEvent(
                                    eventId = cursor.getLong(0),
                                    title = cursor.getString(1)?.takeIf { it.isNotBlank() }
                                        ?: "(başlıksız)",
                                    startMillis = cursor.getLong(2),
                                    endMillis = cursor.getLong(3),
                                    allDay = cursor.getInt(4) == 1,
                                    calendarId = cursor.getLong(5),
                                    colorArgb = cursor.getInt(6),
                                    location = cursor.getString(7)?.takeIf { it.isNotBlank() },
                                ),
                            )
                        }
                    }
                }.orEmpty()
            }.getOrDefault(emptyList())
        }

    /**
     * Etkinlik oluşturur ve sağlayıcının verdiği kimliği döndürür.
     * Google hesabına bağlı bir takvime yazıldığında bulut tarafına
     * Google'ın kendi senkronu taşır.
     */
    suspend fun createEvent(
        calendarId: Long,
        title: String,
        startMillis: Long,
        endMillis: Long,
        allDay: Boolean = false,
        description: String? = null,
        location: String? = null,
    ): Long? = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) return@withContext null

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
            // Tüm gün etkinlikleri sağlayıcı UTC bekler; diğerleri cihaz saatinde.
            put(
                CalendarContract.Events.EVENT_TIMEZONE,
                if (allDay) "UTC" else TimeZone.getDefault().id,
            )
            description?.let { put(CalendarContract.Events.DESCRIPTION, it) }
            location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
        }

        runCatching {
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                ?.lastPathSegment
                ?.toLongOrNull()
        }.getOrNull()
    }
}
