package com.pantelmg.tb2cal.data

import android.content.Context
import android.net.Uri
import com.pantelmg.tb2cal.model.BlockType
import com.pantelmg.tb2cal.model.Goal
import com.pantelmg.tb2cal.model.Plan
import com.pantelmg.tb2cal.model.Settings
import com.pantelmg.tb2cal.plan.Plans
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What a .t2cp file holds: The TimeBlock list, goals and settings (without this phone's own ones). */
class PrefsBundle(val blocks: List<BlockType>, val goals: List<Goal>, val settings: JSONObject, val madeOn: Long)

/**
 * Export and import of preferences (Settings → Export/Import preferences). Importing overwrites the TimeBlock list,
 * goals and settings; The records (TimeBlocks, Tasks, Events) and this phone's own settings stay as they are.
 */
object PrefsTransfer {
    /**
     * Settings that belong to this phone, never written to the file and kept on import: Completion sound, TB2Cal's
     * phone calendar and notification channel, Gmail import (on/off, account, languages, hidden events), Background
     * reliability steps and permissions, and passing states (waiting completion screen, widget's day).
     */
    private val PHONE_KEYS = listOf(
        "sound", "soundTitle", "cal", "chv",
        "ackAuto", "ackSaver", "ackLock", "ackFloating", "ackCalSync", "ackCalAuto", "ackCalData", "hideRel", "permAsked",
        "gmailView", "gmailAccount", "holidayOff", "hidden", "hiddenOnce",
        "showCompletion", "planDay", "planDayOn",
    )

    /** The file's settings, with this phone's own ones from [cur]. */
    fun keepPhone(file: Settings, cur: Settings): Settings = file.copy(
        soundUri = cur.soundUri, soundTitle = cur.soundTitle, calendarId = cur.calendarId, channelVersion = cur.channelVersion,
        ackAutostart = cur.ackAutostart, ackBatterySaver = cur.ackBatterySaver, ackRecentsLock = cur.ackRecentsLock,
        ackFloating = cur.ackFloating, ackCalendarSync = cur.ackCalendarSync, ackCalendarAutostart = cur.ackCalendarAutostart,
        ackCalendarData = cur.ackCalendarData, hideReliabilityCard = cur.hideReliabilityCard,
        permissionsAsked = cur.permissionsAsked,
        gmailView = cur.gmailView, gmailAccount = cur.gmailAccount, holidayOff = cur.holidayOff,
        hiddenEvents = cur.hiddenEvents, hiddenInstances = cur.hiddenInstances,
        completionToShow = cur.completionToShow, planWidgetDay = cur.planWidgetDay, planWidgetDaySetOn = cur.planWidgetDaySetOn,
    )

    /** "TB2Cal Preferences 14 Nov 2026.t2cp" (the day it's saved). */
    fun fileName(day: Long = Plan.today()): String =
        "TB2Cal Preferences ${LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH))}.t2cp"

    fun toJson(blocks: List<BlockType>, goals: List<Goal>, settings: Settings, madeOn: Long = Plan.today()): JSONObject {
        val s = settings.toJson()
        PHONE_KEYS.forEach { s.remove(it) }
        return JSONObject()
            .put("app", "TB2Cal").put("kind", "preferences").put("format", 1).put("madeOn", madeOn)
            .put("blocks", JSONArray().also { a -> blocks.forEach { a.put(it.toJson()) } })
            .put("goals", JSONArray().also { a -> goals.forEach { a.put(it.toJson()) } })
            .put("settings", s)
    }

    fun fromJson(o: JSONObject): PrefsBundle {
        if (o.optString("app") != "TB2Cal" || o.optString("kind") != "preferences") {
            throw IOException("Not a TB2Cal preferences file.")
        }
        val b = o.optJSONArray("blocks")
        val g = o.optJSONArray("goals")
        return PrefsBundle(
            if (b == null) emptyList() else List(b.length()) { i -> BlockType.fromJson(b.getJSONObject(i)) },
            if (g == null) emptyList() else List(g.length()) { i -> Goal.fromJson(g.getJSONObject(i)) },
            o.optJSONObject("settings") ?: JSONObject(),
            o.optLong("madeOn", Plan.today()),
        )
    }

    /** Writes the current preferences. Blocking: Call off the main thread. */
    fun write(ctx: Context, uri: Uri) {
        val text = toJson(Repo.blocks.value, Repo.goals.value, Repo.settings.value).toString()
        val out = ctx.contentResolver.openOutputStream(uri) ?: throw IOException("Can't open the file.")
        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    /** Reads a .t2cp file. Blocking: Call off the main thread. */
    fun read(ctx: Context, uri: Uri): PrefsBundle {
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IOException("Can't open the file.")
        return fromJson(JSONObject(text))
    }

    /** Overwrites the TimeBlock list (kept if the file has none), goals and settings (but this phone's own ones). */
    fun import(ctx: Context, b: PrefsBundle) {
        val before = Repo.settings.value
        if (b.blocks.isNotEmpty()) Repo.setBlocks(b.blocks)
        Repo.setGoals(b.goals)
        val file = Settings.fromJson(b.settings)
        Repo.updateSettings { cur -> keepPhone(file, cur) }
        val after = Repo.settings.value
        // Things outside the app that follow the settings.
        if (after.eventColor != before.eventColor) Plans.resyncEvents(ctx)
        if (after.othersSeeCalendar != before.othersSeeCalendar) Plans.applyVisibility(ctx)
        if (after.remindBefore != before.remindBefore) com.pantelmg.tb2cal.plan.Reminders.schedule(ctx)
    }
}
