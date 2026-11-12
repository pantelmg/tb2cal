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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.pantelmg.tb2cal.data.Repo
import com.pantelmg.tb2cal.model.Fmt
import com.pantelmg.tb2cal.model.Goal
import com.pantelmg.tb2cal.model.GoogleColors

/** One TimeBlock name on the Goals tab. */
private data class GoalRow(val key: String, val name: String, val colorKey: String?, val doneMs: Long, val goalMin: Int)

/**
 * Goals tab: Laid out like Statistics (period, ‹ title ›, card, list). Every TimeBlock name of your list gets a
 * time goal per period type; The bar shows how much of the goal is done. "Apply onward" (per period type): Goals set
 * while it's on repeat in every later period of that type; While it's off, they're for the period shown only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen() {
    val history by Repo.history.collectAsState()
    val blocks by Repo.blocks.collectAsState()
    val goals by Repo.goals.collectAsState()
    val settings by Repo.settings.collectAsState()
    val period = runCatching { StatsPeriod.valueOf(settings.goalsPeriod) }.getOrDefault(StatsPeriod.WEEKLY)
    val onward = period.name in settings.applyOnward
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var hours by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf("") }

    val (start, end) = remember(period, offset) { range(period, offset) }
    val startDay = start.toEpochDay()
    val rows = remember(history, blocks, goals, period, start, end) {
        val done = summarize(history, start, end).associateBy { it.key }
        blocks.distinctBy { it.name.trim().lowercase() }.map { b ->
            val key = b.name.trim().lowercase()
            GoalRow(key, b.name.trim(), b.colorKey, done[key]?.totalMs ?: 0L, Goal.resolve(goals, period.name, key, startDay))
        }
    }
    // Names with a goal of this period type, in the period shown.
    val goalCount = rows.count { it.goalMin > 0 }

    fun save(key: String) {
        // Empty counts as 0; Minutes over 59 just add up (90m = 1h30m). 0h 0m removes the goal.
        val total = (hours.toIntOrNull() ?: 0) * 60 + (minutes.toIntOrNull() ?: 0)
        Repo.setGoal(period.name, key, startDay, total, onward)
        editing = null
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text("Goals", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Set a time goal for each TimeBlock and see how much of it is done.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            StatsPeriod.entries.forEachIndexed { i, p ->
                SegmentedButton(
                    selected = period == p,
                    onClick = {
                        Repo.updateSettings { it.copy(goalsPeriod = p.name) }
                        offset = 0
                        editing = null
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = StatsPeriod.entries.size),
                    icon = {}
                ) { Text(p.label, maxLines = 1) }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { offset--; editing = null }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous")
            }
            Text(
                title(period, offset, start, end),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            // Goals can also be set ahead, for later periods.
            IconButton(onClick = { offset++; editing = null }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
            }
        }

        // ---- Goal count + Apply onward ----
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$goalCount goal${if (goalCount == 1) "" else "s"} set in this time period",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        Repo.updateSettings {
                            it.copy(applyOnward = if (onward) it.applyOnward - period.name else it.applyOnward + period.name)
                        }
                    },
                    border = BorderStroke(1.dp, if (onward) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (onward) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.width(168.dp)
                ) {
                    Text(
                        "Apply onward",
                        textDecoration = if (onward) null else TextDecoration.LineThrough,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        if (rows.isEmpty()) {
            Text(
                "Add TimeBlocks on the TimeBlocks tab to set goals for them.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        } else {
            // Only this list scrolls, and only if it doesn't fit.
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(rows, key = { it.key }) { r ->
                    val open = editing == r.key
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ColorDot(r.colorKey, 14.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(r.name, style = MaterialTheme.typography.titleMedium)
                                    if (r.goalMin > 0) {
                                        val pct = (r.doneMs * 100 / (r.goalMin * 60_000L)).toInt()
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "$pct% of Goal",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (pct >= 100) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    if (r.goalMin > 0) "${Fmt.duration(r.doneMs)} of ${Fmt.duration(r.goalMin * 60_000L)}"
                                    else "No goal · ${Fmt.duration(r.doneMs)} done",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = {
                                if (open) save(r.key)
                                else {
                                    editing = r.key
                                    hours = (r.goalMin / 60).toString()
                                    minutes = (r.goalMin % 60).toString()
                                }
                            }) { Text(if (open) "Retap to save" else "Tap to set") }
                        }
                        if (open) {
                            Row(
                                Modifier.padding(start = 24.dp, top = 4.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = hours,
                                    onValueChange = { v -> hours = v.filter(Char::isDigit).take(3) },
                                    label = { Text("Hours") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = minutes,
                                    onValueChange = { v -> minutes = v.filter(Char::isDigit).take(2) },
                                    label = { Text("Minutes") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        val barColor = GoogleColors.byKey(r.colorKey)?.let { Color(it.argb) } ?: MaterialTheme.colorScheme.primary
                        LinearProgressIndicator(
                            progress = {
                                if (r.goalMin > 0) (r.doneMs.toFloat() / (r.goalMin * 60_000L)).coerceIn(0f, 1f) else 0f
                            },
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
