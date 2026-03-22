package it.manzolo.geojournal.ui.calendar

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.compose.ui.res.stringResource
import it.manzolo.geojournal.R
import it.manzolo.geojournal.domain.model.Reminder
import it.manzolo.geojournal.domain.model.ReminderType
import it.manzolo.geojournal.domain.model.VisitLogEntry
import it.manzolo.geojournal.ui.navigation.Routes
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

// Elemento unificato per la vista mensile
private sealed class MonthEventItem {
    data class DateHeader(val date: LocalDate) : MonthEventItem()
    data class ReminderItem(val reminder: Reminder) : MonthEventItem()
    data class VisitItem(val visit: VisitLogEntry) : MonthEventItem()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(navController: NavController) {
    val viewModel: CalendarViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.calendar_title)) }) }
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
                onNext = viewModel::nextMonth,
                onToday = viewModel::goToToday
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

            val onNavigateToPoint: (String) -> Unit = { pointId ->
                navController.navigate(Routes.PointDetail.createRoute(pointId))
            }

            if (state.selectedDay != null) {
                // Vista giorno: solo gli eventi del giorno selezionato
                DayDetailPanel(
                    day = state.selectedDay,
                    reminders = state.remindersForDay,
                    visits = state.visitsForDay,
                    pointTitles = state.pointTitles,
                    onNavigateToPoint = onNavigateToPoint
                )
            } else {
                // Vista mese: tutti gli eventi del mese corrente
                MonthEventsPanel(
                    reminders = state.remindersForMonth,
                    visits = state.visitsForMonth,
                    pointTitles = state.pointTitles,
                    onNavigateToPoint = onNavigateToPoint
                )
            }
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    val isCurrentMonth = month == YearMonth.now()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.calendar_prev_month))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = month.format(formatter).replaceFirstChar { it.uppercaseChar() },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (!isCurrentMonth) {
                TextButton(
                    onClick = onToday,
                    modifier = Modifier.height(24.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text(
                        stringResource(R.string.calendar_today),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.calendar_next_month))
        }
    }
}

@Composable
private fun DayOfWeekHeader() {
    val days = DayOfWeek.values().map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
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
    val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value // Mon=1, Sun=7
    val offset = firstDayOfWeek - 1
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
    val isTodayColor = MaterialTheme.colorScheme.primaryContainer

    val backgroundModifier = if (isSelected) {
        Modifier.background(
            androidx.compose.ui.graphics.Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.tertiary
                )
            )
        )
    } else if (isToday) {
        Modifier.background(isTodayColor)
    } else {
        Modifier.background(Color.Transparent)
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
            .then(backgroundModifier)
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
private fun MonthEventsPanel(
    reminders: List<Pair<LocalDate, Reminder>>,
    visits: List<VisitLogEntry>,
    pointTitles: Map<String, String>,
    onNavigateToPoint: (String) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // Costruisci lista unificata ordinata per data con header di data
    val items = remember(reminders, visits) {
        val events = buildList {
            reminders.forEach { (date, reminder) ->
                add(date to MonthEventItem.ReminderItem(reminder))
            }
            visits.forEach { visit ->
                val date = Instant.ofEpochMilli(visit.visitedAt).atZone(zone).toLocalDate()
                add(date to MonthEventItem.VisitItem(visit))
            }
        }.sortedWith(compareBy({ it.first }, { if (it.second is MonthEventItem.VisitItem) (it.second as MonthEventItem.VisitItem).visit.visitedAt else 0L }))

        buildList {
            var lastDate: LocalDate? = null
            events.forEach { (date, item) ->
                if (date != lastDate) {
                    add(MonthEventItem.DateHeader(date))
                    lastDate = date
                }
                add(item)
            }
        }
    }

    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📅", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.calendar_no_events_month),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(items) { item ->
                when (item) {
                    is MonthEventItem.DateHeader -> {
                        Text(
                            text = item.date.format(dateFormatter).replaceFirstChar { it.uppercaseChar() },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    is MonthEventItem.ReminderItem -> {
                        ReminderRow(item.reminder, onClick = { onNavigateToPoint(item.reminder.geoPointId) })
                    }
                    is MonthEventItem.VisitItem -> {
                        VisitRow(item.visit, pointTitles[item.visit.geoPointId], timeFormat) {
                            onNavigateToPoint(item.visit.geoPointId)
                        }
                    }
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
    pointTitles: Map<String, String>,
    onNavigateToPoint: (String) -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

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
                    stringResource(R.string.calendar_no_events),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (reminders.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.calendar_section_reminders),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(reminders) { reminder ->
                ReminderRow(reminder, onClick = { onNavigateToPoint(reminder.geoPointId) })
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (visits.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.calendar_section_visits),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(visits) { visit ->
                VisitRow(visit, pointTitles[visit.geoPointId], timeFormat) {
                    onNavigateToPoint(visit.geoPointId)
                }
            }
        }
    }
}

@Composable
private fun ReminderRow(reminder: Reminder, onClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())
    val startStr = dateFormat.format(Date(reminder.startDate))
    val yearlyLabel = stringResource(R.string.calendar_reminder_yearly)
    val dateStr = when (reminder.type) {
        ReminderType.DATE_RANGE -> reminder.endDate?.let { "$startStr → ${dateFormat.format(Date(it))}" } ?: startStr
        ReminderType.ANNUAL_RECURRING -> "$startStr · $yearlyLabel"
        ReminderType.SINGLE -> startStr
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            .clickable(onClick = onClick)
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(reminder.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VisitRow(visit: VisitLogEntry, pointTitle: String?, timeFormat: SimpleDateFormat, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))
            .clickable(onClick = onClick)
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.tertiary)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                pointTitle ?: stringResource(R.string.calendar_unknown_place),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            val time = timeFormat.format(Date(visit.visitedAt))
            val subtitle = if (visit.note.isNotBlank()) "$time · ${visit.note}" else time
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
