package com.pantelmg.tb2cal.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pantelmg.tb2cal.calendar.CalendarData
import com.pantelmg.tb2cal.data.DataBundle
import com.pantelmg.tb2cal.data.DataTransfer
import com.pantelmg.tb2cal.data.Repo
import com.pantelmg.tb2cal.model.GoogleColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Settings → Data management: Which preview to show. */
enum class DataMode(val title: String, val action: String) {
    EXPORT("Export Preview", "Export"),
    IMPORT("Import Preview", "Import"),
    DELETE("Delete Preview", "Delete"),
}

/** What Settings hands to the preview screen (for Import: The file that was read). */
object DataNav {
    var mode = DataMode.EXPORT
    var bundle: DataBundle? = null
}

/** "3 TimeBlocks · 1 Task · 0 Events" (only the ticked kinds). */
internal fun dataCounts(b: DataBundle, pick: DataTransfer.Pick): String {
    fun n(count: Int, one: String, many: String) = "$count ${if (count == 1) one else many}"
    return listOfNotNull(
        if (pick.timeBlocks) n(b.timeBlocks.size, "TimeBlock", "TimeBlocks") else null,
        if (pick.tasks) n(b.tasks, "Task", "Tasks") else null,
        if (pick.events) n(b.events, "Event", "Events") else null,
    ).joinToString(" · ")
}

/**
 * Export, Import and Delete Preview: The Calendar tab's month grid (same rules: Entries per day, TB Total, outlined
 * Tasks/Events), showing only TB2Cal's own data (for Import: The file's). Tap the first and last day of the range,
 * or pick them in "Between … and …" below. Then tick what's included; The action button is at the bottom-right.
 */
@Composable
fun DataScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val mode = remember { DataNav.mode }
    val file = remember { DataNav.bundle }
    if (mode == DataMode.IMPORT && file == null) {
        // The file is gone (the app was restarted): Back to Settings to pick it again.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val settings by Repo.settings.collectAsState()
    val history by Repo.history.collectAsState()
    val items by Repo.items.collectAsState()
    val locale = Locale.getDefault()
    val today = LocalDate.now()
    val todayDay = today.toEpochDay()

    // The range: First tap sets the first day, the second the last one (swapped if earlier), a third starts over.
    // Export: This month to start with; Import: The file's days; Delete: Nothing until chosen.
    var rangeA by rememberSaveable {
        mutableStateOf(
            when (mode) {
                DataMode.EXPORT -> today.withDayOfMonth(1).toEpochDay()
                DataMode.IMPORT -> file!!.from
                DataMode.DELETE -> null
            }
        )
    }
    var rangeB by rememberSaveable {
        mutableStateOf(
            when (mode) {
                DataMode.EXPORT -> today.withDayOfMonth(today.lengthOfMonth()).toEpochDay()
                DataMode.IMPORT -> file!!.to
                DataMode.DELETE -> null
            }
        )
    }
    val from = rangeA
    val to = rangeA?.let { a -> rangeB ?: a }
    fun choose(day: Long) {
        val a = rangeA
        if (a == null || rangeB != null) {
            rangeA = day
            rangeB = null
        } else if (day < a) {
            rangeA = day
            rangeB = a
        } else {
            rangeB = day
        }
    }

    var tickTb by rememberSaveable { mutableStateOf(true) }
    var tickTasks by rememberSaveable { mutableStateOf(true) }
    var tickEvents by rememberSaveable { mutableStateOf(true) }
    val pick = DataTransfer.Pick(tickTb, tickTasks, tickEvents)

    // Shown month: Import opens on this month if the file covers part of it (then all of it is highlighted),
    // otherwise on the file's first month. The arrows keep the highlight (it's by day).
    val startMonth = remember {
        val f = file
        if (f == null) today.withDayOfMonth(1)
        else {
            val m0 = today.withDayOfMonth(1).toEpochDay()
            val m1 = today.withDayOfMonth(today.lengthOfMonth()).toEpochDay()
            if (f.from <= m1 && f.to >= m0) today.withDayOfMonth(1) else LocalDate.ofEpochDay(f.from).withDayOfMonth(1)
        }
    }
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    val month = startMonth.plusMonths(monthOffset.toLong())
    val firstDow = WeekFields.of(locale).firstDayOfWeek
    var gridStart = month
    while (gridStart.dayOfWeek != firstDow) gridStart = gridStart.minusDays(1)
    val gridEnd = gridStart.plusDays(42)

    // Grid data: TB2Cal's own (or the file's), only the ticked kinds; Open undated tasks on today (or the export day).
    val srcHistory = file?.timeBlocks ?: history
    val srcItems = file?.items ?: items
    val openDay = file?.madeOn ?: todayDay
    val shownByDay = remember(gridStart, srcHistory, srcItems, tickTb, tickTasks, tickEvents, settings.tbTotal, settings.taskColor, settings.eventColor) {
        CalendarData.shown(
            CalendarData.sorted(CalendarData.ownByDay(srcHistory, srcItems, gridStart, gridEnd, tickTb, tickTasks, tickEvents, openDay)),
            settings.tbTotal
        )
    }

    // What the action would cover now.
    val selection: DataBundle? = remember(from, to, srcHistory, srcItems, tickTb, tickTasks, tickEvents) {
        if (from == null || to == null) null
        else DataTransfer.collect(srcHistory, srcItems, from, to, pick, openDay)
    }

    var pickingFrom by remember { mutableStateOf(false) }
    var pickingTo by remember { mutableStateOf(false) }
    var askMerge by remember { mutableStateOf<DataBundle?>(null) }
    var askDelete by remember { mutableStateOf(false) }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val b = selection
        if (uri == null || b == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { DataTransfer.write(ctx, uri, b) }.isSuccess }
            Toast.makeText(ctx, if (ok) "Exported: ${dataCounts(b, pick)}." else "Couldn't save the file.", Toast.LENGTH_LONG).show()
            if (ok) onBack()
        }
    }

    fun doImport(overwrite: Boolean) {
        val f = file ?: return
        if (from == null || to == null) return
        val added = runCatching { DataTransfer.import(ctx, f, from, to, pick, overwrite) }.getOrNull()
        Toast.makeText(
            ctx,
            when {
                added == null -> "Couldn't import the data."
                added.isEmpty -> "Nothing new to import; It's already in TB2Cal."
                else -> "Imported: ${dataCounts(added, pick)}."
            },
            Toast.LENGTH_LONG
        ).show()
        if (added != null) onBack()
    }

    fun act() {
        if (from == null || to == null) return
        val b = selection ?: return
        when (mode) {
            DataMode.EXPORT -> runCatching { saver.launch(DataTransfer.fileName(from, to)) }
            DataMode.IMPORT -> {
                val existing = DataTransfer.current(from, to, pick)
                if (existing.isEmpty) doImport(overwrite = false) else askMerge = existing
            }
            DataMode.DELETE -> if (!b.isEmpty) askDelete = true
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // ---- Title ----
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(x = (-8).dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(mode.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Text(
            when (mode) {
                DataMode.EXPORT -> "Tap the first and last day to export. Only TB2Cal's data; Events imported from your Gmail aren't included."
                DataMode.IMPORT -> "The file's days are chosen. Tap to change them. Only TB2Cal's data; Your Gmail isn't affected."
                DataMode.DELETE -> "Tap the first and last day to delete. Only TB2Cal's data; Events imported from your Gmail aren't affected."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ---- Month header ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                month.format(DateTimeFormatter.ofPattern("LLLL", locale)).replaceFirstChar { it.uppercase() } +
                    if (month.year != today.year) " ${month.year}" else "",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (month != today.withDayOfMonth(1)) {
                TextButton(onClick = { monthOffset = (today.year - startMonth.year) * 12 + (today.monthValue - startMonth.monthValue) }) {
                    Text("Today")
                }
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

        // ---- 6 × 7 grid (as in the Calendar tab); The chosen days are highlighted ----
        val line = MaterialTheme.colorScheme.outlineVariant
        val edge = MaterialTheme.colorScheme.primary
        val perDay = settings.dayEntries.coerceIn(3, 5)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
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
                            val day = date.toEpochDay()
                            val inMonth = date.month == month.month
                            val dayItems = shownByDay[date].orEmpty()
                            val inRange = from != null && to != null && day in from..to
                            val isEdge = day == from || day == to
                            Column(
                                Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .border(0.5.dp, line)
                                    .background(if (inRange) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                    .then(if (isEdge) Modifier.border(2.dp, edge) else Modifier)
                                    .clickable { choose(day) }
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
                                    if (dayItems.size > perDay) {
                                        Text(
                                            "+${dayItems.size - perDay}",
                                            fontSize = chipFont,
                                            lineHeight = chipFont,
                                            maxLines = 1,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 1.dp)
                                        )
                                    }
                                }
                                dayItems.take(perDay).forEach { item ->
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

        // ---- Between DD/MM/YY and DD/MM/YY ----
        val df = DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ENGLISH)
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Between", style = MaterialTheme.typography.bodyLarge)
            DateBox(from?.let { LocalDate.ofEpochDay(it).format(df) }) { pickingFrom = true }
            Text("and", style = MaterialTheme.typography.bodyLarge)
            DateBox(to?.let { LocalDate.ofEpochDay(it).format(df) }) { pickingTo = true }
        }

        // ---- Export: TimeBlocks, Tasks, Events ----
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("${mode.action}:", style = MaterialTheme.typography.bodyLarge)
            Tick("TimeBlocks", tickTb) { tickTb = !tickTb }
            Tick("Tasks", tickTasks) { tickTasks = !tickTasks }
            Tick("Events", tickEvents) { tickEvents = !tickEvents }
        }

        // ---- What's included · Action (bottom-right) ----
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    from == null || selection == null -> "Choose the days."
                    !pick.any -> "Tick at least one."
                    selection.isEmpty -> "Nothing in these days."
                    else -> dataCounts(selection, pick)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { act() },
                enabled = pick.any && selection != null && !selection.isEmpty,
                colors = if (mode == DataMode.DELETE) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) else ButtonDefaults.buttonColors()
            ) { Text(mode.action) }
        }
    }

    if (pickingFrom) {
        DayPickerDialog(from ?: todayDay, onPick = { d ->
            rangeA = d
            val b = rangeB
            if (b != null && b < d) rangeB = d
        }, onDismiss = { pickingFrom = false })
    }
    if (pickingTo) {
        DayPickerDialog(to ?: from ?: todayDay, onPick = { d ->
            val a = rangeA
            when {
                a == null -> rangeA = d
                d < a -> { rangeA = d; rangeB = a }
                else -> rangeB = d
            }
        }, onDismiss = { pickingTo = false })
    }

    askMerge?.let { existing ->
        AlertDialog(
            onDismissRequest = { askMerge = null },
            title = { Text("TB2Cal already has data here") },
            text = {
                Text(
                    "These days already have ${dataCounts(existing, pick)}.\n\n" +
                        "Merge: Keeps it and adds the file's data (nothing is added twice).\n" +
                        "Overwrite: Deletes TB2Cal's data of these days first (as picked), then imports the file's.\n\n" +
                        "Events imported from your Gmail aren't affected."
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { askMerge = null; doImport(overwrite = true) }) {
                        Text("Overwrite", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { askMerge = null; doImport(overwrite = false) }) { Text("Merge") }
                }
            },
            dismissButton = { TextButton(onClick = { askMerge = null }) { Text("Cancel") } }
        )
    }

    if (askDelete && from != null && to != null && selection != null) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("Delete from TB2Cal?") },
            text = {
                Text(
                    "${dataCounts(selection, pick)} between ${LocalDate.ofEpochDay(from).format(df)} and " +
                        "${LocalDate.ofEpochDay(to).format(df)} will be deleted from TB2Cal and from TB2Cal's calendar on " +
                        "your phone. Repeating Tasks and Events lose only their days in this range.\n\n" +
                        "This doesn't affect events imported from your Gmail. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askDelete = false
                    val ok = runCatching { DataTransfer.deleteRange(ctx, from, to, pick) }.isSuccess
                    Toast.makeText(ctx, if (ok) "Deleted: ${dataCounts(selection, pick)}." else "Couldn't delete the data.", Toast.LENGTH_LONG).show()
                    if (ok) onBack()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Cancel") } }
        )
    }
}

/** An outlined date to tap (DD/MM/YY until chosen). */
@Composable
private fun DateBox(text: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(
            text ?: "DD/MM/YY",
            style = MaterialTheme.typography.titleSmall,
            color = if (text == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
        )
    }
}

/** A compact checkbox with its label, tappable as a whole. */
@Composable
private fun Tick(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
