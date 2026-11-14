package com.pantelmg.tb2cal.calendar

import android.content.Context
import com.pantelmg.tb2cal.data.Repo
import com.pantelmg.tb2cal.model.Fmt
import com.pantelmg.tb2cal.model.GoogleColors
import com.pantelmg.tb2cal.model.HistoryEntry
import com.pantelmg.tb2cal.model.Settings
import kotlinx.coroutines.launch
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

    /** A day entry's identity, for reordering: Task/Event, TimeBlock (or its TB Total), Gmail event. */
    fun entryKey(i: DayItem): String {
        val plan = i.plan
        return when {
            plan != null -> "p:${plan.id}"
            i.isTimeBlock && i.parts.isNotEmpty() -> "tbt:${i.blockName?.trim()?.lowercase()}"
            i.isTimeBlock -> "tb:${i.blockName?.trim()?.lowercase()}@${i.startMs}"
            else -> "g:${i.eventId}@${i.startMs}"
        }
    }

    /**
     * [list] in a reordered day's [order] (entry keys): The entries it names take the places such entries hold
     * in the normal order, sorted as in [order]; Others (new ones) stay in their normal place.
     */
    fun <T> applyOrder(list: List<T>, order: List<String>?, key: (T) -> String): List<T> {
        if (order.isNullOrEmpty()) return list
        val rank = order.withIndex().associate { (i, k) -> k to i }
        val slots = list.indices.filter { key(list[it]) in rank }
        if (slots.size < 2) return list
        val sorted = slots.map { list[it] }.sortedBy { rank[key(it)] }
        val out = list.toMutableList()
        slots.forEachIndexed { n, i -> out[i] = sorted[n] }
        return out
    }

    /** Every day of [map] in its reordered order (when reordering is on). */
    fun ordered(map: Map<LocalDate, List<DayItem>>, orders: Map<Long, List<String>>, enabled: Boolean): Map<LocalDate, List<DayItem>> =
        if (!enabled || orders.isEmpty()) map
        else map.mapValues { (d, l) -> applyOrder(l, orders[d.toEpochDay()]) { entryKey(it) } }

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

    // ---- Gmail events: Read from the phone's calendar storage (slow), kept in memory for instant redraws ----

    /** The last Gmail events read per month grid (and account/languages), most recent 8. */
    private val gmailCache = object : LinkedHashMap<String, List<GmailEvent>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<GmailEvent>>?) = size > 8
    }

    fun gmailKey(from: LocalDate, toExclusive: LocalDate, st: Settings) =
        "$from|$toExclusive|${st.gmailAccount}|${st.holidayOff.sorted()}"

    /** Gmail events read before for this range (maybe a little old), or null. */
    fun cachedGmail(from: LocalDate, toExclusive: LocalDate, st: Settings): List<GmailEvent>? =
        synchronized(gmailCache) { gmailCache[gmailKey(from, toExclusive, st)] }

    /** Reads the Gmail events of [from, toExclusive) and keeps them. Blocking: Call off the main thread. */
    fun fetchGmail(ctx: Context, from: LocalDate, toExclusive: LocalDate, st: Settings = Repo.settings.value): List<GmailEvent> {
        val zone = ZoneId.systemDefault()
        val fromMs = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMs = toExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        val events = CalendarWriter.gmailEvents(ctx, fromMs - 86_400_000L, toMs + 86_400_000L, st.gmailAccount, st.holidayOff)
        synchronized(gmailCache) { gmailCache[gmailKey(from, toExclusive, st)] = events }
        return events
    }

    /** The 6-week grid of a month (from the locale's first day of the week), as in the Calendar tab and widget. */
    fun gridStart(month: LocalDate, locale: java.util.Locale = java.util.Locale.getDefault()): LocalDate {
        val firstDow = java.time.temporal.WeekFields.of(locale).firstDayOfWeek
        var d = month.withDayOfMonth(1)
        while (d.dayOfWeek != firstDow) d = d.minusDays(1)
        return d
    }

    private val prefetchScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    /** Opening the app: Reads this month's Gmail events in the background, so the Calendar tab shows them at once. */
    fun prefetch(ctx: Context) {
        val app = ctx.applicationContext
        val st = Repo.settings.value
        if (!st.gmailView) return
        prefetchScope.launch {
            val g = gridStart(LocalDate.now())
            runCatching { fetchGmail(app, g, g.plusDays(42), st) }
        }
    }

    /**
     * [own] plus the Gmail [events] as shown: Without hidden ones (when hiding is on) and without TimeBlocks that were
     * once copied there; All-day ones on each of their days. Then sorted. Quick: Fine on the main thread.
     */
    fun withGmail(
        own: Map<LocalDate, List<DayItem>>, events: List<GmailEvent>?, from: LocalDate, toExclusive: LocalDate, st: Settings,
    ): Map<LocalDate, List<DayItem>> {
        val out = own.mapValues { (_, l) -> l.toMutableList() }.toMutableMap()
        if (st.gmailView && events != null) {
            val zone = ZoneId.systemDefault()
            fun add(d: LocalDate, item: DayItem) {
                if (!d.isBefore(from) && d.isBefore(toExclusive)) out.getOrPut(d) { mutableListOf() } += item
            }
            val blockKeys = own.values.flatten().filter { it.isTimeBlock }.map { it.label to it.startMs }.toSet()
            val hidden = if (st.allowHide) st.hiddenEvents else emptySet()
            val hiddenOnce = if (st.allowHide) st.hiddenInstances else emptySet()
            events.forEach { e ->
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

    /** Items per day for [from, toExclusive) (widgets). Blocking (reads the phone's calendar): Call off the main thread. */
    fun byDay(ctx: Context, from: LocalDate, toExclusive: LocalDate): Map<LocalDate, List<DayItem>> {
        val st = Repo.settings.value
        val own = ownByDay(Repo.history.value, Repo.items.value, from, toExclusive)
        return withGmail(own, if (st.gmailView) fetchGmail(ctx, from, toExclusive, st) else null, from, toExclusive, st)
    }
}
