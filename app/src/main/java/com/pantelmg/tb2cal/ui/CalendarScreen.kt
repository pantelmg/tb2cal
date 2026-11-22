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
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.rememberUpdatedState
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
import kotlinx.coroutines.ensureActive
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
    /** An entry to show (widget tap): Its day and entry key; Scrolled to the top of that day's list, pulsing. */
    val focus = MutableStateFlow<Pair<Long, String>?>(null)
}

/**
 * Which entry pulses: Each [fire] is played once ([played]), so an entry scrolled out and back in, or recreated,
 * doesn't pulse again.
 */
class Pulse {
    var key by mutableStateOf<String?>(null)
    var n by mutableIntStateOf(0)
    var played = 0
    fun fire(k: String) {
        key = k
        n++
    }
}

/** Pulses its content's background once when [pulse] fires for [key] (Calendar day list, Edit TimeBlocks). */
@Composable
internal fun PulseBox(key: String, color: Color, pulse: Pulse, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val a = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(pulse.n) {
        if (pulse.n > pulse.played && pulse.key == key) {
            a.animateTo(1f, androidx.compose.animation.core.tween(220))
            a.animateTo(0f, androidx.compose.animation.core.tween(520))
            pulse.played = pulse.n // Only once it has been seen in full.
        } else if (a.value != 0f) {
            a.snapTo(0f) // Another entry's pulse cut this one off: No tint left behind.
        }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.28f * a.value))
    ) { content() }
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
    // Day-list entries pulse when touched (reordering on) or opened from the widget.
    val pulse = remember { Pulse() }
    val locale = Locale.getDefault()
    val today = LocalDate.now()

    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var toDelete by remember { mutableStateOf<DayItem?>(null) }
    // The reset arrow asks first.
    var askReset by remember { mutableStateOf(false) }
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
    val gridStart = CalendarData.gridStart(month, locale)
    val gridEnd = gridStart.plusDays(42)

    // Shown at once: TB2Cal's own TimeBlocks, Tasks and Events (from memory, in the first frame), plus the Gmail
    // events read before for this month (kept in memory). The Gmail events are read again in the background, and
    // the months before and after are read ahead for the arrows.
    val own = remember(gridStart, history, planItems, settings.taskColor, settings.eventColor) {
        CalendarData.ownByDay(history, planItems, gridStart, gridEnd)
    }
    val gmailKey = CalendarData.gmailKey(gridStart, gridEnd, settings)
    var gmail by remember(gmailKey) { mutableStateOf(CalendarData.cachedGmail(gridStart, gridEnd, settings)) }
    LaunchedEffect(gmailKey, settings.gmailView, tick) {
        if (!settings.gmailView) return@LaunchedEffect
        val st = settings
        withContext(Dispatchers.IO) { runCatching { CalendarData.fetchGmail(ctx, gridStart, gridEnd, st) }.getOrNull() }
            ?.let { if (it != gmail) gmail = it } // Unchanged (the usual case): Nothing is redrawn.
        withContext(Dispatchers.IO) {
            listOf(-1L, 1L).forEach { m ->
                val g = CalendarData.gridStart(month.plusMonths(m), locale)
                if (CalendarData.cachedGmail(g, g.plusDays(42), st) == null) runCatching { CalendarData.fetchGmail(ctx, g, g.plusDays(42), st) }
            }
        }
    }
    val byDay = remember(own, gmail, settings.gmailView, settings.allowHide, settings.hiddenEvents, settings.hiddenInstances) {
        CalendarData.withGmail(own, gmail, gridStart, gridEnd, settings)
    }

    // TB Total on: Same-name TimeBlocks of a day are shown merged (grid and list).
    // TB Total, then any reordered days (Settings: Allow reordering), in the grid and the day list.
    val orders by Repo.orders.collectAsState()
    val normalByDay = remember(byDay, settings.tbTotal) { CalendarData.shown(byDay, settings.tbTotal) }
    val shownByDay = remember(normalByDay, orders, settings.allowReorder) {
        CalendarData.ordered(normalByDay, orders, settings.allowReorder)
    }

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

        // ---- Month header (calendar shown) / Day header (calendar hidden) ----
        // The eye hides the month grid so the day list takes the page; Then ‹ › change the day, and the date takes
        // the month's place. Remembered (Settings).
        val hidden = settings.calendarHidden
        fun showDay(d: Long) {
            selected = d
            val ld = LocalDate.ofEpochDay(d)
            monthOffset = (ld.year - today.year) * 12 + (ld.monthValue - today.monthValue)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                // Calendar hidden: The day with its weekday (e.g. Sun 15/11/26), since there's no grid to show it.
                if (hidden) LocalDate.ofEpochDay(selected).format(DateTimeFormatter.ofPattern("EEE dd/MM/yy", Locale.ENGLISH))
                else month.format(DateTimeFormatter.ofPattern("LLLL", locale)).replaceFirstChar { it.uppercase() } +
                    if (month.year != today.year) " ${month.year}" else "",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (if (hidden) selected != today.toEpochDay() else monthOffset != 0) {
                TextButton(onClick = {
                    monthOffset = 0
                    selected = today.toEpochDay()
                }) { Text("Today") }
            }
            IconButton(onClick = { if (hidden) showDay(selected - 1) else monthOffset-- }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = if (hidden) "Previous day" else "Previous month")
            }
            IconButton(onClick = { if (hidden) showDay(selected + 1) else monthOffset++ }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = if (hidden) "Next day" else "Next month")
            }
            IconButton(onClick = {
                // Hiding: The month only changes if the chosen day isn't in this grid (no reloading, no flash).
                if (!hidden && (selected < gridStart.toEpochDay() || selected >= gridEnd.toEpochDay())) showDay(selected)
                Repo.updateSettings { it.copy(calendarHidden = !it.calendarHidden) }
            }) {
                Icon(
                    painterResource(if (hidden) R.drawable.ic_s_show else R.drawable.ic_s_hide),
                    contentDescription = if (hidden) "Show the calendar" else "Hide the calendar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Entries per day (Settings): 3 or 4 fit the usual grid; 5 gets a taller grid (and a shorter day list).
        val perDay = settings.dayEntries.coerceIn(3, 5)
        val gridShare = if (perDay == 5) 0.76f else 0.66f
        if (!hidden) {
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

        } // (calendar shown)

        // ---- Chosen day ----
        val day = LocalDate.ofEpochDay(selected)
        // The day's entries in their normal order (the list applies any reordering itself, below).
        val dayItems = normalByDay[day].orEmpty()
        val tf = DateFormat.getTimeFormat(ctx)
        // Only this list scrolls (and only if it doesn't fit). It starts with the open tasks that have no date (from the
        // day each was added onward), then the day's entries; With reordering on, in the chosen order.
        val taskColor = Plans.color(ItemKind.TASK)
        // (Tasks without a date ticked today keep their place among the open ones; Each entry once, for unique keys.)
        val natural = remember(planItems, dayItems, selected) {
            CalendarData.dayList(dayItems, planItems, selected, taskColor).distinctBy { CalendarData.entryKey(it) }
        }
        val entries = remember(natural, orders, settings.allowReorder, selected) {
            if (settings.allowReorder) CalendarData.applyOrder(natural, orders[selected]) { CalendarData.entryKey(it) } else natural
        }
        // The reset arrow shows only while this day's list differs from its normal order.
        val dayReordered = settings.allowReorder && entries.map { CalendarData.entryKey(it) } != natural.map { CalendarData.entryKey(it) }
        val currentNatural by rememberUpdatedState(natural)
        // Date (e.g. 15/12/26; With the calendar hidden it's up in the header), then the reset arrow (a reordered
        // day), +Task/Event and TB Total.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!hidden) {
                Text(
                    day.format(DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ENGLISH)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Spacer(Modifier.weight(1f))
            val boxShape = RoundedCornerShape(10.dp)
            if (dayReordered) {
                Box(
                    Modifier
                        .clip(boxShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, boxShape)
                        .clickable { askReset = true }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.drawable.ic_s_reset), contentDescription = "Back to the normal order",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
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
        val currentEntries by rememberUpdatedState(entries)
        // Touch+hold an entry to move it (pulse + short vibration); The order is saved for this day when let go.
        // The dragged entry is drawn where the finger is (from its position, not added-up moves), so it can't drift;
        // Holding it near the list's top or bottom edge scrolls slowly, faster the closer to the edge.
        var dragList by remember { mutableStateOf<List<DayItem>?>(null) }
        var dragKey by remember { mutableStateOf<String?>(null) }
        var fingerY by remember { mutableFloatStateOf(0f) }
        var grabY by remember { mutableFloatStateOf(0f) }
        var dragMoved by remember { mutableStateOf(false) }
        // Let go: The entry glides from under the finger into its place (no snap).
        var dropKey by remember { mutableStateOf<String?>(null) }
        val dropY = remember { androidx.compose.animation.core.Animatable(0f) }
        val listState = rememberLazyListState()
        val haptic = LocalHapticFeedback.current
        val density = LocalDensity.current
        val edgePx = with(density) { 56.dp.toPx() }
        val maxSpeedPx = with(density) { 420.dp.toPx() } // Per second, at the very edge.
        fun keyOf(i: DayItem) = "e/" + CalendarData.entryKey(i)
        fun moveIfNeeded() {
            val list = dragList ?: return
            val k = dragKey ?: return
            val from = list.indexOfFirst { keyOf(it) == k }
            if (from < 0) return
            val visible = listState.layoutInfo.visibleItemsInfo
            val cur = visible.firstOrNull { it.key == k } ?: return
            // The list hasn't laid out the last move yet: Decide on the next frame (never on stale positions).
            if (cur.index != from) return
            val top = fingerY - grabY
            val bottom = top + cur.size
            // Swap with a neighbour once the dragged entry covers half of it. After the swap, the way back needs
            // the same half the other way, so entries of any height never flip back and forth.
            val below = list.getOrNull(from + 1)?.let { n -> visible.firstOrNull { it.key == keyOf(n) } }
            val above = list.getOrNull(from - 1)?.let { p -> visible.firstOrNull { it.key == keyOf(p) } }
            val to = when {
                below != null && bottom > below.offset + below.size / 2f -> from + 1
                above != null && top < above.offset + above.size / 2f -> from - 1
                else -> return
            }
            // The list keeps its first visible entry in place (or it would follow the moved one): Same frame.
            val first = listState.firstVisibleItemIndex
            val firstOffset = listState.firstVisibleItemScrollOffset
            dragList = list.toMutableList().apply { add(to, removeAt(from)) }
            if (from == first || to == first) listState.requestScrollToItem(first, firstOffset)
        }
        fun endDrag() {
            val k = dragKey
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == k }
            if (k != null && info != null) {
                val from = fingerY - grabY - info.offset
                dropKey = k
                scope.launch {
                    dropY.snapTo(from)
                    dropY.animateTo(0f, androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow))
                    if (dropKey == k) dropKey = null
                }
            }
            dragList?.let { l ->
                val keys = l.map { CalendarData.entryKey(it) }
                // Dragged back into the normal order: Nothing to keep (the reset arrow goes).
                if (keys == currentNatural.map { CalendarData.entryKey(it) }) Repo.clearOrder(selected) else Repo.setOrder(selected, keys)
            }
            dragList = null
            dragKey = null
            dragMoved = false
        }
        // While dragging, every frame: Slow scroll near an edge (same speed on any refresh rate), and moves settle.
        // Started by the drag itself (not by the screen), so starting or ending a drag doesn't redraw the month grid.
        fun startFrameLoop() = scope.launch {
            var last = 0L
            while (dragKey != null) {
                val now = withFrameNanos { it }
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                val info = listState.layoutInfo
                val height = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
                // By the dragged entry's own edges (not the finger): Wherever it was grabbed, it scrolls when the
                // entry reaches the top or bottom of the list. Only once it's been moved.
                val size = info.visibleItemsInfo.firstOrNull { it.key == dragKey }?.size ?: 0
                val top = fingerY - grabY
                val bottom = top + size
                val depth = when {
                    !dragMoved -> 0f
                    top < edgePx -> -((edgePx - top) / edgePx).coerceIn(0f, 1f)
                    bottom > height - edgePx -> ((bottom - (height - edgePx)) / edgePx).coerceIn(0f, 1f)
                    else -> 0f
                }
                if (depth != 0f && dt > 0f) {
                    // A scroll step can be interrupted: Just go on.
                    try {
                        listState.scrollBy(depth * maxSpeedPx * dt)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    }
                }
                // Every frame (also with the finger still): Finishes any move a fast drag left half-way.
                moveIfNeeded()
            }
        }

        // Opened from the widget: Its entry at the top of its day's list, pulsing once the screen is really shown
        // (opening the app animates first, which would hide it).
        val focus by CalendarNav.focus.collectAsState()
        var pendingPulse by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(focus, selected, entries) {
            val (fDay, fKey) = focus ?: return@LaunchedEffect
            if (fDay != selected || entries.none { CalendarData.entryKey(it) == fKey }) return@LaunchedEffect
            pendingPulse = fKey
            CalendarNav.focus.value = null
        }
        LaunchedEffect(focus) {
            // Not found within a moment (e.g. it was just deleted): Forget it.
            if (focus != null) {
                kotlinx.coroutines.delay(3000)
                CalendarNav.focus.value = null
            }
        }
        val lifecycle = (ctx as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        LaunchedEffect(pendingPulse) {
            val k = pendingPulse ?: return@LaunchedEffect
            val i = entries.indexOfFirst { CalendarData.entryKey(it) == k }
            if (i >= 0) runCatching { listState.scrollToItem(i) }
            while (lifecycle != null && !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                kotlinx.coroutines.delay(40)
            }
            kotlinx.coroutines.delay(350)
            pulse.fire(k)
            pendingPulse = null
        }

        val listDrag = if (settings.allowReorder) Modifier.pointerInput(selected) {
            detectDragGesturesAfterLongPress(
                onDragStart = { at ->
                    val hit = listState.layoutInfo.visibleItemsInfo.firstOrNull { v ->
                        (v.key as? String)?.startsWith("e/") == true && at.y >= v.offset && at.y < v.offset + v.size
                    } ?: return@detectDragGesturesAfterLongPress
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    dragList = currentEntries
                    dragKey = hit.key as String
                    dragMoved = false
                    dropKey = null
                    fingerY = at.y
                    startFrameLoop()
                    grabY = at.y - hit.offset
                    pulse.fire((hit.key as String).removePrefix("e/"))
                },
                onDrag = { change, _ ->
                    if (dragKey == null) return@detectDragGesturesAfterLongPress
                    change.consume()
                    fingerY = change.position.y
                    dragMoved = true
                    moveIfNeeded()
                },
                onDragEnd = { endDrag() },
                onDragCancel = { endDrag() }
            )
        } else Modifier
        LazyColumn(Modifier.weight(if (hidden) 1f else 1f - gridShare).fillMaxWidth().then(listDrag), state = listState) {
            // Read here (not by the screen): A move during a drag redraws only the list, never the month grid.
            val shownEntries = dragList ?: entries
            if (shownEntries.isEmpty()) {
                item(key = "empty") {
                    Text(
                        if (day.isBefore(gridStart) || !day.isBefore(gridEnd)) "Go to this day's month to see it."
                        else "Nothing on this day.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(shownEntries, key = { keyOf(it) }) { item ->
                val k = CalendarData.entryKey(item)
                val listKey = "e/$k"
                val dragging = dragKey == listKey
                val dropping = dropKey == listKey
                PulseBox(
                    k, Color(item.color), pulse,
                    Modifier
                        // The others slide to their new places; The dragged one follows the finger instead.
                        .animateItem(
                            fadeInSpec = null, fadeOutSpec = null,
                            // Only during a drag: When entries just load or change, they're in place at once.
                            placementSpec = if (dragging || dropping || (dragKey == null && dropKey == null)) null
                            else androidx.compose.animation.core.spring(
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                                visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold
                            )
                        )
                        .zIndex(if (dragging || dropping) 1f else 0f)
                        .graphicsLayer {
                            // Under the finger: Where it was grabbed, wherever the list has put the entry.
                            translationY = when {
                                dragging -> {
                                    val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == listKey }
                                    if (info == null) 0f else fingerY - grabY - info.offset
                                }
                                dropping -> dropY.value
                                else -> 0f
                            }
                        }
                ) {
                    // A short tap pulses too, as a hint that it can be moved (Tasks/Events also open).
                    val tapPulse = { if (settings.allowReorder) pulse.fire(k) }
                    val plan = item.plan
                    if (plan != null) {
                        PlanRow(plan, item.planDay, item.done, item.color, planUi, onTouch = tapPulse)
                    } else {
                        EntryRow(item, day, today, settings, tf, onTouch = tapPulse,
                            onDelete = { toDelete = item }, onHide = { toHide = item })
                    }
                }
            }
        }
    }

    if (askReset) {
        AlertDialog(
            onDismissRequest = { askReset = false },
            title = { Text("Reset this day's order?") },
            text = {
                Text(
                    "The list of ${LocalDate.ofEpochDay(selected).format(DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ENGLISH))} " +
                        "goes back to its normal order (also on the calendars and widgets)."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Repo.clearOrder(selected)
                    askReset = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { askReset = false }) { Text("Cancel") } }
        )
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
private fun PlanRow(item: PlanItem, day: Long?, done: Boolean, color: Int, ui: PlanUi, onTouch: () -> Unit = {}) {
    val ctx = LocalContext.current
    val c = Color(color)
    // A past day's entry (also a task without a date ticked on a past day) is read-only unless previous day editing
    // is on (Settings).
    val allowPast = Repo.settings.collectAsState().value.allowPast
    val recordDay = day ?: (if (item.undated && done) item.doneAt?.let { Plan.dayOf(it) } else null)
    val locked = !allowPast && recordDay != null && recordDay < Plan.today()
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        // Same-size squares: Tasks outlined (tap to tick or untick), Events solid.
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .then(if (item.kind == ItemKind.TASK && !locked) Modifier.clickable { Plans.setDone(ctx, item, day, !done) } else Modifier),
            contentAlignment = Alignment.Center
        ) { PlanMarker(item.kind, done, c) }
        Spacer(Modifier.width(6.dp))
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .clickable {
                    onTouch()
                    if (!locked) ui.edit(item, day)
                }
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
                    Plan.scheduleText(item),
                    if (item.undated) (if (done) "Done" else "No date") else null,
                ).plus(Plan.honestyNotes(item, day)).plus(item.kind.label).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
        if (locked) Spacer(Modifier.size(48.dp)) // Keeps the rows lined up without the bin.
        else IconButton(onClick = { ui.delete(item, day) }) {
            Icon(Icons.Default.Delete, contentDescription = "Delete ${item.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A TimeBlock or Gmail event in the day list: Its color, name and time; Bin (TimeBlocks) or hide (Gmail). */
@Composable
private fun EntryRow(
    item: DayItem, day: LocalDate, today: LocalDate, settings: com.pantelmg.tb2cal.model.Settings,
    tf: java.text.DateFormat, onTouch: () -> Unit, onDelete: () -> Unit, onHide: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (settings.allowReorder) Modifier.clickable { onTouch() } else Modifier)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Same 32dp slot as the Task/Event squares, so the colors and the names line up.
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(Color(item.color)))
        }
        Spacer(Modifier.width(6.dp))
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
            // A past day's TimeBlocks can be removed only with previous day editing on (Settings).
            if (!settings.allowPast && day.toEpochDay() < today.toEpochDay()) Spacer(Modifier.size(48.dp))
            else IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete ${item.label}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (settings.allowHide && item.eventId != null) {
            IconButton(onClick = onHide) {
                Icon(painterResource(R.drawable.ic_s_hide), contentDescription = "Hide ${item.label}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
