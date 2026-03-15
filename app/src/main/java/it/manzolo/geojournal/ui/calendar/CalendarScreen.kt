package it.manzolo.geojournal.ui.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import it.manzolo.geojournal.domain.model.Reminder
import it.manzolo.geojournal.domain.model.ReminderType
import it.manzolo.geojournal.domain.model.VisitLogEntry
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(navController: NavController) {
    val viewModel: CalendarViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Calendario") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Month navigation
            MonthHeader(
                month = state.currentMonth,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth
            )

            // Day-of-week headers
            DayOfWeekHeader()

            // Calendar grid
            MonthGrid(
                month = state.currentMonth,
                reminderDays = state.reminderDays,
                visitDays = state.visitDays,
                selectedDay = state.selectedDay,
                onDayClick = { day ->
                    viewModel.selectDay(if (state.selectedDay == day) null else day)
                }
            )

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            // Day detail panel
            AnimatedVisibility(
                visible = state.selectedDay != null,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                DayDetailPanel(
                    day = state.selectedDay,
                    reminders = state.remindersForDay,
                    visits = state.visitsForDay,
                    pointTitles = state.pointTitles
                )
            }

            if (state.selectedDay == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📅", style = MaterialTheme.typography.displaySmall)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Seleziona un giorno per vedere\npromemoria e visite",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ITALIAN)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mese precedente")
        }
        Text(
            text = month.format(formatter).replaceFirstChar { it.uppercaseChar() },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mese successivo")
        }
    }
}

@Composable
private fun DayOfWeekHeader() {
    val days = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        days.forEach { day ->
            Text(
                text = day,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    reminderDays: Set<LocalDate>,
    visitDays: Set<LocalDate>,
    selectedDay: LocalDate?,
    onDayClick: (LocalDate) -> Unit
) {
    val firstDayOfMonth = month.atDay(1)
    // Monday=1, so offset = (dayOfWeek.value - 1) where Monday=1
    val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value // Mon=1, Sun=7
    val offset = firstDayOfWeek - 1 // cells before the 1st
    val daysInMonth = month.lengthOfMonth()
    val totalCells = offset + daysInMonth
    val rows = (totalCells + 6) / 7

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        repeat(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val cellIndex = row * 7 + col
                    val dayNumber = cellIndex - offset + 1
                    if (dayNumber < 1 || dayNumber > daysInMonth) {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = month.atDay(dayNumber)
                        DayCell(
                            day = dayNumber,
                            date = date,
                            isSelected = date == selectedDay,
                            isToday = date == LocalDate.now(),
                            hasReminder = date in reminderDays,
                            hasVisit = date in visitDays,
                            modifier = Modifier.weight(1f),
                            onClick = { onDayClick(date) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    hasReminder: Boolean,
    hasVisit: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (hasReminder) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                    )
                }
                if (hasVisit) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.tertiary)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayDetailPanel(
    day: LocalDate?,
    reminders: List<Reminder>,
    visits: List<VisitLogEntry>,
    pointTitles: Map<String, String>
) {
    val formatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)
    val timeFormat = SimpleDateFormat("HH:mm", Locale.ITALIAN)

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        item {
            Text(
                text = day?.format(formatter)?.replaceFirstChar { it.uppercaseChar() } ?: "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (reminders.isEmpty() && visits.isEmpty()) {
            item {
                Text(
                    "Nessun evento per questo giorno",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (reminders.isNotEmpty()) {
            item {
                Text(
                    "🔔 Promemoria",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(reminders) { reminder ->
                ReminderRow(reminder)
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (visits.isNotEmpty()) {
            item {
                Text(
                    "📍 Visite",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(visits) { visit ->
                VisitRow(visit, pointTitles[visit.geoPointId], timeFormat)
            }
        }
    }
}

@Composable
private fun ReminderRow(reminder: Reminder) {
    val dateFormat = SimpleDateFormat("d MMM", Locale.ITALIAN)
    val startStr = dateFormat.format(Date(reminder.startDate))
    val dateStr = when (reminder.type) {
        ReminderType.DATE_RANGE -> reminder.endDate?.let { "$startStr → ${dateFormat.format(Date(it))}" } ?: startStr
        ReminderType.ANNUAL_RECURRING -> "$startStr · ogni anno"
        ReminderType.SINGLE -> startStr
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(reminder.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VisitRow(visit: VisitLogEntry, pointTitle: String?, timeFormat: SimpleDateFormat) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                pointTitle ?: "Luogo sconosciuto",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            val time = timeFormat.format(Date(visit.visitedAt))
            val subtitle = if (visit.note.isNotBlank()) "$time · ${visit.note}" else time
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
