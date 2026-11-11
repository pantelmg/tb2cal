package com.pantelmg.tb2cal.engine

import android.content.Context
import android.os.SystemClock
import android.provider.Settings as SysSettings
import com.pantelmg.tb2cal.calendar.AutoRegister
import com.pantelmg.tb2cal.data.Repo
import com.pantelmg.tb2cal.model.BlockType
import com.pantelmg.tb2cal.model.CompletedBlock
import com.pantelmg.tb2cal.model.HistoryEntry
import com.pantelmg.tb2cal.model.Mode
import com.pantelmg.tb2cal.model.Session
import com.pantelmg.tb2cal.model.Status
import com.pantelmg.tb2cal.service.Alarms
import com.pantelmg.tb2cal.service.AlertService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** Single source of truth for the running TimeBlock. Survives process death via Repo. */
object Engine {
    /** A timer counts as due this close to zero (absorbs alarm/clock jitter). */
    private const val DUE_TOLERANCE_MS = 1_500L

    private val _state = MutableStateFlow(Session())
    val state: StateFlow<Session> = _state.asStateFlow()
    private var initialized = false

    @Synchronized
    fun init(ctx: Context) {
        Repo.init(ctx)
        if (initialized) return
        initialized = true
        var s = Repo.loadSession()
        if (s.status == Status.RUNNING && s.bootCount != bootCount(ctx)) {
            // Device rebooted: The elapsed-time clock restarted, so fall back to wall clock for the gap.
            val nowWall = System.currentTimeMillis()
            s = s.copy(
                accumulatedMs = s.accumulatedMs + (nowWall - s.runningSinceWall).coerceAtLeast(0),
                runningSinceElapsed = SystemClock.elapsedRealtime(),
                runningSinceWall = nowWall,
                bootCount = bootCount(ctx),
            )
            Repo.saveSession(s)
        }
        _state.value = s
        Alarms.sync(ctx, s)
    }

    @Synchronized
    fun start(ctx: Context, block: BlockType, mode: Mode, targetMs: Long) {
        if (_state.value.active) return
        if (mode == Mode.TIMER && targetMs <= 0) return
        val nowWall = System.currentTimeMillis()
        val s = Session(
            status = Status.RUNNING,
            blockId = block.id,
            blockName = block.name,
            colorKey = block.colorKey,
            mode = mode,
            targetMs = if (mode == Mode.TIMER) targetMs else 0,
            startWallMs = nowWall,
            accumulatedMs = 0,
            runningSinceElapsed = SystemClock.elapsedRealtime(),
            runningSinceWall = nowWall,
            bootCount = bootCount(ctx),
        )
        set(ctx, s)
    }

    @Synchronized
    fun pause(ctx: Context) {
        val s = _state.value
        if (s.status != Status.RUNNING) return
        set(ctx, s.copy(status = Status.PAUSED, accumulatedMs = s.elapsedMs()))
    }

    @Synchronized
    fun resume(ctx: Context) {
        val s = _state.value
        if (s.status != Status.PAUSED) return
        val n = s.copy(
            status = Status.RUNNING,
            runningSinceElapsed = SystemClock.elapsedRealtime(),
            runningSinceWall = System.currentTimeMillis(),
            bootCount = bootCount(ctx),
        )
        set(ctx, n)
    }

    /** Discards the running TimeBlock. */
    @Synchronized
    fun stop(ctx: Context) {
        if (!_state.value.active) return
        set(ctx, Session())
    }

    /** Ends the TimeBlock and queues it for calendar transfer. */
    @Synchronized
    fun complete(ctx: Context): CompletedBlock? {
        val s = _state.value
        if (!s.active) return null
        val nowWall = System.currentTimeMillis()
        val elapsed = s.elapsedMs()
        val active: Long
        val end: Long
        if (s.mode == Mode.TIMER && s.targetMs - elapsed <= DUE_TOLERANCE_MS) {
            active = s.targetMs
            // End exactly when the countdown hit zero, even if we're a bit late/early.
            end = if (s.status == Status.RUNNING) nowWall + (s.targetMs - elapsed) else nowWall
        } else {
            active = elapsed
            end = nowWall
        }
        val c = CompletedBlock(
            id = UUID.randomUUID().toString(),
            name = s.blockName,
            colorKey = s.colorKey,
            mode = s.mode,
            startWallMs = s.startWallMs,
            endWallMs = end.coerceAtLeast(s.startWallMs + 1_000),
            activeMs = active,
        )
        val idle = Session()
        _state.value = idle
        val added = Repo.commitCompletion(c, HistoryEntry(c.name, c.colorKey, c.startWallMs, c.activeMs), idle)
        Alarms.sync(ctx, idle)
        if (!added) return null
        AutoRegister.start(ctx, c.id) // No-op unless Auto-register is on
        return c
    }

    /** Completes a running timer whose countdown reached zero and alerts the user. */
    @Synchronized
    fun checkTimerDue(ctx: Context): Boolean {
        val s = _state.value
        if (s.status != Status.RUNNING || s.mode != Mode.TIMER) return false
        if (s.remainingMs() > DUE_TOLERANCE_MS) return false
        val c = complete(ctx) ?: return false
        AlertService.start(ctx, c.label, id = c.id)
        return true
    }

    private fun set(ctx: Context, s: Session) {
        _state.value = s
        Repo.saveSession(s)
        Alarms.sync(ctx, s)
    }

    private fun bootCount(ctx: Context): Int =
        SysSettings.Global.getInt(ctx.contentResolver, SysSettings.Global.BOOT_COUNT, -1)
}
