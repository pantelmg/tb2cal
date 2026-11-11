package com.pantelmg.tb2cal.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pantelmg.tb2cal.data.Repo
import com.pantelmg.tb2cal.engine.Engine
import com.pantelmg.tb2cal.model.Mode
import com.pantelmg.tb2cal.model.Session
import com.pantelmg.tb2cal.model.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Keeps the TimeBlock alive and mirrors Engine state into the permanent notification. */
class TimeblockService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collector: Job? = null
    private var dueJob: Job? = null
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        Engine.init(this)
        when (intent?.action) {
            ACTION_START -> startFromSettings()
            ACTION_PAUSE -> Engine.pause(this)
            ACTION_RESUME -> Engine.resume(this)
            ACTION_STOP -> Engine.stop(this)
        }
        if (!goForeground(Notifications.ongoing(this, Engine.state.value))) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (collector == null) {
            collector = scope.launch { Engine.state.collect { render(it) } }
        }
        return START_STICKY
    }

    /** Widget START: Uses the TimeBlock and mode last chosen in the app or widget, with that TimeBlock's own countdown. */
    private fun startFromSettings() {
        val st = Repo.settings.value
        val blocks = Repo.blocks.value
        val block = blocks.firstOrNull { it.id == st.lastBlockId } ?: blocks.firstOrNull() ?: return
        Engine.start(this, block, st.lastMode, st.timerMinutes(block.id) * 60_000L)
    }

    private fun render(s: Session) {
        dueJob?.cancel()
        dueJob = null
        if (!s.active) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelfResult(lastStartId)
            return
        }
        getSystemService(NotificationManager::class.java)
            .notify(Notifications.ONGOING_ID, Notifications.ongoing(this, s))
        if (s.status == Status.RUNNING && s.mode == Mode.TIMER) {
            // Backup to the AlarmManager alarm while the process is awake.
            dueJob = scope.launch {
                delay(s.remainingMs())
                Engine.checkTimerDue(this@TimeblockService)
            }
        }
    }

    private fun goForeground(n: Notification): Boolean = try {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifications.ONGOING_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifications.ONGOING_ID, n)
        }
        true
    } catch (e: Exception) {
        false
    }

    /** Swiped from Recents: Some OEMs (MIUI/HyperOS) kill the process right after — Arm a quick revive. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (Engine.state.value.active) Alarms.scheduleHeartbeat(this, REVIVE_DELAY_MS)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        // Destroyed while a block is still active = killed by the system, not by us.
        if (Engine.state.value.active) Alarms.scheduleHeartbeat(this, REVIVE_DELAY_MS)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.pantelmg.tb2cal.action.START"
        const val ACTION_PAUSE = "com.pantelmg.tb2cal.action.PAUSE"
        const val ACTION_RESUME = "com.pantelmg.tb2cal.action.RESUME"
        const val ACTION_STOP = "com.pantelmg.tb2cal.action.STOP"
        /** Re-posts the notification if the user swipes it away (allowed on Android 14+). */
        const val ACTION_SHOW = "com.pantelmg.tb2cal.action.SHOW"
        private const val REVIVE_DELAY_MS = 3_000L

        /** Call from the foreground UI after START/RESUME or when the app opens. */
        fun ensureRunning(ctx: Context) {
            if (!Engine.state.value.active) return
            runCatching {
                ContextCompat.startForegroundService(ctx, Intent(ctx, TimeblockService::class.java))
            }
        }

        fun actionIntent(ctx: Context, action: String): PendingIntent = PendingIntent.getForegroundService(
            ctx, action.hashCode(),
            Intent(ctx, TimeblockService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
