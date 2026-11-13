package com.pantelmg.tb2cal.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.AssistChip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import com.pantelmg.tb2cal.data.Repo
import com.pantelmg.tb2cal.engine.Engine
import com.pantelmg.tb2cal.model.Fmt
import com.pantelmg.tb2cal.model.Mode
import com.pantelmg.tb2cal.model.Status
import com.pantelmg.tb2cal.reliability.Reliability
import com.pantelmg.tb2cal.service.TimeblockService
import kotlinx.coroutines.delay
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onEdit: () -> Unit,
    onEditPresets: () -> Unit,
    onSettings: () -> Unit,
    onOpenCompletion: () -> Unit,
    onOpenReliability: () -> Unit,
) {
    val ctx = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val session by Engine.state.collectAsState()
    val blocks by Repo.blocks.collectAsState()
    val settings by Repo.settings.collectAsState()
    val pending by Repo.pending.collectAsState()
    val tick by Reliability.resumeTick.collectAsState()
    val reliabilityIssues = remember(tick, settings) { Reliability.openIssues(ctx, settings) }
    val locked = session.active
    val density = LocalDensity.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
    }
    // Where the bottom of the "TB2Cal" letters is (px from the header's top), for lining up the version.
    var titleTopPx by remember { mutableFloatStateOf(0f) }
    var titleBaselinePx by remember { mutableFloatStateOf(0f) }

    // TimeBlock choice and mode live in settings, so the app and the widget always agree.
    val selected = blocks.firstOrNull { it.id == settings.lastBlockId } ?: blocks.firstOrNull()
    val mode = settings.lastMode
    // Each TimeBlock keeps its own countdown: The fields reload when another TimeBlock is chosen
    // (here or on the widget), and every change is saved for that TimeBlock right away.
    val savedMin = settings.timerMinutes(selected?.id)
    var hours by rememberSaveable(selected?.id) { mutableStateOf((savedMin / 60).toString()) }
    var minutes by rememberSaveable(selected?.id) { mutableStateOf((savedMin % 60).toString()) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }

    val h = hours.toIntOrNull()
    val m = minutes.toIntOrNull()
    val validTime = h != null && m != null && h in 0..23 && m in 0..59 && (h > 0 || m > 0)
    val targetMs = if (validTime) (h!! * 60L + m!!) * 60_000L else 0L

    // UI clock
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(session.status, session.runningSinceElapsed) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            val s = Engine.state.value
            if (s.status != Status.RUNNING) break
            if (s.mode == Mode.TIMER && s.remainingMs(now) <= 0) {
                Engine.checkTimerDue(ctx)
                break
            }
            delay(250)
        }
    }

    /** Saves the selected TimeBlock's countdown; A null part keeps its saved value. */
    fun saveTimer(newH: Int?, newM: Int?) {
        val id = selected?.id ?: return
        Repo.updateSettings { s ->
            val cur = s.timerMinutes(id)
            val total = (newH ?: (cur / 60)) * 60 + (newM ?: (cur % 60))
            s.copy(blockTimers = s.blockTimers + (id to total))
        }
    }

    fun startNow() {
        val b = selected ?: return
        Repo.updateSettings { it.copy(lastBlockId = b.id) }
        if (mode == Mode.TIMER) saveTimer(h, m)
        Engine.start(ctx, b, mode, targetMs)
        TimeblockService.ensureRunning(ctx)
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        startNow() // Start either way; Without permission the notification is just hidden.
    }

    // Values shown: The live session when locked, the user's choices otherwise.
    val shownMode = if (locked) session.mode else mode
    val shownName = if (locked) session.blockName else selected?.name ?: ""
    val shownColor = if (locked) session.colorKey else selected?.colorKey
    val shownH = if (locked) (session.targetMs / 3_600_000).toString() else hours
    val shownM = if (locked) ((session.targetMs / 60_000) % 60).toString() else minutes

    val clockText = when {
        locked && session.mode == Mode.TIMER -> Fmt.clock(session.remainingMs(now), roundUp = true)
        locked -> Fmt.clock(session.elapsedMs(now))
        mode == Mode.TIMER -> Fmt.clock(targetMs)
        else -> Fmt.clock(0)
    }
    val tf = DateFormat.getTimeFormat(ctx)
    val clockCaption = when {
        !locked -> "Ready"
        session.status == Status.PAUSED -> "Paused"
        session.mode == Mode.TIMER ->
            "Ends at ${tf.format(Date(System.currentTimeMillis() + session.remainingMs(now)))}"
        else -> "Started at ${tf.format(Date(session.startWallMs))}"
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(start = 20.dp, end = 20.dp, top = 10.dp)
    ) {
        // ---- Header ----
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "TB2Cal",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        onTextLayout = { titleBaselinePx = it.firstBaseline },
                        modifier = Modifier.onGloballyPositioned { titleTopPx = it.positionInParent().y }
                    )
                    Spacer(Modifier.width(12.dp))
                    AppSymbol(40.dp)
                }
                Text("TimeBlocks to Calendar", style = MaterialTheme.typography.titleMedium)
                Text(
                    "by github.com/pantelmg",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable { uriHandler.openUri("https://github.com/pantelmg") }
                )
            }
            // Version and settings gear, with the widget tip in a small outlined box under them. The version
            // starts where the box starts and sits on the same line as the bottom of the "TB2Cal" letters.
            Column(Modifier.width(IntrinsicSize.Max)) {
                Row(Modifier.fillMaxWidth()) {
                    val baseline = with(density) { (titleTopPx + titleBaselinePx).toDp() }
                    Text(
                        if (version.isNotEmpty()) "v$version" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = if (baseline > 0.dp) Modifier.paddingFromBaseline(top = baseline) else Modifier
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                }
                Text(
                    "Tip: Use the app's widgets\nfor efficiency!",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    softWrap = false,
                    maxLines = 2,
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Create TimeBlocks, with your own conditions, to track and improve productivity. " +
                "Completed TimeBlocks are registered in the app and the local Calendar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ---- Pending completion banner ----
        val unregistered = pending.filterNot { it.registered }
        if (unregistered.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Card(
                onClick = onOpenCompletion,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text("TimeBlock completed!", fontWeight = FontWeight.SemiBold)
                    Text(
                        unregistered.joinToString(", ") { it.label } +
                            if (settings.autoRegister) " — Couldn't be added automatically. Tap to retry."
                            else " — Tap to transfer to calendar.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // ---- Background reliability nudge ----
        if (reliabilityIssues > 0 && !settings.hideReliabilityCard) {
            Spacer(Modifier.height(10.dp))
            Card(
                onClick = onOpenReliability,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Keep TimeBlocks running", fontWeight = FontWeight.SemiBold)
                        Text(
                            "$reliabilityIssues setting(s) may let Android stop a running timer. Tap to fix.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(onClick = { Repo.updateSettings { it.copy(hideReliabilityCard = true) } }) { Text("Hide") }
                }
            }
        }

        // ---- Start a TimeBlock ----
        Spacer(Modifier.height(14.dp))
        Text("Start a TimeBlock:", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExposedDropdownMenuBox(
                expanded = menuOpen && !locked,
                onExpandedChange = { if (!locked) menuOpen = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = shownName,
                    onValueChange = {},
                    readOnly = true,
                    enabled = !locked,
                    singleLine = true,
                    leadingIcon = { ColorDot(shownColor, 18.dp) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuOpen && !locked) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = !locked)
                )
                ExposedDropdownMenu(expanded = menuOpen && !locked, onDismissRequest = { menuOpen = false }) {
                    blocks.forEach { b ->
                        DropdownMenuItem(
                            text = { Text(b.name) },
                            leadingIcon = { ColorDot(b.colorKey, 18.dp) },
                            onClick = {
                                Repo.updateSettings { it.copy(lastBlockId = b.id) }
                                menuOpen = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(onClick = onEdit, enabled = !locked) {
                Icon(Icons.Default.Edit, contentDescription = "Edit TimeBlocks")
            }
        }

        // ---- TimeBlock mode ----
        Spacer(Modifier.height(12.dp))
        Text("TimeBlock mode:", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Mode.entries.forEachIndexed { i, md ->
                SegmentedButton(
                    selected = shownMode == md,
                    onClick = { Repo.updateSettings { it.copy(lastMode = md) } },
                    enabled = !locked,
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = Mode.entries.size)
                ) { Text(md.label) }
            }
        }

        if (shownMode == Mode.TIMER) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = shownH,
                    onValueChange = { v ->
                        hours = v.filter(Char::isDigit).take(2)
                        hours.toIntOrNull()?.takeIf { it in 0..23 }?.let { n -> saveTimer(n, null) }
                    },
                    label = { Text("Hours") },
                    enabled = !locked,
                    singleLine = true,
                    isError = !locked && (h == null || h !in 0..23),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = shownM,
                    onValueChange = { v ->
                        minutes = v.filter(Char::isDigit).take(2)
                        minutes.toIntOrNull()?.takeIf { it in 0..59 }?.let { n -> saveTimer(null, n) }
                    },
                    label = { Text("Minutes") },
                    enabled = !locked,
                    singleLine = true,
                    isError = !locked && (m == null || m !in 0..59),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            // Presets scroll sideways; The Edit chip stays fixed at the right end.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .clipToBounds()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    settings.presets.forEach { mins ->
                        SuggestionChip(
                            onClick = {
                                hours = (mins / 60).toString()
                                minutes = (mins % 60).toString()
                                saveTimer(mins / 60, mins % 60)
                            },
                            enabled = !locked,
                            label = { Text(Fmt.duration(mins * 60_000L)) }
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                // Edit the preset list (own screen).
                AssistChip(
                    onClick = onEditPresets,
                    enabled = !locked,
                    label = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }
        }

        // ---- Clock: Takes the free space, so the buttons below always sit at the bottom ----
        // The digits are sized to the space that's left (and the caption drops out when it's tight),
        // so the clock is always drawn whole, whatever the screen, font size or cards above it.
        // Gaps: 12dp everywhere around the clock and the buttons (the chips, segmented buttons and buttons
        // already add 4–8dp of invisible touch area, so the spacers make up the rest).
        Spacer(Modifier.height(if (shownMode == Mode.TIMER) 4.dp else 8.dp))
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val captionH = with(density) { MaterialTheme.typography.bodyMedium.lineHeight.toDp() }
            val showCaption = maxHeight >= captionH + 56.dp
            val room = maxHeight - 20.dp - (if (showCaption) captionH else 0.dp)
            val byHeight = with(density) { (room / 1.2f).toSp() }.value
            val byWidth = with(density) { ((maxWidth - 32.dp) / 4.4f).toSp() }.value
            val clockSp = minOf(52f, byHeight, byWidth).coerceAtLeast(12f).sp
            Card(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        clockText,
                        fontSize = clockSp,
                        lineHeight = clockSp * 1.2f,
                        maxLines = 1,
                        softWrap = false,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    if (showCaption) {
                        Text(
                            clockCaption,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ---- Controls (same place in both modes) ----
        Spacer(Modifier.height(8.dp))
        if (shownMode == Mode.STOPWATCH) {
            Button(
                onClick = { if (Engine.complete(ctx) != null) onOpenCompletion() },
                enabled = locked,
                modifier = Modifier.fillMaxWidth()
            ) { Text("COMPLETE") }
            Spacer(Modifier.height(4.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val needsAsk = Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                        ctx, Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                    if (needsAsk) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else startNow()
                },
                enabled = !locked && selected != null && (mode == Mode.STOPWATCH || validTime),
                modifier = Modifier.weight(1f)
            ) { Text("START") }
            FilledTonalButton(
                onClick = {
                    if (session.status == Status.RUNNING) {
                        Engine.pause(ctx)
                    } else {
                        Engine.resume(ctx)
                        TimeblockService.ensureRunning(ctx)
                    }
                },
                enabled = locked,
                modifier = Modifier.weight(1f)
            ) { Text(if (session.status == Status.PAUSED) "RESUME" else "PAUSE", maxLines = 1) }
            OutlinedButton(
                onClick = { confirmStop = true },
                enabled = locked,
                modifier = Modifier.weight(1f)
            ) { Text("STOP") }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Stop TimeBlock?") },
            text = { Text("The current TimeBlock will be discarded and not added to your calendar.") },
            confirmButton = {
                TextButton(onClick = {
                    Engine.stop(ctx)
                    confirmStop = false
                }) { Text("STOP") }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Cancel") } }
        )
    }
}
