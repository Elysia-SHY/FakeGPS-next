package com.mockrun.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration

@HiltAndroidApp
class MockRunApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // OSMDroid requires setting a user agent string to avoid tile download rejection
        Configuration.getInstance().userAgentValue = packageName
    }
}
