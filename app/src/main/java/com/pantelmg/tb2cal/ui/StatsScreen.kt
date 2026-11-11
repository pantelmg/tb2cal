package com.pantelmg.tb2cal.ui

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

enum class StatsPeriod(val label: String) { DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly") }

private data class StatRow(val key: String, val name: String, val colorKey: String?, val totalMs: Long, val count: Int)

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
    }
}

/** Sums same-name TimeBlocks (case and spacing ignored) that started inside the period. */
private fun epochMs(d: LocalDate) = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

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

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text("Statistics", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text(
            "Total time of completed TimeBlocks with the same name.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            StatsPeriod.entries.forEachIndexed { i, p ->
                SegmentedButton(
                    selected = period == p,
                    onClick = {
                        period = p
                        offset = 0
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = StatsPeriod.entries.size)
                ) { Text(p.label) }
            }
        }

        Spacer(Modifier.height(12.dp))
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

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (total > 0) Fmt.duration(total) else "0m",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Total · ${rows.sumOf { it.count }} TimeBlock(s)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        if (rows.isEmpty()) {
            Text(
                "No completed TimeBlocks in this period.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            rows.forEach { r ->
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
                    Spacer(Modifier.height(6.dp))
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
        Spacer(Modifier.height(24.dp))
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
