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
data class GmailEvent(val title: String, val beginMs: Long, val endMs: Long, val allDay: Boolean, val color: Int)

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

    /**
     * Events of every Gmail account on the phone between [fromMs] and [toMs], recurring ones
     * expanded. They come from the phone's own copy, kept up to date by Android's account sync.
     */
    fun gmailEvents(ctx: Context, fromMs: Long, toMs: Long): List<GmailEvent> {
        if (!hasPermission(ctx)) return emptyList()
        val calIds = ctx.contentResolver.query(
            Calendars.CONTENT_URI, arrayOf(Calendars._ID),
            "${Calendars.ACCOUNT_TYPE}=? AND ${Calendars.VISIBLE}=1", arrayOf(GMAIL_TYPE), null
        )?.use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }.orEmpty()
        if (calIds.isEmpty()) return emptyList()

        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, fromMs)
            ContentUris.appendId(it, toMs)
        }.build()
        val out = mutableListOf<GmailEvent>()
        runCatching {
            ctx.contentResolver.query(
                uri,
                arrayOf(Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.DISPLAY_COLOR),
                "${Instances.CALENDAR_ID} IN (${calIds.joinToString(",")})", null,
                "${Instances.BEGIN} ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    out += GmailEvent(
                        title = c.getString(0)?.takeIf { it.isNotBlank() } ?: "(No title)",
                        beginMs = c.getLong(1),
                        endMs = c.getLong(2),
                        allDay = !c.isNull(3) && c.getInt(3) == 1,
                        color = if (c.isNull(4)) 0xFF1565C0.toInt() else c.getInt(4) or 0xFF000000.toInt(),
                    )
                }
            }
        }
        return out
    }
}
