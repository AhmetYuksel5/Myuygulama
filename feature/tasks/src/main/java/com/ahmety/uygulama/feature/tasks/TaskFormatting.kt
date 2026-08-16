package com.ahmety.uygulama.feature.tasks

import com.ahmety.uygulama.core.model.TaskPriority
import kotlinx.datetime.LocalDate

private val monthNames = listOf(
    "Oca", "Şub", "Mar", "Nis", "May", "Haz",
    "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara",
)

/**
 * Tarihi kullanıcının okuduğu gibi yazar: yakın günler isimle ("Bugün", "Yarın"),
 * uzaklar "17 Ağu" biçiminde. Geçmiş tarihler "3 gün gecikti" olarak görünür,
 * çünkü asıl bilgi tarihin kendisi değil, ne kadar geciktiğidir.
 */
internal fun formatDueDate(dueDate: Int, today: Int): String = when (val delta = dueDate - today) {
    0 -> "Bugün"
    1 -> "Yarın"
    -1 -> "Dün gecikti"
    else -> if (delta < 0) {
        "${-delta} gün gecikti"
    } else {
        val date = LocalDate.fromEpochDays(dueDate)
        val monthLabel = monthNames.getOrElse(date.monthNumber - 1) { "" }
        if (delta < 300) "${date.dayOfMonth} $monthLabel" else "${date.dayOfMonth} $monthLabel ${date.year}"
    }
}

internal fun priorityLabel(priority: TaskPriority): String? = when (priority) {
    TaskPriority.HIGH -> "Yüksek"
    TaskPriority.NORMAL -> null
    TaskPriority.LOW -> "Düşük"
    TaskPriority.NONE -> null
}
