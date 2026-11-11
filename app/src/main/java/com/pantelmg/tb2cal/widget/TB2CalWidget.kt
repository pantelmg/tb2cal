package com.pantelmg.tb2cal.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.pantelmg.tb2cal.R
import com.pantelmg.tb2cal.calendar.AutoRegister
import com.pantelmg.tb2cal.data.Repo
import com.pantelmg.tb2cal.engine.Engine
import com.pantelmg.tb2cal.model.Fmt
import com.pantelmg.tb2cal.model.GoogleColors
import com.pantelmg.tb2cal.model.Mode
import com.pantelmg.tb2cal.model.Status
import com.pantelmg.tb2cal.service.Notifications
import com.pantelmg.tb2cal.service.TimeblockService
import com.pantelmg.tb2cal.ui.MainActivity

/**
 * TimeBlock manager widget (4×1): T/S · TimeBlock choice · (✓ in Stopwatch mode) · Start · Pause · Stop.
 * After a completion it shows "Completed! Tap to register." until the block is in the calendar.
 */
class TB2CalWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Engine.init(context)
        manager.updateAppWidget(ids, build(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_NEXT_BLOCK -> {
                Engine.init(context)
                if (Engine.state.value.active) return
                val blocks = Repo.blocks.value
                if (blocks.isEmpty()) return
                val i = blocks.indexOfFirst { it.id == Repo.settings.value.lastBlockId }
                val next = blocks[(i + 1).mod(blocks.size)]
                Repo.updateSettings { it.copy(lastBlockId = next.id) }
            }
            ACTION_TOGGLE_MODE -> {
                Engine.init(context)
                if (Engine.state.value.active) return
                Repo.updateSettings {
                    it.copy(lastMode = if (it.lastMode == Mode.TIMER) Mode.STOPWATCH else Mode.TIMER)
                }
            }
        }
    }

    companion object {
        private const val ACTION_NEXT_BLOCK = "com.pantelmg.tb2cal.widget.NEXT_BLOCK"
        private const val ACTION_TOGGLE_MODE = "com.pantelmg.tb2cal.widget.TOGGLE_MODE"
        private const val ACTION_NOOP = "com.pantelmg.tb2cal.widget.NOOP"
        private const val DIM = 70
        private const val FULL = 255

        /** Redraws every placed widget. Called whenever app state changes. */
        fun refresh(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(ctx, TB2CalWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, build(ctx))
        }

        private fun build(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_tb2cal)
            val s = Engine.state.value
            val st = Repo.settings.value

            // ---- Completed state ----
            val unregistered = Repo.pending.value.any { !it.registered }
            if (unregistered && !s.active) {
                v.setViewVisibility(R.id.controls, View.GONE)
                v.setViewVisibility(R.id.completed, View.VISIBLE)
                v.setTextViewText(
                    R.id.completed,
                    when {
                        !st.autoRegister -> "Completed! Tap to register."
                        Repo.pending.value.any { !it.registered && AutoRegister.stateOf(it.id) == AutoRegister.State.FAILED } ->
                            "Completed! Tap to retry."
                        else -> "Completed! Adding to calendar…"
                    }
                )
                v.setOnClickPendingIntent(R.id.root, Notifications.openApp(ctx, MainActivity.ACTION_OPEN_COMPLETION, 21, fromWidget = true))
                return v
            }
            v.setViewVisibility(R.id.controls, View.VISIBLE)
            v.setViewVisibility(R.id.completed, View.GONE)
            v.setOnClickPendingIntent(R.id.root, noop(ctx)) // Clears the "Tap to register" action

            // ---- TimeBlock choice + mode ----
            val blocks = Repo.blocks.value
            val chosen = blocks.firstOrNull { it.id == st.lastBlockId } ?: blocks.firstOrNull()
            val name = if (s.active) s.blockName else chosen?.name ?: "—"
            val colorKey = if (s.active) s.colorKey else chosen?.colorKey
            val mode = if (s.active) s.mode else st.lastMode
            val targetMs = st.timerMinutes(chosen?.id) * 60_000L

            v.setTextViewText(R.id.name, name)
            val color = GoogleColors.byKey(colorKey)
            if (color != null) {
                v.setViewVisibility(R.id.dot, View.VISIBLE)
                v.setInt(R.id.dot, "setColorFilter", color.argb.toInt())
            } else {
                v.setViewVisibility(R.id.dot, View.GONE)
            }
            v.setTextViewText(R.id.mode, if (mode == Mode.TIMER) "T" else "S")

            if (!s.active) {
                // Idle: Tap the name to cycle TimeBlocks, tap T/S to switch mode.
                v.setViewVisibility(R.id.sub, View.VISIBLE)
                v.setViewVisibility(R.id.time, View.GONE)
                v.setTextViewText(
                    R.id.sub,
                    if (mode == Mode.TIMER) "Timer · ${if (targetMs > 0) Fmt.duration(targetMs) else "Set a time in the app"}"
                    else "Stopwatch"
                )
                v.setTextColor(R.id.name, 0xFFFFFFFF.toInt())
                v.setTextColor(R.id.mode, 0xFFFFFFFF.toInt())
                v.setOnClickPendingIntent(R.id.block, broadcast(ctx, ACTION_NEXT_BLOCK, 22))
                v.setOnClickPendingIntent(R.id.mode, broadcast(ctx, ACTION_TOGGLE_MODE, 23))

                val canStart = chosen != null && (mode == Mode.STOPWATCH || targetMs > 0)
                button(ctx, v, R.id.play, canStart, TimeblockService.actionIntent(ctx, TimeblockService.ACTION_START))
                button(ctx, v, R.id.pause, false, null)
                button(ctx, v, R.id.stop, false, null)
                v.setViewVisibility(R.id.complete, if (mode == Mode.STOPWATCH) View.VISIBLE else View.GONE)
                button(ctx, v, R.id.complete, false, null)
            } else {
                // Active: Live clock, choices locked.
                v.setViewVisibility(R.id.sub, View.GONE)
                v.setViewVisibility(R.id.time, View.VISIBLE)
                val now = SystemClock.elapsedRealtime()
                val running = s.status == Status.RUNNING
                if (mode == Mode.TIMER) {
                    v.setChronometerCountDown(R.id.time, true)
                    v.setChronometer(R.id.time, now + s.remainingMs(now), null, running)
                } else {
                    v.setChronometerCountDown(R.id.time, false)
                    v.setChronometer(R.id.time, now - s.elapsedMs(now), null, running)
                }
                v.setTextColor(R.id.name, 0x99FFFFFF.toInt())
                v.setTextColor(R.id.mode, 0x99FFFFFF.toInt())
                v.setOnClickPendingIntent(R.id.block, noop(ctx))
                v.setOnClickPendingIntent(R.id.mode, noop(ctx))

                button(ctx, v, R.id.play, !running, TimeblockService.actionIntent(ctx, TimeblockService.ACTION_RESUME))
                button(ctx, v, R.id.pause, running, TimeblockService.actionIntent(ctx, TimeblockService.ACTION_PAUSE))
                button(ctx, v, R.id.stop, true, TimeblockService.actionIntent(ctx, TimeblockService.ACTION_STOP))
                // Stopwatch: ✓ completes the TimeBlock and opens the completion screen.
                v.setViewVisibility(R.id.complete, if (mode == Mode.STOPWATCH) View.VISIBLE else View.GONE)
                button(ctx, v, R.id.complete, true, Notifications.openApp(ctx, MainActivity.ACTION_COMPLETE_NOW, 24, fromWidget = true))
            }
            return v
        }

        private fun button(ctx: Context, v: RemoteViews, id: Int, enabled: Boolean, pi: PendingIntent?) {
            v.setInt(id, "setImageAlpha", if (enabled) FULL else DIM)
            // Launchers re-apply updates onto the existing views, so an old click action would survive
            // unless it's replaced. Disabled buttons get a do-nothing action instead.
            v.setOnClickPendingIntent(id, if (enabled && pi != null) pi else noop(ctx))
        }

        /** Click action that does nothing (the widget receiver ignores it). */
        private fun noop(ctx: Context): PendingIntent = broadcast(ctx, ACTION_NOOP, 29)

        private fun broadcast(ctx: Context, action: String, rc: Int): PendingIntent = PendingIntent.getBroadcast(
            ctx, rc, Intent(ctx, TB2CalWidget::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
