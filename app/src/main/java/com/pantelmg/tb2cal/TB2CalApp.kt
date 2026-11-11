package com.pantelmg.tb2cal

import android.app.Application
import com.pantelmg.tb2cal.data.Repo
import com.pantelmg.tb2cal.engine.Engine
import com.pantelmg.tb2cal.service.Notifications

class TB2CalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Engine.init(this)
        Notifications.createChannels(this)
    }
}
