package com.pantelmg.tb2cal.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.pantelmg.tb2cal.R
import com.pantelmg.tb2cal.calendar.CalendarData
import com.pantelmg.tb2cal.data.Repo
import com.pantelmg.tb2cal.model.ItemKind
import com.pantelmg.tb2cal.model.Plan
import com.pantelmg.tb2cal.plan.Plans
import com.pantelmg.tb2cal.ui.QuickAddActivity
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/**
 * Task/Event manager widget (Google Tasks-style): (icon) Tasks/Events ▾ · ‹ Today › · + , then that day's list. Tap
 * the title to switch between Tasks/Events, Tasks and Events; The arrows change the day (tap the day for Today; It
 * goes back to Today at midnight). Tasks have an outlined square to tick (or untick); Events a solid square of the
 * same size. The day's ticked tasks and past events stay crossed out, or are removed (Settings).
 */
class PlanWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Repo.init(context)
        redrawAsync(context)
    }

    /** Redraws off the main thread (the rows read the phone's calendar), keeping this receiver alive until done. */
    private fun redrawAsync(context: Context) {
        val app = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                runCatching { refresh(app) }
            } finally {
                pending.finish()
            }
        }.start()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TICK -> tick(context, intent)
            // An event just ended, or midnight: Redraw (crossed out / removed, back to Today).
            ACTION_REFRESH -> {
                Repo.init(context)
                redrawAsync(context)
            }
            ACTION_PREV, ACTION_NEXT, ACTION_TODAY -> {
                Repo.init(context)
                val today = Plan.today()
                val day = when (intent.action) {
                    ACTION_PREV -> shownDay() - 1
                    ACTION_NEXT -> shownDay() + 1
                    else -> today
                }
                Repo.updateSettings { it.copy(planWidgetDay = day, planWidgetDaySetOn = today) }
            }
            ACTION_CYCLE -> {
                Repo.init(context)
                Repo.updateSettings {
                    it.copy(planWidgetList = when (it.planWidgetList) { "BOTH" -> "TASKS"; "TASKS" -> "EVENTS"; else -> "BOTH" })
                }
            }
        }
    }

    companion object {
        private const val ACTION_TICK = "com.pantelmg.tb2cal.widget.PLAN_TICK"
        private const val ACTION_CYCLE = "com.pantelmg.tb2cal.widget.PLAN_CYCLE"
        private const val ACTION_PREV = "com.pantelmg.tb2cal.widget.PLAN_PREV"
        private const val ACTION_NEXT = "com.pantelmg.tb2cal.widget.PLAN_NEXT"
        private const val ACTION_TODAY = "com.pantelmg.tb2cal.widget.PLAN_TODAY"
        private const val ACTION_REFRESH = "com.pantelmg.tb2cal.widget.PLAN_REFRESH"

        /**
         * Redraws the widget at [atMs] (the next event end of the day shown) or at midnight, whichever comes first.
         * Not a wake-up alarm: If the phone sleeps, it runs as soon as the screen is on again.
         */
        fun scheduleRedraw(ctx: Context, atMs: Long?) {
            val midnight = Plan.dayStartMs(Plan.today() + 1) + 1_000L
            val at = minOf(atMs ?: Long.MAX_VALUE, midnight)
            val am = ctx.getSystemService(android.app.AlarmManager::class.java) ?: return
            runCatching { am.set(android.app.AlarmManager.RTC, at, broadcast(ctx, ACTION_REFRESH, 47)) }
        }

        /** The day shown: The one picked with the arrows today, else today. */
        fun shownDay(): Long {
            val st = Repo.settings.value
            val today = Plan.today()
            return if (st.planWidgetDaySetOn == today && st.planWidgetDay >= 0) st.planWidgetDay else today
        }

        private fun broadcast(ctx: Context, action: String, rc: Int) = PendingIntent.getBroadcast(
            ctx, rc, Intent(ctx, PlanWidget::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        const val EXTRA_ID = "id"
        const val EXTRA_DAY = "day"
        const val EXTRA_DONE = "done"
        /** Row taps (WidgetActionActivity): "tick" a task's square, or "open" the entry in the app. */
        const val EXTRA_ACT = "act"
        const val EXTRA_KEY = "key"

        /** Ticks (or unticks) the task a square's tap names. */
        fun tick(context: Context, intent: Intent) {
            Repo.init(context)
            val id = intent.getStringExtra(EXTRA_ID) ?: return
            val day = intent.getLongExtra(EXTRA_DAY, -1L).takeIf { it >= 0 }
            val wasDone = intent.getBooleanExtra(EXTRA_DONE, false)
            Repo.items.value.firstOrNull { it.id == id }?.let { Plans.setDone(context, it, day, !wasDone) }
        }

        /** Blocking (reads the phone's calendar): Call off the main thread. */
        fun refresh(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(ctx, PlanWidget::class.java))
            if (ids.isEmpty()) return
            // Android 12+: The rows go inside the widget itself, so the home screen keeps showing them even when
            // the system closes TB2Cal (no list service to reconnect to). Older: The list service fills them in.
            val rows = if (Build.VERSION.SDK_INT >= 31) PlanRows.compute(ctx) else null
            ids.forEach { manager.updateAppWidget(it, build(ctx, it, rows)) }
            if (rows == null) manager.notifyAppWidgetViewDataChanged(ids, R.id.plan_list)
        }

        private fun build(ctx: Context, widgetId: Int, rows: List<PlanRows.Row>?): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_plan)
            val list = Repo.settings.value.planWidgetList
            v.setTextViewText(
                R.id.plan_title,
                when (list) { "TASKS" -> "Tasks ▾"; "EVENTS" -> "Events ▾"; else -> "Tasks/Events ▾" }
            )
            v.setImageViewResource(
                R.id.plan_icon,
                when (list) { "TASKS" -> R.drawable.ic_w_list_tasks; "EVENTS" -> R.drawable.ic_w_list_events; else -> R.drawable.ic_w_list_both }
            )
            val day = shownDay()
            val isToday = day == Plan.today()
            v.setTextViewText(
                R.id.plan_day,
                if (isToday) "Today" else LocalDate.ofEpochDay(day).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"))
            )
            v.setOnClickPendingIntent(R.id.plan_prev, broadcast(ctx, ACTION_PREV, 44))
            v.setOnClickPendingIntent(R.id.plan_next, broadcast(ctx, ACTION_NEXT, 45))
            v.setOnClickPendingIntent(R.id.plan_day, broadcast(ctx, ACTION_TODAY, 46))
            v.setTextViewText(
                R.id.plan_empty,
                when (list) {
                    "TASKS" -> if (isToday) "No tasks today." else "No tasks on this day."
                    "EVENTS" -> if (isToday) "No events today." else "No events on this day."
                    else -> if (isToday) "Nothing for today." else "Nothing on this day."
                }
            )
            v.setOnClickPendingIntent(
                R.id.plan_header,
                PendingIntent.getBroadcast(
                    ctx, 41, Intent(ctx, PlanWidget::class.java).setAction(ACTION_CYCLE),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            v.setOnClickPendingIntent(
                R.id.plan_add,
                PendingIntent.getActivity(
                    ctx, 42,
                    Intent(ctx, QuickAddActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            if (rows != null && Build.VERSION.SDK_INT >= 31) {
                val items = RemoteViews.RemoteCollectionItems.Builder().setViewTypeCount(1)
                rows.forEachIndexed { i, r -> items.addItem(i.toLong(), PlanRows.view(ctx, r)) }
                v.setRemoteAdapter(R.id.plan_list, items.build())
            } else {
                val svc = Intent(ctx, PlanWidgetService::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))
                @Suppress("DEPRECATION")
                v.setRemoteAdapter(R.id.plan_list, svc)
            }
            v.setEmptyView(R.id.plan_list, R.id.plan_empty)
            // Each row fills in what its tap does: A task's square ticks it; The rest of the row opens it in the
            // app (Settings). An invisible activity carries both out (it may open the app; A broadcast may not).
            v.setPendingIntentTemplate(
                R.id.plan_list,
                PendingIntent.getActivity(
                    ctx, 43, Intent(ctx, com.pantelmg.tb2cal.ui.WidgetActionActivity::class.java),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            return v
        }
    }
}

/**
 * The Task/Event manager's rows for the day shown: The Calendar tab's own list of that day (same order, same
 * reordering), without TimeBlocks. Ticked tasks and past events stay in their place, crossed out, or are left out
 * (Settings: Completed on the Task/Event widget).
 */
internal object PlanRows {
    class Row(
        val title: String, val sub: String?, val color: Int, val taskId: String?, val day: Long?, val crossed: Boolean,
        /** A past day's task with previous day editing off: Not tickable here. */
        val locked: Boolean,
        /** Its entry key (as in the app's day list): To open it in the app. */
        val key: String,
        /** The day shown (opening it in the app goes there). */
        val shownDay: Long,
    )

    /** Blocking (reads the phone's calendar): Call off the main thread. */
    fun compute(ctx: Context): List<Row> = runCatching { rows(ctx) }.getOrDefault(emptyList())

    private fun rows(ctx: Context): List<Row> {
        Repo.init(ctx)
        val st = Repo.settings.value
        val items = Repo.items.value
        val day = PlanWidget.shownDay()
        val now = System.currentTimeMillis()
        val keepDone = st.planWidgetDone != "HIDE"
        val tasks = st.planWidgetList != "EVENTS"
        val events = st.planWidgetList != "TASKS"
        val tf = SimpleDateFormat(if (android.text.format.DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm a", Locale.getDefault())
        val date = LocalDate.ofEpochDay(day)
        val dayItems = CalendarData.byDay(ctx, date, date.plusDays(1))[date].orEmpty()
        val natural = CalendarData.dayList(dayItems, items, day, Plans.color(ItemKind.TASK)).distinctBy { CalendarData.entryKey(it) }
        // Reordered in the app (Settings: Allow reordering): The same order here.
        val list = if (st.allowReorder) CalendarData.applyOrder(natural, Repo.orders.value[day]) { CalendarData.entryKey(it) } else natural
        var nextEnd: Long? = null
        val out = mutableListOf<Row>()
        list.forEach { e ->
            val plan = e.plan
            val key = CalendarData.entryKey(e)
            when {
                e.isTimeBlock -> Unit
                plan?.kind == ItemKind.TASK -> {
                    if (!tasks || (e.done && !keepDone)) return@forEach
                    val d = e.planDay
                    val sub = when {
                        plan.undated -> if (e.done) null else "No date"
                        else -> (listOfNotNull(Plan.timeText(plan), Plan.scheduleText(plan)) + Plan.honestyNotes(plan, d))
                            .joinToString(" · ").ifEmpty { null }
                    }
                    // An open task without a date can always be ticked; A ticked or dated one by its day.
                    val locked = if (plan.undated && !e.done) false else !Plans.canChange(d ?: day)
                    out += Row(e.label, sub, e.color, plan.id, d, e.done, locked, key, day)
                }
                else -> {
                    // An event: TB2Cal's, or Gmail's when that's on (Settings).
                    if (!events || (plan == null && !st.widgetGmail)) return@forEach
                    val over = e.endMs <= now
                    if (!over) nextEnd = minOf(nextEnd ?: Long.MAX_VALUE, e.endMs)
                    if (over && !keepDone) return@forEach
                    val time = if (e.allDay) "All day" else "${tf.format(Date(e.startMs))} – ${tf.format(Date(e.endMs))}"
                    val sub = if (plan == null) "$time · Gmail" else (listOf(time) + Plan.honestyNotes(plan, e.planDay)).joinToString(" · ")
                    out += Row(e.label, sub, e.color, null, null, over, false, key, day)
                }
            }
        }
        PlanWidget.scheduleRedraw(ctx, nextEnd)
        return out
    }

    /**
     * One row. Every part is set each time (also to "nothing"): The home screen reuses row views, and anything left
     * unset would keep the previous row's text or tap (e.g. a square ticking another task).
     */
    fun view(ctx: Context, r: Row): RemoteViews {
        val v = RemoteViews(ctx.packageName, R.layout.widget_plan_item)
        v.setTextViewText(
            R.id.row_title,
            if (r.crossed) SpannableString(r.title).apply { setSpan(StrikethroughSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            else r.title
        )
        v.setTextColor(R.id.row_title, if (r.crossed) 0x80FFFFFF.toInt() else 0xFFFFFFFF.toInt())
        v.setViewVisibility(R.id.row_sub, if (r.sub != null) View.VISIBLE else View.GONE)
        v.setTextViewText(R.id.row_sub, r.sub ?: "")
        // The row: Opens it in the app, on its day, pulsing (Settings: Tapping … shows it in the app), or nothing.
        val open = if (Repo.settings.value.widgetOpensApp && r.key.isNotEmpty()) {
            Intent().putExtra(PlanWidget.EXTRA_ACT, "open").putExtra(PlanWidget.EXTRA_DAY, r.shownDay).putExtra(PlanWidget.EXTRA_KEY, r.key)
        } else Intent().putExtra(PlanWidget.EXTRA_ACT, "none")
        v.setOnClickFillInIntent(R.id.row, open)
        if (r.taskId != null) {
            v.setViewVisibility(R.id.row_tick, View.VISIBLE)
            v.setViewVisibility(R.id.row_dot, View.GONE)
            v.setInt(R.id.row_tick, "setImageAlpha", if (r.locked) 110 else 255)
            v.setImageViewResource(R.id.row_tick, if (r.crossed) R.drawable.ic_w_box_done else R.drawable.ic_w_box)
            v.setInt(R.id.row_tick, "setColorFilter", r.color)
            // The square ticks exactly this task; Locked (a past day): It acts like the rest of the row.
            v.setOnClickFillInIntent(
                R.id.row_tick,
                if (r.locked) open
                else Intent().putExtra(PlanWidget.EXTRA_ACT, "tick").putExtra(PlanWidget.EXTRA_ID, r.taskId)
                    .putExtra(PlanWidget.EXTRA_DAY, r.day ?: -1L).putExtra(PlanWidget.EXTRA_DONE, r.crossed)
            )
        } else {
            v.setViewVisibility(R.id.row_tick, View.GONE)
            v.setViewVisibility(R.id.row_dot, View.VISIBLE)
            v.setInt(R.id.row_dot, "setColorFilter", r.color)
        }
        return v
    }
}

/** Rows of the Task/Event manager's list (Android 8–11; Newer ones get the rows inside the widget itself). */
class PlanWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsService.RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val ctx: Context) : RemoteViewsService.RemoteViewsFactory {
        private var rows: List<PlanRows.Row> = emptyList()

        override fun onCreate() {}
        override fun onDestroy() {}
        override fun onDataSetChanged() {
            rows = PlanRows.compute(ctx)
        }
        override fun getCount() = rows.size
        override fun getViewTypeCount() = 1
        override fun hasStableIds() = false
        override fun getItemId(position: Int) = position.toLong()
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewAt(position: Int): RemoteViews =
            rows.getOrNull(position)?.let { PlanRows.view(ctx, it) } ?: RemoteViews(ctx.packageName, R.layout.widget_plan_item)
    }
}
