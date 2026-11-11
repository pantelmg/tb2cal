package com.pantelmg.tb2cal.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.pantelmg.tb2cal.calendar.CalendarWriter
import com.pantelmg.tb2cal.data.Repo
import com.pantelmg.tb2cal.data.SoundStore
import com.pantelmg.tb2cal.model.Vibration
import com.pantelmg.tb2cal.reliability.Reliability
import com.pantelmg.tb2cal.service.AlertService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenReliability: () -> Unit) {
    val ctx = LocalContext.current
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
    val alertRunning by AlertService.runningFlow.collectAsState()

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            SectionTitle("Completion alert")

            // ---- Sound ----
            Text("Sound", style = MaterialTheme.typography.titleSmall)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        val current: Uri? = when (val u = settings.soundUri) {
                            "" -> null
                            null -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                            else -> Uri.parse(u)
                        }
                        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(
                                RingtoneManager.EXTRA_RINGTONE_TYPE,
                                RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_ALARM
                            )
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "TimeBlock completed sound")
                        runCatching { soundPicker.launch(i) }
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(soundLabel, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                if (settings.soundUri != null) {
                    TextButton(onClick = {
                        SoundStore.cleanup(ctx, keep = null)
                        Repo.updateSettings { it.copy(soundUri = null, soundTitle = null) }
                    }) {
                        Text("Reset to default")
                    }
                }
            }
            TextButton(onClick = { runCatching { filePicker.launch(arrayOf("audio/*")) } }) {
                Text("Choose an audio file…")
            }
            soundMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }

            // ---- Volume ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { Repo.updateSettings { it.copy(useMediaVolume = !it.useMediaVolume) } }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Use media volume", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (settings.useMediaVolume) "Plays at the multimedia volume. Rings even in silent mode."
                        else "Plays at the notification volume. Follows silent and vibrate mode.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.useMediaVolume,
                    onCheckedChange = { v -> Repo.updateSettings { it.copy(useMediaVolume = v) } }
                )
            }

            // ---- Vibration ----
            Spacer(Modifier.height(8.dp))
            Text("Vibration", style = MaterialTheme.typography.titleSmall)
            Vibration.entries.forEach { v ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            Repo.updateSettings { it.copy(vibration = v) }
                            AlertService.preview(ctx, v)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = settings.vibration == v, onClick = null)
                    Text(v.label, modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 12.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                if (alertRunning) AlertService.stop(ctx, keepNotification = false)
                else AlertService.start(ctx, "Test", test = true)
            }) { Text(if (alertRunning) "Stop alert" else "Test alert") }

            // ---- Reliability ----
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            SectionTitle("Background reliability")
            Text(
                "Stop ${Reliability.oemName}'s battery manager from killing running TimeBlocks " +
                    "(battery optimization, autostart, battery saver).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenReliability) { Text("Open checklist") }

            // ---- Calendar access ----
            if (!hasCalPerm) {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                SectionTitle("Calendar")
                Text(
                    "Calendar access is needed to save completed TimeBlocks in your phone's calendar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { calPerm.launch(CalendarWriter.PERMS) }) { Text("Allow calendar access") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
    )
}
