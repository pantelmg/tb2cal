package com.pantelmg.tb2cal.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
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
        ids.forEach { manager.updateAppWidget(it, build(context, it)) }
        manager.notifyAppWidgetViewDataChanged(ids, R.id.plan_list)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TICK -> {
                Repo.init(context)
                val id = intent.getStringExtra(EXTRA_ID) ?: return
                val day = intent.getLongExtra(EXTRA_DAY, -1L).takeIf { it >= 0 }
                val wasDone = intent.getBooleanExtra(EXTRA_DONE, false)
                Repo.items.value.firstOrNull { it.id == id }?.let { Plans.setDone(context, it, day, !wasDone) }
            }
            // An event just ended, or midnight: Redraw (crossed out / removed, back to Today).
            ACTION_REFRESH -> {
                Repo.init(context)
                refresh(context)
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

        fun refresh(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(ctx, PlanWidget::class.java))
            if (ids.isEmpty()) return
            ids.forEach { manager.updateAppWidget(it, build(ctx, it)) }
            manager.notifyAppWidgetViewDataChanged(ids, R.id.plan_list)
        }

        private fun build(ctx: Context, widgetId: Int): RemoteViews {
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
            val svc = Intent(ctx, PlanWidgetService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))
            @Suppress("DEPRECATION")
            v.setRemoteAdapter(R.id.plan_list, svc)
            v.setEmptyView(R.id.plan_list, R.id.plan_empty)
            // Each task's square fills in its id, day and done state (ticking needs a mutable template).
            v.setPendingIntentTemplate(
                R.id.plan_list,
                PendingIntent.getBroadcast(
                    ctx, 43, Intent(ctx, PlanWidget::class.java).setAction(ACTION_TICK),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            return v
        }
    }
}

/** Rows of the Task/Event manager's list. */
class PlanWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsService.RemoteViewsFactory = Factory(applicationContext)

    private class Row(
        val title: String, val sub: String?, val color: Int, val taskId: String?, val day: Long?, val crossed: Boolean,
    )

    private class Factory(private val ctx: Context) : RemoteViewsService.RemoteViewsFactory {
        private var rows: List<Row> = emptyList()

        override fun onCreate() {}
        override fun onDestroy() {}

        override fun onDataSetChanged() {
            Repo.init(ctx)
            rows = runCatching { compute() }.getOrDefault(emptyList())
        }

        private fun compute(): List<Row> {
            val st = Repo.settings.value
            val items = Repo.items.value
            val day = PlanWidget.shownDay()
            val now = System.currentTimeMillis()
            // The day's ticked tasks and past events: Crossed out (until the day ends) or removed (Settings).
            val keepDone = st.planWidgetDone != "HIDE"
            val out = mutableListOf<Row>()
            if (st.planWidgetList != "EVENTS") {
                // Open tasks without a date first (on Today), then the day's dated/repeating ones (by time), then
                // the tasks without a date ticked that day.
                val color = Plans.color(ItemKind.TASK)
                if (day == Plan.today()) Plan.openUndated(items).forEach { out += Row(it.title, null, color, it.id, null, false) }
                items.filter { it.kind == ItemKind.TASK && !it.undated }
                    .flatMap { item -> Plan.occurrences(item, day, day + 1).map { item to it } }
                    .filter { (item, d) -> keepDone || d !in item.doneDays }
                    .sortedBy { (item, d) -> Plan.startMs(item, d) }
                    .forEach { (item, d) ->
                        out += Row(item.title, listOfNotNull(Plan.timeText(item), item.repeat?.describe()).joinToString(" · ").ifEmpty { null },
                            color, item.id, d, d in item.doneDays)
                    }
                if (keepDone) items.filter { it.undated && it.doneAt?.let { at -> Plan.dayOf(at) } == day }
                    .sortedBy { it.doneAt }
                    .forEach { out += Row(it.title, null, color, it.id, null, true) }
            }
            var nextEnd: Long? = null
            if (st.planWidgetList != "TASKS") {
                // The day's events: TB2Cal's, plus Gmail's when that's on (Settings).
                val tf = SimpleDateFormat(if (android.text.format.DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm a", Locale.getDefault())
                val date = LocalDate.ofEpochDay(day)
                CalendarData.byDay(ctx, date, date.plusDays(1))[date].orEmpty()
                    .filter { it.plan?.kind == ItemKind.EVENT || (it.plan == null && !it.isTimeBlock && st.widgetGmail) }
                    .forEach { e ->
                        val over = e.endMs <= now
                        if (!over) nextEnd = minOf(nextEnd ?: Long.MAX_VALUE, e.endMs)
                        if (over && !keepDone) return@forEach
                        val time = if (e.allDay) "All day" else "${tf.format(Date(e.startMs))} – ${tf.format(Date(e.endMs))}"
                        out += Row(e.label, if (e.plan == null) "$time · Gmail" else time, e.color, null, null, over)
                    }
            }
            PlanWidget.scheduleRedraw(ctx, nextEnd)
            return out
        }

        override fun getCount() = rows.size
        override fun getViewTypeCount() = 1
        override fun hasStableIds() = false
        override fun getItemId(position: Int) = position.toLong()
        override fun getLoadingView(): RemoteViews? = null

        override fun getViewAt(position: Int): RemoteViews {
            val r = rows.getOrNull(position) ?: return RemoteViews(ctx.packageName, R.layout.widget_plan_item)
            val v = RemoteViews(ctx.packageName, R.layout.widget_plan_item)
            v.setTextViewText(
                R.id.row_title,
                if (r.crossed) SpannableString(r.title).apply { setSpan(StrikethroughSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                else r.title
            )
            v.setTextColor(R.id.row_title, if (r.crossed) 0x80FFFFFF.toInt() else 0xFFFFFFFF.toInt())
            if (r.sub != null) {
                v.setViewVisibility(R.id.row_sub, View.VISIBLE)
                v.setTextViewText(R.id.row_sub, r.sub)
            }
            if (r.taskId != null) {
                v.setViewVisibility(R.id.row_tick, View.VISIBLE)
                v.setViewVisibility(R.id.row_dot, View.GONE)
                v.setImageViewResource(R.id.row_tick, if (r.crossed) R.drawable.ic_w_box_done else R.drawable.ic_w_box)
                v.setInt(R.id.row_tick, "setColorFilter", r.color)
                v.setOnClickFillInIntent(
                    R.id.row_tick,
                    Intent().putExtra(PlanWidget.EXTRA_ID, r.taskId).putExtra(PlanWidget.EXTRA_DAY, r.day ?: -1L)
                        .putExtra(PlanWidget.EXTRA_DONE, r.crossed)
                )
            } else {
                v.setViewVisibility(R.id.row_tick, View.GONE)
                v.setViewVisibility(R.id.row_dot, View.VISIBLE)
                v.setInt(R.id.row_dot, "setColorFilter", r.color)
            }
            return v
        }
    }
}
