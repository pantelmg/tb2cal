package com.pantelmg.tb2cal.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.pantelmg.tb2cal.engine.Engine
import com.pantelmg.tb2cal.model.Mode
import com.pantelmg.tb2cal.model.Session
import com.pantelmg.tb2cal.model.Status
import com.pantelmg.tb2cal.ui.MainActivity

/**
 * Two alarms keep a TimeBlock alive even if the OS kills the process:
 *  - Completion: An alarm-clock alarm at the timer's end (highest priority, survives Doze/battery savers)
 *  - Heartbeat: Every few minutes while a block is active, revives the service/notification if killed
 */
object Alarms {
    private const val HEARTBEAT_MS = 10 * 60_000L
    private const val RC_COMPLETION = 1
    private const val RC_HEARTBEAT = 2
    private const val RC_SHOW = 3

    /** Arms/disarms both alarms to match the session. */
    fun sync(ctx: Context, s: Session) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        if (s.status == Status.RUNNING && s.mode == Mode.TIMER) scheduleCompletion(ctx, am, s)
        else am.cancel(pi(ctx, RC_COMPLETION))
        if (s.active) scheduleHeartbeat(ctx, HEARTBEAT_MS)
        else am.cancel(pi(ctx, RC_HEARTBEAT))
    }

    fun scheduleHeartbeat(ctx: Context, delayMs: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val at = SystemClock.elapsedRealtime() + delayMs
        val p = pi(ctx, RC_HEARTBEAT)
        try {
            if (canExact(am)) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, p)
            else am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, p)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, p)
        }
    }

    private fun scheduleCompletion(ctx: Context, am: AlarmManager, s: Session) {
        val remaining = s.remainingMs()
        val p = pi(ctx, RC_COMPLETION)
        try {
            if (canExact(am)) {
                val show = PendingIntent.getActivity(
                    ctx, RC_SHOW,
                    Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(System.currentTimeMillis() + remaining, show), p)
                return
            }
        } catch (e: SecurityException) {
            // fall through to inexact
        }
        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + remaining, p)
    }

    private fun canExact(am: AlarmManager) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    private fun pi(ctx: Context, rc: Int): PendingIntent = PendingIntent.getBroadcast(
        ctx, rc, Intent(ctx, AlarmReceiver::class.java).setAction("com.pantelmg.tb2cal.alarm.$rc"),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

/** Brings a killed TimeBlock back: Completes due timers, restarts the service, re-arms alarms. */
object KeepAlive {
    fun revive(ctx: Context) {
        Engine.init(ctx)
        Engine.checkTimerDue(ctx)
        val s = Engine.state.value
        if (s.active) {
            TimeblockService.ensureRunning(ctx)
            Alarms.sync(ctx, s)
        }
    }
}

/** Completion + heartbeat alarms. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = KeepAlive.revive(context)
}

/** Reboot (incl. Xiaomi/HTC quick-boot) and app updates. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        KeepAlive.revive(context)
        // Alarms don't survive a reboot: Re-arm the next task/event reminder too.
        runCatching {
            com.pantelmg.tb2cal.data.Repo.init(context)
            com.pantelmg.tb2cal.plan.Reminders.schedule(context)
        }
    }
}
