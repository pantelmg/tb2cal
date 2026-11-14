package com.pantelmg.tb2cal.ui

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pantelmg.tb2cal.R
import com.pantelmg.tb2cal.model.ItemKind
import com.pantelmg.tb2cal.model.Plan
import com.pantelmg.tb2cal.model.PlanItem
import com.pantelmg.tb2cal.model.Repeat
import com.pantelmg.tb2cal.model.RepeatUnit
import com.pantelmg.tb2cal.plan.Plans
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** What the add/edit/delete flow is showing. One per screen (Calendar tab, or the widget's quick-add). */
class PlanUi {
    var chooser by mutableStateOf(false)
    var newKind by mutableStateOf<ItemKind?>(null)
    /** Editing: The item, and the day when only that day of a repeat is edited. */
    var editing by mutableStateOf<Pair<PlanItem, Long?>?>(null)
    var askEdit by mutableStateOf<Pair<PlanItem, Long>?>(null)
    var askDelete by mutableStateOf<Pair<PlanItem, Long?>?>(null)

    val busy: Boolean get() = chooser || newKind != null || editing != null || askEdit != null || askDelete != null

    fun add() { chooser = true }

    /** Tap on an item: Repeating ones ask This day only / All repeats first. */
    fun edit(item: PlanItem, day: Long?) {
        if (item.repeats && day != null) askEdit = item to day else editing = item to null
    }

    fun delete(item: PlanItem, day: Long?) { askDelete = item to day }
}

/** All the sheets and dialogs of [ui]. [onClosed] runs when a flow ends (saved, deleted or dismissed). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDialogs(ui: PlanUi, defaultDay: Long, onClosed: () -> Unit = {}) {
    val ctx = LocalContext.current
    fun close() = onClosed()

    if (ui.chooser) {
        AddChooserSheet(
            onTask = { ui.chooser = false; ui.newKind = ItemKind.TASK },
            onEvent = { ui.chooser = false; ui.newKind = ItemKind.EVENT },
            onDismiss = { ui.chooser = false; close() }
        )
    }
    when (ui.newKind) {
        ItemKind.TASK -> TaskSheet(
            initial = null,
            onSave = { Plans.save(ctx, it); ui.newKind = null; close() },
            onDismiss = { ui.newKind = null; close() }
        )
        ItemKind.EVENT -> EventEditor(
            initial = null, defaultDay = defaultDay,
            onSave = { Plans.save(ctx, it); ui.newKind = null; close() },
            onDismiss = { ui.newKind = null; close() }
        )
        null -> {}
    }
    ui.askEdit?.let { (item, day) ->
        ScopeDialog(
            title = "Edit ${item.title}",
            text = "It repeats. Edit this day only, or all its repeats?",
            onThisDay = { ui.askEdit = null; ui.editing = item to day },
            onAll = { ui.askEdit = null; ui.editing = item to null },
            onDismiss = { ui.askEdit = null; close() }
        )
    }
    ui.editing?.let { (item, day) ->
        // This day only: Edited as a one-time item on that day; It then leaves the repeat.
        val initial = if (day != null) item.copy(repeat = null, day = day) else item
        val save: (PlanItem) -> Unit = { edited ->
            if (day != null) Plans.editDay(ctx, item, day, edited) else Plans.save(ctx, edited)
            ui.editing = null
            close()
        }
        if (item.kind == ItemKind.TASK) TaskSheet(initial, onSave = save, onDismiss = { ui.editing = null; close() })
        else EventEditor(initial, defaultDay, onSave = save, onDismiss = { ui.editing = null; close() })
    }
    ui.askDelete?.let { (item, day) ->
        if (item.repeats && day != null) {
            ScopeDialog(
                title = "Delete ${item.title}?",
                text = "It repeats. Delete this day only, or all its repeats? This can't be undone.",
                onThisDay = { Plans.deleteDay(ctx, item, day); ui.askDelete = null; close() },
                onAll = { Plans.delete(ctx, item); ui.askDelete = null; close() },
                onDismiss = { ui.askDelete = null; close() }
            )
        } else {
            AlertDialog(
                onDismissRequest = { ui.askDelete = null; close() },
                title = { Text("Delete ${item.title}?") },
                text = { Text("This ${item.kind.label.lowercase()} will be removed. This can't be undone.") },
                confirmButton = {
                    TextButton(onClick = { Plans.delete(ctx, item); ui.askDelete = null; close() }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { ui.askDelete = null; close() }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun ScopeDialog(title: String, text: String, onThisDay: () -> Unit, onAll: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onAll) { Text("All repeats") } },
        dismissButton = { TextButton(onClick = onThisDay) { Text("This day only") } }
    )
}

/** Semi-screen: Add Task or Add Event. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChooserSheet(onTask: () -> Unit, onEvent: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text("Add", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            ChoiceCard(
                painterResource(R.drawable.ic_s_task), Color(Plans.color(ItemKind.TASK)),
                "Task", "Something to tick off: Once, or repeating.", onTask
            )
            Spacer(Modifier.height(10.dp))
            ChoiceCard(
                rememberVectorPainter(Icons.Default.DateRange), Color(Plans.color(ItemKind.EVENT)),
                "Event", "On a date: All day or at a time, once or repeating.", onEvent
            )
        }
    }
}

@Composable
private fun ChoiceCard(icon: Painter, color: Color, title: String, sub: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, color, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(2.dp, color, CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Google-style task sheet: The name, then Repeat… (the only option) and Save. Without a repeat: One-time, no date. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskSheet(initial: PlanItem?, onSave: (PlanItem) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var repeat by remember { mutableStateOf(initial?.repeat) }
    var time by remember { mutableStateOf(initial?.startMin) }
    var showRepeat by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
                .imePadding()
        ) {
            TextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text(if (initial == null) "New task" else "Task") },
                textStyle = MaterialTheme.typography.titleMedium,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
            )
            initial?.day?.let { d ->
                if (repeat == null) {
                    Text(
                        "On ${Plan.shortDate(d)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                AssistChip(
                    onClick = { showRepeat = true },
                    label = {
                        Text(
                            repeat?.let { r -> r.describe() + (time?.let { " · ${Plan.clock(it)}" } ?: "") } ?: "Repeat…",
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_s_clock), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    enabled = title.isNotBlank(),
                    onClick = {
                        val keepDay = if (repeat == null) initial?.day else null
                        onSave(
                            PlanItem(
                                id = initial?.id ?: Plans.newId(),
                                kind = ItemKind.TASK,
                                title = title.trim(),
                                day = keepDay,
                                allDay = time == null,
                                startMin = if (repeat != null || keepDay != null) time else null,
                                repeat = repeat,
                                doneDays = if (repeat != null || keepDay != null) initial?.doneDays.orEmpty() else emptySet(),
                                doneAt = if (repeat == null && keepDay == null) initial?.doneAt else null,
                                skipped = if (repeat != null) initial?.skipped.orEmpty() else emptySet(),
                                createdAt = initial?.createdAt ?: System.currentTimeMillis(),
                            )
                        )
                    }
                ) { Text("Save") }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    if (showRepeat) {
        RepeatDialog(
            initial = repeat, defaultStart = initial?.day ?: Plan.today(), showTime = true, time = time, showStart = true,
            onDone = { r, t -> repeat = r; time = if (r == null) null else t; showRepeat = false },
            onDismiss = { showRepeat = false }
        )
    }
}

/** Full-screen event editor: Name, date, all day or start–end time, Repeat…. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditor(initial: PlanItem?, defaultDay: Long, onSave: (PlanItem) -> Unit, onDismiss: () -> Unit) {
    val nextHour = ((LocalTime.now().hour + 1) % 24) * 60
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var day by remember { mutableStateOf(initial?.day ?: initial?.repeat?.startDay ?: defaultDay) }
    var allDay by remember { mutableStateOf(initial?.allDay ?: false) }
    var start by remember { mutableStateOf(initial?.startMin ?: nextHour) }
    var end by remember { mutableStateOf(initial?.endMin ?: (start + 60).coerceAtMost(23 * 60 + 59)) }
    var repeat by remember { mutableStateOf(initial?.repeat) }
    var pickDay by remember { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var showRepeat by remember { mutableStateOf(false) }
    val valid = title.isNotBlank() && (allDay || end > start)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Cancel") }
                    Text(
                        if (initial == null) "New event" else "Edit event",
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        enabled = valid,
                        onClick = {
                            onSave(
                                PlanItem(
                                    id = initial?.id ?: Plans.newId(),
                                    kind = ItemKind.EVENT,
                                    title = title.trim(),
                                    day = if (repeat == null) day else null,
                                    allDay = allDay,
                                    startMin = if (allDay) null else start,
                                    endMin = if (allDay) null else end,
                                    repeat = repeat?.copy(startDay = day),
                                    skipped = if (repeat != null) initial?.skipped.orEmpty() else emptySet(),
                                    createdAt = initial?.createdAt ?: System.currentTimeMillis(),
                                    providerId = initial?.providerId,
                                )
                            )
                        }
                    ) { Text("Save") }
                }
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, label = { Text("Event name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                LabeledField(if (repeat == null) "Date" else "Starts", Plan.shortDate(day)) { pickDay = true }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("All day", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = allDay, onCheckedChange = { allDay = it })
                }
                if (!allDay) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) { LabeledField("From", Plan.clock(start)) { pickStart = true } }
                        Box(Modifier.weight(1f)) { LabeledField("To", Plan.clock(end), error = end <= start) { pickEnd = true } }
                    }
                }
                AssistChip(
                    onClick = { showRepeat = true },
                    label = { Text(repeat?.describe() ?: "Repeat…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_s_clock), contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }
        }
    }
    if (pickDay) DayPickerDialog(day, onPick = { day = it }, onDismiss = { pickDay = false })
    if (pickStart) ClockPickerDialog(start, onPick = { v ->
        val len = end - start
        start = v
        if (end <= start) end = (v + len.coerceAtLeast(60)).coerceAtMost(23 * 60 + 59)
    }, onDismiss = { pickStart = false })
    if (pickEnd) ClockPickerDialog(end, onPick = { end = it }, onDismiss = { pickEnd = false })
    if (showRepeat) {
        RepeatDialog(
            initial = repeat?.copy(startDay = day), defaultStart = day, showTime = false, time = null, showStart = false,
            onDone = { r, _ -> repeat = r; showRepeat = false },
            onDismiss = { showRepeat = false }
        )
    }
}

/** A field that opens a picker (looks like an outlined text field). */
@Composable
private fun LabeledField(label: String, value: String, error: Boolean = false, onClick: () -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
        ) {
            Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp))
        }
    }
}

/**
 * Repeat screen (like Google's): Every N day/week/month/year, weekdays, optional time (tasks), start date
 * (tasks; events use their own date), and the end: Never, on a date, or after N times.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepeatDialog(
    initial: Repeat?, defaultStart: Long, showTime: Boolean, time: Int?, showStart: Boolean,
    onDone: (Repeat?, Int?) -> Unit, onDismiss: () -> Unit,
) {
    var every by remember { mutableStateOf((initial?.every ?: 1).toString()) }
    var unit by remember { mutableStateOf(initial?.unit ?: RepeatUnit.WEEK) }
    var weekdays by remember { mutableStateOf(initial?.days ?: setOf(LocalDate.ofEpochDay(defaultStart).dayOfWeek.value)) }
    var t by remember { mutableStateOf(time) }
    var start by remember { mutableStateOf(initial?.startDay ?: defaultStart) }
    var endMode by remember { mutableStateOf(if (initial?.endDay != null) 1 else if (initial?.count != null) 2 else 0) }
    var endDay by remember { mutableStateOf(initial?.endDay ?: (defaultStart + 91)) }
    var count by remember { mutableStateOf((initial?.count ?: 13).toString()) }
    var unitMenu by remember { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val n = every.toIntOrNull()?.takeIf { it in 1..999 }
    val c = count.toIntOrNull()?.takeIf { it in 1..999 }
    val valid = n != null && (endMode != 2 || c != null) && (endMode != 1 || endDay >= start) &&
        (unit != RepeatUnit.WEEK || weekdays.isNotEmpty())

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    Text("Repeats", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(
                        enabled = valid,
                        onClick = {
                            onDone(
                                Repeat(
                                    every = n ?: 1, unit = unit,
                                    weekdays = if (unit == RepeatUnit.WEEK) weekdays else emptySet(),
                                    startDay = start,
                                    endDay = if (endMode == 1) endDay else null,
                                    count = if (endMode == 2) c else null,
                                ),
                                t
                            )
                        }
                    ) { Text("Done") }
                }

                Text("Every", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = every, onValueChange = { v -> every = v.filter(Char::isDigit).take(3) },
                        singleLine = true, isError = n == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(80.dp)
                    )
                    Box(Modifier.weight(1f)) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { unitMenu = true }
                        ) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (n == 1) unit.one else unit.many, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                        DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                            RepeatUnit.entries.forEach { u ->
                                DropdownMenuItem(text = { Text(if (n == 1) u.one else u.many) }, onClick = { unit = u; unitMenu = false })
                            }
                        }
                    }
                }
                if (unit == RepeatUnit.WEEK) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        (1..7).forEach { d ->
                            val on = d in weekdays
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (on) Modifier.background(MaterialTheme.colorScheme.primary)
                                        else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    )
                                    .clickable { weekdays = if (on) weekdays - d else weekdays + d },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(2),
                                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
                if (showTime) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            LabeledField("Time (reminder)", t?.let { Plan.clock(it) } ?: "Set time") { pickTime = true }
                        }
                        if (t != null) {
                            IconButton(onClick = { t = null }) { Icon(Icons.Default.Close, contentDescription = "No time") }
                        }
                    }
                }
                HorizontalDivider()
                if (showStart) {
                    LabeledField("Starts", Plan.shortDate(start)) { pickStart = true }
                    HorizontalDivider()
                }
                Text("Ends", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = endMode == 0, onClick = { endMode = 0 })
                    Text("Never", style = MaterialTheme.typography.bodyLarge)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = endMode == 1, onClick = { endMode = 1 })
                    Text("On", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(56.dp))
                    Box(Modifier.weight(1f)) {
                        LabeledField("", Plan.shortDate(endDay), error = endMode == 1 && endDay < start) { endMode = 1; pickEnd = true }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = endMode == 2, onClick = { endMode = 2 })
                    Text("After", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(56.dp))
                    OutlinedTextField(
                        value = count, onValueChange = { v -> count = v.filter(Char::isDigit).take(3); endMode = 2 },
                        singleLine = true, isError = endMode == 2 && c == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(80.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("times", style = MaterialTheme.typography.bodyLarge)
                }
                if (initial != null) {
                    TextButton(onClick = { onDone(null, null) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Don't repeat", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    if (pickStart) DayPickerDialog(start, onPick = { start = it }, onDismiss = { pickStart = false })
    if (pickEnd) DayPickerDialog(endDay, onPick = { endDay = it }, onDismiss = { pickEnd = false })
    if (pickTime) ClockPickerDialog(t ?: 9 * 60, onPick = { t = it }, onDismiss = { pickTime = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPickerDialog(day: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = day * 86_400_000L)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Math.floorDiv(it, 86_400_000L)) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockPickerDialog(minutes: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val state = rememberTimePickerState(minutes / 60, minutes % 60, DateFormat.is24HourFormat(ctx))
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) }
    )
}
