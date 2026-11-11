package com.pantelmg.tb2cal.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Canvas
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
private fun range(p: StatsPeriod, offset: Int): Pair<LocalDate, LocalDate> {
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

private fun title(p: StatsPeriod, offset: Int, start: LocalDate, end: LocalDate): String {
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
private fun summarize(history: List<HistoryEntry>, start: LocalDate, end: LocalDate): List<StatRow> {
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
    var period by rememberSaveable { mutableStateOf(StatsPeriod.WEEKLY) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var graph by rememberSaveable { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<StatRow?>(null) }
    val requestedDay by StatsNav.day.collectAsState()
    LaunchedEffect(requestedDay) {
        requestedDay?.let { d ->
            period = StatsPeriod.DAILY
            offset = (d - LocalDate.now().toEpochDay()).toInt()
            StatsNav.day.value = null
        }
    }

    val (start, end) = remember(period, offset) { range(period, offset) }
    val rows = remember(history, start, end) { summarize(history, start, end) }
    val total = rows.sumOf { it.totalMs }
    val max = rows.maxOfOrNull { it.totalMs } ?: 0L

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
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { StatsPdf.write(ctx, uri, snapshot, r0, p, t, s0, e0) }.isSuccess
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Statistics",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { exportPdf.launch(StatsPdf.fileName(period, start, end)) },
                enabled = rows.isNotEmpty()
            ) { Text("Export PDF") }
        }
        Text(
            "Total time of completed TimeBlocks with the same name.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            StatsPeriod.entries.forEachIndexed { i, p ->
                SegmentedButton(
                    selected = period == p,
                    onClick = {
                        period = p
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
            IconButton(onClick = { offset++ }, enabled = offset < 0) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
            }
        }

        // ---- Total + List/Graph switch ----
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
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = !graph,
                        onClick = { graph = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("List") }
                    SegmentedButton(
                        selected = graph,
                        onClick = { graph = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("Graph") }
                }
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
        } else if (graph) {
            StatsGraph(history, rows, period, start, end, Modifier.weight(1f).fillMaxWidth())
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
                            IconButton(onClick = { toDelete = r }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete ${r.name}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
