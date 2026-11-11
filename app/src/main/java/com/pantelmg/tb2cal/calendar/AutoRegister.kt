package com.pantelmg.tb2cal.calendar

import android.content.Context
import com.pantelmg.tb2cal.service.Notifications
import com.pantelmg.tb2cal.widget.TB2CalWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Registers a completed TimeBlock in the calendar the moment it finishes, in the background.
 * Uses the same Registrar as the completion screen, so it can never add a second event.
 * If calendar access is missing, the notification says "Tap to retry" and the screen asks for it.
 */
object AutoRegister {
    enum class State { RUNNING, FAILED }

    /** TimeBlock id → state, while running or after a failure. Gone once registered. */
    val states = MutableStateFlow<Map<String, State>>(emptyMap())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun stateOf(id: String?): State? = id?.let { states.value[it] }

    fun start(ctx: Context, id: String) {
        val app = ctx.applicationContext
        setState(id, State.RUNNING)
        refresh(app, id)
        scope.launch {
            val ok = runCatching { Registrar.register(app, id) is Registrar.Result.Done }.getOrDefault(false)
            setState(id, if (ok) null else State.FAILED)
            refresh(app, id)
        }
    }

    private fun setState(id: String, st: State?) {
        states.value = if (st == null) states.value - id else states.value + (id to st)
    }

    /** Updates the completion notification (if it's still showing) and the widget. */
    private fun refresh(ctx: Context, id: String) {
        runCatching { Notifications.refreshCompleted(ctx, id) }
        runCatching { TB2CalWidget.refresh(ctx) }
    }
}
