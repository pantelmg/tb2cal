package com.pantelmg.tb2cal.widget

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Redraws the Calendar and Task/Event manager widgets off the main thread (they read Gmail events from the phone's
 * calendar storage). Changes that come in a burst (typing, ticking several tasks) are drawn once, shortly after.
 */
object Widgets {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val queued = AtomicBoolean(false)

    fun refreshSoon(ctx: Context) {
        val app = ctx.applicationContext
        if (!queued.compareAndSet(false, true)) return // A redraw is already on its way and will see this change.
        scope.launch {
            delay(100)
            queued.set(false)
            runCatching { MonthWidget.refresh(app) }
            runCatching { PlanWidget.refresh(app) }
        }
    }
}
