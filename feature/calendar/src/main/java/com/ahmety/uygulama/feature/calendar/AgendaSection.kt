package com.ahmety.uygulama.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** "Bugün" ekranındaki ajanda bölümü. */
@Composable
fun AgendaSection(
    state: AgendaUiState,
    modifier: Modifier = Modifier,
) {
    if (!state.loaded) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Ajanda",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (state.hasPermission && state.events.isNotEmpty()) {
                Text(
                    text = "${state.events.size} etkinlik",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when {
            !state.hasPermission -> Text(
                text = "Takvim izni verilmedi. Ayarlar → İzinler bölümünden " +
                    "açtığında Google takvimin buraya gelir.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            state.events.isEmpty() -> Text(
                text = "Bugün için etkinlik yok.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            else -> state.events.forEach { event -> EventRow(event) }
        }
    }
}

@Composable
private fun EventRow(event: CalendarEvent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .width(4.dp)
                .height(36.dp)
                .background(Color(event.colorArgb), RoundedCornerShape(2.dp)),
        ) {}

        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(text = event.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = eventTimeLabel(event),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun eventTimeLabel(event: CalendarEvent): String {
    if (event.allDay) return "Tüm gün"
    val zone = TimeZone.currentSystemDefault()
    val start = Instant.fromEpochMilliseconds(event.startMillis).toLocalDateTime(zone)
    val end = Instant.fromEpochMilliseconds(event.endMillis).toLocalDateTime(zone)
    val startLabel = "%02d:%02d".format(start.hour, start.minute)
    val endLabel = "%02d:%02d".format(end.hour, end.minute)
    val place = event.location?.let { " · $it" }.orEmpty()
    return "$startLabel – $endLabel$place"
}
