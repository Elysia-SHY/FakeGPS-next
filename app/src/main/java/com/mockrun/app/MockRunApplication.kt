package com.mockrun.app

import android.app.Application
import com.mockrun.app.location.RootSuBridge
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration

@HiltAndroidApp
class MockRunApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // OSMDroid requires setting a user agent string to avoid tile download rejection
        Configuration.getInstance().userAgentValue = packageName

        // Auto-heal: Ensure system is in High Accuracy mode and Wi-Fi/BLE scanning is enabled
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val rootBridge = RootSuBridge()
                if (rootBridge.isRootAvailable()) {
                    rootBridge.restoreScanningHardware()
                }
            }
        }
    }
}
