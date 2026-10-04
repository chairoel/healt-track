package com.mascill.healthtrack

import android.app.Application
import com.mascill.healthtrack.data.datasource.local.datastore.HealthTrackPreferences
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {
    @Inject
    lateinit var preferences: HealthTrackPreferences

    override fun onCreate() {
        super.onCreate()
        preferences.store()
    }
}
