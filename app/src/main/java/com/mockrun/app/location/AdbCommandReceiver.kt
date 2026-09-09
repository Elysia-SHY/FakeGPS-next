package com.mockrun.app.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * BroadcastReceiver for controlling simulation via ADB commands.
 *
 * Example ADB usage:
 *
 * 1. Start simulation (resumes last route, or default):
 *    adb shell am broadcast -a com.mockrun.app.ACTION_RESUME
 *
 * 2. Pause simulation:
 *    adb shell am broadcast -a com.mockrun.app.ACTION_PAUSE
 *
 * 3. Resume simulation:
 *    adb shell am broadcast -a com.mockrun.app.ACTION_RESUME
 *
 * 4. Stop simulation:
 *    adb shell am broadcast -a com.mockrun.app.ACTION_STOP
 *
 * 5. Change speed to 12 km/h:
 *    adb shell am broadcast -a com.mockrun.app.ACTION_SET_SPEED --ef extra_speed 12.0
 *
 * 6. Seek to 50% progress:
 *    adb shell am broadcast -a com.mockrun.app.ACTION_SEEK --ef extra_seek_progress 0.5
 */
class AdbCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        val serviceIntent = Intent(context, MockLocationService::class.java).apply {
            this.action = action
            intent.extras?.let { putExtras(it) }
        }

        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
