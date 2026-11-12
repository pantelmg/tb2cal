package com.pantelmg.tb2cal.data

import android.content.Context
import android.content.SharedPreferences
import com.pantelmg.tb2cal.model.BlockType
import com.pantelmg.tb2cal.model.CompletedBlock
import com.pantelmg.tb2cal.model.Goal
import com.pantelmg.tb2cal.model.HistoryEntry
import com.pantelmg.tb2cal.model.Session
import com.pantelmg.tb2cal.model.Settings
import com.pantelmg.tb2cal.widget.MonthWidget
import com.pantelmg.tb2cal.widget.TB2CalWidget
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** All persisted state, exposed as flows for the UI. Every change also refreshes the widget. */
object Repo {
    private lateinit var sp: SharedPreferences
    private lateinit var appCtx: Context
    private val lock = Any()

    val blocks = MutableStateFlow<List<BlockType>>(emptyList())
    val settings = MutableStateFlow(Settings())
    val pending = MutableStateFlow<List<CompletedBlock>>(emptyList())
    val history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val goals = MutableStateFlow<List<Goal>>(emptyList())

    fun init(ctx: Context) {
        synchronized(lock) {
            if (::sp.isInitialized) return
            appCtx = ctx.applicationContext
            sp = appCtx.getSharedPreferences("tb2cal", Context.MODE_PRIVATE)
            blocks.value = readList("blocks") { BlockType.fromJson(it) } ?: listOf(
                BlockType(UUID.randomUUID().toString(), "study", "7"),
                BlockType(UUID.randomUUID().toString(), "workout", "11"),
            ).also { writeList("blocks", it) { b -> b.toJson() } }
            settings.value = sp.getString("settings", null)
                ?.let { runCatching { Settings.fromJson(JSONObject(it)) }.getOrNull() } ?: Settings()
            // Registered items nobody closed with DONE (for example, auto-registered) are dropped after a day.
            val dayAgo = System.currentTimeMillis() - 24L * 3_600_000
            pending.value = (readList("pending") { CompletedBlock.fromJson(it) } ?: emptyList())
                .filterNot { it.registered && it.endWallMs < dayAgo }
            history.value = readList("history") { HistoryEntry.fromJson(it) } ?: emptyList()
            goals.value = readList("goals") { Goal.fromJson(it) } ?: emptyList()
        }
    }

    // ---- Blocks ----
    fun setBlocks(list: List<BlockType>) {
        synchronized(lock) {
            blocks.value = list
            writeList("blocks", list) { it.toJson() }
        }
        changed()
    }

    // ---- Goals ----
    fun setGoal(period: String, nameKey: String, startDay: Long, minutes: Int, onward: Boolean) {
        synchronized(lock) {
            val list = Goal.set(goals.value, period, nameKey, startDay, minutes, onward)
            goals.value = list
            writeList("goals", list) { it.toJson() }
        }
    }

    // ---- Settings ----
    fun updateSettings(f: (Settings) -> Settings) {
        synchronized(lock) {
            val s = f(settings.value)
            settings.value = s
            sp.edit().putString("settings", s.toJson().toString()).apply()
        }
        changed()
    }

    // ---- Session ----
    fun loadSession(): Session = sp.getString("session", null)
        ?.let { runCatching { Session.fromJson(JSONObject(it)) }.getOrNull() } ?: Session()

    fun saveSession(s: Session) {
        sp.edit().putString("session", s.toJson().toString()).commit()
        changed()
    }

    // ---- Pending completions ----
    /**
     * Records a completion in one atomic write: The pending item, its statistics entry and the
     * now-idle session. A crash can't leave the session "running" with the completion already
     * saved (which would complete it a second time). The same session is never added twice.
     */
    fun commitCompletion(c: CompletedBlock, h: HistoryEntry, idle: Session): Boolean {
        val added: Boolean
        synchronized(lock) {
            added = pending.value.none { it.sameSession(c) }
            val newPending = if (added) pending.value + c else pending.value
            val cutoff = System.currentTimeMillis() - 400L * 24 * 3_600_000
            val newHistory = if (history.value.any { it.startWallMs == h.startWallMs && it.name == h.name }) history.value
            else (history.value + h).filter { it.startWallMs >= cutoff }
            sp.edit()
                .putString("pending", toArray(newPending) { it.toJson() })
                .putString("history", toArray(newHistory) { it.toJson() })
                .putString("session", idle.toJson().toString())
                .commit()
            pending.value = newPending
            history.value = newHistory
        }
        changed()
        return added
    }

    fun findPending(id: String): CompletedBlock? = pending.value.firstOrNull { it.id == id }

    /** Changes one pending item based on its latest saved state (never a stale copy). */
    fun updatePending(id: String, f: (CompletedBlock) -> CompletedBlock) {
        synchronized(lock) {
            val list = pending.value.map { if (it.id == id) f(it) else it }
            pending.value = list
            writeList("pending", list) { it.toJson() }
        }
        changed()
    }

    fun removePending(id: String) = setPending(pending.value.filterNot { it.id == id })

    fun removeTransferred() = setPending(pending.value.filterNot { it.registered })

    // ---- History (statistics) ----
    /** Keeps about 13 months, enough for the monthly view and a year back. */
    fun addHistory(e: HistoryEntry) {
        synchronized(lock) {
            val cutoff = System.currentTimeMillis() - 400L * 24 * 3_600_000
            val list = (history.value + e).filter { it.startWallMs >= cutoff }
            history.value = list
            writeList("history", list) { it.toJson() }
        }
    }

    /**
     * Deletes one statistic: Every TimeBlock with this name (case and spacing ignored) that started
     * in [fromMs, toMs). Longer periods containing them shrink by the same amount automatically.
     */
    fun removeHistory(nameKey: String, fromMs: Long, toMs: Long) {
        synchronized(lock) {
            val list = history.value.filterNot {
                it.name.trim().lowercase() == nameKey && it.startWallMs >= fromMs && it.startWallMs < toMs
            }
            history.value = list
            writeList("history", list) { it.toJson() }
        }
        changed()
    }

    /** Deletes one completed TimeBlock from the records (Calendar tab bin). */
    fun removeHistoryEntry(name: String, startWallMs: Long) {
        synchronized(lock) {
            val list = history.value.filterNot { it.name == name && it.startWallMs == startWallMs }
            history.value = list
            writeList("history", list) { it.toJson() }
        }
        changed()
    }

    private fun setPending(list: List<CompletedBlock>) {
        synchronized(lock) {
            pending.value = list
            writeList("pending", list) { it.toJson() }
        }
        changed()
    }

    // ---- Helpers ----
    private fun changed() {
        if (::appCtx.isInitialized) {
            runCatching { TB2CalWidget.refresh(appCtx) }
            runCatching { MonthWidget.refresh(appCtx) }
        }
    }

    private fun <T> readList(key: String, parse: (JSONObject) -> T): List<T>? {
        val raw = sp.getString(key, null) ?: return null
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { parse(arr.getJSONObject(it)) }
        }.getOrNull()
    }

    private fun <T> toArray(list: List<T>, toJson: (T) -> JSONObject): String {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        return arr.toString()
    }

    private fun <T> writeList(key: String, list: List<T>, toJson: (T) -> JSONObject) {
        sp.edit().putString(key, toArray(list, toJson)).commit()
    }
}
