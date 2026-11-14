package com.pantelmg.tb2cal.model

import android.os.SystemClock
import org.json.JSONObject
import java.util.Locale

enum class Mode(val label: String) { TIMER("Timer"), STOPWATCH("Stopwatch") }


enum class Status { IDLE, RUNNING, PAUSED }

/** Vibration patterns. Repeating ones keep pulsing until the user stops the alert. */
enum class Vibration(val label: String, val pattern: LongArray?, val repeats: Boolean = false) {
    OFF("Off", null),
    SHORT("Short", longArrayOf(0, 350)),
    LONG("Long", longArrayOf(0, 1500)),
    PULSE("Pulse until stopped", longArrayOf(0, 500, 300, 500, 300, 500, 1200), repeats = true),
}

/** The 11 preset calendar colors (keys are their usual color ids), plus custom "#RRGGBB" colors. */
data class GColor(val key: String, val name: String, val argb: Long)

object GoogleColors {
    val all = listOf(
        GColor("1", "Light purple", 0xFF7986CB),
        GColor("2", "Green", 0xFF33B679),
        GColor("3", "Purple", 0xFF8E24AA),
        GColor("4", "Pink", 0xFFE67C73),
        GColor("5", "Yellow", 0xFFF6BF26),
        GColor("6", "Orange", 0xFFF4511E),
        GColor("7", "Light blue", 0xFF039BE5),
        GColor("8", "Gray", 0xFF616161),
        GColor("9", "Blue", 0xFF3F51B5),
        GColor("10", "Dark green", 0xFF0B8043),
        GColor("11", "Red", 0xFFD50000),
    )

    /** A preset key ("1".."11"), or a custom color saved as "#RRGGBB". */
    fun byKey(key: String?): GColor? {
        if (key == null) return null
        if (key.startsWith("#")) {
            val rgb = key.substring(1).toLongOrNull(16) ?: return null
            return GColor(key, "Custom", 0xFF000000L or (rgb and 0xFFFFFFL))
        }
        return all.firstOrNull { it.key == key }
    }

    fun customKey(r: Int, g: Int, b: Int): String =
        String.format(java.util.Locale.ROOT, "#%02X%02X%02X", r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))

    fun isCustom(key: String?) = key?.startsWith("#") == true

    /**
     * Text color for a label drawn on [argb]: White, unless the color is so light that white text would be hard to
     * read (yellow, white, light grays, pastels…); Then black.
     */
    fun textOn(argb: Int): Int {
        fun ch(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        val l = 0.2126 * ch((argb shr 16) and 0xFF) + 0.7152 * ch((argb shr 8) and 0xFF) + 0.0722 * ch(argb and 0xFF)
        return if (l > 0.37) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
}

/** A selectable TimeBlock name with its calendar color (null = calendar default). */
data class BlockType(val id: String, val name: String, val colorKey: String?) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("color", colorKey ?: "")

    companion object {
        fun fromJson(o: JSONObject) = BlockType(
            o.getString("id"), o.getString("name"), o.optString("color").ifEmpty { null }
        )
    }
}

data class Session(
    val status: Status = Status.IDLE,
    val blockId: String = "",
    val blockName: String = "",
    val colorKey: String? = null,
    val mode: Mode = Mode.TIMER,
    val targetMs: Long = 0,
    val startWallMs: Long = 0,
    val accumulatedMs: Long = 0,
    val runningSinceElapsed: Long = 0,
    val runningSinceWall: Long = 0,
    val bootCount: Int = -1,
) {
    val active: Boolean get() = status != Status.IDLE

    fun elapsedMs(nowElapsed: Long = SystemClock.elapsedRealtime()): Long =
        if (status == Status.RUNNING) accumulatedMs + (nowElapsed - runningSinceElapsed).coerceAtLeast(0)
        else accumulatedMs

    fun remainingMs(nowElapsed: Long = SystemClock.elapsedRealtime()): Long =
        (targetMs - elapsedMs(nowElapsed)).coerceAtLeast(0)

    fun toJson(): JSONObject = JSONObject()
        .put("status", status.name).put("blockId", blockId).put("blockName", blockName)
        .put("color", colorKey ?: "").put("mode", mode.name).put("target", targetMs)
        .put("start", startWallMs).put("acc", accumulatedMs)
        .put("runE", runningSinceElapsed).put("runW", runningSinceWall).put("boot", bootCount)

    companion object {
        fun fromJson(o: JSONObject) = Session(
            status = Status.valueOf(o.getString("status")),
            blockId = o.optString("blockId"),
            blockName = o.optString("blockName"),
            colorKey = o.optString("color").ifEmpty { null },
            mode = Mode.valueOf(o.getString("mode")),
            targetMs = o.optLong("target"),
            startWallMs = o.optLong("start"),
            accumulatedMs = o.optLong("acc"),
            runningSinceElapsed = o.optLong("runE"),
            runningSinceWall = o.optLong("runW"),
            bootCount = o.optInt("boot", -1),
        )
    }
}

data class CompletedBlock(
    val id: String,
    val name: String,
    val colorKey: String?,
    val mode: Mode,
    val startWallMs: Long,
    val endWallMs: Long,
    val activeMs: Long,
    val eventId: Long? = null,
    val calendarName: String? = null,
    /** Legacy: Set by older versions that also sent TimeBlocks online. Counts as registered. */
    val remoteId: String? = null,
    val link: String? = null,
) {
    val label: String get() = "${Fmt.duration(activeMs)} $name"
    val registered: Boolean get() = eventId != null || remoteId != null

    /** Same TimeBlock session (used to drop accidental double completions). */
    fun sameSession(o: CompletedBlock) = startWallMs == o.startWallMs && name == o.name

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("color", colorKey ?: "").put("mode", mode.name)
        .put("start", startWallMs).put("end", endWallMs).put("active", activeMs)
        .put("event", eventId ?: -1L).put("cal", calendarName ?: "")
        .put("remote", remoteId ?: "").put("link", link ?: "")

    companion object {
        fun fromJson(o: JSONObject) = CompletedBlock(
            id = o.getString("id"),
            name = o.getString("name"),
            colorKey = o.optString("color").ifEmpty { null },
            mode = Mode.valueOf(o.getString("mode")),
            startWallMs = o.getLong("start"),
            endWallMs = o.getLong("end"),
            activeMs = o.getLong("active"),
            eventId = o.optLong("event", -1L).takeIf { it >= 0 },
            calendarName = o.optString("cal").ifEmpty { null },
            remoteId = o.optString("remote").ifEmpty { null },
            link = o.optString("link").ifEmpty { null },
        )
    }
}

/** One completed TimeBlock, kept for statistics (independent of calendar registration). */
data class HistoryEntry(val name: String, val colorKey: String?, val startWallMs: Long, val activeMs: Long) {
    fun toJson(): JSONObject = JSONObject()
        .put("n", name).put("c", colorKey ?: "").put("s", startWallMs).put("a", activeMs)

    companion object {
        fun fromJson(o: JSONObject) = HistoryEntry(
            o.getString("n"), o.optString("c").ifEmpty { null }, o.getLong("s"), o.getLong("a")
        )
    }
}

data class Settings(
    val vibration: Vibration = Vibration.PULSE,
    /** null = default notification sound, "" = silent, otherwise a ringtone URI. */
    val soundUri: String? = null,
    /** Name shown for the chosen sound (saved when picked, since the source may become unreadable). */
    val soundTitle: String? = null,
    /** True: Play at the media volume. False: Play at the notification volume. */
    val useMediaVolume: Boolean = false,
    val calendarId: Long = -1,
    /** Default countdown, for TimeBlocks without one of their own yet. */
    val timerH: Int = 0,
    val timerM: Int = 25,
    /** Each TimeBlock's own countdown (TimeBlock id → minutes), saved as soon as it's changed. */
    val blockTimers: Map<String, Int> = emptyMap(),
    val lastBlockId: String? = null,
    val lastMode: Mode = Mode.TIMER,
    val channelVersion: Int = 0,
    // Background-reliability steps the app can't detect itself (user confirms them).
    val ackAutostart: Boolean = false,
    val ackBatterySaver: Boolean = false,
    /** Xiaomi: "TimeBlock completed" notifications allowed to pop up (floating) and show on the lock screen. */
    val ackFloating: Boolean = false,
    /** A timer ended: Its completion screen shows whenever the app is opened, until DONE (TimeBlock id; "" = none). */
    val completionToShow: String = "",
    val ackRecentsLock: Boolean = false,
    val hideReliabilityCard: Boolean = false,
    val ackCalendarSync: Boolean = false,
    val ackCalendarAutostart: Boolean = false,
    val ackCalendarData: Boolean = false,
    /** Register completed TimeBlocks automatically, without tapping the notification. */
    /** Always on: Completed TimeBlocks register by themselves. */
    val autoRegister: Boolean = true,
    /** Calendar tab: Also show the events of the Gmail accounts on this phone (read-only). */
    val gmailView: Boolean = false,
    /** First-launch permission prompt already shown. */
    val permissionsAsked: Boolean = false,
    /** Countdown presets on the main screen (minutes, ascending). */
    val presets: List<Int> = DEFAULT_PRESETS,
    /** Calendar tab and widget: Merge same-name TimeBlocks of a day into one total. */
    val tbTotal: Boolean = false,
    /** Statistics tab: Last period (DAILY, WEEKLY, MONTHLY, YEARLY) and Graph instead of List. */
    val statsPeriod: String = "WEEKLY",
    val statsGraph: Boolean = false,
    /** Goals tab: Last period, and the periods with "Apply onward" turned OFF (it's ON by default). */
    val goalsPeriod: String = "WEEKLY",
    val onwardOff: Set<String> = emptySet(),
    /** App color for buttons, switches and highlights: "" = the phone's wallpaper colors, else an accent key. */
    val accent: String = "",
    /** Calendar tab: TimeBlocks/events shown per day in the month grid (3, 4 or 5). */
    val dayEntries: Int = 3,
    /** Gmail account whose events are shown; "" = the most likely one (most events). */
    val gmailAccount: String = "",
    /** Multi-language (holiday) calendars turned off, by their calendar id (e.g. "en.greek#holiday@…"). */
    val holidayOff: Set<String> = emptySet(),
    /** Calendar tab: Hide next to Gmail events. Hidden: Whole events (all repeats) by event id, and single days of
     *  repeating events as "eventId@instanceStartMs". */
    val allowHide: Boolean = false,
    val hiddenEvents: Set<Long> = emptySet(),
    val hiddenInstances: Set<String> = emptySet(),
    /** Tasks and Events: One preset color each, for all of them (outline style). */
    val taskColor: String = "1",
    val eventColor: String = "6",
    /** Completed TimeBlocks and Events shown in other calendar apps (TB2Cal's phone calendar visible). */
    val othersSeeCalendar: Boolean = true,
    /** Task/Event manager widget: "BOTH" (Tasks/Events), "TASKS" or "EVENTS"; And whether it shows Gmail events. */
    val planWidgetList: String = "BOTH",
    /** Task/Event manager widget: The day shown (epoch day) and the day it was picked on (back to Today at midnight). */
    val planWidgetDay: Long = -1,
    val planWidgetDaySetOn: Long = -1,
    val widgetGmail: Boolean = false,
    /** Task/Event manager widget: The day's ticked tasks and past events stay crossed out ("CROSS") or go ("HIDE"). */
    val planWidgetDone: String = "CROSS",
    /** Task/Event reminders: Minutes before the start (0: At the start). */
    val remindBefore: Int = 0,
    /** Statistics: "TB" (TimeBlocks) or "TE" (Tasks/Events); In Tasks/Events: "TASKS", "EVENTS" or "BOTH". */
    val statsMode: String = "TB",
    val statsItems: String = "BOTH",
) {

    /** Countdown minutes of a TimeBlock: Its own, or the default. */
    fun timerMinutes(blockId: String?): Int = blockId?.let { blockTimers[it] } ?: (timerH * 60 + timerM)

    fun toJson(): JSONObject = JSONObject()
        .put("vib", vibration.name).put("sound", soundUri).put("soundTitle", soundTitle).put("media", useMediaVolume).put("cal", calendarId)
        .put("h", timerH).put("m", timerM).put("lastBlock", lastBlockId ?: "")
        .put("lastMode", lastMode.name).put("chv", channelVersion)
        .put("ackAuto", ackAutostart).put("ackSaver", ackBatterySaver).put("ackLock", ackRecentsLock)
        .put("showCompletion", completionToShow).put("ackFloating", ackFloating)
        .put("hideRel", hideReliabilityCard)
        .put("ackCalSync", ackCalendarSync)
        .put("ackCalAuto", ackCalendarAutostart).put("ackCalData", ackCalendarData).put("permAsked", permissionsAsked).put("autoReg", autoRegister)
        .put("gmailView", gmailView)
        .put("timers", JSONObject().also { j -> blockTimers.forEach { (k, v) -> j.put(k, v) } })
        .put("presets", org.json.JSONArray().also { a -> presets.forEach { a.put(it) } })
        .put("tbTotal", tbTotal).put("statsPeriod", statsPeriod).put("statsGraph", statsGraph)
        .put("goalsPeriod", goalsPeriod)
        .put("onwardOff", org.json.JSONArray().also { a -> onwardOff.forEach { a.put(it) } })
        .put("accent", accent)
        .put("taskColor", taskColor).put("eventColor", eventColor).put("othersSee", othersSeeCalendar)
        .put("planList", planWidgetList).put("widgetGmail", widgetGmail)
        .put("planDay", planWidgetDay).put("planDayOn", planWidgetDaySetOn).put("planDone", planWidgetDone).put("remindBefore", remindBefore)
        .put("statsMode", statsMode).put("statsItems", statsItems)
        .put("dayEntries", dayEntries).put("gmailAccount", gmailAccount).put("allowHide", allowHide)
        .put("holidayOff", org.json.JSONArray().also { a -> holidayOff.forEach { a.put(it) } })
        .put("hidden", org.json.JSONArray().also { a -> hiddenEvents.forEach { a.put(it) } })
        .put("hiddenOnce", org.json.JSONArray().also { a -> hiddenInstances.forEach { a.put(it) } })

    companion object {
        val DEFAULT_PRESETS = listOf(15, 25, 45, 60, 90, 120)

        fun fromJson(o: JSONObject) = Settings(
            vibration = runCatching { Vibration.valueOf(o.getString("vib")) }.getOrDefault(Vibration.PULSE),
            soundUri = if (o.has("sound")) o.getString("sound") else null,
            soundTitle = o.optString("soundTitle").ifEmpty { null },
            useMediaVolume = o.optBoolean("media"),
            calendarId = o.optLong("cal", -1),
            timerH = o.optInt("h", 0),
            timerM = o.optInt("m", 25),
            lastBlockId = o.optString("lastBlock").ifEmpty { null },
            lastMode = runCatching { Mode.valueOf(o.getString("lastMode")) }.getOrDefault(Mode.TIMER),
            channelVersion = o.optInt("chv", 0),
            ackAutostart = o.optBoolean("ackAuto"),
            ackBatterySaver = o.optBoolean("ackSaver"),
            completionToShow = o.optString("showCompletion"),
            ackFloating = o.optBoolean("ackFloating"),
            ackRecentsLock = o.optBoolean("ackLock"),
            hideReliabilityCard = o.optBoolean("hideRel"),
            ackCalendarSync = o.optBoolean("ackCalSync"),
            ackCalendarAutostart = o.optBoolean("ackCalAuto"),
            ackCalendarData = o.optBoolean("ackCalData"),
            autoRegister = true,
            gmailView = o.optBoolean("gmailView"),
            blockTimers = o.optJSONObject("timers")?.let { j ->
                j.keys().asSequence().associateWith { k -> j.optInt(k) }
            } ?: emptyMap(),
            presets = o.optJSONArray("presets")?.let { a -> List(a.length()) { a.optInt(it) }.filter { it > 0 } }
                ?.takeIf { it.isNotEmpty() } ?: DEFAULT_PRESETS,
            tbTotal = o.optBoolean("tbTotal"),
            statsPeriod = o.optString("statsPeriod").ifEmpty { "WEEKLY" },
            statsGraph = o.optBoolean("statsGraph"),
            goalsPeriod = o.optString("goalsPeriod").ifEmpty { "WEEKLY" },
            accent = o.optString("accent"),
            dayEntries = o.optInt("dayEntries", 3).coerceIn(3, 5),
            taskColor = o.optString("taskColor").ifEmpty { "1" },
            eventColor = o.optString("eventColor").ifEmpty { "6" },
            othersSeeCalendar = o.optBoolean("othersSee", true),
            planWidgetList = o.optString("planList").ifEmpty { "BOTH" },
            planWidgetDay = o.optLong("planDay", -1L),
            planWidgetDaySetOn = o.optLong("planDayOn", -1L),
            widgetGmail = o.optBoolean("widgetGmail"),
            planWidgetDone = o.optString("planDone").ifEmpty { "CROSS" },
            remindBefore = o.optInt("remindBefore", 0),
            statsMode = o.optString("statsMode").ifEmpty { "TB" },
            statsItems = o.optString("statsItems").ifEmpty { "BOTH" },
            gmailAccount = o.optString("gmailAccount"),
            allowHide = o.optBoolean("allowHide"),
            holidayOff = o.optJSONArray("holidayOff")?.let { a -> List(a.length()) { a.optString(it) }.toSet() } ?: emptySet(),
            hiddenEvents = o.optJSONArray("hidden")?.let { a -> List(a.length()) { a.optLong(it) }.toSet() } ?: emptySet(),
            hiddenInstances = o.optJSONArray("hiddenOnce")?.let { a -> List(a.length()) { a.optString(it) }.toSet() } ?: emptySet(),
            onwardOff = o.optJSONArray("onwardOff")?.let { a -> List(a.length()) { a.optString(it) }.toSet() } ?: emptySet(),
            permissionsAsked = o.optBoolean("permAsked"),
        )
    }
}

object Fmt {
    /** 1h20m, 1h, 45m; Under a minute → 40s. */
    fun duration(ms: Long): String {
        val totalMin = ms / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 && m > 0 -> "${h}h${m}m"
            h > 0 -> "${h}h"
            m > 0 -> "${m}m"
            else -> "${ms / 1000}s"
        }
    }

    fun clock(ms: Long, roundUp: Boolean = false): String {
        val s = if (roundUp) (ms + 999) / 1000 else ms / 1000
        return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }
}

/**
 * A goal for one TimeBlock name (key: Trimmed, lowercase) and one period type (DAILY, WEEKLY, MONTHLY, YEARLY),
 * set in the period that starts on [startDay] (epoch day). [onward]: Also applies to every later period of that
 * type, until another onward goal is set. [minutes] 0 = no goal.
 */
data class Goal(val period: String, val nameKey: String, val startDay: Long, val minutes: Int, val onward: Boolean) {
    fun toJson(): JSONObject = JSONObject()
        .put("p", period).put("n", nameKey).put("s", startDay).put("m", minutes).put("o", onward)

    companion object {
        fun fromJson(o: JSONObject) = Goal(
            o.getString("p"), o.getString("n"), o.getLong("s"), o.getInt("m"), o.optBoolean("o")
        )

        /** Goal minutes for a name in the period starting on [startDay]: Its own one-off goal, else the latest onward one. */
        fun resolve(list: List<Goal>, period: String, nameKey: String, startDay: Long): Int {
            val mine = list.filter { it.period == period && it.nameKey == nameKey }
            mine.firstOrNull { !it.onward && it.startDay == startDay }?.let { return it.minutes }
            return mine.filter { it.onward && it.startDay <= startDay }.maxByOrNull { it.startDay }?.minutes ?: 0
        }

        /**
         * Sets a goal. One-off: Only for that period. Onward: From that period on, replacing onward goals set for
         * later periods (one-off goals of later periods stay).
         */
        fun set(list: List<Goal>, period: String, nameKey: String, startDay: Long, minutes: Int, onward: Boolean): List<Goal> =
            list.filterNot {
                it.period == period && it.nameKey == nameKey &&
                    ((!it.onward && it.startDay == startDay) || (onward && it.onward && it.startDay >= startDay))
            } + Goal(period, nameKey, startDay, minutes, onward)
    }
}
