package com.pantelmg.tb2cal.ui

import android.widget.Toast
import com.pantelmg.tb2cal.plan.Plans
import com.pantelmg.tb2cal.model.PlanOccurrence
import com.pantelmg.tb2cal.model.Plan
import com.pantelmg.tb2cal.model.ItemKind
import androidx.compose.material3.Checkbox
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Check
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.text.drawText
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import java.time.format.TextStyle
import androidx.compose.ui.unit.dp
import com.pantelmg.tb2cal.data.Repo
import com.pantelmg.tb2cal.model.Fmt
import com.pantelmg.tb2cal.model.GoogleColors
import com.pantelmg.tb2cal.model.HistoryEntry
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/** A day to open in Statistics (set by the calendar widget). */
object StatsNav {
    val day = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
}

enum class StatsPeriod(val label: String) { DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly"), YEARLY("Yearly") }

internal data class StatRow(val key: String, val name: String, val colorKey: String?, val totalMs: Long, val count: Int)

/** Start (inclusive) and end (exclusive) dates of the period [offset] steps from now. */
internal fun range(p: StatsPeriod, offset: Int): Pair<LocalDate, LocalDate> {
    val today = LocalDate.now()
    return when (p) {
        StatsPeriod.DAILY -> today.plusDays(offset.toLong()).let { it to it.plusDays(1) }
        StatsPeriod.WEEKLY -> {
            val wf = WeekFields.of(Locale.getDefault())
            today.with(wf.dayOfWeek(), 1).plusWeeks(offset.toLong()).let { it to it.plusWeeks(1) }
        }
        StatsPeriod.MONTHLY -> today.withDayOfMonth(1).plusMonths(offset.toLong()).let { it to it.plusMonths(1) }
        StatsPeriod.YEARLY -> today.withDayOfYear(1).plusYears(offset.toLong()).let { it to it.plusYears(1) }
    }
}

internal fun title(p: StatsPeriod, offset: Int, start: LocalDate, end: LocalDate): String {
    val loc = Locale.getDefault()
    return when (p) {
        StatsPeriod.DAILY -> when (offset) {
            0 -> "Today"
            -1 -> "Yesterday"
            else -> start.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", loc))
        }
        StatsPeriod.WEEKLY -> {
            val span = "${start.format(DateTimeFormatter.ofPattern("d MMM", loc))} – " +
                end.minusDays(1).format(DateTimeFormatter.ofPattern("d MMM yyyy", loc))
            when (offset) {
                0 -> "This week · $span"
                -1 -> "Last week · $span"
                else -> span
            }
        }
        StatsPeriod.MONTHLY -> {
            val m = start.format(DateTimeFormatter.ofPattern("LLLL yyyy", loc)).replaceFirstChar { it.uppercase() }
            when (offset) {
                0 -> "This month · $m"
                -1 -> "Last month · $m"
                else -> m
            }
        }
        StatsPeriod.YEARLY -> when (offset) {
            0 -> "This year · ${start.year}"
            -1 -> "Last year · ${start.year}"
            else -> "${start.year}"
        }
    }
}

internal fun epochMs(d: LocalDate) = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Graph columns: Daily = 24 hours, Weekly/Monthly = days, Yearly = 12 months. */
internal fun statsBucketCount(p: StatsPeriod, start: LocalDate, end: LocalDate): Int = when (p) {
    StatsPeriod.DAILY -> 24
    StatsPeriod.YEARLY -> 12
    else -> (end.toEpochDay() - start.toEpochDay()).toInt()
}

/** Column index → (name key → total ms) for the TimeBlocks that started inside the period. */
internal fun statsBuckets(history: List<HistoryEntry>, p: StatsPeriod, start: LocalDate, end: LocalDate): List<Map<String, Long>> {
    val zone = ZoneId.systemDefault()
    val n = statsBucketCount(p, start, end)
    val out = List(n) { mutableMapOf<String, Long>() }
    val from = epochMs(start)
    val to = epochMs(end)
    history.filter { it.startWallMs in from until to }.forEach { h ->
        val t = java.time.Instant.ofEpochMilli(h.startWallMs).atZone(zone)
        val i = when (p) {
            StatsPeriod.DAILY -> t.hour
            StatsPeriod.YEARLY -> t.monthValue - 1
            else -> (t.toLocalDate().toEpochDay() - start.toEpochDay()).toInt()
        }.coerceIn(0, n - 1)
        val k = h.name.trim().lowercase()
        out[i][k] = (out[i][k] ?: 0L) + h.activeMs
    }
    return out
}

/** Sums same-name TimeBlocks (case and spacing ignored) that started inside the period. */
internal fun summarize(history: List<HistoryEntry>, start: LocalDate, end: LocalDate): List<StatRow> {
    val from = epochMs(start)
    val to = epochMs(end)
    return history
        .filter { it.startWallMs in from until to }
        .groupBy { it.name.trim().lowercase() }
        .map { (key, list) ->
            val latest = list.maxBy { it.startWallMs }
            StatRow(key, latest.name.trim(), latest.colorKey, list.sumOf { it.activeMs }, list.size)
        }
        .sortedByDescending { it.totalMs }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen() {
    val history by Repo.history.collectAsState()
    val settings by Repo.settings.collectAsState()
    val planItems by Repo.items.collectAsState()
    // TimeBlocks or Tasks/Events (remembered); In Tasks/Events: Tasks, Events or Both (remembered).
    val te = settings.statsMode == "TE"
    val which = settings.statsItems
    // Period and List/Graph/Pie are remembered (saved in the settings).
    val period = runCatching { StatsPeriod.valueOf(settings.statsPeriod) }.getOrDefault(StatsPeriod.WEEKLY)
    val view = settings.statsView
    fun setPeriod(p: StatsPeriod) = Repo.updateSettings { it.copy(statsPeriod = p.name) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var toDelete by remember { mutableStateOf<StatRow?>(null) }
    val requestedDay by StatsNav.day.collectAsState()
    LaunchedEffect(requestedDay) {
        requestedDay?.let { d ->
            setPeriod(StatsPeriod.DAILY)
            offset = (d - LocalDate.now().toEpochDay()).toInt()
            StatsNav.day.value = null
        }
    }

    val (start, end) = remember(period, offset) { range(period, offset) }
    val rows = remember(history, start, end) { summarize(history, start, end) }
    val total = rows.sumOf { it.totalMs }
    val max = rows.maxOfOrNull { it.totalMs } ?: 0L
    // Tasks/Events of the period (done ones crossed out) and how many are completed.
    val planRows = remember(planItems, start, end, which) {
        Plan.occurrencesIn(planItems, start.toEpochDay(), end.toEpochDay(), tasks = which != "EVENTS", events = which != "TASKS")
    }
    val planDone = planRows.count { it.done }
    var askExport by remember { mutableStateOf(false) }
    var exTb by remember { mutableStateOf(true) }
    var exTasks by remember { mutableStateOf(false) }
    var exEvents by remember { mutableStateOf(false) }

    // Export: The user picks where to save; The graph of the selected period is written as a PDF.
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            val snapshot = history
            val p = period
            val t = title(period, offset, start, end)
            val s0 = start
            val e0 = end
            val r0 = rows
            val tb = exTb
            val tk = exTasks
            val ev = exEvents
            // Tasks and/or Events: Only the completed ones of the period.
            val done = if (tk || ev) {
                Plan.occurrencesIn(planItems, start.toEpochDay(), end.toEpochDay(), tasks = tk, events = ev).filter { it.done }
            } else emptyList()
            val planTitle = when {
                tk && ev -> "Completed tasks and events"
                tk -> "Completed tasks"
                ev -> "Completed events"
                else -> null
            }
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { StatsPdf.write(ctx, uri, snapshot, r0, p, t, s0, e0, tb, planTitle, done) }.isSuccess
                }
                Toast.makeText(ctx, if (ok) "PDF saved." else "Couldn't save the PDF.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // One page: Only the list (or the graph area) below the total takes the remaining space.
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text("Statistics", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        // Description, then TimeBlocks ⇄ Tasks/Events (fixed size, like TB Total) and Export PDF.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (te) "Your tasks and events of the period; Completed ones are crossed out."
                else "Total time of completed TimeBlocks with the same name.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            val boxShape = RoundedCornerShape(10.dp)
            Box(
                Modifier
                    .clip(boxShape)
                    .border(1.dp, MaterialTheme.colorScheme.primary, boxShape)
                    .clickable { Repo.updateSettings { it.copy(statsMode = if (te) "TB" else "TE") } }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Tasks/Events", style = MaterialTheme.typography.titleSmall, modifier = Modifier.alpha(0f), maxLines = 1)
                Text(if (te) "Tasks/Events" else "TimeBlocks", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .clip(boxShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, boxShape)
                    .clickable {
                        exTb = !te
                        exTasks = te && which != "EVENTS"
                        exEvents = te && which != "TASKS"
                        askExport = true
                    }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Export PDF", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Spacer(Modifier.height(8.dp))

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            StatsPeriod.entries.forEachIndexed { i, p ->
                SegmentedButton(
                    selected = period == p,
                    onClick = {
                        setPeriod(p)
                        offset = 0
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = StatsPeriod.entries.size),
                    icon = {}
                ) { Text(p.label, maxLines = 1) }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { offset-- }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
            }
            Text(
                title(period, offset, start, end),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            // Tasks/Events can also be looked at ahead (planned ones).
            IconButton(onClick = { offset++ }, enabled = offset < 0 || te) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
            }
        }

        if (te) {
            PlanStats(planRows, planDone, which)
        } else {
        // ---- Total + List/Graph/Pie, stacked (like Tasks/Events/Both) ----
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (total > 0) Fmt.duration(total) else "0m",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Total · ${rows.sumOf { it.count }} TimeBlock(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                StackedChoice(
                    listOf("LIST" to "List", "GRAPH" to "Graph", "PIE" to "Pie"), view
                ) { v -> Repo.updateSettings { it.copy(statsView = v) } }
            }
        }
        Spacer(Modifier.height(10.dp))

        if (rows.isEmpty()) {
            Text(
                "No completed TimeBlocks in this period.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        } else if (view == "GRAPH") {
            StatsGraph(history, rows, period, start, end, Modifier.weight(1f).fillMaxWidth())
        } else if (view == "PIE") {
            StatsPie(rows, total, Modifier.weight(1f).fillMaxWidth())
        } else {
            // Only this list scrolls, and only if it doesn't fit.
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(rows, key = { it.key }) { r ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ColorDot(r.colorKey, 14.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(r.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(
                                "${r.count}×",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(Fmt.duration(r.totalMs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            // Deleting removes the period's TimeBlocks: With past days in it, only with previous day
                            // editing on (Settings).
                            if (settings.allowPast || !start.isBefore(java.time.LocalDate.now())) {
                                IconButton(onClick = { toDelete = r }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete ${r.name}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        val barColor = GoogleColors.byKey(r.colorKey)?.let { Color(it.argb) } ?: MaterialTheme.colorScheme.primary
                        LinearProgressIndicator(
                            progress = { if (max > 0) r.totalMs.toFloat() / max else 0f },
                            color = barColor,
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )
                    }
                }
            }
        }
        }
    }

    if (askExport) {
        AlertDialog(
            onDismissRequest = { askExport = false },
            title = { Text("Export PDF") },
            text = {
                Column {
                    Text(
                        "${title(period, offset, start, end)}. Tasks and Events list the completed ones.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        Triple("TimeBlocks", exTb) { v: Boolean -> exTb = v },
                        Triple("Tasks", exTasks) { v: Boolean -> exTasks = v },
                        Triple("Events", exEvents) { v: Boolean -> exEvents = v },
                    ).forEach { (label, on, set) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable { set(!on) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = exTb || exTasks || exEvents,
                    onClick = {
                        askExport = false
                        exportPdf.launch(StatsPdf.fileName(period, start, end))
                    }
                ) { Text("Export") }
            },
            dismissButton = { TextButton(onClick = { askExport = false }) { Text("Cancel") } }
        )
    }

    toDelete?.let { r ->
        val periodName = title(period, offset, start, end)
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete ${r.name}?") },
            text = {
                Text(
                    "${Fmt.duration(r.totalMs)} of ${r.name} (${r.count} TimeBlock(s)) in $periodName will be removed " +
                        "from Statistics. Totals of longer periods that include it go down by the same amount. " +
                        "Calendar events are not affected."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Repo.removeHistory(r.key, epochMs(start), epochMs(end))
                    toDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }
}


/**
 * Stacked columns: Daily = per hour, Weekly = per day, Monthly = per day of the month, Yearly = per month.
 * Each column stacks the TimeBlock names in their colors; Legend underneath.
 */
@Composable
private fun StatsGraph(
    history: List<HistoryEntry>,
    rows: List<StatRow>,
    period: StatsPeriod,
    start: LocalDate,
    end: LocalDate,
    modifier: Modifier,
) {
    val locale = Locale.getDefault()
    val n = statsBucketCount(period, start, end)
    val buckets = remember(history, period, start, end) { statsBuckets(history, period, start, end) }
    val order = rows.map { it.key }
    val colors = rows.associate { it.key to (GoogleColors.byKey(it.colorKey)?.let { c -> Color(c.argb) }) }
    val fallback = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val maxMs = (buckets.maxOfOrNull { it.values.sum() } ?: 0L).coerceAtLeast(60_000L)

    Column(modifier) {
        Text(
            "Highest: ${Fmt.duration(maxMs)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            for (f in listOf(0.25f, 0.5f, 0.75f, 1f)) {
                val y = size.height * (1 - f)
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            val gap = if (n > 12) 2.dp.toPx() else 6.dp.toPx()
            val barW = (size.width - gap * (n - 1)) / n
            buckets.forEachIndexed { i, b ->
                var top = size.height
                val x = i * (barW + gap)
                order.forEach { k ->
                    val v = b[k] ?: return@forEach
                    val h = size.height * v / maxMs
                    drawRect(colors[k] ?: fallback, topLeft = Offset(x, top - h), size = Size(barW, h))
                    top -= h
                }
            }
        }
        // X labels: Every 6th hour, every weekday, every 7th day of the month, or every month.
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until n) {
                val label = when (period) {
                    StatsPeriod.DAILY -> if (i % 6 == 0) "$i" else ""
                    StatsPeriod.WEEKLY -> start.plusDays(i.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW_STANDALONE, locale)
                    StatsPeriod.MONTHLY -> if (i % 7 == 0) "${i + 1}" else ""
                    StatsPeriod.YEARLY -> java.time.Month.of(i + 1).getDisplayName(TextStyle.NARROW, locale)
                }
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // Legend
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            rows.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(r.colorKey, 10.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(r.name, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Statistics in Tasks/Events mode: Completed count + Tasks/Events/Both, then the period's list (done crossed out). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.PlanStats(rows: List<PlanOccurrence>, done: Int, which: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$done", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    when (which) { "TASKS" -> "Tasks completed"; "EVENTS" -> "Events completed"; else -> "Tasks+Events completed" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Tasks / Events / Both, stacked (narrow), so the count and its description keep the room on the left.
            Spacer(Modifier.width(12.dp))
            StackedChoice(
                listOf("TASKS" to "Tasks", "EVENTS" to "Events", "BOTH" to "Both"), which
            ) { v -> Repo.updateSettings { it.copy(statsItems = v) } }
        }
    }
    Spacer(Modifier.height(10.dp))
    if (rows.isEmpty()) {
        Text(
            when (which) { "TASKS" -> "No tasks in this period."; "EVENTS" -> "No events in this period."; else -> "No tasks or events in this period." },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        return
    }
    // Only this list scrolls, and only if it doesn't fit.
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(rows) { o ->
            val c = Color(Plans.color(o.item.kind))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlanMarker(o.item.kind, o.done && o.item.kind == ItemKind.TASK, c, 20.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        o.item.title, style = MaterialTheme.typography.titleMedium,
                        textDecoration = if (o.done) TextDecoration.LineThrough else null,
                        color = if (o.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        listOfNotNull(
                            Plan.shortDate(o.day ?: Plan.dayOf(o.startMs)),
                            if (o.day == null) "Done" else Plan.timeText(o.item),
                        ).plus(Plan.honestyNotes(o.item, o.day)).plus(o.item.kind.label).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Choices stacked in a narrow box (Tasks/Events/Both, List/Graph/Pie): A tick and a tint on the chosen one. */
@Composable
private fun StackedChoice(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val boxShape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .width(androidx.compose.foundation.layout.IntrinsicSize.Max)
            .clip(boxShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, boxShape)
    ) {
        options.forEachIndexed { i, (key, label) ->
            if (i > 0) androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            val sel = selected == key
            Row(
                Modifier
                    .fillMaxWidth()
                    // Unselected on the surface color (black in dark mode).
                    .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                    .clickable { onSelect(key) }
                    .padding(start = 10.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Check, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp).alpha(if (sel) 1f else 0f)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    label, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                    color = if (sel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** A share of the period as a whole percentage for a slice ("<1%" for tiny ones). */
internal fun shareText(ms: Long, total: Long): String {
    if (total <= 0) return "0%"
    val p = ms * 100.0 / total
    return if (p > 0 && p < 1) "<1%" else "${Math.round(p)}%"
}

/**
 * Pie: Each TimeBlock's share of the period's total (shown in the card above), clockwise from the top in the
 * list's order. Slices big enough carry their percentage (and time, when there's room); The legend below lists
 * every TimeBlock with its time and share.
 */
@Composable
private fun StatsPie(rows: List<StatRow>, total: Long, modifier: Modifier) {
    val fallback = MaterialTheme.colorScheme.primary
    val gap = MaterialTheme.colorScheme.background
    val colors = rows.map { r -> GoogleColors.byKey(r.colorKey)?.let { Color(it.argb) } ?: fallback }
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val big = MaterialTheme.typography.titleSmall.copy(
        color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.45f), blurRadius = 4f)
    )
    val small = big.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = FontWeight.SemiBold)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .fillMaxWidth(0.72f)
                .widthIn(max = 280.dp)
                .aspectRatio(1f)
        ) {
            if (total <= 0) return@Canvas
            val r = size.minDimension / 2
            val c = Offset(size.width / 2, size.height / 2)
            var angle = -90f
            val sweeps = rows.map { 360f * it.totalMs / total }
            rows.forEachIndexed { i, _ ->
                drawArc(colors[i], angle, sweeps[i], useCenter = true, topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r))
                angle += sweeps[i]
            }
            // Thin gaps between slices (none for a single TimeBlock).
            if (rows.size > 1) {
                angle = -90f
                sweeps.forEach { sw ->
                    val a = Math.toRadians(angle.toDouble())
                    drawLine(gap, c, Offset(c.x + r * kotlin.math.cos(a).toFloat(), c.y + r * kotlin.math.sin(a).toFloat()), strokeWidth = 2.dp.toPx())
                    angle += sw
                }
            }
            // Labels inside the slices: Percentage from 6%, plus the time from 14%.
            angle = -90f
            rows.forEachIndexed { i, row ->
                val sw = sweeps[i]
                if (sw >= 360f * 0.06f) {
                    val mid = Math.toRadians((angle + sw / 2).toDouble())
                    val dist = if (rows.size == 1) 0f else r * 0.62f
                    val p = Offset(c.x + dist * kotlin.math.cos(mid).toFloat(), c.y + dist * kotlin.math.sin(mid).toFloat())
                    val pct = measurer.measure(shareText(row.totalMs, total), big)
                    if (sw >= 360f * 0.14f) {
                        val time = measurer.measure(Fmt.duration(row.totalMs), small)
                        val h = pct.size.height + time.size.height
                        drawText(pct, topLeft = Offset(p.x - pct.size.width / 2, p.y - h / 2))
                        drawText(time, topLeft = Offset(p.x - time.size.width / 2, p.y - h / 2 + pct.size.height))
                    } else {
                        drawText(pct, topLeft = Offset(p.x - pct.size.width / 2, p.y - pct.size.height / 2))
                    }
                }
                angle += sw
            }
        }
        Spacer(Modifier.height(12.dp))
        // Legend: Every TimeBlock with its time and share (only this scrolls, and only if it doesn't fit).
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.key }) { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(r.colorKey, 14.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(r.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(Fmt.duration(r.totalMs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        String.format(Locale.getDefault(), "%.1f%%", if (total > 0) r.totalMs * 100.0 / total else 0.0),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.widthIn(min = 52.dp)
                    )
                }
            }
        }
    }
}
