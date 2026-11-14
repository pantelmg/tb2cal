package com.pantelmg.tb2cal.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import com.pantelmg.tb2cal.R
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pantelmg.tb2cal.data.Repo
import com.pantelmg.tb2cal.engine.Engine
import com.pantelmg.tb2cal.reliability.Reliability
import com.pantelmg.tb2cal.service.AlertService
import com.pantelmg.tb2cal.service.Notifications
import com.pantelmg.tb2cal.service.TimeblockService
import com.pantelmg.tb2cal.widget.MonthWidget
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.remember
import java.time.LocalDate
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.pantelmg.tb2cal.calendar.CalendarWriter

enum class Screen { MAIN, CALENDAR, STATS, GOALS, EDIT, PRESETS, SETTINGS, COMPLETION, RELIABILITY, DATA }

class MainActivity : ComponentActivity() {
    private val navRequest = MutableStateFlow<Screen?>(null)
    /** Opened from the widget: Finishing the completion screen goes back to the home screen. */
    private var returnHome = false
    /** Where to go after a waiting timer's completion screen (e.g. the calendar day that was tapped). */
    private var afterCompletion: Screen? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Engine.init(this)
        returnHome = savedInstanceState?.getBoolean(KEY_RETURN_HOME) ?: false
        if (savedInstanceState == null) handle(intent)
        TimeblockService.ensureRunning(this)
        setContent { TB2CalTheme { AppRoot(navRequest, ::completionFinished) } }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_RETURN_HOME, returnHome)
    }

    /**
     * DONE (or Back) on the completion screen: Stops the alert and clears the waiting-completion flag.
     * Returns the screen to show next, or null if the activity closed (back to the home screen).
     */
    private fun completionFinished(): Screen? {
        AlertService.stop(this, keepNotification = false)
        // Also when the alert was already stopped from the notification: Its "TimeBlock completed!" goes away.
        runCatching { getSystemService(android.app.NotificationManager::class.java)?.cancel(Notifications.COMPLETE_ID) }
        if (Repo.settings.value.completionToShow.isNotEmpty()) Repo.updateSettings { it.copy(completionToShow = "") }
        val next = afterCompletion ?: Screen.MAIN
        afterCompletion = null
        if (!returnHome) return next
        returnHome = false
        finish()
        return null
    }

    /** A timer ended and its completion screen hasn't been closed with DONE yet. */
    private fun completionWaiting(): Boolean {
        val id = Repo.settings.value.completionToShow
        if (id.isEmpty()) return false
        if (Repo.pending.value.any { it.id == id }) return true
        Repo.updateSettings { it.copy(completionToShow = "") } // Already gone (e.g. deleted)
        return false
    }

    /** Opens [dest], or first the waiting completion screen and [dest] after DONE. */
    private fun go(dest: Screen) {
        if (dest != Screen.COMPLETION && completionWaiting()) {
            afterCompletion = dest
            navRequest.value = Screen.COMPLETION
        } else {
            navRequest.value = dest
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        com.pantelmg.tb2cal.plan.Plans.onAppResume(this)
        super.onResume()
        // Opening the app (icon, widget, notification…) shows a waiting timer's completion screen first; There DONE
        // stops the alert. Otherwise, opening the app silences the alert.
        if (completionWaiting()) {
            val requested = navRequest.value
            if (requested != Screen.COMPLETION) {
                if (requested != null) afterCompletion = requested
                navRequest.value = Screen.COMPLETION
            }
        } else {
            AlertService.stop(this, keepNotification = false)
        }
        Engine.checkTimerDue(this)
        TimeblockService.ensureRunning(this) // revive if the OS killed it while we were away
        Reliability.resumeTick.value++
        com.pantelmg.tb2cal.widget.Widgets.refreshSoon(this) // Picks up new Gmail events (in the background)
    }

    private fun handle(i: Intent?) {
        if (i?.action == ACTION_OPEN_COMPLETION || i?.action == ACTION_COMPLETE_NOW) {
            returnHome = i.getBooleanExtra(EXTRA_FROM_WIDGET, false)
        }
        when (i?.action) {
            ACTION_OPEN_COMPLETION -> navRequest.value = Screen.COMPLETION
            ACTION_OPEN_MAIN -> go(Screen.MAIN)
            ACTION_OPEN_CALENDAR_DAY -> {
                CalendarNav.day.value = i?.getLongExtra(EXTRA_EPOCH_DAY, LocalDate.now().toEpochDay()) ?: LocalDate.now().toEpochDay()
                go(Screen.CALENDAR)
            }
            ACTION_OPEN_STATS_DAY -> {
                StatsNav.day.value = i?.getLongExtra(EXTRA_EPOCH_DAY, LocalDate.now().toEpochDay()) ?: LocalDate.now().toEpochDay()
                go(Screen.STATS)
            }
            ACTION_COMPLETE_NOW -> {
                Engine.complete(this)
                if (Repo.pending.value.isNotEmpty()) navRequest.value = Screen.COMPLETION
            }
        }
        // Consume so a recreation doesn't replay it.
        setIntent(Intent(this, MainActivity::class.java))
    }

    companion object {
        const val ACTION_OPEN_COMPLETION = "com.pantelmg.tb2cal.action.OPEN_COMPLETION"
        const val ACTION_OPEN_MAIN = "com.pantelmg.tb2cal.action.OPEN_MAIN"
        const val ACTION_COMPLETE_NOW = "com.pantelmg.tb2cal.action.COMPLETE_NOW"
        const val EXTRA_FROM_WIDGET = "from_widget"
        const val ACTION_OPEN_STATS_DAY = "com.pantelmg.tb2cal.action.OPEN_STATS_DAY"
        const val ACTION_OPEN_CALENDAR_DAY = "com.pantelmg.tb2cal.action.OPEN_CALENDAR_DAY"
        const val EXTRA_EPOCH_DAY = "epoch_day"
        private const val KEY_RETURN_HOME = "return_home"
    }
}

@Composable
private fun AppRoot(navRequest: MutableStateFlow<Screen?>, completionFinished: () -> Screen?) {
    var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }
    val request by navRequest.collectAsState()
    LaunchedEffect(request) {
        request?.let {
            screen = it
            navRequest.value = null
        }
    }
    BackHandler(enabled = screen != Screen.MAIN) {
        when (screen) {
            Screen.COMPLETION -> completionFinished()?.let { screen = it }
            Screen.DATA -> screen = Screen.SETTINGS
            else -> screen = Screen.MAIN
        }
    }
    FirstRunPermissions()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (screen) {
            Screen.MAIN, Screen.CALENDAR, Screen.STATS, Screen.GOALS -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (screen) {
                        Screen.CALENDAR -> CalendarScreen()
                        Screen.STATS -> StatsScreen()
                        Screen.GOALS -> GoalsScreen()
                        else -> MainScreen(
                            onEdit = { screen = Screen.EDIT },
                            onEditPresets = { screen = Screen.PRESETS },
                            onSettings = { screen = Screen.SETTINGS },
                            onOpenCompletion = { screen = Screen.COMPLETION },
                            onOpenReliability = { screen = Screen.RELIABILITY },
                        )
                    }
                }
                // Bottom tabs: TimeBlocks · Calendar · Statistics
                NavigationBar {
                    NavigationBarItem(
                        selected = screen == Screen.MAIN,
                        onClick = { screen = Screen.MAIN },
                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        label = { Text("TimeBlocks") }
                    )
                    NavigationBarItem(
                        selected = screen == Screen.CALENDAR,
                        onClick = { screen = Screen.CALENDAR },
                        icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                        label = { Text("Calendar") }
                    )
                    NavigationBarItem(
                        selected = screen == Screen.STATS,
                        onClick = { screen = Screen.STATS },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { Text("Statistics") }
                    )
                    NavigationBarItem(
                        selected = screen == Screen.GOALS,
                        onClick = { screen = Screen.GOALS },
                        icon = { Icon(painterResource(R.drawable.ic_s_goal), contentDescription = null) },
                        label = { Text("Goals") }
                    )
                }
            }
            Screen.EDIT -> EditBlocksScreen(onBack = { screen = Screen.MAIN })
            Screen.PRESETS -> PresetsScreen(onBack = { screen = Screen.MAIN })
            Screen.SETTINGS -> SettingsScreen(
                onBack = { screen = Screen.MAIN },
                onOpenReliability = { screen = Screen.RELIABILITY },
                onOpenData = { screen = Screen.DATA },
            )
            Screen.DATA -> DataScreen(onBack = { screen = Screen.SETTINGS })
            Screen.COMPLETION -> CompletionScreen(onDone = { completionFinished()?.let { screen = it } })
            Screen.RELIABILITY -> ReliabilityScreen(onBack = { screen = Screen.MAIN })
        }
    }
}

/** First launch: Explain, then ask for notifications and calendar access in one go. */
@Composable
private fun FirstRunPermissions() {
    val settings by Repo.settings.collectAsState()
    if (settings.permissionsAsked) return

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        Repo.updateSettings { it.copy(permissionsAsked = true) }
        Reliability.resumeTick.value++
    }
    val perms = buildList {
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        addAll(CalendarWriter.PERMS)
    }.toTypedArray()

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Welcome to TB2Cal") },
        text = {
            Text(
                "TB2Cal needs two permissions:\n\n" +
                    "• Notifications: Shows the running TimeBlock and alerts you when it completes.\n" +
                    "• Calendar: Saves completed TimeBlocks in your phone's calendar.\n\n" +
                    "You can change them later in Settings → Background reliability."
            )
        },
        confirmButton = { TextButton(onClick = { launcher.launch(perms) }) { Text("Continue") } },
        dismissButton = {
            TextButton(onClick = { Repo.updateSettings { it.copy(permissionsAsked = true) } }) { Text("Not now") }
        },
    )
}
