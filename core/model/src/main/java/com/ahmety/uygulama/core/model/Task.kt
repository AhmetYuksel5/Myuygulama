package com.ahmety.uygulama.core.model

data class TaskList(
    val id: Long = 0L,
    val uuid: String,
    val name: String,
    val position: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

enum class TaskPriority { NONE, LOW, NORMAL, HIGH }

data class Task(
    val id: Long = 0L,
    val uuid: String,
    val listUuid: String,
    val title: String,
    val notes: String = "",
    /** Epoch gün sayısı; null ise tarihsiz. */
    val dueDate: Int? = null,
    /** Gün içindeki dakika; null ise "gün boyu". */
    val dueMinuteOfDay: Int? = null,
    val completedAt: Long? = null,
    val priority: TaskPriority = TaskPriority.NONE,
    /** Alt görevse üst görevin uuid'si. */
    val parentUuid: String? = null,
    val recurrence: RecurrenceRule? = null,
    val position: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
) {
    val isCompleted: Boolean get() = completedAt != null
}

/**
 * Tekrar kuralı.
 *
 * [fromCompletion] ayrımı önemli: "her 3 günde bir" dediğinde bu, planlanan
 * tarihten mi yoksa gerçekten yaptığın günden mi sayılacak? Spor için ikincisi,
 * fatura ödemesi için birincisi doğrudur.
 */
data class RecurrenceRule(
    val unit: RecurrenceUnit,
    val interval: Int = 1,
    /** WEEKLY için bit maskesi: bit 0 = Pazartesi … bit 6 = Pazar. 0 ise özgün gün korunur. */
    val daysMask: Int = 0,
    val fromCompletion: Boolean = false,
)

enum class RecurrenceUnit { DAY, WEEK, MONTH, YEAR }
