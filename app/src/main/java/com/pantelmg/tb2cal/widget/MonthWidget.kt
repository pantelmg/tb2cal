package com.pantelmg.tb2cal.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.pantelmg.tb2cal.R
import com.pantelmg.tb2cal.calendar.CalendarData
import com.pantelmg.tb2cal.calendar.DayItem
import com.pantelmg.tb2cal.data.Repo
import com.pantelmg.tb2cal.model.GoogleColors
import com.pantelmg.tb2cal.ui.MainActivity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Calendar widget: A resizable month grid of completed TimeBlocks (and Gmail events, if that's on),
 * the same as TB2Cal's Calendar tab.
 * ‹ › change month · Tap the title for this month · + opens TB2Cal · Tap a day to open it in the app.
 */
class MonthWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Repo.init(context)
        ids.forEach { manager.updateAppWidget(it, build(context, it)) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val e = prefs(context).edit()
        ids.forEach { e.remove(key(it)) }
        e.apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val offset = prefs(context).getInt(key(id), 0)
        val newOffset = when (intent.action) {
            ACTION_PREV -> offset - 1
            ACTION_NEXT -> offset + 1
            ACTION_TODAY -> 0
            else -> return
        }
        prefs(context).edit().putInt(key(id), newOffset).apply()
        Repo.init(context)
        AppWidgetManager.getInstance(context).updateAppWidget(id, build(context, id))
    }

    companion object {
        private const val ACTION_PREV = "com.pantelmg.tb2cal.month.PREV"
        private const val ACTION_NEXT = "com.pantelmg.tb2cal.month.NEXT"
        private const val ACTION_TODAY = "com.pantelmg.tb2cal.month.TODAY"
        private const val MAX_CHIPS = 3
        private val CHIP_IDS = intArrayOf(R.id.chip0, R.id.chip1, R.id.chip2)
        private val WEEK_IDS = intArrayOf(R.id.week0, R.id.week1, R.id.week2, R.id.week3, R.id.week4, R.id.week5)
        private val WEEKDAY_IDS = intArrayOf(R.id.wd0, R.id.wd1, R.id.wd2, R.id.wd3, R.id.wd4, R.id.wd5, R.id.wd6)
        private const val DEFAULT_CHIP = 0xFF1565C0.toInt()

        private fun prefs(ctx: Context) = ctx.getSharedPreferences("month_widget", Context.MODE_PRIVATE)
        private fun key(id: Int) = "offset_$id"

        /** Redraws every placed calendar widget (called whenever TimeBlock records change). */
        fun refresh(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(ctx, MonthWidget::class.java))
            ids.forEach { manager.updateAppWidget(it, build(ctx, it)) }
        }

        private fun build(ctx: Context, widgetId: Int): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_month)
            val locale = Locale.getDefault()
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val offset = prefs(ctx).getInt(key(widgetId), 0)
            val month = today.withDayOfMonth(1).plusMonths(offset.toLong())

            // ---- Header ----
            val title = month.format(DateTimeFormatter.ofPattern("LLLL", locale)).replaceFirstChar { it.uppercase() } +
                if (month.year != today.year) " ${month.year}" else ""
            v.setTextViewText(R.id.month_title, title)
            v.setOnClickPendingIntent(R.id.month_title, nav(ctx, widgetId, ACTION_TODAY, 1))
            v.setOnClickPendingIntent(R.id.month_prev, nav(ctx, widgetId, ACTION_PREV, 2))
            v.setOnClickPendingIntent(R.id.month_next, nav(ctx, widgetId, ACTION_NEXT, 3))
            v.setOnClickPendingIntent(R.id.month_add, openApp(ctx, null, null, widgetId * 100 + 4))

            // ---- Weekday letters, starting on the locale's first day of the week ----
            val firstDow = WeekFields.of(locale).firstDayOfWeek
            for (i in 0 until 7) {
                v.setTextViewText(WEEKDAY_IDS[i], firstDow.plus(i.toLong()).getDisplayName(TextStyle.NARROW_STANDALONE, locale))
            }

            // ---- Completed TimeBlocks grouped by day ----
            var gridStart = month
            while (gridStart.dayOfWeek != firstDow) gridStart = gridStart.minusDays(1)
            val gridEnd = gridStart.plusDays(42)
            // Completed TimeBlocks, plus Gmail events when "Sync with your Gmail" is on.
            val byDay: Map<LocalDate, List<DayItem>> =
                runCatching { CalendarData.byDay(ctx, gridStart, gridEnd) }.getOrDefault(emptyMap())

            // ---- 6 × 7 grid ----
            for (w in 0 until 6) {
                v.removeAllViews(WEEK_IDS[w])
                for (d in 0 until 7) {
                    val index = w * 7 + d
                    val date = gridStart.plusDays(index.toLong())
                    v.addView(WEEK_IDS[w], cell(ctx, widgetId, index, date, month, today, byDay[date].orEmpty()))
                }
            }
            return v
        }

        private fun cell(
            ctx: Context, widgetId: Int, index: Int, date: LocalDate, month: LocalDate,
            today: LocalDate, blocks: List<DayItem>,
        ): RemoteViews {
            val c = RemoteViews(ctx.packageName, R.layout.widget_month_cell)
            val inMonth = date.month == month.month && date.year == month.year
            c.setTextViewText(R.id.day, date.dayOfMonth.toString())
            if (date == today) {
                c.setInt(R.id.day, "setBackgroundResource", R.drawable.widget_today_bg)
                c.setTextColor(R.id.day, 0xFFFFFFFF.toInt())
            } else {
                c.setInt(R.id.day, "setBackgroundResource", 0)
                c.setTextColor(R.id.day, if (inMonth) 0xFFFFFFFF.toInt() else 0x66FFFFFF)
            }

            blocks.take(MAX_CHIPS).forEachIndexed { i, b ->
                val id = CHIP_IDS[i]
                c.setViewVisibility(id, View.VISIBLE)
                c.setTextViewText(id, b.label)
                val base = b.color
                // Days of the neighbouring months: Same chips, half transparent.
                val color = if (inMonth) base else (base and 0x00FFFFFF) or 0x80000000.toInt()
                if (!inMonth) c.setTextColor(id, 0x99FFFFFF.toInt())
                if (Build.VERSION.SDK_INT >= 31) {
                    c.setColorStateList(id, "setBackgroundTintList", ColorStateList.valueOf(color))
                } else {
                    c.setInt(id, "setBackgroundColor", color)
                }
            }
            if (blocks.size > MAX_CHIPS) {
                // Next to the day number, so it never needs a row of its own.
                c.setViewVisibility(R.id.more, View.VISIBLE)
                c.setTextViewText(R.id.more, "+${blocks.size - MAX_CHIPS}")
            }

            // Tap a day → TB2Cal's Calendar tab, on that day.
            c.setOnClickPendingIntent(R.id.cell, openApp(ctx, MainActivity.ACTION_OPEN_CALENDAR_DAY, date.toEpochDay(), widgetId * 100 + 10 + index))
            return c
        }

        private fun nav(ctx: Context, widgetId: Int, action: String, n: Int): PendingIntent = PendingIntent.getBroadcast(
            ctx, widgetId * 10 + n,
            Intent(ctx, MonthWidget::class.java).setAction(action).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        private fun openApp(ctx: Context, action: String?, epochDay: Long?, rc: Int): PendingIntent {
            val i = Intent(ctx, MainActivity::class.java)
                .setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (epochDay != null) i.putExtra(MainActivity.EXTRA_EPOCH_DAY, epochDay)
            return PendingIntent.getActivity(ctx, rc, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
