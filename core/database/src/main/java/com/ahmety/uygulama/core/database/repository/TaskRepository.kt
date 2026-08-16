package com.ahmety.uygulama.core.database.repository

import com.ahmety.uygulama.core.database.dao.TaskDao
import com.ahmety.uygulama.core.database.entity.ChangeOperation
import com.ahmety.uygulama.core.database.entity.TaskEntity
import com.ahmety.uygulama.core.database.entity.TaskListEntity
import com.ahmety.uygulama.core.database.importer.ImportResult
import com.ahmety.uygulama.core.database.sync.ChangeRecorder
import com.ahmety.uygulama.core.database.sync.Now
import com.ahmety.uygulama.core.model.RecurrenceRule
import com.ahmety.uygulama.core.model.RecurrenceUnit
import com.ahmety.uygulama.core.model.Task
import com.ahmety.uygulama.core.model.TaskList
import com.ahmety.uygulama.core.model.TaskPriority
import com.ahmety.uygulama.core.model.TaskRecurrence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** İçe aktarma sonucu; kullanıcıya "kaç geldi, kaç zaten vardı" demek için. */
data class ImportSummary(val imported: Int, val skipped: Int)

private const val TASK_ENTITY = "task"
private const val TASK_LIST_ENTITY = "task_list"

const val DEFAULT_TASK_LIST_NAME = "Görevler"

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val changeRecorder: ChangeRecorder,
    private val json: Json,
    private val now: Now,
) {

    fun observeLists(): Flow<List<TaskList>> =
        taskDao.observeLists().map { rows -> rows.map(TaskListEntity::toDomain) }

    fun observeTasks(listUuid: String): Flow<List<Task>> =
        taskDao.observeByList(listUuid).map { rows -> rows.map(TaskEntity::toDomain) }

    fun observeSubtasks(parentUuid: String): Flow<List<Task>> =
        taskDao.observeSubtasks(parentUuid).map { rows -> rows.map(TaskEntity::toDomain) }

    fun observeDueThrough(today: Int): Flow<List<Task>> =
        taskDao.observeDueThrough(today).map { rows -> rows.map(TaskEntity::toDomain) }

    fun observeCompletedOn(today: Int): Flow<List<Task>> =
        taskDao.observeCompletedOn(today).map { rows -> rows.map(TaskEntity::toDomain) }

    /** Varsayılan listeyi döndürür; yoksa oluşturur. */
    suspend fun ensureDefaultList(): String {
        taskDao.getListByName(DEFAULT_TASK_LIST_NAME)?.let { return it.uuid }
        return createList(DEFAULT_TASK_LIST_NAME)
    }

    suspend fun createList(name: String): String {
        val timestamp = now.millis()
        val entity = TaskListEntity(
            uuid = UUID.randomUUID().toString(),
            name = name.trim(),
            position = taskDao.nextListPosition(),
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        writeList(entity)
        return entity.uuid
    }

    suspend fun deleteList(uuid: String) {
        val existing = taskDao.getListByUuid(uuid) ?: return
        val timestamp = now.millis()
        writeList(existing.copy(deletedAt = timestamp, updatedAt = timestamp), ChangeOperation.DELETE)
    }

    suspend fun createTask(
        listUuid: String,
        title: String,
        notes: String = "",
        dueDate: Int? = null,
        dueMinuteOfDay: Int? = null,
        priority: TaskPriority = TaskPriority.NONE,
        parentUuid: String? = null,
        recurrence: RecurrenceRule? = null,
        completed: Boolean = false,
        externalId: String? = null,
    ): String {
        val timestamp = now.millis()
        val entity = TaskEntity(
            uuid = UUID.randomUUID().toString(),
            listUuid = listUuid,
            title = title.trim(),
            notes = notes.trim(),
            externalId = externalId,
            dueDate = dueDate,
            dueMinuteOfDay = dueMinuteOfDay,
            completedAt = if (completed) timestamp else null,
            priority = priority.name,
            parentUuid = parentUuid,
            recurrenceUnit = recurrence?.unit?.name,
            recurrenceInterval = recurrence?.interval ?: 1,
            recurrenceDaysMask = recurrence?.daysMask ?: 0,
            recurrenceFromCompletion = recurrence?.fromCompletion ?: false,
            position = taskDao.nextPosition(listUuid),
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        writeTask(entity)
        return entity.uuid
    }

    suspend fun updateTask(task: Task) {
        val existing = taskDao.getByUuid(task.uuid) ?: return
        writeTask(
            existing.copy(
                listUuid = task.listUuid,
                title = task.title.trim(),
                notes = task.notes.trim(),
                dueDate = task.dueDate,
                dueMinuteOfDay = task.dueMinuteOfDay,
                priority = task.priority.name,
                recurrenceUnit = task.recurrence?.unit?.name,
                recurrenceInterval = task.recurrence?.interval ?: 1,
                recurrenceDaysMask = task.recurrence?.daysMask ?: 0,
                recurrenceFromCompletion = task.recurrence?.fromCompletion ?: false,
                updatedAt = now.millis(),
            ),
        )
    }

    /**
     * Tamamlama/geri alma.
     *
     * Tekrarlayan bir görev tamamlandığında görev "biter" ve yerine bir sonraki
     * tarihiyle yenisi doğar. Tamamlananı olduğu gibi bırakmak önemli: geçmiş
     * kaydı silinmesin, "ne zaman yapmıştım" sorusu cevaplanabilsin.
     */
    suspend fun setCompleted(uuid: String, completed: Boolean, todayEpochDay: Int) {
        val existing = taskDao.getByUuid(uuid) ?: return
        val timestamp = now.millis()
        val updated = existing.copy(
            completedAt = if (completed) timestamp else null,
            updatedAt = timestamp,
        )
        writeTask(updated)

        if (!completed) return
        val rule = existing.recurrenceRule() ?: return
        val nextDue = TaskRecurrence.nextDueDate(
            rule = rule,
            currentDue = existing.dueDate,
            completedOn = todayEpochDay,
        )
        writeTask(
            existing.copy(
                id = 0L,
                uuid = UUID.randomUUID().toString(),
                dueDate = nextDue,
                completedAt = null,
                position = taskDao.nextPosition(existing.listUuid),
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
    }

    suspend fun deleteTask(uuid: String) {
        val existing = taskDao.getByUuid(uuid) ?: return
        val timestamp = now.millis()
        val deleted = existing.copy(deletedAt = timestamp, updatedAt = timestamp)
        taskDao.upsert(deleted)
        changeRecorder.record(
            entityType = TASK_ENTITY,
            entityUuid = uuid,
            operation = ChangeOperation.DELETE,
            payload = json.encodeToString(TaskEntity.serializer(), deleted),
        )
    }

    /**
     * İçe aktarılan listeleri ve görevleri yazar.
     *
     * İki mükerrerlik koruması var:
     * - Aynı adlı liste varsa yenisi oluşturulmaz, mevcut listeye eklenir.
     * - Kaynaktaki kimliği (`externalId`) daha önce görülmüş görev atlanır.
     *   Binlerce görevlik bir liste sayfa sayfa aktarılırken sayfalar üst üste
     *   binebiliyor; bu koruma olmasa aynı görev birkaç kez eklenirdi.
     */
    suspend fun importTasks(result: ImportResult): ImportSummary {
        var imported = 0
        var skipped = 0

        result.lists.forEach { list ->
            val listUuid = taskDao.getListByName(list.name)?.uuid ?: createList(list.name)

            list.tasks.forEach { task ->
                val existing = task.externalId?.let { taskDao.getByExternalId(it) }
                if (existing != null) {
                    skipped++
                    return@forEach
                }

                val parentUuid = createTask(
                    listUuid = listUuid,
                    title = task.title,
                    notes = task.notes,
                    dueDate = task.dueDate,
                    priority = task.priority,
                    completed = task.completed,
                    externalId = task.externalId,
                )
                imported++

                task.subtasks.forEach { subtask ->
                    createTask(
                        listUuid = listUuid,
                        title = subtask.title,
                        parentUuid = parentUuid,
                        completed = subtask.completed,
                    )
                    imported++
                }
            }
        }
        return ImportSummary(imported = imported, skipped = skipped)
    }

    private suspend fun writeTask(entity: TaskEntity) {
        taskDao.upsert(entity)
        changeRecorder.record(
            entityType = TASK_ENTITY,
            entityUuid = entity.uuid,
            operation = ChangeOperation.UPSERT,
            payload = json.encodeToString(TaskEntity.serializer(), entity),
        )
    }

    private suspend fun writeList(
        entity: TaskListEntity,
        operation: String = ChangeOperation.UPSERT,
    ) {
        taskDao.upsertList(entity)
        changeRecorder.record(
            entityType = TASK_LIST_ENTITY,
            entityUuid = entity.uuid,
            operation = operation,
            payload = json.encodeToString(TaskListEntity.serializer(), entity),
        )
    }
}

private fun TaskEntity.recurrenceRule(): RecurrenceRule? {
    val unit = recurrenceUnit ?: return null
    val parsed = runCatching { RecurrenceUnit.valueOf(unit) }.getOrNull() ?: return null
    return RecurrenceRule(
        unit = parsed,
        interval = recurrenceInterval,
        daysMask = recurrenceDaysMask,
        fromCompletion = recurrenceFromCompletion,
    )
}

internal fun TaskEntity.toDomain(): Task = Task(
    id = id,
    uuid = uuid,
    listUuid = listUuid,
    title = title,
    notes = notes,
    dueDate = dueDate,
    dueMinuteOfDay = dueMinuteOfDay,
    completedAt = completedAt,
    priority = runCatching { TaskPriority.valueOf(priority) }.getOrDefault(TaskPriority.NONE),
    parentUuid = parentUuid,
    recurrence = recurrenceRule(),
    position = position,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun TaskListEntity.toDomain(): TaskList = TaskList(
    id = id,
    uuid = uuid,
    name = name,
    position = position,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
