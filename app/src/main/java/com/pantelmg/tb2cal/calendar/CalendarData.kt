package com.pantelmg.tb2cal.calendar

import android.content.Context
import com.pantelmg.tb2cal.data.Repo
import com.pantelmg.tb2cal.model.Fmt
import com.pantelmg.tb2cal.model.GoogleColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** One entry shown on a calendar day: A completed TimeBlock, or a Gmail event. */
data class DayItem(
    val label: String,
    val color: Int,
    val startMs: Long,
    val endMs: Long,
    val allDay: Boolean,
    val isTimeBlock: Boolean,
)

/**
 * What TB2Cal's Calendar tab and Calendar widget show: Completed TimeBlocks from TB2Cal's records,
 * plus (with "Sync with your Gmail" on) the events of the phone's Gmail accounts.
 */
object CalendarData {
    private const val DEFAULT_COLOR = 0xFF1565C0.toInt()

    /** Items per day for [from, toExclusive). Blocking (reads the phone's calendar): Call off the main thread. */
    fun byDay(ctx: Context, from: LocalDate, toExclusive: LocalDate): Map<LocalDate, List<DayItem>> {
        val zone = ZoneId.systemDefault()
        val fromMs = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMs = toExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        val out = mutableMapOf<LocalDate, MutableList<DayItem>>()
        fun add(d: LocalDate, item: DayItem) {
            if (!d.isBefore(from) && d.isBefore(toExclusive)) out.getOrPut(d) { mutableListOf() } += item
        }

        // Completed TimeBlocks
        val blockKeys = mutableSetOf<Pair<String, Long>>()
        Repo.history.value.filter { it.startWallMs in fromMs until toMs }.forEach { h ->
            val label = "${Fmt.duration(h.activeMs)} ${h.name}"
            blockKeys += label to h.startWallMs
            add(
                Instant.ofEpochMilli(h.startWallMs).atZone(zone).toLocalDate(),
                DayItem(label, GoogleColors.byKey(h.colorKey)?.argb?.toInt() ?: DEFAULT_COLOR,
                    h.startWallMs, h.startWallMs + h.activeMs, allDay = false, isTimeBlock = true)
            )
        }

        // Gmail events (read-only), skipping TimeBlocks that were once copied there
        if (Repo.settings.value.gmailView) {
            CalendarWriter.gmailEvents(ctx, fromMs - 86_400_000L, toMs + 86_400_000L).forEach { e ->
                if ((e.title to e.beginMs) in blockKeys) return@forEach
                val item = DayItem(e.title, e.color, e.beginMs, e.endMs, e.allDay, isTimeBlock = false)
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
        return out.mapValues { (_, list) ->
            list.sortedWith(compareByDescending<DayItem> { it.allDay }.thenBy { it.startMs })
        }
    }
}
