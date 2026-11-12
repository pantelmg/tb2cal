package com.pantelmg.tb2cal.ui

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pantelmg.tb2cal.model.GoogleColors
import com.pantelmg.tb2cal.calendar.CalendarData
import com.pantelmg.tb2cal.calendar.CalendarWriter
import com.pantelmg.tb2cal.calendar.DayItem
import com.pantelmg.tb2cal.data.Repo
import com.pantelmg.tb2cal.reliability.Reliability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale

/** A day to open in the Calendar tab (set by the Calendar widget). */
object CalendarNav {
    val day = MutableStateFlow<Long?>(null)
}

/**
 * Calendar tab, on one page: Month grid on top, the chosen day's list below.
 * Only that list scrolls, and only when it doesn't fit.
 */
@Composable
fun CalendarScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by Repo.settings.collectAsState()
    val history by Repo.history.collectAsState()
    val tick by Reliability.resumeTick.collectAsState()
    val locale = Locale.getDefault()
    val today = LocalDate.now()

    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var toDelete by remember { mutableStateOf<DayItem?>(null) }
    val requested by CalendarNav.day.collectAsState()
    LaunchedEffect(requested) {
        requested?.let { d ->
            val day = LocalDate.ofEpochDay(d)
            selected = d
            monthOffset = (day.year - today.year) * 12 + (day.monthValue - today.monthValue)
            CalendarNav.day.value = null
        }
    }

    val month = today.withDayOfMonth(1).plusMonths(monthOffset.toLong())
    val firstDow = WeekFields.of(locale).firstDayOfWeek
    var gridStart = month
    while (gridStart.dayOfWeek != firstDow) gridStart = gridStart.minusDays(1)
    val gridEnd = gridStart.plusDays(42)

    var byDay by remember { mutableStateOf<Map<LocalDate, List<DayItem>>>(emptyMap()) }
    LaunchedEffect(gridStart, history, settings.gmailView, tick) {
        byDay = withContext(Dispatchers.IO) {
            runCatching { CalendarData.byDay(ctx, gridStart, gridEnd) }.getOrDefault(emptyMap())
        }
    }

    // TB Total on: Same-name TimeBlocks of a day are shown merged (grid and list).
    val shownByDay = remember(byDay, settings.tbTotal) { CalendarData.shown(byDay, settings.tbTotal) }

    val calPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (CalendarWriter.hasPermission(ctx)) Repo.updateSettings { it.copy(gmailView = true) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // ---- Title + Sync with your Gmail ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Calendar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("Sync with your Gmail", style = MaterialTheme.typography.labelLarge)
                Text(
                    if (settings.gmailView) "Shown here, read-only" else "Off",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = settings.gmailView,
                onCheckedChange = { on ->
                    if (!on) Repo.updateSettings { it.copy(gmailView = false) }
                    else if (CalendarWriter.hasPermission(ctx)) Repo.updateSettings { it.copy(gmailView = true) }
                    else calPerm.launch(CalendarWriter.PERMS)
                }
            )
        }

        // ---- Month header ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                month.format(DateTimeFormatter.ofPattern("LLLL", locale)).replaceFirstChar { it.uppercase() } +
                    if (month.year != today.year) " ${month.year}" else "",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (monthOffset != 0) {
                TextButton(onClick = {
                    monthOffset = 0
                    selected = today.toEpochDay()
                }) { Text("Today") }
            }
            IconButton(onClick = { monthOffset-- }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            IconButton(onClick = { monthOffset++ }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
            }
        }

        // ---- Weekday letters ----
        Row(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
            for (i in 0 until 7) {
                Text(
                    firstDow.plus(i.toLong()).getDisplayName(TextStyle.NARROW_STANDALONE, locale),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---- 6 × 7 grid: Fills the top part of the page ----
        // Each cell: Day number (with +N at the right when more) on top, then up to 3 TimeBlocks/events.
        // Sizes come from the cell height (not the phone's font size), so all of it always fits.
        val line = MaterialTheme.colorScheme.outlineVariant
        BoxWithConstraints(Modifier.weight(0.66f).fillMaxWidth()) {
        val density = LocalDensity.current
        val cellH = maxHeight / 6
        val headH = (cellH * 0.3f).coerceIn(12.dp, 20.dp)
        val chipH = ((cellH - headH - 2.dp) / 3).coerceIn(7.dp, 16.dp)
        val chipLine = with(density) { (chipH - 1.dp).toSp() }
        val chipFont = with(density) { ((chipH - 1.dp) * 0.82f).toSp() }
        val headFont = with(density) { (headH * 0.62f).toSp() }
        Column(Modifier.fillMaxSize()) {
            for (w in 0 until 6) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (d in 0 until 7) {
                        val date = gridStart.plusDays((w * 7 + d).toLong())
                        val inMonth = date.month == month.month
                        val items = shownByDay[date].orEmpty()
                        val isSelected = date.toEpochDay() == selected
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .border(0.5.dp, line)
                                .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .clickable { selected = date.toEpochDay() }
                                .padding(1.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(Modifier.fillMaxWidth().height(headH)) {
                                Box(
                                    Modifier
                                        .align(Alignment.Center)
                                        .size(headH)
                                        .clip(CircleShape)
                                        .background(if (date == today) MaterialTheme.colorScheme.primary else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        date.dayOfMonth.toString(),
                                        fontSize = headFont,
                                        lineHeight = headFont,
                                        maxLines = 1,
                                        color = when {
                                            date == today -> MaterialTheme.colorScheme.onPrimary
                                            inMonth -> MaterialTheme.colorScheme.onSurface
                                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        }
                                    )
                                }
                                if (items.size > 3) {
                                    Text(
                                        "+${items.size - 3}",
                                        fontSize = chipFont,
                                        lineHeight = chipFont,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 1.dp)
                                    )
                                }
                            }
                            items.take(3).forEach { item ->
                                Text(
                                    item.label,
                                    fontSize = chipFont,
                                    lineHeight = chipLine,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color(GoogleColors.textOn(item.color)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 1.dp)
                                        .height(chipH - 1.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Color(item.color).copy(alpha = if (inMonth) 1f else 0.5f))
                                        .padding(horizontal = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        }

        // ---- Chosen day ----
        val day = LocalDate.ofEpochDay(selected)
        val dayItems = shownByDay[day].orEmpty()
        val tf = DateFormat.getTimeFormat(ctx)
        // Short date (e.g. Mon 15/12/26) and, on the other side, the TB Total switch (crossed out when off).
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                day.format(DateTimeFormatter.ofPattern("EEE dd/MM/yy", Locale.ENGLISH)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            // TB Total ON/OFF: Same size either way (sized for the longer "OFF" text); Only the colors change.
            val on = settings.tbTotal
            val boxShape = RoundedCornerShape(10.dp)
            val tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                Modifier
                    .clip(boxShape)
                    .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, boxShape)
                    .clickable { Repo.updateSettings { it.copy(tbTotal = !it.tbTotal) } }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("TB Total OFF", style = MaterialTheme.typography.titleSmall, modifier = Modifier.alpha(0f))
                Text(if (on) "TB Total ON" else "TB Total OFF", style = MaterialTheme.typography.titleSmall, color = tint)
            }
        }
        // Only this list scrolls (and only if it doesn't fit).
        LazyColumn(Modifier.weight(0.34f).fillMaxWidth()) {
            if (dayItems.isEmpty()) {
                item {
                    Text(
                        if (day.isBefore(gridStart) || !day.isBefore(gridEnd)) "Go to this day's month to see it."
                        else "Nothing on this day.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(dayItems) { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(Color(item.color)))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (settings.tbTotal && item.isTimeBlock) "TimeBlock Total"
                            else buildString {
                                append(if (item.allDay) "All day" else "${tf.format(Date(item.startMs))} – ${tf.format(Date(item.endMs))}")
                                append(if (item.isTimeBlock) " · TimeBlock" else " · Gmail")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // TimeBlocks can be deleted; Gmail events are read-only.
                    if (item.isTimeBlock) {
                        IconButton(onClick = { toDelete = item }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete ${item.label}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { item ->
        // A TB Total entry deletes every TimeBlock of that name on that day.
        val parts = item.parts.ifEmpty { listOf(item) }
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete ${item.label}?") },
            text = {
                Text(
                    (if (parts.size > 1) "All ${parts.size} ${item.blockName ?: ""} TimeBlocks of this day will be removed"
                    else "This TimeBlock will be removed") +
                        " from the app (Calendar and Statistics) and from TB2Cal's calendar on your phone. " +
                        "This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    parts.forEach { p ->
                        val name = p.blockName ?: return@forEach
                        Repo.removeHistoryEntry(name, p.startMs)
                    }
                    scope.launch(Dispatchers.IO) {
                        parts.forEach { p -> CalendarWriter.deleteFromLocal(ctx, p.label, p.startMs) }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }
}
