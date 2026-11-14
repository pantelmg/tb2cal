package com.pantelmg.tb2cal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pantelmg.tb2cal.data.Repo
import com.pantelmg.tb2cal.model.Fmt
import com.pantelmg.tb2cal.model.Goal
import com.pantelmg.tb2cal.model.GoalSpan
import com.pantelmg.tb2cal.model.Plan
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The current period's first day for a goal type ("DAILY", "WEEKLY", …), as on the Goals tab. */
internal fun currentPeriodStart(period: String): Long =
    range(runCatching { StatsPeriod.valueOf(period) }.getOrDefault(StatsPeriod.WEEKLY), 0).first.toEpochDay()

/** "14 Nov 26", "2–8 Nov 26", "30 Nov – 6 Dec 26", "29 Dec 26 – 4 Jan 27", "Nov 26", "2026". */
internal fun goalPeriodText(period: String, startDay: Long): String {
    val a = LocalDate.ofEpochDay(startDay)
    fun f(d: LocalDate, p: String) = d.format(DateTimeFormatter.ofPattern(p, Locale.ENGLISH))
    return when (period) {
        "DAILY" -> f(a, "d MMM yy")
        "WEEKLY" -> {
            val b = a.plusDays(6)
            when {
                a.year != b.year -> "${f(a, "d MMM yy")} – ${f(b, "d MMM yy")}"
                a.month != b.month -> "${f(a, "d MMM")} – ${f(b, "d MMM yy")}"
                else -> "${f(a, "d")}–${f(b, "d MMM yy")}"
            }
        }
        "MONTHLY" -> f(a, "MMM yy")
        else -> "${a.year}"
    }
}

/** A goal's periods: "2–8 Nov 26", "From 2–8 Nov 26 on", "2–8 Nov 26 to 16–22 Nov 26". */
private fun spanText(s: GoalSpan): String {
    val until = s.untilStart ?: return "From ${goalPeriodText(s.period, s.fromStart)} on"
    if (until == Goal.nextStart(s.period, s.fromStart)) return goalPeriodText(s.period, s.fromStart)
    val u = LocalDate.ofEpochDay(until)
    val last = when (s.period) {
        "DAILY" -> u.minusDays(1)
        "WEEKLY" -> u.minusWeeks(1)
        "MONTHLY" -> u.minusMonths(1)
        else -> u.minusYears(1)
    }.toEpochDay()
    return "${goalPeriodText(s.period, s.fromStart)} to ${goalPeriodText(s.period, last)}"
}

/**
 * Active goals overview (Goals tab): Every goal per TimeBlock, by range (Daily, Weekly, Monthly, Yearly), each with
 * the periods it covers. The bin follows Settings → Allow previous day editing: OFF, only goals that haven't ended
 * (a running one ends from now, its past periods keep it); ON, any goal.
 */
@Composable
fun GoalsOverview(onDismiss: () -> Unit) {
    val goals by Repo.goals.collectAsState()
    val blocks by Repo.blocks.collectAsState()
    val settings by Repo.settings.collectAsState()
    val allowPast = settings.allowPast
    val today = Plan.today()
    var toDelete by remember { mutableStateOf<GoalSpan?>(null) }

    val spans = remember(goals) { Goal.spans(goals) }
    // TimeBlocks in your list's order, then names no longer in it (their past goals kept as a record).
    val names = remember(spans, blocks) {
        val listed = blocks.map { it.name.trim().lowercase() to it }.distinctBy { it.first }
        val keys = spans.map { it.nameKey }.toSet()
        listed.filter { it.first in keys }.map { (k, b) -> Triple(k, b.name.trim(), b.colorKey) } +
            (keys - listed.map { it.first }.toSet()).sorted().map { Triple(it, it, null) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(x = (-12).dp)) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    Text("Active goals", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Every goal you set, per TimeBlock and range, with the periods it covers. " +
                        if (allowPast) "Any goal can be deleted (Allow previous day editing is ON)."
                        else "Goals that haven't ended can be deleted; A running one ends from now, so its past periods " +
                            "keep it (Allow previous day editing is OFF).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                if (names.isEmpty()) {
                    Text(
                        "No goals set yet. Set them with Tap to set on the Goals tab.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    names.forEach { (key, name, colorKey) ->
                        item(key = "n$key") {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)) {
                                ColorDot(colorKey, 14.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        StatsPeriod.entries.forEach { p ->
                            val mine = spans.filter { it.nameKey == key && it.period == p.name }.sortedBy { it.fromStart }
                            if (mine.isNotEmpty()) {
                                item(key = "p$key${p.name}") {
                                    Text(
                                        p.label, style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 24.dp, top = 6.dp)
                                    )
                                }
                                mine.forEach { s ->
                                    item(key = "s$key${p.name}${s.fromStart}${s.onward}") {
                                        val past = Goal.isPast(s, today)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(start = 24.dp).alpha(if (past) 0.6f else 1f)
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(Fmt.duration(s.minutes * 60_000L), style = MaterialTheme.typography.bodyLarge)
                                                Text(
                                                    spanText(s) + if (past) " · Ended" else "",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                // Periods inside it that have their own goal (listed on their own rows).
                                                if (s.except.isNotEmpty()) {
                                                    Text(
                                                        "except " + s.except.joinToString(", ") { goalPeriodText(s.period, it) },
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            if (Goal.canDelete(s, allowPast, today)) {
                                                IconButton(onClick = { toDelete = s }) {
                                                    Icon(
                                                        Icons.Default.Delete, contentDescription = "Delete this goal",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            } else Spacer(Modifier.size(48.dp)) // Keeps the rows lined up.
                                        }
                                    }
                                }
                            }
                        }
                        item(key = "d$key") { HorizontalDivider(Modifier.padding(top = 10.dp)) }
                    }
                }
            }
        }

        toDelete?.let { s ->
            val cur = currentPeriodStart(s.period)
            val endsNow = s.onward && !allowPast && s.fromStart < cur
            AlertDialog(
                onDismissRequest = { toDelete = null },
                title = { Text("Delete this goal?") },
                text = {
                    Text(
                        "${Fmt.duration(s.minutes * 60_000L)} · ${spanText(s)}" +
                            (if (s.except.isNotEmpty()) "\nexcept " + s.except.joinToString(", ") { goalPeriodText(s.period, it) } else "") +
                            "\n\n" +
                            when {
                                endsNow -> "It ends from the current period on; Earlier periods keep it as it was. Goals " +
                                    "set for single periods stay."
                                !s.onward -> "That period goes back to the goal that applies around it (if any). This " +
                                    "can't be undone."
                                else -> "It's removed from all its periods; Goals set for single periods stay. This " +
                                    "can't be undone."
                            }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        Repo.setGoals(Goal.delete(Repo.goals.value, s, allowPast, cur))
                        toDelete = null
                    }) { Text(if (endsNow) "End it" else "Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
            )
        }
    }
}
