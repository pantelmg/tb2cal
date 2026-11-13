package com.pantelmg.tb2cal.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat
import com.pantelmg.tb2cal.model.CompletedBlock
import com.pantelmg.tb2cal.model.Fmt
import com.pantelmg.tb2cal.model.GoogleColors
import java.util.TimeZone

data class CalInfo(val id: Long, val name: String)

/** An event read from the phone's Gmail calendars ("Sync with your Gmail"). Read-only. */
data class GmailEvent(
    val title: String, val beginMs: Long, val endMs: Long, val allDay: Boolean, val color: Int,
    /** The event (all its repeats share it); Used for hiding. */
    val eventId: Long,
    /** A repeating event (or a changed day of one). */
    val repeats: Boolean = false,
)

/** A calendar that publishes the same events in one language (Google's holiday calendars). */
data class LanguageCalendar(val key: String, val language: String, val name: String)

/**
 * The phone's calendar storage. Completed TimeBlocks go into TB2Cal's own local calendar, which the
 * phone's calendar app shows. Gmail calendars on the phone are only read, never changed.
 */
object CalendarWriter {
    private const val GMAIL_TYPE = "com.google"
    private const val LOCAL_ACCOUNT = "TB2Cal"
    val PERMS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    fun hasPermission(ctx: Context) = PERMS.all {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }

    /** TB2Cal's local calendar, created on first use. */
    fun localCalendar(ctx: Context): CalInfo? {
        if (!hasPermission(ctx)) return null
        fun find(): CalInfo? = ctx.contentResolver.query(
            Calendars.CONTENT_URI,
            arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME),
            "${Calendars.ACCOUNT_NAME}=? AND ${Calendars.ACCOUNT_TYPE}=?",
            arrayOf(LOCAL_ACCOUNT, CalendarContract.ACCOUNT_TYPE_LOCAL), null
        )?.use { c -> if (c.moveToFirst()) CalInfo(c.getLong(0), c.getString(1) ?: "TB2Cal") else null }
        find()?.let { return it }
        val uri = Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, "tb2cal")
            put(Calendars.CALENDAR_DISPLAY_NAME, "TB2Cal")
            put(Calendars.CALENDAR_COLOR, 0xFF1565C0.toInt())
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, LOCAL_ACCOUNT)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        }
        runCatching { ctx.contentResolver.insert(uri, values) }
        return find()
    }

    fun describe(c: CompletedBlock): String {
        val span = c.endWallMs - c.startWallMs
        return buildString {
            append("TB2Cal · ${c.mode.label} · Active ${Fmt.duration(c.activeMs)}")
            if (span - c.activeMs >= 60_000) append(" · Paused ${Fmt.duration(span - c.activeMs)}")
        }
    }

    /** The event for this TimeBlock if an earlier, interrupted attempt already saved it. */
    fun findExisting(ctx: Context, cal: CalInfo, c: CompletedBlock): Long? {
        if (!hasPermission(ctx)) return null
        return ctx.contentResolver.query(
            Events.CONTENT_URI, arrayOf(Events._ID),
            "${Events.CALENDAR_ID}=? AND ${Events.DELETED}=0 AND ${Events.TITLE}=? AND " +
                "${Events.DTSTART}=? AND ${Events.DTEND}=?",
            arrayOf(cal.id.toString(), c.label, c.startWallMs.toString(), c.endWallMs.toString()),
            null
        )?.use { cur -> if (cur.moveToFirst()) cur.getLong(0) else null }
    }

    /** Saves the TimeBlock in TB2Cal's calendar unless it's already there. Returns its id. Blocking. */
    fun insert(ctx: Context, cal: CalInfo, c: CompletedBlock): Long {
        findExisting(ctx, cal, c)?.let { return it }
        val values = ContentValues().apply {
            put(Events.CALENDAR_ID, cal.id)
            put(Events.TITLE, c.label)
            put(Events.DESCRIPTION, describe(c))
            put(Events.DTSTART, c.startWallMs)
            put(Events.DTEND, c.endWallMs)
            put(Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(Events.AVAILABILITY, Events.AVAILABILITY_BUSY)
            GoogleColors.byKey(c.colorKey)?.let { put(Events.EVENT_COLOR, it.argb.toInt()) }
        }
        val uri = ctx.contentResolver.insert(Events.CONTENT_URI, values)
        return uri?.let { ContentUris.parseId(it) } ?: error("The calendar rejected the event")
    }

    /** Deletes a TimeBlock's event from TB2Cal's own calendar (only ever that calendar). Blocking. */
    fun deleteFromLocal(ctx: Context, label: String, startMs: Long) {
        val cal = localCalendar(ctx) ?: return
        // As the calendar's owner ("sync adapter") the row is removed for good, not just marked deleted.
        val uri = Events.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        runCatching {
            ctx.contentResolver.delete(
                uri,
                "${Events.CALENDAR_ID}=? AND ${Events.TITLE}=? AND ${Events.DTSTART}=?",
                arrayOf(cal.id.toString(), label, startMs.toString())
            )
        }
    }

    /**
     * The phone's calendar storage keeps Gmail colors in the old ("classic") palette, e.g. Peacock as pale cyan
     * #46D6DB. Google's calendar app shows them in the current palette, so they're translated the same way.
     * Colors outside both palettes (custom colors) are shown as they are.
     */
    private val MODERN: Map<Int, Int> = mapOf(
        // Event colors
        0xA4BDFC to 0x7986CB, // Lavender
        0x7AE7BF to 0x33B679, // Sage
        0xDBADFF to 0x8E24AA, // Grape
        0xFF887C to 0xE67C73, // Flamingo
        0xFBD75B to 0xF6BF26, // Banana
        0xFFB878 to 0xF4511E, // Tangerine
        0x46D6DB to 0x039BE5, // Peacock
        0xE1E1E1 to 0x616161, // Graphite
        0x5484ED to 0x3F51B5, // Blueberry
        0x51B749 to 0x0B8043, // Basil
        0xDC2127 to 0xD50000, // Tomato
        // Calendar colors
        0xAC725E to 0x795548, // Cocoa
        0xD06B64 to 0xE67C73, // Flamingo
        0xF83A22 to 0xD50000, // Tomato
        0xFA573C to 0xF4511E, // Tangerine
        0xFF7537 to 0xEF6C00, // Pumpkin
        0xFFAD46 to 0xF09300, // Mango
        0x42D692 to 0x009688, // Eucalyptus
        0x16A765 to 0x0B8043, // Basil
        0x7BD148 to 0x7CB342, // Pistachio
        0xB3DC6C to 0xC0CA33, // Avocado
        0xFBE983 to 0xE4C441, // Citron
        0xFAD165 to 0xF6BF26, // Banana
        0x92E1C0 to 0x33B679, // Sage
        0x9FE1E7 to 0x039BE5, // Peacock
        0x9FC6E7 to 0x4285F4, // Cobalt
        0x4986E7 to 0x3F51B5, // Blueberry
        0x9A9CFF to 0x7986CB, // Lavender
        0xB99AFF to 0xB39DDB, // Wisteria
        0xC2C2C2 to 0x616161, // Graphite
        0xCABDBF to 0xA79B8E, // Birch
        0xCCA6AC to 0xAD1457, // Radicchio
        0xF691B2 to 0xD81B60, // Cherry Blossom
        0xCD74E6 to 0x8E24AA, // Grape
        0xA47AE2 to 0x9E69AF, // Amethyst
    )

    /** Stored color → the color Google's calendar app shows (opaque). */
    fun shownColor(stored: Int): Int {
        val rgb = stored and 0xFFFFFF
        return (MODERN[rgb] ?: rgb) or 0xFF000000.toInt()
    }

    private fun accountNames(ctx: Context): Set<String> = ctx.contentResolver.query(
        Calendars.CONTENT_URI, arrayOf(Calendars.ACCOUNT_NAME),
        "${Calendars.ACCOUNT_TYPE}=?", arrayOf(GMAIL_TYPE), null
    )?.use { c -> buildSet { while (c.moveToNext()) c.getString(0)?.let { add(it) } } }.orEmpty()

    // The ranking counts events, so it's reused for a minute (the calendar and widget redraw often).
    private var ranked: List<String> = emptyList()
    private var rankedAt = 0L

    /** Gmail accounts with calendars on the phone, most likely first (the one with the most events). Blocking. */
    fun gmailAccounts(ctx: Context): List<String> {
        if (!hasPermission(ctx)) return emptyList()
        val names = accountNames(ctx)
        val now = System.currentTimeMillis()
        if (now - rankedAt < 60_000L && ranked.toSet() == names) return ranked
        val counts = names.associateWith { name ->
            runCatching {
                ctx.contentResolver.query(
                    Events.CONTENT_URI, arrayOf(Events._ID),
                    "${Events.ACCOUNT_NAME}=? AND ${Events.ACCOUNT_TYPE}=? AND ${Events.DELETED}=0",
                    arrayOf(name, GMAIL_TYPE), null
                )?.use { it.count } ?: 0
            }.getOrDefault(0)
        }
        return names.sortedWith(compareByDescending<String> { counts[it] ?: 0 }.thenBy { it }).also {
            ranked = it
            rankedAt = now
        }
    }

    /** The account to show: The chosen one if it's still on the phone, else the most likely one. Blocking. */
    fun effectiveAccount(ctx: Context, chosen: String): String? {
        if (!hasPermission(ctx)) return null
        if (chosen.isNotEmpty() && chosen in accountNames(ctx)) return chosen
        return gmailAccounts(ctx).firstOrNull()
    }

    private fun isLanguageCalendar(key: String) = key.contains("#holiday@group.v.calendar.google.com")

    /** The account's multi-language calendars (e.g. holidays in Greek and in English). Blocking. */
    fun languageCalendars(ctx: Context, account: String): List<LanguageCalendar> {
        if (!hasPermission(ctx)) return emptyList()
        val out = mutableListOf<LanguageCalendar>()
        runCatching {
            ctx.contentResolver.query(
                Calendars.CONTENT_URI,
                arrayOf(Calendars.OWNER_ACCOUNT, Calendars._SYNC_ID, Calendars.CALENDAR_DISPLAY_NAME),
                "${Calendars.ACCOUNT_NAME}=? AND ${Calendars.ACCOUNT_TYPE}=? AND ${Calendars.VISIBLE}=1",
                arrayOf(account, GMAIL_TYPE), null
            )?.use { c ->
                while (c.moveToNext()) {
                    val key = listOfNotNull(c.getString(0), c.getString(1)).firstOrNull { isLanguageCalendar(it) } ?: continue
                    val code = key.substringBefore('.')
                    val lang = java.util.Locale.forLanguageTag(code).getDisplayLanguage(java.util.Locale.ENGLISH)
                        .ifBlank { code }
                    out += LanguageCalendar(key, lang, c.getString(2) ?: key)
                }
            }
        }
        return out.distinctBy { it.key }.sortedBy { it.language }
    }

    /**
     * Events of one Gmail account (the chosen or most likely one) between [fromMs] and [toMs], recurring ones
     * expanded, skipping the language calendars in [languagesOff]. They come from the phone's own copy, kept up to
     * date by Android's account sync. Blocking.
     */
    fun gmailEvents(
        ctx: Context, fromMs: Long, toMs: Long, chosenAccount: String = "", languagesOff: Set<String> = emptySet(),
    ): List<GmailEvent> {
        if (!hasPermission(ctx)) return emptyList()
        val account = effectiveAccount(ctx, chosenAccount) ?: return emptyList()
        val calIds = ctx.contentResolver.query(
            Calendars.CONTENT_URI, arrayOf(Calendars._ID, Calendars.OWNER_ACCOUNT, Calendars._SYNC_ID),
            "${Calendars.ACCOUNT_NAME}=? AND ${Calendars.ACCOUNT_TYPE}=? AND ${Calendars.VISIBLE}=1",
            arrayOf(account, GMAIL_TYPE), null
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val keys = listOfNotNull(c.getString(1), c.getString(2))
                    if (keys.any { isLanguageCalendar(it) && it in languagesOff }) continue
                    add(c.getLong(0))
                }
            }
        }.orEmpty()
        if (calIds.isEmpty()) return emptyList()

        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, fromMs)
            ContentUris.appendId(it, toMs)
        }.build()
        val out = mutableListOf<GmailEvent>()
        runCatching {
            ctx.contentResolver.query(
                uri,
                arrayOf(
                    Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.DISPLAY_COLOR,
                    Instances.EVENT_ID, Instances.RRULE, Instances.RDATE, Instances.ORIGINAL_ID
                ),
                "${Instances.CALENDAR_ID} IN (${calIds.joinToString(",")})", null,
                "${Instances.BEGIN} ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    out += GmailEvent(
                        title = c.getString(0)?.takeIf { it.isNotBlank() } ?: "(No title)",
                        beginMs = c.getLong(1),
                        endMs = c.getLong(2),
                        allDay = !c.isNull(3) && c.getInt(3) == 1,
                        color = if (c.isNull(4)) 0xFF1565C0.toInt() else shownColor(c.getInt(4)),
                        eventId = c.getLong(5),
                        repeats = !c.getString(6).isNullOrBlank() || !c.getString(7).isNullOrBlank() || !c.isNull(8),
                    )
                }
            }
        }
        return out
    }
}
