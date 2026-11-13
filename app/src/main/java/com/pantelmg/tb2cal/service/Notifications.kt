package com.pantelmg.tb2cal.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import com.pantelmg.tb2cal.R
import com.pantelmg.tb2cal.calendar.AutoRegister
import com.pantelmg.tb2cal.data.Repo
import com.pantelmg.tb2cal.model.Fmt
import com.pantelmg.tb2cal.model.GoogleColors
import com.pantelmg.tb2cal.model.Mode
import com.pantelmg.tb2cal.model.Session
import com.pantelmg.tb2cal.model.Status
import com.pantelmg.tb2cal.ui.MainActivity
import java.util.Date

object Notifications {
    /**
     * Running TimeBlock. Default importance (not low) so it sits with the important notifications
     * instead of the silent group at the bottom. It's still silent: No sound, no vibration.
     */
    const val CH_ONGOING = "tb_running"
    /** Older low-importance channel. Importance can't be raised after creation, so it's replaced. */
    private const val OLD_ONGOING = "tb_ongoing"
    /** Silent, high-importance channel. AlertService plays the sound and vibration itself. */
    const val CH_ALERT = "tb_alert"
    /** Channels from older versions that carried their own sound. */
    private const val OLD_COMPLETE_PREFIX = "tb_complete_"
    private const val DEFAULT_COLOR = 0xFF1565C0.toInt()
    const val ONGOING_ID = 1001
    const val COMPLETE_ID = 2001

    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)

    fun createChannels(ctx: Context) {
        val nm = nm(ctx)
        nm.notificationChannels
            .filter { it.id.startsWith(OLD_COMPLETE_PREFIX) || it.id == OLD_ONGOING }
            .forEach { nm.deleteNotificationChannel(it.id) }

        val ongoing = NotificationChannel(CH_ONGOING, "Running TimeBlock", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Shows the running TimeBlock's name, mode and time."
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        val alert = NotificationChannel(CH_ALERT, "TimeBlock completed", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alert when a timer TimeBlock finishes. Sound and vibration are set inside TB2Cal."
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(ongoing)
        nm.createNotificationChannel(alert)
    }

    fun post(ctx: Context, id: Int, n: Notification) = nm(ctx).notify(id, n)

    fun cancelCompleted(ctx: Context) = nm(ctx).cancel(COMPLETE_ID)

    /** Second line of the completion notification, following auto-register's progress. */
    private fun completedText(id: String?): String {
        if (!Repo.settings.value.autoRegister) return "Tap to transfer to calendar."
        val item = id?.let { Repo.findPending(it) }
        return when {
            item == null || item.registered -> "Added to calendar. Tap to view."
            AutoRegister.stateOf(id) == AutoRegister.State.FAILED -> "Couldn't add it automatically. Tap to retry."
            else -> "Adding to calendar…"
        }
    }

    /** Re-draws the completion notification with the latest status, only if it's still showing. */
    fun refreshCompleted(ctx: Context, id: String) {
        val nm = nm(ctx)
        if (nm.activeNotifications.none { it.id == COMPLETE_ID }) return
        val item = Repo.findPending(id) ?: return
        nm.notify(COMPLETE_ID, completed(ctx, item.label, test = false, alerting = AlertService.running, id = id))
    }

    /** fromWidget: The completion screen's DONE then returns to the home screen instead of the app. */
    fun openApp(ctx: Context, action: String?, requestCode: Int, fromWidget: Boolean = false): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .setAction(action)
            .putExtra(MainActivity.EXTRA_FROM_WIDGET, fromWidget)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            ctx, requestCode, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** Permanent notification while a TimeBlock is running or paused. */
    fun ongoing(ctx: Context, s: Session): Notification {
        val b = NotificationCompat.Builder(ctx, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_tb)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            // Tapping it opens the TimeBlocks tab (the running timer), whatever screen was open last.
            .setContentIntent(openApp(ctx, MainActivity.ACTION_OPEN_MAIN, 0))
        if (!s.active) return b.setContentTitle("TB2Cal").build()

        // Colorized foreground-service notifications are ranked above all others (like calls and media).
        b.setColor(GoogleColors.byKey(s.colorKey)?.argb?.toInt() ?: DEFAULT_COLOR)
        b.setColorized(true)
        // Android 16+: Ask to be shown as a Live Update (top of the shade and a status bar chip).
        if (Build.VERSION.SDK_INT >= 36) {
            b.addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
        }
        b.setDeleteIntent(TimeblockService.actionIntent(ctx, TimeblockService.ACTION_SHOW))
        b.setContentTitle("${s.blockName} · ${s.mode.label}")
        val tf = DateFormat.getTimeFormat(ctx)
        val nowWall = System.currentTimeMillis()

        if (s.status == Status.RUNNING) {
            // The time is written by TimeblockService exactly when each second changes (like the app's clock),
            // instead of Android's own chronometer, which drifts and sometimes skips a second.
            b.setShowWhen(false)
            if (s.mode == Mode.TIMER) {
                val endsAt = nowWall + s.remainingMs()
                b.setContentText("${Fmt.clock(s.remainingMs(), roundUp = true)} left · Ends at ${tf.format(Date(endsAt))}")
            } else {
                b.setContentText("${Fmt.clock(s.elapsedMs())} · Started at ${tf.format(Date(s.startWallMs))}")
            }
            b.addAction(0, "Pause", TimeblockService.actionIntent(ctx, TimeblockService.ACTION_PAUSE))
        } else {
            b.setShowWhen(false)
            val t = if (s.mode == Mode.TIMER) "${Fmt.clock(s.remainingMs(), roundUp = true)} left"
            else "${Fmt.clock(s.elapsedMs())} elapsed"
            b.setContentText("Paused · $t")
            b.addAction(0, "Resume", TimeblockService.actionIntent(ctx, TimeblockService.ACTION_RESUME))
        }
        b.addAction(0, "Stop", TimeblockService.actionIntent(ctx, TimeblockService.ACTION_STOP))
        if (s.mode == Mode.STOPWATCH) {
            b.addAction(0, "Complete", openApp(ctx, MainActivity.ACTION_COMPLETE_NOW, 2))
        }
        return b.build()
    }

    /**
     * Completion notification. While [alerting], it is AlertService's foreground notification
     * and carries a "Stop alert" action. Afterwards it stays as a plain notification.
     */
    fun completed(ctx: Context, label: String, test: Boolean, alerting: Boolean, id: String? = null): Notification {
        val b = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat_tb)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setDeleteIntent(AlertService.stopIntent(ctx, keepNotification = false))
        if (test) {
            b.setContentTitle("Test alert")
                .setContentText("This is how a completed TimeBlock alerts you.")
                .setContentIntent(AlertService.stopIntent(ctx, keepNotification = false))
        } else {
            b.setContentTitle("TimeBlock completed!")
                .setContentText(completedText(id))
                .setSubText(label)
                .setContentIntent(openApp(ctx, MainActivity.ACTION_OPEN_COMPLETION, 1))
        }
        if (alerting) b.addAction(0, "Stop alert", AlertService.stopIntent(ctx, keepNotification = !test))
        return b.build()
    }
}
