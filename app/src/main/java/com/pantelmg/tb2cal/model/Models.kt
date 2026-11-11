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

/** The 11 standard calendar event colors (keys are their usual color ids). */
data class GColor(val key: String, val name: String, val argb: Long)

object GoogleColors {
    val all = listOf(
        GColor("1", "Lavender", 0xFF7986CB),
        GColor("2", "Sage", 0xFF33B679),
        GColor("3", "Grape", 0xFF8E24AA),
        GColor("4", "Flamingo", 0xFFE67C73),
        GColor("5", "Banana", 0xFFF6BF26),
        GColor("6", "Tangerine", 0xFFF4511E),
        GColor("7", "Peacock", 0xFF039BE5),
        GColor("8", "Graphite", 0xFF616161),
        GColor("9", "Blueberry", 0xFF3F51B5),
        GColor("10", "Basil", 0xFF0B8043),
        GColor("11", "Tomato", 0xFFD50000),
    )

    fun byKey(key: String?): GColor? = all.firstOrNull { it.key == key }
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
    val timerH: Int = 0,
    val timerM: Int = 25,
    val lastBlockId: String? = null,
    val lastMode: Mode = Mode.TIMER,
    val channelVersion: Int = 0,
    // Background-reliability steps the app can't detect itself (user confirms them).
    val ackAutostart: Boolean = false,
    val ackBatterySaver: Boolean = false,
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
) {
    fun toJson(): JSONObject = JSONObject()
        .put("vib", vibration.name).put("sound", soundUri).put("soundTitle", soundTitle).put("media", useMediaVolume).put("cal", calendarId)
        .put("h", timerH).put("m", timerM).put("lastBlock", lastBlockId ?: "")
        .put("lastMode", lastMode.name).put("chv", channelVersion)
        .put("ackAuto", ackAutostart).put("ackSaver", ackBatterySaver).put("ackLock", ackRecentsLock)
        .put("hideRel", hideReliabilityCard)
        .put("ackCalSync", ackCalendarSync)
        .put("ackCalAuto", ackCalendarAutostart).put("ackCalData", ackCalendarData).put("permAsked", permissionsAsked).put("autoReg", autoRegister)
        .put("gmailView", gmailView)

    companion object {
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
            ackRecentsLock = o.optBoolean("ackLock"),
            hideReliabilityCard = o.optBoolean("hideRel"),
            ackCalendarSync = o.optBoolean("ackCalSync"),
            ackCalendarAutostart = o.optBoolean("ackCalAuto"),
            ackCalendarData = o.optBoolean("ackCalData"),
            autoRegister = true,
            gmailView = o.optBoolean("gmailView"),
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
