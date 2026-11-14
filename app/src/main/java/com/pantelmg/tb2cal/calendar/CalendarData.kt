package com.pantelmg.tb2cal.calendar

import android.content.Context
import com.pantelmg.tb2cal.data.Repo
import com.pantelmg.tb2cal.model.Fmt
import com.pantelmg.tb2cal.model.GoogleColors
import com.pantelmg.tb2cal.model.HistoryEntry
import com.pantelmg.tb2cal.model.ItemKind
import com.pantelmg.tb2cal.model.Plan
import com.pantelmg.tb2cal.model.PlanItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** One entry shown on a calendar day: A completed TimeBlock, a Gmail event, or a TB2Cal Task/Event. */
data class DayItem(
    val label: String,
    val color: Int,
    val startMs: Long,
    val endMs: Long,
    val allDay: Boolean,
    val isTimeBlock: Boolean,
    /** TimeBlocks only: Its name as recorded, used to delete exactly this one. */
    val blockName: String? = null,
    /** TimeBlocks only: Active time. */
    val activeMs: Long = 0,
    /** TB Total: The TimeBlocks merged into this one (empty when not merged). */
    val parts: List<DayItem> = emptyList(),
    /** Gmail events only: The event's id (shared by all its repeats), for hiding, and whether it repeats. */
    val eventId: Long? = null,
    val repeats: Boolean = false,
    /** TB2Cal Task/Event (shown outlined): The item, the day of this occurrence (null: Undated task), and whether
     *  it's a done task (crossed out). */
    val plan: PlanItem? = null,
    val planDay: Long? = null,
    val done: Boolean = false,
)

/**
 * What TB2Cal's Calendar tab and Calendar widget show: Completed TimeBlocks from TB2Cal's records,
 * plus (with "Import from your Gmail" on) the events of the phone's Gmail accounts.
 */
object CalendarData {
    private const val DEFAULT_COLOR = 0xFF1565C0.toInt()

    /**
     * TB Total: Same-name TimeBlocks of one day become one entry with their time sum, placed where the first of
     * them was (so the order of the first of each name is kept). Events are left as they are.
     */
    fun mergeTotals(list: List<DayItem>): List<DayItem> {
        val out = mutableListOf<DayItem>()
        val index = mutableMapOf<String, Int>()
        list.forEach { item ->
            val name = item.blockName
            if (!item.isTimeBlock || name == null) {
                out += item
                return@forEach
            }
            val key = name.trim().lowercase()
            val i = index[key]
            if (i == null) {
                index[key] = out.size
                out += item.copy(parts = listOf(item))
            } else {
                val m = out[i]
                val parts = m.parts + item
                val sum = parts.sumOf { it.activeMs }
                out[i] = m.copy(label = "${Fmt.duration(sum)} ${m.blockName}", activeMs = sum, parts = parts)
            }
        }
        return out
    }

    /** [byDay], merged by name when TB Total is on. */
    fun shown(byDay: Map<LocalDate, List<DayItem>>, tbTotal: Boolean): Map<LocalDate, List<DayItem>> =
        if (tbTotal) byDay.mapValues { (_, l) -> mergeTotals(l) } else byDay

    /**
     * TB2Cal's own entries per day for [from, toExclusive): Completed TimeBlocks of [history] and the Tasks/Events of
     * [items] (outlined, one color each; Done tasks crossed out; Undated ones on the day they were done). No Gmail.
     * [timeBlocks] / [tasks] / [events]: Which kinds to include. [openDay]: If set, open tasks without a date are
     * shown on that day too (the Export, Import and Delete previews).
     */
    fun ownByDay(
        history: List<HistoryEntry>, items: List<PlanItem>, from: LocalDate, toExclusive: LocalDate,
        timeBlocks: Boolean = true, tasks: Boolean = true, events: Boolean = true, openDay: Long? = null,
    ): MutableMap<LocalDate, MutableList<DayItem>> {
        val zone = ZoneId.systemDefault()
        val fromMs = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMs = toExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        val out = mutableMapOf<LocalDate, MutableList<DayItem>>()
        fun add(d: LocalDate, item: DayItem) {
            if (!d.isBefore(from) && d.isBefore(toExclusive)) out.getOrPut(d) { mutableListOf() } += item
        }

        // Completed TimeBlocks
        if (timeBlocks) history.filter { it.startWallMs in fromMs until toMs }.forEach { h ->
            add(
                Instant.ofEpochMilli(h.startWallMs).atZone(zone).toLocalDate(),
                DayItem("${Fmt.duration(h.activeMs)} ${h.name}", GoogleColors.byKey(h.colorKey)?.argb?.toInt() ?: DEFAULT_COLOR,
                    h.startWallMs, h.startWallMs + h.activeMs, allDay = false, isTimeBlock = true, blockName = h.name,
                    activeMs = h.activeMs)
            )
        }

        // TB2Cal Tasks and Events
        val st = Repo.settings.value
        val taskColor = GoogleColors.byKey(st.taskColor)?.argb?.toInt() ?: DEFAULT_COLOR
        val eventColor = GoogleColors.byKey(st.eventColor)?.argb?.toInt() ?: DEFAULT_COLOR
        Plan.occurrencesIn(items, from.toEpochDay(), toExclusive.toEpochDay(), tasks, events).forEach { o ->
            val item = o.item
            val start = o.startMs
            val untimed = o.day != null && (item.allDay || item.startMin == null)
            add(
                LocalDate.ofEpochDay(o.day ?: Plan.dayOf(start)),
                DayItem(
                    item.title, if (item.kind == ItemKind.TASK) taskColor else eventColor,
                    start, if (o.day != null) Plan.endMs(item, o.day) else start, allDay = untimed, isTimeBlock = false,
                    repeats = item.repeats, plan = item, planDay = o.day, done = item.kind == ItemKind.TASK && o.done,
                )
            )
        }
        if (openDay != null && tasks) Plan.openUndated(items).forEach { t ->
            add(LocalDate.ofEpochDay(openDay), DayItem(t.title, taskColor, Plan.dayStartMs(openDay), Plan.dayStartMs(openDay),
                allDay = true, isTimeBlock = false, plan = t, planDay = null))
        }
        return out
    }

    /** Sorted for showing: All-day entries first, then by time. */
    fun sorted(map: Map<LocalDate, List<DayItem>>): Map<LocalDate, List<DayItem>> =
        map.mapValues { (_, list) -> list.sortedWith(compareByDescending<DayItem> { it.allDay }.thenBy { it.startMs }) }

    /** Items per day for [from, toExclusive). Blocking (reads the phone's calendar): Call off the main thread. */
    fun byDay(ctx: Context, from: LocalDate, toExclusive: LocalDate): Map<LocalDate, List<DayItem>> {
        val zone = ZoneId.systemDefault()
        val fromMs = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMs = toExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        val out = ownByDay(Repo.history.value, Repo.items.value, from, toExclusive)
        fun add(d: LocalDate, item: DayItem) {
            if (!d.isBefore(from) && d.isBefore(toExclusive)) out.getOrPut(d) { mutableListOf() } += item
        }
        // TimeBlocks that were once copied to Gmail aren't shown twice.
        val blockKeys = out.values.flatten().filter { it.isTimeBlock }.map { it.label to it.startMs }.toSet()

        // Gmail events (read-only), skipping TimeBlocks that were once copied there
        // Only the chosen account, without the languages turned off, and without hidden events (when hiding is on).
        val st = Repo.settings.value
        if (st.gmailView) {
            val hidden = if (st.allowHide) st.hiddenEvents else emptySet()
            val hiddenOnce = if (st.allowHide) st.hiddenInstances else emptySet()
            CalendarWriter.gmailEvents(ctx, fromMs - 86_400_000L, toMs + 86_400_000L, st.gmailAccount, st.holidayOff)
                .forEach { e ->
                if ((e.title to e.beginMs) in blockKeys || e.eventId in hidden || "${e.eventId}@${e.beginMs}" in hiddenOnce) return@forEach
                val item = DayItem(
                    e.title, e.color, e.beginMs, e.endMs, e.allDay, isTimeBlock = false,
                    eventId = e.eventId, repeats = e.repeats
                )
                if (e.allDay) {
                    // All-day events are stored in UTC midnights and may span several days.
                    var d = Instant.ofEpochMilli(e.beginMs).atZone(ZoneOffset.UTC).toLocalDate()
                    val end = Instant.ofEpochMilli(e.endMs).atZone(ZoneOffset.UTC).toLocalDate()
                    do {
                        add(d, item)
                        d = d.plusDays(1)
                    } while (d.isBefore(end))
                } else {
                    add(Instant.ofEpochMilli(e.beginMs).atZone(zone).toLocalDate(), item)
                }
            }
        }
        return sorted(out)
    }
}
