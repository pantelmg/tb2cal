package com.pantelmg.tb2cal.data

import android.content.Context
import android.net.Uri
import com.pantelmg.tb2cal.calendar.CalendarWriter
import com.pantelmg.tb2cal.model.Fmt
import com.pantelmg.tb2cal.model.HistoryEntry
import com.pantelmg.tb2cal.model.ItemKind
import com.pantelmg.tb2cal.model.Plan
import com.pantelmg.tb2cal.model.PlanItem
import com.pantelmg.tb2cal.model.RepeatUnit
import com.pantelmg.tb2cal.plan.Plans
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What a .t2c file holds: TB2Cal's own TimeBlocks, Tasks and Events between two days (inclusive). Never Gmail's.
 * [madeOn]: The day it was exported; Open tasks without a date count as that day's.
 */
class DataBundle(
    val from: Long, val to: Long, val timeBlocks: List<HistoryEntry>, val items: List<PlanItem>,
    val madeOn: Long = Plan.today(),
) {
    val tasks: Int get() = items.count { it.kind == ItemKind.TASK }
    val events: Int get() = items.count { it.kind == ItemKind.EVENT }
    val isEmpty: Boolean get() = timeBlocks.isEmpty() && items.isEmpty()

    fun toJson(): JSONObject = JSONObject()
        .put("app", "TB2Cal").put("format", 1).put("from", from).put("to", to).put("madeOn", madeOn)
        .put("timeBlocks", JSONArray().also { a -> timeBlocks.forEach { a.put(it.toJson()) } })
        .put("items", JSONArray().also { a -> items.forEach { a.put(it.toJson()) } })

    companion object {
        fun fromJson(o: JSONObject): DataBundle {
            if (o.optString("app") != "TB2Cal") throw IOException("Not a TB2Cal data file.")
            val tb = o.optJSONArray("timeBlocks")
            val it = o.optJSONArray("items")
            return DataBundle(
                o.getLong("from"), o.getLong("to"),
                if (tb == null) emptyList() else List(tb.length()) { i -> HistoryEntry.fromJson(tb.getJSONObject(i)) },
                if (it == null) emptyList() else List(it.length()) { i -> PlanItem.fromJson(it.getJSONObject(i)) },
                o.optLong("madeOn", o.getLong("to")),
            )
        }
    }
}

/** Export, import and delete of TB2Cal data by date range (Settings → Data management). */
object DataTransfer {
    data class Pick(val timeBlocks: Boolean, val tasks: Boolean, val events: Boolean) {
        val any: Boolean get() = timeBlocks || tasks || events
    }

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** "11 Nov 2026", "09 to 15 Nov 2026", "27 Oct to 02 Nov 2026", "29 Dec 2025 to 04 Jan 2026", "Oct 2026", "2026". */
    fun rangeName(from: Long, to: Long): String {
        val a = LocalDate.ofEpochDay(from)
        val b = LocalDate.ofEpochDay(to)
        fun f(d: LocalDate, p: String) = d.format(DateTimeFormatter.ofPattern(p, Locale.ENGLISH))
        return when {
            from == to -> f(a, "dd MMM yyyy")
            a.dayOfYear == 1 && b == a.withDayOfYear(a.lengthOfYear()) -> "${a.year}"
            a.dayOfMonth == 1 && b == a.withDayOfMonth(a.lengthOfMonth()) -> f(a, "MMM yyyy")
            a.year != b.year -> "${f(a, "dd MMM yyyy")} to ${f(b, "dd MMM yyyy")}"
            a.month != b.month -> "${f(a, "dd MMM")} to ${f(b, "dd MMM yyyy")}"
            else -> "${f(a, "dd")} to ${f(b, "dd MMM yyyy")}"
        }
    }

    fun fileName(from: Long, to: Long) = "TB2Cal Data ${rangeName(from, to)}.t2c"

    /** The day an undated task belongs to: The day it was done, or (still open) [openDay] (today, or a file's export day). */
    fun undatedDay(item: PlanItem, openDay: Long = Plan.today()) = item.doneAt?.let { Plan.dayOf(it) } ?: openDay

    /**
     * The part of [item] inside [from]–[to]: One-time items if their day is in it; Repeating items keep their rule,
     * from their first to their last day inside the range (with that range's done and removed days).
     */
    fun clip(item: PlanItem, from: Long, to: Long, openDay: Long = Plan.today()): PlanItem? {
        if (item.undated) return if (undatedDay(item, openDay) in from..to) item.copy(providerId = null) else null
        val r = item.repeat ?: return if (item.day != null && item.day in from..to) item.copy(providerId = null) else null
        val occ = Plan.occurrences(item, from, to + 1)
        if (occ.isEmpty()) return null
        val first = occ.first()
        val last = occ.last()
        return item.copy(
            repeat = r.copy(
                startDay = first, endDay = last, count = null,
                weekdays = if (r.unit == RepeatUnit.WEEK) r.days else emptySet(),
            ),
            doneDays = item.doneDays.filter { it in first..last }.toSet(),
            skipped = item.skipped.filter { it in first..last }.toSet(),
            providerId = null,
        )
    }

    /** TimeBlocks, Tasks and Events (as picked) inside [from]–[to]. */
    fun collect(
        history: List<HistoryEntry>, items: List<PlanItem>, from: Long, to: Long, pick: Pick, openDay: Long = Plan.today(),
    ): DataBundle = DataBundle(
        from, to,
        if (pick.timeBlocks) history.filter { Plan.dayOf(it.startWallMs) in from..to } else emptyList(),
        items.filter { (it.kind == ItemKind.TASK && pick.tasks) || (it.kind == ItemKind.EVENT && pick.events) }
            .mapNotNull { clip(it, from, to, openDay) },
    )

    /** TB2Cal's own data in the range right now. */
    fun current(from: Long, to: Long, pick: Pick) = collect(Repo.history.value, Repo.items.value, from, to, pick)

    fun write(ctx: Context, uri: Uri, b: DataBundle) {
        val out = ctx.contentResolver.openOutputStream(uri) ?: throw IOException("Can't open the file.")
        out.use { it.write(b.toJson().toString().toByteArray(Charsets.UTF_8)) }
    }

    fun read(ctx: Context, uri: Uri): DataBundle {
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IOException("Can't open the file.")
        return DataBundle.fromJson(JSONObject(text))
    }

    /** The last day a repeating item happens, or null if it never ends. */
    private fun lastDay(item: PlanItem): Long? {
        val r = item.repeat ?: return item.day
        return when {
            r.endDay != null -> Plan.occurrences(item.copy(skipped = emptySet()), r.startDay, r.endDay + 1).lastOrNull()
            r.count != null -> Plan.occurrences(item.copy(skipped = emptySet()), r.startDay, r.startDay + 366L * 1000).lastOrNull()
            else -> null
        }
    }

    /**
     * Deletes TB2Cal's TimeBlocks, Tasks and Events (as picked) inside [from]–[to], also from TB2Cal's phone
     * calendar. Repeating items lose only their days inside the range (all of them go if they're wholly inside).
     * Gmail events are never touched.
     */
    fun deleteRange(ctx: Context, from: Long, to: Long, pick: Pick) {
        val app = ctx.applicationContext
        if (pick.timeBlocks) {
            val gone = Repo.history.value.filter { Plan.dayOf(it.startWallMs) in from..to }
            if (gone.isNotEmpty()) {
                Repo.removeHistoryEntries(gone)
                io.launch { gone.forEach { h -> CalendarWriter.deleteFromLocal(app, "${Fmt.duration(h.activeMs)} ${h.name}", h.startWallMs) } }
            }
        }
        Repo.items.value
            .filter { (it.kind == ItemKind.TASK && pick.tasks) || (it.kind == ItemKind.EVENT && pick.events) }
            .forEach { item ->
                when {
                    item.undated -> if (undatedDay(item) in from..to) Plans.delete(app, item)
                    item.repeat == null -> if (item.day != null && item.day in from..to) Plans.delete(app, item)
                    else -> {
                        val r = item.repeat ?: return@forEach
                        val occ = Plan.occurrences(item, from, to + 1)
                        if (occ.isEmpty()) return@forEach
                        val last = lastDay(item)
                        if (r.startDay >= from && last != null && last <= to) Plans.delete(app, item)
                        else Plans.save(app, item.copy(skipped = item.skipped + occ, doneDays = item.doneDays - occ.toSet()))
                    }
                }
            }
    }

    /**
     * Imports the file's data inside [from]–[to] (as picked). Overwrite: TB2Cal's data there is deleted first (same
     * rules as Delete). Merge: Both are kept, without adding the same TimeBlock or item twice.
     */
    fun import(ctx: Context, b: DataBundle, from: Long, to: Long, pick: Pick, overwrite: Boolean): DataBundle {
        val app = ctx.applicationContext
        val part = collect(b.timeBlocks, b.items, from, to, pick, openDay = b.madeOn)
        if (overwrite) deleteRange(app, from, to, pick)

        val have = Repo.history.value.map { it.name to it.startWallMs }.toSet()
        val newBlocks = part.timeBlocks.filter { (it.name to it.startWallMs) !in have }
        if (newBlocks.isNotEmpty()) {
            Repo.addHistoryAll(newBlocks)
            io.launch { newBlocks.forEach { CalendarWriter.insertTimeBlock(app, it) } }
        }
        val ids = Repo.items.value.map { it.id }.toSet()
        val added = mutableListOf<PlanItem>()
        part.items.forEach { item ->
            val copy = when {
                item.id !in ids -> item
                overwrite -> item.copy(id = Plans.newId())
                else -> return@forEach // Merge: That item is already here.
            }
            Plans.save(app, copy.copy(providerId = null))
            added += copy
        }
        return DataBundle(from, to, newBlocks, added)
    }
}
