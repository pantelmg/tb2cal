package com.pantelmg.tb2cal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pantelmg.tb2cal.data.Repo
import com.pantelmg.tb2cal.reliability.Reliability
import com.pantelmg.tb2cal.calendar.CalendarWriter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReliabilityScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val settings by Repo.settings.collectAsState()
    val tick by Reliability.resumeTick.collectAsState()

    // Re-read every time the user returns from a system settings page.
    val notif = remember(tick) { Reliability.notificationsOk(ctx) }
    val battery = remember(tick) { Reliability.batteryUnrestricted(ctx) }
    val bgRestricted = remember(tick) { Reliability.backgroundRestricted(ctx) }
    val exact = remember(tick) { Reliability.exactAlarmsOk(ctx) }
    val sync = remember(tick) { Reliability.syncOk(ctx) }
    val gmail = settings.gmailView // Account sync only matters when the Calendar tab shows Gmail events
    var calendar by remember(tick) { mutableStateOf(Reliability.calendarOk(ctx)) }
    var calendarAsked by remember { mutableStateOf(false) }
    val calendarPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        calendar = Reliability.calendarOk(ctx)
        calendarAsked = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Background reliability") },
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Android can stop apps running in the background. These settings keep the " +
                    "timer, its notification and the completion alert alive with the screen off or after " +
                    "clearing Recents. Items with a checkbox can't be detected — Tick them once done.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            CheckCard(
                title = "Notifications allowed",
                body = "Needed for the running-TimeBlock notification and the completion alert.",
                done = notif,
                actionLabel = "Open",
                onAction = { Reliability.openNotificationSettings(ctx) },
            )
            CheckCard(
                title = "Calendar access",
                body = "Needed to save completed TimeBlocks in your phone's calendar.",
                done = calendar,
                actionLabel = "Allow",
                // After a denial Android stops showing the prompt, so go to App info → Permissions.
                onAction = {
                    if (calendarAsked) Reliability.openAppDetails(ctx) else calendarPerm.launch(CalendarWriter.PERMS)
                },
            )
            CheckCard(
                title = "Battery optimization: Unrestricted",
                body = "Lets TB2Cal keep running and wake up exactly when a timer ends.",
                done = battery,
                actionLabel = "Allow",
                onAction = { Reliability.requestBatteryUnrestricted(ctx) },
            )
            if (bgRestricted) {
                CheckCard(
                    title = "Background activity restricted",
                    body = "Android is blocking TB2Cal in the background. Open App info → Battery and remove the restriction.",
                    done = false,
                    actionLabel = "App info",
                    onAction = { Reliability.openAppDetails(ctx) },
                )
            }
            if (!exact) {
                CheckCard(
                    title = "Alarms & reminders",
                    body = "Required for the timer to finish on time.",
                    done = false,
                    actionLabel = "Allow",
                    onAction = { Reliability.openExactAlarmSettings(ctx) },
                )
            }
            if (Reliability.hasAutostartManager) {
                CheckCard(
                    title = "Autostart",
                    body = if (Reliability.isXiaomi)
                        "Security → Autostart → turn on TB2Cal. Without it, MIUI/HyperOS blocks the timer alarm and restart after the app is killed."
                    else
                        "Allow TB2Cal to start automatically / run in the background in your phone's app launch manager.",
                    done = settings.ackAutostart,
                    actionLabel = "Open",
                    onAction = { Reliability.openAutostart(ctx) },
                    manual = { v -> Repo.updateSettings { it.copy(ackAutostart = v) } },
                )
            }
            if (Reliability.isXiaomi) {
                CheckCard(
                    title = "Battery saver: No restrictions",
                    body = "App info → Battery saver → No restrictions. MIUI's own saver ignores Android's setting above.",
                    done = settings.ackBatterySaver,
                    actionLabel = "Open",
                    onAction = { Reliability.openXiaomiBatterySaver(ctx) },
                    manual = { v -> Repo.updateSettings { it.copy(ackBatterySaver = v) } },
                )
            }
            if (gmail) CheckCard(
                title = "Auto-sync and Calendar sync",
                body = "Accounts & sync: Auto-sync on, and Calendar sync on for your Gmail account. " +
                    "Without them, your Gmail events in TB2Cal's Calendar don't stay up to date.",
                done = sync,
                actionLabel = "Open",
                onAction = { Reliability.openSyncSettings(ctx) },
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CheckCard(
    title: String,
    body: String,
    done: Boolean,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    manual: ((Boolean) -> Unit)? = null,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (done) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = if (done) "Done" else "Needs attention",
                    tint = if (done) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
            val showAction = actionLabel != null && (!done || manual != null)
            if (showAction || manual != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showAction) {
                        FilledTonalButton(onClick = onAction) { Text(actionLabel ?: "") }
                    }
                    if (manual != null) {
                        Spacer(Modifier.weight(1f))
                        Checkbox(checked = done, onCheckedChange = manual)
                        Text("Done", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
