package com.ahmety.uygulama.core.database.repository

import com.ahmety.uygulama.core.database.dao.HabitDao
import com.ahmety.uygulama.core.database.entity.ChangeEntityType
import com.ahmety.uygulama.core.database.entity.ChangeOperation
import com.ahmety.uygulama.core.database.entity.HabitCheckEntity
import com.ahmety.uygulama.core.database.entity.HabitEntity
import com.ahmety.uygulama.core.database.sync.ChangeRecorder
import com.ahmety.uygulama.core.database.sync.Now
import com.ahmety.uygulama.core.model.Habit
import com.ahmety.uygulama.core.model.HabitCheck
import com.ahmety.uygulama.core.model.HabitSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Alışkanlıklara yapılan **tüm** yazmalar buradan geçer. Sebebi tek: her yazma
 * aynı anda değişiklik günlüğüne de satır bıraksın. Ekranların doğrudan DAO'ya
 * yazmasına izin verirsek, o yazma ikinci telefona hiç ulaşmaz.
 */
@Singleton
class HabitRepository @Inject constructor(
    private val habitDao: HabitDao,
    private val changeRecorder: ChangeRecorder,
    private val json: Json,
    private val now: Now,
) {

    fun observeHabits(): Flow<List<Habit>> =
        habitDao.observeActive().map { list -> list.map(HabitEntity::toDomain) }

    fun observeArchivedHabits(): Flow<List<Habit>> =
        habitDao.observeArchived().map { list -> list.map(HabitEntity::toDomain) }

    fun observeChecksBetween(from: Int, to: Int): Flow<List<HabitCheck>> =
        habitDao.observeChecksBetween(from, to).map { list -> list.map(HabitCheckEntity::toDomain) }

    fun observeChecksForHabit(habitUuid: String): Flow<List<HabitCheck>> =
        habitDao.observeChecksForHabit(habitUuid).map { list -> list.map(HabitCheckEntity::toDomain) }

    suspend fun getHabit(uuid: String): Habit? = habitDao.getByUuid(uuid)?.toDomain()

    /** Yeni alışkanlık oluşturur ve kalıcı kimliğini (uuid) döndürür. */
    suspend fun createHabit(
        name: String,
        description: String = "",
        schedule: HabitSchedule = HabitSchedule.Daily,
        targetPerDay: Int = 1,
        colorArgb: Int? = null,
        reminderMinuteOfDay: Int? = null,
    ): String {
        val timestamp = now.millis()
        val entity = HabitEntity(
            uuid = UUID.randomUUID().toString(),
            name = name.trim(),
            description = description.trim(),
            scheduleType = schedule.typeName(),
            scheduleDaysMask = (schedule as? HabitSchedule.SpecificDays)?.daysMask ?: 0,
            scheduleTimesPerWeek = (schedule as? HabitSchedule.TimesPerWeek)?.times ?: 0,
            targetPerDay = targetPerDay.coerceAtLeast(1),
            colorArgb = colorArgb,
            reminderMinuteOfDay = reminderMinuteOfDay,
            position = habitDao.nextPosition(),
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        writeHabit(entity)
        return entity.uuid
    }

    suspend fun updateHabit(habit: Habit) {
        val existing = habitDao.getByUuid(habit.uuid) ?: return
        writeHabit(
            existing.copy(
                name = habit.name.trim(),
                description = habit.description.trim(),
                scheduleType = habit.schedule.typeName(),
                scheduleDaysMask = (habit.schedule as? HabitSchedule.SpecificDays)?.daysMask ?: 0,
                scheduleTimesPerWeek = (habit.schedule as? HabitSchedule.TimesPerWeek)?.times ?: 0,
                targetPerDay = habit.targetPerDay.coerceAtLeast(1),
                colorArgb = habit.colorArgb,
                reminderMinuteOfDay = habit.reminderMinuteOfDay,
                archived = habit.archived,
                updatedAt = now.millis(),
            ),
        )
    }

    suspend fun setArchived(uuid: String, archived: Boolean) {
        val existing = habitDao.getByUuid(uuid) ?: return
        writeHabit(existing.copy(archived = archived, updatedAt = now.millis()))
    }

    /**
     * Silme kaydı gerçekten silmez, mezar taşı bırakır. Aksi hâlde bu cihazda
     * silinen alışkanlık, diğer cihazın eski günlüğü uygulanınca geri dirilirdi.
     */
    suspend fun deleteHabit(uuid: String) {
        val existing = habitDao.getByUuid(uuid) ?: return
        val timestamp = now.millis()
        val deleted = existing.copy(deletedAt = timestamp, updatedAt = timestamp)
        habitDao.upsert(deleted)
        changeRecorder.record(
            entityType = ChangeEntityType.HABIT,
            entityUuid = uuid,
            operation = ChangeOperation.DELETE,
            payload = json.encodeToString(HabitEntity.serializer(), deleted),
        )
    }

    /** Belirli bir gün için sayacı doğrudan ayarlar. */
    suspend fun setCheckCount(habitUuid: String, date: Int, count: Int) {
        val entity = HabitCheckEntity(
            habitUuid = habitUuid,
            date = date,
            count = count.coerceAtLeast(0),
            updatedAt = now.millis(),
        )
        habitDao.upsertCheck(entity)
        changeRecorder.record(
            entityType = ChangeEntityType.HABIT_CHECK,
            entityUuid = "$habitUuid@$date",
            operation = ChangeOperation.UPSERT,
            payload = json.encodeToString(HabitCheckEntity.serializer(), entity),
        )
    }

    /**
     * Bir dokunuşla ilerletir: hedefe ulaşılmamışsa bir artırır, ulaşılmışsa
     * sıfırlar. Günde tek kez yapılan alışkanlıklarda bu, klasik aç/kapa davranışı olur.
     */
    suspend fun advanceCheck(habitUuid: String, date: Int, targetPerDay: Int) {
        val current = habitDao.getCheck(habitUuid, date)?.count ?: 0
        val target = targetPerDay.coerceAtLeast(1)
        val next = if (current >= target) 0 else current + 1
        setCheckCount(habitUuid, date, next)
    }

    private suspend fun writeHabit(entity: HabitEntity) {
        habitDao.upsert(entity)
        changeRecorder.record(
            entityType = ChangeEntityType.HABIT,
            entityUuid = entity.uuid,
            operation = ChangeOperation.UPSERT,
            payload = json.encodeToString(HabitEntity.serializer(), entity),
        )
    }
}

private fun HabitSchedule.typeName(): String = when (this) {
    HabitSchedule.Daily -> "DAILY"
    is HabitSchedule.SpecificDays -> "SPECIFIC_DAYS"
    is HabitSchedule.TimesPerWeek -> "TIMES_PER_WEEK"
}

internal fun HabitEntity.toDomain(): Habit = Habit(
    id = id,
    uuid = uuid,
    name = name,
    description = description,
    schedule = when (scheduleType) {
        "SPECIFIC_DAYS" -> HabitSchedule.SpecificDays(scheduleDaysMask)
        "TIMES_PER_WEEK" -> HabitSchedule.TimesPerWeek(scheduleTimesPerWeek)
        else -> HabitSchedule.Daily
    },
    targetPerDay = targetPerDay,
    colorArgb = colorArgb,
    reminderMinuteOfDay = reminderMinuteOfDay,
    position = position,
    archived = archived,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun HabitCheckEntity.toDomain(): HabitCheck = HabitCheck(
    habitUuid = habitUuid,
    date = date,
    count = count,
    updatedAt = updatedAt,
)
