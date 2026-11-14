package com.pantelmg.tb2cal.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.pantelmg.tb2cal.data.Repo
import com.pantelmg.tb2cal.widget.PlanWidget

/**
 * Task/Event manager widget taps, without any screen: Ticks a task's square, or opens the entry in the app (Calendar
 * tab, its day, at the top of the list, pulsing). An activity, since a broadcast may not open the app.
 */
class WidgetActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        val i = intent
        when (i?.getStringExtra(PlanWidget.EXTRA_ACT)) {
            "tick" -> PlanWidget.tick(this, i)
            "open" -> runCatching {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .setAction(MainActivity.ACTION_OPEN_CALENDAR_DAY)
                        .putExtra(MainActivity.EXTRA_EPOCH_DAY, i.getLongExtra(PlanWidget.EXTRA_DAY, com.pantelmg.tb2cal.model.Plan.today()))
                        .putExtra(MainActivity.EXTRA_FOCUS, i.getStringExtra(PlanWidget.EXTRA_KEY))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }
        }
        finish()
        // No animation: Nothing is seen of this activity.
        if (Build.VERSION.SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        else @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }
}
