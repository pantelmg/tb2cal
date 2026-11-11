package com.pantelmg.tb2cal.calendar

import android.content.Context
import com.pantelmg.tb2cal.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The only place that writes completed TimeBlocks to the calendar. Guarantees one event per TimeBlock:
 *  - One registration at a time, app-wide (a lock), always re-reading the latest saved state.
 *  - Runs to the end even if the screen closes, so a saved event is always recorded.
 *  - Looks for the event before inserting (in case an earlier attempt was interrupted).
 */
object Registrar {
    private val lock = Mutex()

    sealed interface Result {
        data object Done : Result
        data class Failed(val reason: String) : Result
    }

    suspend fun register(ctx: Context, id: String): Result = withContext(NonCancellable + Dispatchers.IO) {
        lock.withLock {
            val c = Repo.findPending(id)
            if (c == null || c.registered) return@withLock Result.Done
            if (!CalendarWriter.hasPermission(ctx)) return@withLock Result.Failed("Calendar access is needed.")
            val cal = CalendarWriter.localCalendar(ctx)
                ?: return@withLock Result.Failed("Couldn't create TB2Cal's calendar on this phone.")
            val eventId = runCatching { CalendarWriter.insert(ctx, cal, c) }.getOrElse { e ->
                return@withLock Result.Failed(
                    "Couldn't add: ${(e.message ?: e.javaClass.simpleName).replaceFirstChar { ch -> ch.uppercase() }}"
                )
            }
            Repo.updatePending(id) { it.copy(eventId = eventId, calendarName = "${cal.name} calendar") }
            Result.Done
        }
    }
}
