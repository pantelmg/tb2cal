package com.pantelmg.tb2cal.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.pantelmg.tb2cal.data.Repo
import com.pantelmg.tb2cal.model.Plan

/**
 * The Task/Event manager widget's "+": A semi-screen over the home screen to pick Add Task or Add Event, then the
 * same sheets as in the app. Closes itself when done or dismissed.
 */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        setContent {
            TB2CalTheme {
                val ui = remember { PlanUi().also { it.add() } }
                PlanDialogs(ui, defaultDay = Plan.today(), onClosed = { finish() })
            }
        }
    }
}
