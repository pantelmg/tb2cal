package com.pantelmg.tb2cal.plan

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pantelmg.tb2cal.R
import com.pantelmg.tb2cal.calendar.CalendarWriter
import com.pantelmg.tb2cal.data.Repo
import com.pantelmg.tb2cal.model.GoogleColors
import com.pantelmg.tb2cal.model.ItemKind
import com.pantelmg.tb2cal.model.Plan
import com.pantelmg.tb2cal.model.PlanItem
import com.pantelmg.tb2cal.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Every change to Tasks and Events goes through here: It saves the item, mirrors Events into TB2Cal's phone
 * calendar (off the main thread) and re-arms the reminders. Widgets refresh through Repo.
 */
object Plans {
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun newId(): String = UUID.randomUUID().toString()

    fun color(kind: ItemKind): Int {
        val s = Repo.settings.value
        val key = if (kind == ItemKind.TASK) s.taskColor else s.eventColor
        return GoogleColors.byKey(key)?.argb?.toInt() ?: 0xFF1565C0.toInt()
    }

    /**
     * Whether a past day's records may be changed: Always for today and later (and tasks without a date); For
     * earlier days only with Settings → Allow previous day editing.
     */
    fun canChange(day: Long?): Boolean = day == null || day >= Plan.today() || Repo.settings.value.allowPast

    /** Adds or replaces an item. */
    fun save(ctx: Context, item: PlanItem) {
        Repo.putItem(item)
        afterChange(ctx, item.id)
    }

    /** Deletes the whole item (every repeat). */
    fun delete(ctx: Context, item: PlanItem) {
        Repo.removeItem(item.id)
        val pid = item.providerId
        val app = ctx.applicationContext
        if (pid != null) io.launch { CalendarWriter.deleteEvent(app, pid) }
        Reminders.schedule(app)
    }

    /** Deletes one day of a repeating item. */
    fun deleteDay(ctx: Context, item: PlanItem, day: Long) {
        Repo.updateItem(item.id) { it.copy(skipped = it.skipped + day, doneDays = it.doneDays - day) }
        afterChange(ctx, item.id)
    }

    /**
     * Edits one day of a repeating item: That day leaves the repeat and becomes its own one-time item, with the
     * edited values (and that day's done state, for tasks).
     */
    fun editDay(ctx: Context, series: PlanItem, day: Long, edited: PlanItem) {
        val wasDone = day in series.doneDays
        deleteDay(ctx, series, day)
        val oneTime = edited.repeat == null
        val newDay = if (oneTime) edited.day ?: day else null
        save(
            ctx,
            edited.copy(
                id = newId(), day = newDay,
                doneDays = if (wasDone && newDay != null) setOf(newDay) else emptySet(),
                doneAt = null, skipped = emptySet(), providerId = null, createdAt = series.createdAt,
                lateDays = if (day in series.lateDays && newDay != null) setOf(newDay) else emptySet(),
            )
        )
    }

    /**
     * "This and following" (previous day editing OFF): The repeat keeps its days before [day] as they are and ends
     * the day before; From [day] on, the edited version continues as a new item.
     */
    fun editFrom(ctx: Context, series: PlanItem, day: Long, edited: PlanItem) {
        val r = series.repeat
        val before = if (r == null) 0 else Plan.occurrences(series.copy(skipped = emptySet()), r.startDay, day).size
        if (r == null || before == 0) {
            save(ctx, edited)
            return
        }
        endBefore(ctx, series, day)
        val er = edited.repeat
        val next = if (er == null) {
            val d = edited.day ?: day
            edited.copy(
                id = newId(), day = d, doneDays = series.doneDays.filter { it == d }.toSet(), skipped = emptySet(),
                lateDays = series.lateDays.filter { it == d }.toSet(),
            )
        } else {
            edited.copy(
                id = newId(),
                repeat = er.copy(
                    startDay = maxOf(er.startDay, day),
                    // The count goes on from where the earlier days left it (if it wasn't changed).
                    count = er.count?.let { n -> if (n == r.count) (n - before).coerceAtLeast(1) else n },
                ),
                doneDays = series.doneDays.filter { it >= day }.toSet(),
                skipped = series.skipped.filter { it >= day }.toSet(),
                lateDays = series.lateDays.filter { it >= day }.toSet(),
            )
        }
        save(ctx, next.copy(doneAt = null, providerId = null, createdAt = series.createdAt))
    }

    /** "This and following" delete: Days before [day] stay; The repeat ends the day before (or goes, if none). */
    fun deleteFrom(ctx: Context, series: PlanItem, day: Long) {
        val r = series.repeat
        if (r == null || Plan.occurrences(series.copy(skipped = emptySet()), r.startDay, day).isEmpty()) {
            delete(ctx, series)
            return
        }
        endBefore(ctx, series, day)
    }

    private fun endBefore(ctx: Context, series: PlanItem, day: Long) {
        val r = series.repeat ?: return
        save(
            ctx,
            series.copy(
                repeat = r.copy(endDay = day - 1, count = null),
                doneDays = series.doneDays.filter { it < day }.toSet(),
                skipped = series.skipped.filter { it < day }.toSet(),
                lateDays = series.lateDays.filter { it < day }.toSet(),
            )
        )
    }

    /**
     * Ticks a task off (or back on): One day of a dated/repeating task, or the whole undated task. A past day only
     * with previous day editing on; Then it's noted "Ticked later" / "Unticked later". Returns whether it changed.
     */
    fun setDone(ctx: Context, item: PlanItem, day: Long?, done: Boolean): Boolean {
        val undatedDoneDay = if (item.undated && !done) item.doneAt?.let { Plan.dayOf(it) } else null
        if (!canChange(day ?: undatedDoneDay)) return false
        val today = Plan.today()
        Repo.updateItem(item.id) {
            if (it.undated || day == null) it.copy(doneAt = if (done) System.currentTimeMillis() else null)
            else it.copy(
                doneDays = if (done) it.doneDays + day else it.doneDays - day,
                lateDays = if (day < today) it.lateDays + day else it.lateDays - day,
            )
        }
        Reminders.schedule(ctx.applicationContext)
        return true
    }

    /** Events: Copies into TB2Cal's phone calendar again (after a change, or a color change for all of them). */
    private fun afterChange(ctx: Context, id: String) {
        val app = ctx.applicationContext
        val item = Repo.items.value.firstOrNull { it.id == id }
        if (item != null && item.kind == ItemKind.EVENT) {
            io.launch {
                val newId = CalendarWriter.upsertEvent(app, item, color(ItemKind.EVENT))
                if (newId != item.providerId) Repo.updateItem(id) { it.copy(providerId = newId) }
            }
        }
        Reminders.schedule(app)
    }

    /** All Events again (new event color). */
    fun resyncEvents(ctx: Context) {
        val app = ctx.applicationContext
        io.launch {
            Repo.items.value.filter { it.kind == ItemKind.EVENT }.forEach { item ->
                val newId = CalendarWriter.upsertEvent(app, item, color(ItemKind.EVENT))
                if (newId != item.providerId) Repo.updateItem(item.id) { it.copy(providerId = newId) }
            }
        }
    }

    private var visibilityApplied = false

    /**
     * App opened: Copies Events that aren't in TB2Cal's phone calendar yet (for example, saved before calendar
     * access was allowed), and applies the "Visible to other calendar apps" setting once.
     */
    fun onAppResume(ctx: Context) {
        val app = ctx.applicationContext
        if (!CalendarWriter.hasPermission(app)) return
        if (!visibilityApplied) {
            visibilityApplied = true
            applyVisibility(app)
        }
        val missing = Repo.items.value.filter { it.kind == ItemKind.EVENT && it.providerId == null }
        if (missing.isEmpty()) return
        io.launch {
            missing.forEach { item ->
                val newId = CalendarWriter.upsertEvent(app, item, color(ItemKind.EVENT)) ?: return@forEach
                Repo.updateItem(item.id) { it.copy(providerId = newId) }
            }
        }
    }

    /** "Visible to other apps" setting → TB2Cal's phone calendar shown or hidden there. */
    fun applyVisibility(ctx: Context) {
        val app = ctx.applicationContext
        val visible = Repo.settings.value.othersSeeCalendar
        io.launch { CalendarWriter.setLocalVisible(app, visible) }
    }
}

/** Notifications at the time of timed tasks and events. One exact alarm for the next one, re-armed after each. */
object Reminders {
    const val CHANNEL = "tb_reminders"
    private const val ACTION_FIRE = "com.pantelmg.tb2cal.action.REMINDER"
    const val ACTION_DONE = "com.pantelmg.tb2cal.action.REMINDER_DONE"
    const val EXTRA_ID = "id"
    const val EXTRA_DAY = "day"

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "Task and event reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Before or at the start of a task or event that has a time (Settings: Remind me before)."
        }
        ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
    }

    private fun firePi(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 4100, Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** Arms the alarm for the next reminder (or clears it). */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val next = Plan.nextReminder(Repo.items.value, System.currentTimeMillis(), Repo.settings.value.remindBefore)
        val pi = firePi(ctx)
        if (next == null) {
            am.cancel(pi)
            return
        }
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        runCatching {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        }
    }

    /** Posts the notifications for what's due now, then arms the next one. */
    fun fire(ctx: Context) {
        val now = System.currentTimeMillis()
        val nm = ctx.getSystemService(NotificationManager::class.java)
        // Anything due in the last minute (the alarm can be a few seconds late).
        val before = Repo.settings.value.remindBefore
        val due = (Plan.dueAt(Repo.items.value, now, before) + Plan.dueAt(Repo.items.value, now - 60_000L, before))
            .distinctBy { it.item.id }
        due.forEach { o ->
            val item = o.item
            val day = o.day ?: return@forEach
            val nid = (item.id.hashCode() xor day.toInt())
            val b = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_tb)
                .setContentTitle("${item.kind.label}: ${item.title}")
                .setContentText(
                    when {
                        before == 0 -> Plan.timeText(item) ?: "Now"
                        else -> "In ${reminderLead(before)} · ${Plan.timeText(item) ?: ""}".trimEnd(' ', '·')
                    }
                )
                .setColor(Plans.color(item.kind))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(if (item.kind == ItemKind.EVENT) NotificationCompat.CATEGORY_EVENT else NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(
                    PendingIntent.getActivity(
                        ctx, nid,
                        Intent(ctx, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_CALENDAR_DAY)
                            .putExtra(MainActivity.EXTRA_EPOCH_DAY, day)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            if (item.kind == ItemKind.TASK) {
                b.addAction(
                    0, "Done",
                    PendingIntent.getBroadcast(
                        ctx, nid,
                        Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_DONE)
                            .putExtra(EXTRA_ID, item.id).putExtra(EXTRA_DAY, day),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            }
            runCatching { nm?.notify(nid, b.build()) }
        }
        schedule(ctx)
    }

    internal fun notificationId(id: String, day: Long) = id.hashCode() xor day.toInt()

    /** "10m", "1h", "1h30m" (like the app's durations). */
    fun reminderLead(min: Int) = com.pantelmg.tb2cal.model.Fmt.duration(min * 60_000L)
}

/** Reminder alarm, and the reminder's "Done" button. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Repo.init(context)
        when (intent.action) {
            Reminders.ACTION_DONE -> {
                val id = intent.getStringExtra(Reminders.EXTRA_ID) ?: return
                val day = intent.getLongExtra(Reminders.EXTRA_DAY, -1L).takeIf { it >= 0 } ?: return
                Repo.items.value.firstOrNull { it.id == id }?.let { Plans.setDone(context, it, day, true) }
                context.getSystemService(NotificationManager::class.java)?.cancel(Reminders.notificationId(id, day))
            }
            else -> Reminders.fire(context)
        }
    }
}
