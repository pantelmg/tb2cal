package com.pantelmg.tb2cal.ui

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import com.pantelmg.tb2cal.plan.Plans
import com.pantelmg.tb2cal.model.PlanItem
import com.pantelmg.tb2cal.model.Plan
import com.pantelmg.tb2cal.model.ItemKind
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.text.style.TextDecoration
import com.pantelmg.tb2cal.R
import androidx.compose.ui.res.painterResource
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
    val planItems by Repo.items.collectAsState()
    val tick by Reliability.resumeTick.collectAsState()
    // Adding, editing and deleting Tasks/Events (sheets and dialogs).
    val planUi = remember { PlanUi() }
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
    LaunchedEffect(
        gridStart, history, planItems, settings.taskColor, settings.eventColor, settings.gmailView,
        settings.gmailAccount, settings.holidayOff, settings.allowHide, settings.hiddenEvents, settings.hiddenInstances, tick
    ) {
        byDay = withContext(Dispatchers.IO) {
            runCatching { CalendarData.byDay(ctx, gridStart, gridEnd) }.getOrDefault(emptyMap())
        }
    }

    // TB Total on: Same-name TimeBlocks of a day are shown merged (grid and list).
    val shownByDay = remember(byDay, settings.tbTotal) { CalendarData.shown(byDay, settings.tbTotal) }

    val calPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (CalendarWriter.hasPermission(ctx)) Repo.updateSettings {
            it.copy(gmailView = true, hiddenEvents = emptySet(), hiddenInstances = emptySet())
        }
    }
    var toHide by remember { mutableStateOf<DayItem?>(null) }
    // Repeating event: After the first prompt, all repeats or this day only.
    var hideScope by remember { mutableStateOf<DayItem?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // ---- Title + Import from your Gmail ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Calendar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("Import from your Gmail", style = MaterialTheme.typography.labelLarge)
                Text(
                    if (settings.gmailView) "Shown here, read-only" else "OFF",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = settings.gmailView,
                onCheckedChange = { on ->
                    if (!on) Repo.updateSettings { it.copy(gmailView = false) }
                    // Turning Sync on again brings back the Gmail events that were hidden.
                    else if (CalendarWriter.hasPermission(ctx)) Repo.updateSettings {
                        it.copy(gmailView = true, hiddenEvents = emptySet(), hiddenInstances = emptySet())
                    }
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
        // Entries per day (Settings): 3 or 4 fit the usual grid; 5 gets a taller grid (and a shorter day list).
        val perDay = settings.dayEntries.coerceIn(3, 5)
        val gridShare = if (perDay == 5) 0.76f else 0.66f
        BoxWithConstraints(Modifier.weight(gridShare).fillMaxWidth()) {
        val density = LocalDensity.current
        val cellH = maxHeight / 6
        val headH = (cellH * (if (perDay == 3) 0.3f else 0.25f)).coerceIn(12.dp, 20.dp)
        val chipH = ((cellH - headH - 2.dp) / perDay).coerceIn(7.dp, 16.dp)
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
                                if (items.size > perDay) {
                                    Text(
                                        "+${items.size - perDay}",
                                        fontSize = chipFont,
                                        lineHeight = chipFont,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 1.dp)
                                    )
                                }
                            }
                            items.take(perDay).forEach { item ->
                                val c = Color(item.color).copy(alpha = if (inMonth) 1f else 0.5f)
                                val outlined = item.plan != null
                                Text(
                                    item.label,
                                    fontSize = chipFont,
                                    lineHeight = chipLine,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (outlined) c else Color(GoogleColors.textOn(item.color)),
                                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 1.dp)
                                        .height(chipH - 1.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .then(
                                            // Tasks and Events: Transparent with an outline in their color.
                                            if (outlined) Modifier.border(1.dp, c, RoundedCornerShape(3.dp))
                                            else Modifier.background(c)
                                        )
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
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.weight(1f)
            )
            val boxShape = RoundedCornerShape(10.dp)
            // +Task/Event: Same style as TB Total, fixed size, just left of it.
            Box(
                Modifier
                    .clip(boxShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, boxShape)
                    .clickable { planUi.add() }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("+Task/Event", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            // TB Total ON/OFF: Same size either way (sized for the longer "OFF" text); Only the colors change.
            val on = settings.tbTotal
            val tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                Modifier
                    .clip(boxShape)
                    .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, boxShape)
                    .clickable { Repo.updateSettings { it.copy(tbTotal = !it.tbTotal) } }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("TB Total OFF", style = MaterialTheme.typography.titleSmall, modifier = Modifier.alpha(0f))
                Text(if (on) "TB Total ON" else "TB Total OFF", style = MaterialTheme.typography.titleSmall, color = tint)
            }
        }
        // Only this list scrolls (and only if it doesn't fit).
        // Today's list starts with the open tasks that have no date.
        val undatedOpen = if (selected == today.toEpochDay()) Plan.openUndated(planItems) else emptyList()
        LazyColumn(Modifier.weight(1f - gridShare).fillMaxWidth()) {
            items(undatedOpen, key = { "u" + it.id }) { t ->
                PlanRow(t, null, done = false, color = Plans.color(ItemKind.TASK), ui = planUi)
            }
            if (dayItems.isEmpty() && undatedOpen.isEmpty()) {
                item {
                    Text(
                        if (day.isBefore(gridStart) || !day.isBefore(gridEnd)) "Go to this day's month to see it."
                        else "Nothing on this day.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(dayItems) { item ->
                val plan = item.plan
                if (plan != null) {
                    PlanRow(plan, item.planDay, item.done, item.color, planUi)
                    return@items
                }
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
                    // TimeBlocks can be deleted; Gmail events are read-only, but can be hidden (Settings).
                    if (item.isTimeBlock) {
                        IconButton(onClick = { toDelete = item }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete ${item.label}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (settings.allowHide && item.eventId != null) {
                        IconButton(onClick = { toHide = item }) {
                            Icon(painterResource(R.drawable.ic_s_hide), contentDescription = "Hide ${item.label}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    PlanDialogs(planUi, defaultDay = selected)

    toHide?.let { item ->
        AlertDialog(
            onDismissRequest = { toHide = null },
            title = { Text("Hide ${item.label}?") },
            text = {
                Text(
                    "It's hidden here and on the Calendar widget. Your Google account isn't changed. Hidden events " +
                        "come back when you turn Import from your Gmail off and on, or turn off Allow hiding in Settings."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = item.eventId
                    toHide = null
                    if (item.repeats) hideScope = item
                    else if (id != null) Repo.updateSettings { it.copy(hiddenEvents = it.hiddenEvents + id) }
                }) { Text("Hide") }
            },
            dismissButton = { TextButton(onClick = { toHide = null }) { Text("Cancel") } }
        )
    }

    hideScope?.let { item ->
        val id = item.eventId
        AlertDialog(
            onDismissRequest = { hideScope = null },
            title = { Text("${item.label} repeats") },
            text = { Text("Hide all its repeating events, or on this day only?") },
            confirmButton = {
                TextButton(onClick = {
                    hideScope = null
                    if (id != null) Repo.updateSettings { it.copy(hiddenEvents = it.hiddenEvents + id) }
                }) { Text("All repeating events") }
            },
            dismissButton = {
                TextButton(onClick = {
                    hideScope = null
                    if (id != null) Repo.updateSettings { it.copy(hiddenInstances = it.hiddenInstances + "$id@${item.startMs}") }
                }) { Text("On this day only") }
            }
        )
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

/**
 * A Task or Event in the day list: Tasks have an outlined square to tick (crossed out when done), Events a solid
 * square of the same size.
 * Tap to edit, bin to delete (repeating ones ask This day only / All repeats).
 */
@Composable
private fun PlanRow(item: PlanItem, day: Long?, done: Boolean, color: Int, ui: PlanUi) {
    val ctx = LocalContext.current
    val c = Color(color)
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        // Same-size squares: Tasks outlined (tap to tick or untick), Events solid.
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .then(if (item.kind == ItemKind.TASK) Modifier.clickable { Plans.setDone(ctx, item, day, !done) } else Modifier),
            contentAlignment = Alignment.Center
        ) { PlanMarker(item.kind, done, c) }
        Spacer(Modifier.width(6.dp))
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .clickable { ui.edit(item, day) }
                .padding(vertical = 2.dp)
        ) {
            Text(
                item.title, style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            Text(
                listOfNotNull(
                    Plan.timeText(item),
                    item.repeat?.describe(),
                    if (item.undated) (if (done) "Done" else "No date") else null,
                    item.kind.label,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { ui.delete(item, day) }) {
            Icon(Icons.Default.Delete, contentDescription = "Delete ${item.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
