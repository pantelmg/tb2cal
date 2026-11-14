package com.pantelmg.tb2cal.model

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Tasks and Events: A side feature next to TimeBlocks (TB2Cal's main focus). */
enum class ItemKind(val label: String) { TASK("Task"), EVENT("Event") }

enum class RepeatUnit(val one: String, val many: String) {
    DAY("day", "days"), WEEK("week", "weeks"), MONTH("month", "months"), YEAR("year", "years")
}

/**
 * A repeat rule, like Google's: Every [every] [unit] (weekly: On [weekdays], 1 = Monday … 7 = Sunday), from
 * [startDay] (epoch day), ending never, on [endDay] (inclusive) or after [count] occurrences.
 */
data class Repeat(
    val every: Int = 1,
    val unit: RepeatUnit = RepeatUnit.WEEK,
    val weekdays: Set<Int> = emptySet(),
    val startDay: Long,
    val endDay: Long? = null,
    val count: Int? = null,
) {
    private val step get() = every.coerceAtLeast(1)

    /** Weekdays actually used: The chosen ones, or the start date's. */
    val days: Set<Int> get() = weekdays.ifEmpty { setOf(LocalDate.ofEpochDay(startDay).dayOfWeek.value) }

    /** Whether [day] (on or after the start) fits the pattern; Start, end and count are checked elsewhere. */
    fun matches(day: Long): Boolean {
        if (day < startDay) return false
        val start = LocalDate.ofEpochDay(startDay)
        val date = LocalDate.ofEpochDay(day)
        return when (unit) {
            RepeatUnit.DAY -> (day - startDay) % step == 0L
            RepeatUnit.WEEK -> date.dayOfWeek.value in days &&
                (Math.floorDiv(day + 3, 7L) - Math.floorDiv(startDay + 3, 7L)) % step == 0L
            RepeatUnit.MONTH -> date.dayOfMonth == start.dayOfMonth &&
                ((date.year * 12 + date.monthValue) - (start.year * 12 + start.monthValue)) % step == 0
            RepeatUnit.YEAR -> date.monthValue == start.monthValue && date.dayOfMonth == start.dayOfMonth &&
                (date.year - start.year) % step == 0
        }
    }

    /** "Every week on Sat", "Every 2 days", "Every month on the 14th · 5 times", "Every year · until 13/02/27". */
    fun describe(): String = buildString {
        append(if (step == 1) "Every ${unit.one}" else "Every $step ${unit.many}")
        when (unit) {
            RepeatUnit.WEEK -> append(" on ").append(
                days.sorted().joinToString(", ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
            )
            RepeatUnit.MONTH -> append(" on the ").append(ordinal(LocalDate.ofEpochDay(startDay).dayOfMonth))
            else -> {}
        }
        count?.let { append(" · $it times") }
        endDay?.let { append(" · until ").append(Plan.shortDate(it)) }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("e", every).put("u", unit.name).put("w", JSONArray(weekdays.sorted())).put("s", startDay)
        .put("end", endDay ?: -1L).put("n", count ?: -1)

    companion object {
        fun fromJson(o: JSONObject) = Repeat(
            every = o.optInt("e", 1),
            unit = runCatching { RepeatUnit.valueOf(o.getString("u")) }.getOrDefault(RepeatUnit.WEEK),
            weekdays = o.optJSONArray("w")?.let { a -> List(a.length()) { a.optInt(it) }.toSet() } ?: emptySet(),
            startDay = o.getLong("s"),
            endDay = o.optLong("end", -1L).takeIf { it >= 0 },
            count = o.optInt("n", -1).takeIf { it > 0 },
        )

        private fun ordinal(n: Int) = "$n" + when {
            n % 100 in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }
    }
}

/**
 * A Task or an Event.
 * - Task: One-time without a date ([day] null) until done; Or repeating ([repeat]); Or, when one day of a
 *   repeating task was edited on its own, a one-time task on that [day]. [startMin]: Its time (reminder), if any.
 * - Event: On [day] (or repeating), all day or from [startMin] to [endMin] (minutes of the day).
 */
data class PlanItem(
    val id: String,
    val kind: ItemKind,
    val title: String,
    val day: Long? = null,
    val allDay: Boolean = true,
    val startMin: Int? = null,
    val endMin: Int? = null,
    val repeat: Repeat? = null,
    /** Tasks with dates: The days that are done. */
    val doneDays: Set<Long> = emptySet(),
    /** Undated task: When it was done (wall time), or null while open. */
    val doneAt: Long? = null,
    /** Days taken out of a repeat ("This day only" delete or edit). */
    val skipped: Set<Long> = emptySet(),
    val createdAt: Long = 0,
    /** Events: Its id in TB2Cal's phone calendar. */
    val providerId: Long? = null,
) {
    val undated: Boolean get() = kind == ItemKind.TASK && repeat == null && day == null
    val repeats: Boolean get() = repeat != null

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("k", kind.name).put("t", title).put("d", day ?: -1L).put("ad", allDay)
        .put("sm", startMin ?: -1).put("em", endMin ?: -1)
        .put("r", repeat?.toJson() ?: JSONObject.NULL)
        .put("done", JSONArray(doneDays.toList())).put("doneAt", doneAt ?: -1L)
        .put("skip", JSONArray(skipped.toList())).put("c", createdAt).put("p", providerId ?: -1L)

    companion object {
        fun fromJson(o: JSONObject) = PlanItem(
            id = o.getString("id"),
            kind = runCatching { ItemKind.valueOf(o.getString("k")) }.getOrDefault(ItemKind.TASK),
            title = o.optString("t"),
            day = o.optLong("d", -1L).takeIf { it >= 0 },
            allDay = o.optBoolean("ad", true),
            startMin = o.optInt("sm", -1).takeIf { it >= 0 },
            endMin = o.optInt("em", -1).takeIf { it >= 0 },
            repeat = o.optJSONObject("r")?.let { Repeat.fromJson(it) },
            doneDays = o.optJSONArray("done")?.let { a -> List(a.length()) { a.optLong(it) }.toSet() } ?: emptySet(),
            doneAt = o.optLong("doneAt", -1L).takeIf { it >= 0 },
            skipped = o.optJSONArray("skip")?.let { a -> List(a.length()) { a.optLong(it) }.toSet() } ?: emptySet(),
            createdAt = o.optLong("c"),
            providerId = o.optLong("p", -1L).takeIf { it >= 0 },
        )
    }
}

/** One occurrence of a Task/Event on a day (or an undated task), for lists, statistics and the PDF. */
data class PlanOccurrence(val item: PlanItem, val day: Long?, val done: Boolean) {
    val startMs: Long get() = Plan.startMs(item, day)
}

object Plan {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun today(): Long = LocalDate.now().toEpochDay()

    fun dayStartMs(day: Long): Long = LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli()

    fun dayOf(ms: Long): Long = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()

    /** "Sat 14/11/26". */
    fun shortDate(day: Long): String =
        LocalDate.ofEpochDay(day).format(java.time.format.DateTimeFormatter.ofPattern("EEE dd/MM/yy", Locale.ENGLISH))

    fun clock(min: Int): String = String.format(Locale.ROOT, "%02d:%02d", min / 60, min % 60)

    /** Days in [from, toExclusive) on which the item happens (one-time: its day). */
    fun occurrences(item: PlanItem, from: Long, toExclusive: Long): List<Long> {
        val r = item.repeat
        if (r == null) {
            val d = item.day ?: return emptyList()
            return if (d in from until toExclusive && d !in item.skipped) listOf(d) else emptyList()
        }
        val last = minOf(toExclusive - 1, r.endDay ?: Long.MAX_VALUE)
        val out = mutableListOf<Long>()
        if (r.count == null) {
            var d = maxOf(from, r.startDay)
            while (d <= last) {
                if (r.matches(d) && d !in item.skipped) out += d
                d++
            }
        } else {
            // "After N occurrences" counts from the start, so walk from there.
            var d = r.startDay
            var n = 0
            while (d <= last) {
                if (r.matches(d)) {
                    n++
                    if (n > r.count) break
                    if (d >= from && d !in item.skipped) out += d
                }
                d++
            }
        }
        return out
    }

    /** The first day the repeat happens (its real start, if the start date itself isn't one of the days). */
    fun firstDay(r: Repeat): Long? {
        var d = r.startDay
        val stop = minOf(r.startDay + 800, r.endDay ?: Long.MAX_VALUE)
        while (d <= stop) {
            if (r.matches(d)) return d
            d++
        }
        return null
    }

    fun startMs(item: PlanItem, day: Long?): Long {
        if (day == null) return item.doneAt ?: item.createdAt
        val base = dayStartMs(day)
        return if (item.allDay || item.startMin == null) base else base + item.startMin * 60_000L
    }

    fun endMs(item: PlanItem, day: Long): Long {
        val base = dayStartMs(day)
        return when {
            item.allDay || item.startMin == null -> dayStartMs(day + 1)
            item.endMin != null && item.endMin > item.startMin -> base + item.endMin * 60_000L
            else -> base + item.startMin * 60_000L
        }
    }

    /** Task: Ticked off that day (undated: at all). Event: Its time is over. */
    fun isDone(item: PlanItem, day: Long?, now: Long = System.currentTimeMillis()): Boolean = when (item.kind) {
        ItemKind.TASK -> if (day == null || item.undated) item.doneAt != null else day in item.doneDays
        ItemKind.EVENT -> day != null && endMs(item, day) <= now
    }

    /** Time text: "09:00 – 10:00", "09:00", or "All day" for events (null for tasks without a time). */
    fun timeText(item: PlanItem): String? = when {
        item.kind == ItemKind.EVENT && (item.allDay || item.startMin == null) -> "All day"
        item.startMin == null -> null
        item.kind == ItemKind.EVENT && item.endMin != null && item.endMin != item.startMin ->
            "${clock(item.startMin)} – ${clock(item.endMin)}"
        else -> clock(item.startMin)
    }

    /**
     * Every occurrence in [from, toExclusive): Dated and repeating tasks and events, plus undated tasks done in
     * that period (on the day they were done). Sorted by time, all-day/untimed first within a day.
     */
    fun occurrencesIn(
        items: List<PlanItem>, from: Long, toExclusive: Long,
        tasks: Boolean = true, events: Boolean = true, now: Long = System.currentTimeMillis(),
    ): List<PlanOccurrence> {
        val out = mutableListOf<PlanOccurrence>()
        items.forEach { item ->
            if (item.kind == ItemKind.TASK && !tasks) return@forEach
            if (item.kind == ItemKind.EVENT && !events) return@forEach
            if (item.undated) {
                val at = item.doneAt ?: return@forEach
                if (dayOf(at) in from until toExclusive) out += PlanOccurrence(item, null, true)
            } else {
                occurrences(item, from, toExclusive).forEach { d -> out += PlanOccurrence(item, d, isDone(item, d, now)) }
            }
        }
        return out.sortedWith(
            compareBy<PlanOccurrence> { it.day ?: dayOf(it.startMs) }
                .thenBy { if (it.item.allDay || it.item.startMin == null) 0 else 1 }
                .thenBy { it.startMs }
        )
    }

    /** Open undated tasks, oldest first (top of today's list and the widget). */
    fun openUndated(items: List<PlanItem>): List<PlanItem> =
        items.filter { it.undated && it.doneAt == null }.sortedBy { it.createdAt }

    /**
     * Next reminder time after [after] across timed tasks (not done that day) and timed events, or null. Reminders
     * come [beforeMin] minutes before the start (0: At the start). One whose time has already passed isn't given.
     */
    fun nextReminder(items: List<PlanItem>, after: Long, beforeMin: Int = 0): Long? {
        val lead = beforeMin * 60_000L
        val today = dayOf(after)
        var best: Long? = null
        items.forEach { item ->
            if (item.startMin == null || (item.kind == ItemKind.EVENT && item.allDay)) return@forEach
            if (item.undated) return@forEach
            val days = occurrences(item, today, today + 400)
            for (d in days) {
                if (item.kind == ItemKind.TASK && d in item.doneDays) continue
                val t = startMs(item, d) - lead
                if (t > after) {
                    if (best == null || t < best!!) best = t
                    break
                }
            }
        }
        return best
    }

    /** What's reminded at [at] (to the minute), [beforeMin] minutes before its start: For the notifications. */
    fun dueAt(items: List<PlanItem>, at: Long, beforeMin: Int = 0): List<PlanOccurrence> {
        val lead = beforeMin * 60_000L
        // The reminder can be on the day before (e.g. 23:50 for 00:05).
        val day = dayOf(at + lead)
        return items.mapNotNull { item ->
            if (item.startMin == null || (item.kind == ItemKind.EVENT && item.allDay) || item.undated) return@mapNotNull null
            if (occurrences(item, day, day + 1).isEmpty()) return@mapNotNull null
            if (item.kind == ItemKind.TASK && day in item.doneDays) return@mapNotNull null
            val t = startMs(item, day) - lead
            if (t / 60_000L == at / 60_000L) PlanOccurrence(item, day, false) else null
        }
    }
}
