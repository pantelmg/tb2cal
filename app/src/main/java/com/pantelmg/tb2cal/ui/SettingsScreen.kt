package com.pantelmg.tb2cal.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import com.pantelmg.tb2cal.calendar.LanguageCalendar
import androidx.compose.runtime.produceState
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Checkbox
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.pantelmg.tb2cal.R
import com.pantelmg.tb2cal.model.GoogleColors
import com.pantelmg.tb2cal.plan.Plans
import com.pantelmg.tb2cal.calendar.CalendarWriter
import com.pantelmg.tb2cal.data.Repo
import com.pantelmg.tb2cal.data.SoundStore
import com.pantelmg.tb2cal.data.DataTransfer
import android.widget.Toast
import com.pantelmg.tb2cal.model.Vibration
import com.pantelmg.tb2cal.reliability.Reliability
import com.pantelmg.tb2cal.service.AlertService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings, styled like the app's tabs: Big bold title, then grouped cards with a round icon per row.
 * Completion alert (sound, volume, vibration, test) · Appearance (secondary color) · Background reliability ·
 * Calendar access (only if missing).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenReliability: () -> Unit, onOpenData: () -> Unit) {
    val ctx = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val settings by Repo.settings.collectAsState()
    var hasCalPerm by remember { mutableStateOf(CalendarWriter.hasPermission(ctx)) }
    val calPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasCalPerm = CalendarWriter.hasPermission(ctx)
    }

    // ---- Sound: Copied into the app right after picking, so it can't be "forgotten" later ----
    val soundScope = rememberCoroutineScope()
    var soundMsg by remember { mutableStateOf<String?>(null) }
    fun useSound(picked: Uri?) {
        if (picked == null) { // "Silent" in the picker
            SoundStore.cleanup(ctx, keep = null)
            Repo.updateSettings { it.copy(soundUri = "", soundTitle = null) }
            return
        }
        soundMsg = "Saving sound…"
        soundScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { SoundStore.import(ctx, picked) } }
            r.onSuccess { st ->
                Repo.updateSettings { it.copy(soundUri = st.uri, soundTitle = st.title) }
                soundMsg = null
            }.onFailure { soundMsg = "Couldn't read that sound file. Pick another one." }
        }
    }
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            useSound(
                r.data?.let {
                    IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
                }
            )
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) useSound(uri)
    }
    // Data management → Import: Read the picked .t2c file, then open Import Preview with it.
    val dataScope = rememberCoroutineScope()
    val dataFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        dataScope.launch {
            val b = withContext(Dispatchers.IO) { runCatching { DataTransfer.read(ctx, uri) }.getOrNull() }
            when {
                b == null -> Toast.makeText(ctx, "This isn't a TB2Cal data file (.t2c).", Toast.LENGTH_LONG).show()
                b.isEmpty -> Toast.makeText(ctx, "This file has no TB2Cal data.", Toast.LENGTH_LONG).show()
                else -> {
                    DataNav.mode = DataMode.IMPORT
                    DataNav.bundle = b
                    onOpenData()
                }
            }
        }
    }
    fun openData(mode: DataMode) {
        DataNav.mode = mode
        DataNav.bundle = null
        onOpenData()
    }
    val alertRunning by AlertService.runningFlow.collectAsState()
    // Leaving Settings stops a running test alert (a real TimeBlock alert keeps going).
    DisposableEffect(Unit) { onDispose { AlertService.stopTest(ctx) } }
    val dark = isSystemInDarkTheme()

    // Gmail accounts (most likely first) and the chosen account's multi-language calendars, read off the main thread.
    val accounts by produceState(emptyList<String>(), hasCalPerm) {
        value = withContext(Dispatchers.IO) { runCatching { CalendarWriter.gmailAccounts(ctx) }.getOrDefault(emptyList()) }
    }
    val account = settings.gmailAccount.takeIf { it in accounts } ?: accounts.firstOrNull()
    val languages by produceState(emptyList<LanguageCalendar>(), account, hasCalPerm) {
        value = if (account == null) emptyList()
        else withContext(Dispatchers.IO) { runCatching { CalendarWriter.languageCalendars(ctx, account) }.getOrDefault(emptyList()) }
    }

    val soundLabel = remember(settings.soundUri, settings.soundTitle) {
        when (val u = settings.soundUri) {
            "" -> "Silent"
            null -> "Default notification sound"
            else -> settings.soundTitle
                ?: runCatching { RingtoneManager.getRingtone(ctx, Uri.parse(u))?.getTitle(ctx) }.getOrNull()
                    ?.takeIf { !it.all(Char::isDigit) }
                ?: "Custom sound"
        }
    }
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
    }

    fun openSoundPicker() {
        val current: Uri? = when (val u = settings.soundUri) {
            "" -> null
            null -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            else -> Uri.parse(u)
        }
        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_ALARM)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "TimeBlock completed sound")
        runCatching { soundPicker.launch(i) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp)
    ) {
        // ---- Header (same style as the tabs) ----
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(x = (-12).dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Text(
            "Completion alert, secondary color, calendar and Task/Event preferences, data management and background reliability.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ---- Completion alert ----
        SectionLabel("Completion alert")
        Card(Modifier.fillMaxWidth()) {
            SettingRow(
                icon = painterResource(R.drawable.ic_s_sound),
                title = "Sound",
                subtitle = soundLabel,
                subtitleColor = MaterialTheme.colorScheme.primary,
                onClick = { openSoundPicker() },
                trailing = { Chevron() }
            )
            Row(
                Modifier.padding(start = 64.dp, end = 8.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(
                    onClick = { runCatching { filePicker.launch(arrayOf("audio/*")) } },
                    contentPadding = ButtonDefaults.TextButtonWithIconContentPadding
                ) {
                    Icon(painterResource(R.drawable.ic_s_folder), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Audio file…")
                }
                if (settings.soundUri != null) {
                    TextButton(
                        onClick = {
                            SoundStore.cleanup(ctx, keep = null)
                            Repo.updateSettings { it.copy(soundUri = null, soundTitle = null) }
                        },
                        contentPadding = ButtonDefaults.TextButtonWithIconContentPadding
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reset to default")
                    }
                }
            }
            soundMsg?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 72.dp, end = 16.dp, bottom = 8.dp)
                )
            }
            RowDivider()
            SettingRow(
                icon = painterResource(R.drawable.ic_s_volume),
                title = "Use media volume",
                subtitle = if (settings.useMediaVolume) "Plays at the multimedia volume.\nRings even in silent mode."
                else "Plays at the notification volume.\nFollows silent and vibrate mode.",
                onClick = { Repo.updateSettings { it.copy(useMediaVolume = !it.useMediaVolume) } },
                trailing = {
                    Switch(
                        checked = settings.useMediaVolume,
                        onCheckedChange = { v -> Repo.updateSettings { it.copy(useMediaVolume = v) } }
                    )
                }
            )
            RowDivider()
            SettingRow(
                icon = painterResource(R.drawable.ic_s_vibration),
                title = "Vibration",
                subtitle = "Tap an option to feel it."
            )
            Column(Modifier.padding(start = 56.dp, end = 8.dp, bottom = 8.dp)) {
                Vibration.entries.forEach { v ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                Repo.updateSettings { it.copy(vibration = v) }
                                AlertService.preview(ctx, v)
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = settings.vibration == v, onClick = null, modifier = Modifier.padding(8.dp))
                        Text(v.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(
            onClick = {
                if (alertRunning) AlertService.stop(ctx, keepNotification = false)
                else AlertService.start(ctx, "Test", test = true)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                if (alertRunning) Icons.Default.Close else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(if (alertRunning) "Stop alert" else "Test alert")
        }

        // ---- Appearance ----
        SectionLabel("Appearance")
        Card(Modifier.fillMaxWidth()) {
            SettingRow(
                icon = painterResource(R.drawable.ic_s_palette),
                title = "Secondary color",
                subtitle = "Buttons, switches and highlights: " +
                    (Accents.byKey(settings.accent)?.name ?: "Phone's colors")
            )
            // "Phone's colors" (wallpaper, the default) first, then the offered colors.
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val options: List<Accents.Accent?> = listOf<Accents.Accent?>(null) + Accents.all
                options.forEach { a ->
                    val key = a?.key ?: ""
                    val chosen = settings.accent == key || (a == null && Accents.byKey(settings.accent) == null)
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .then(
                                if (a == null) Modifier.background(
                                    Brush.sweepGradient(
                                        listOf(Color(0xFFD0BCFF), Color(0xFFAAC7FF), Color(0xFF82D5C8), Color(0xFFDCC66E), Color(0xFFFFB0CA), Color(0xFFD0BCFF))
                                    )
                                ) else Modifier.background(Color(if (dark) a.dark.primary else a.light.primary))
                            )
                            .border(
                                if (chosen) 3.dp else 1.dp,
                                if (chosen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape
                            )
                            .clickable { Repo.updateSettings { it.copy(accent = key) } },
                        contentAlignment = Alignment.Center
                    ) {
                        if (chosen) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = a?.name ?: "Phone's colors",
                                tint = if (a == null) Color.Black else Color(if (dark) a.dark.onPrimary else a.light.onPrimary),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // ---- Calendar preferences ----
        SectionLabel("Calendar preferences")
        Card(Modifier.fillMaxWidth()) {
            SettingRow(
                icon = rememberVectorPainter(Icons.Default.DateRange),
                title = "In-app Calendar: TimeBlocks/day",
                subtitle = "Widget calendar's TimeBlock capacity increases with its size."
            )
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 72.dp, end = 16.dp, bottom = 12.dp)
            ) {
                listOf(3, 4, 5).forEachIndexed { i, n ->
                    SegmentedButton(
                        selected = settings.dayEntries == n,
                        onClick = { Repo.updateSettings { it.copy(dayEntries = n) } },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = 3)
                    ) { Text("$n") }
                }
            }
            RowDivider()

            // 1. Which Gmail account (the most likely one first and picked until you choose).
            SettingRow(
                icon = rememberVectorPainter(Icons.Default.Email),
                title = "Gmail account",
                subtitle = "Its events are shown when Import from your Gmail is on."
            )
            Column(Modifier.padding(start = 56.dp, end = 8.dp, bottom = 8.dp)) {
                when {
                    !hasCalPerm -> Hint("Needs calendar access.")
                    accounts.isEmpty() -> Hint("No Google account with a calendar on this phone.")
                    else -> accounts.forEach { a ->
                        ChoiceRow(a, selected = a == account, radio = true) {
                            Repo.updateSettings { it.copy(gmailAccount = a) }
                        }
                    }
                }
            }
            RowDivider()

            // 2. Languages of the calendars that publish the same events in several languages (e.g. holidays).
            SettingRow(
                icon = painterResource(R.drawable.ic_s_language),
                title = "Gmail import: Languages",
                subtitle = "For events in several languages, like holidays. Pick any, or none; Your other events " +
                    "are always shown."
            )
            Column(Modifier.padding(start = 56.dp, end = 8.dp, bottom = 8.dp)) {
                if (languages.isEmpty()) Hint("This account has no events in several languages.")
                languages.forEach { l ->
                    ChoiceRow("${l.language} · ${l.name}", selected = l.key !in settings.holidayOff, radio = false) {
                        Repo.updateSettings {
                            it.copy(holidayOff = if (l.key in it.holidayOff) it.holidayOff - l.key else it.holidayOff + l.key)
                        }
                    }
                }
            }
            RowDivider()

            // 3. Hiding Gmail events (Hide next to them in the Calendar tab).
            SettingRow(
                icon = painterResource(R.drawable.ic_s_hide),
                title = "Allow hiding Gmail events",
                subtitle = "Hide appears next to Gmail events in the Calendar tab. Hidden events come back when " +
                    "Import from your Gmail is turned off and on.",
                onClick = { Repo.updateSettings { it.copy(allowHide = !it.allowHide) } },
                trailing = {
                    Switch(
                        checked = settings.allowHide,
                        onCheckedChange = { v -> Repo.updateSettings { it.copy(allowHide = v) } }
                    )
                }
            )
            RowDivider()

            // Gmail events also on the Task/Event manager widget (Events list).
            SettingRow(
                icon = painterResource(R.drawable.ic_w_target),
                title = "Gmail events on the Task/Event widget",
                subtitle = "Today's Gmail events also show in the widget's Events list (with Import from your Gmail on).",
                onClick = { Repo.updateSettings { it.copy(widgetGmail = !it.widgetGmail) } },
                trailing = {
                    Switch(checked = settings.widgetGmail, onCheckedChange = { v -> Repo.updateSettings { it.copy(widgetGmail = v) } })
                }
            )
            RowDivider()

            // Completed TimeBlocks and Events in other calendar apps (TB2Cal's phone calendar shown or hidden).
            SettingRow(
                icon = rememberVectorPainter(Icons.Default.DateRange),
                title = "Visible to other calendar apps",
                subtitle = "Completed TimeBlocks and Events also show in your phone's other calendar apps.",
                onClick = {
                    Repo.updateSettings { it.copy(othersSeeCalendar = !it.othersSeeCalendar) }
                    Plans.applyVisibility(ctx)
                },
                trailing = {
                    Switch(
                        checked = settings.othersSeeCalendar,
                        onCheckedChange = { v ->
                            Repo.updateSettings { it.copy(othersSeeCalendar = v) }
                            Plans.applyVisibility(ctx)
                        }
                    )
                }
            )
            RowDivider()

            // One color for all Tasks and one for all Events (presets only; changes past and future ones).
            SettingRow(
                icon = painterResource(R.drawable.ic_s_task),
                title = "Task and Event colors",
                subtitle = "All tasks share one color, and all events another (outlined in the calendars)."
            )
            ColorChoiceRow("Tasks", settings.taskColor) { key -> Repo.updateSettings { it.copy(taskColor = key) } }
            ColorChoiceRow("Events", settings.eventColor) { key ->
                Repo.updateSettings { it.copy(eventColor = key) }
                Plans.resyncEvents(ctx)
            }
            Spacer(Modifier.height(12.dp))
        }

        // ---- Data management (TB2Cal's own data only) ----
        SectionLabel("Data management")
        Card(Modifier.fillMaxWidth()) {
            Text(
                "Only TB2Cal's own TimeBlocks, Tasks and Events. Events imported from your Gmail stay in your Google " +
                    "account; They're never exported, imported or deleted here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp)
            )
            SettingRow(
                icon = painterResource(R.drawable.ic_s_export),
                title = "Export",
                subtitle = "Choose the days and save them as a .t2c file.",
                onClick = { openData(DataMode.EXPORT) },
                trailing = { Chevron() }
            )
            RowDivider()
            SettingRow(
                icon = painterResource(R.drawable.ic_s_import),
                title = "Import",
                subtitle = "Open a .t2c file, check its days, then merge or overwrite.",
                onClick = { runCatching { dataFilePicker.launch(arrayOf("*/*")) } },
                trailing = { Chevron() }
            )
            RowDivider()
            SettingRow(
                icon = painterResource(R.drawable.ic_s_delete),
                title = "Delete",
                subtitle = "Choose the days to delete from TB2Cal.",
                onClick = { openData(DataMode.DELETE) },
                trailing = { Chevron() }
            )
        }

        // ---- Background ----
        SectionLabel("Background")
        Card(Modifier.fillMaxWidth()) {
            SettingRow(
                icon = painterResource(R.drawable.ic_s_shield),
                title = "Background reliability",
                subtitle = "Keep Android's battery manager from stopping running TimeBlocks.",
                onClick = onOpenReliability,
                trailing = { Chevron() }
            )
        }

        // ---- Calendar access (only when it's missing) ----
        if (!hasCalPerm) {
            SectionLabel("Calendar")
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                SettingRow(
                    icon = rememberVectorPainter(Icons.Default.DateRange),
                    title = "Calendar access",
                    subtitle = "Needed to save completed TimeBlocks in your phone's calendar.",
                    onClick = { calPerm.launch(CalendarWriter.PERMS) },
                    trailing = { Button(onClick = { calPerm.launch(CalendarWriter.PERMS) }) { Text("Allow") } }
                )
            }
        }

        // ---- About ----
        Spacer(Modifier.height(28.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AppSymbol(32.dp)
            Spacer(Modifier.height(6.dp))
            Text(
                if (version.isNotEmpty()) "TB2Cal v$version" else "TB2Cal",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "TimeBlocks to Calendar",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "by github.com/pantelmg",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clickable { uriHandler.openUri("https://github.com/pantelmg") }
            )
        }
    }
}

/** Small primary label above a group of settings. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp)
    )
}

/** One setting: Round tonal icon, title and description, then a switch, button or arrow at the end. */
@Composable
private fun SettingRow(
    icon: Painter,
    title: String,
    subtitle: String? = null,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun Chevron() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        Modifier.padding(start = 72.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 6.dp)
    )
}

/** A radio (one choice) or checkbox (any number) row, tappable as a whole. */
@Composable
private fun ChoiceRow(text: String, selected: Boolean, radio: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (radio) RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
        else Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.padding(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A row of the preset colors (no custom ones), for Tasks or Events. */
@Composable
private fun ColorChoiceRow(label: String, current: String, onPick: (String) -> Unit) {
    Column(Modifier.padding(start = 72.dp, end = 16.dp, bottom = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GoogleColors.all.forEach { gc ->
                val c = Color(gc.argb)
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .border(if (gc.key == current) 3.dp else 2.dp, if (gc.key == current) MaterialTheme.colorScheme.onSurface else c, CircleShape)
                        .clickable { onPick(gc.key) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(if (gc.key == current) 14.dp else 0.dp).clip(CircleShape).background(c))
                }
            }
        }
    }
}
