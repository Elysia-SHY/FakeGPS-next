package com.mockrun.app.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HookChannelPrefsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `default is provider`() {
        assertEquals(HookChannel.PROVIDER, HookChannelPrefs.get(context))
    }

    @Test
    fun `legacy auto is migrated to provider`() {
        context.getSharedPreferences("fake_gps_hook_channel_prefs", Context.MODE_PRIVATE)
            .edit().putString("hook_channel", HookChannel.AUTO.name).commit()
        assertEquals(HookChannel.PROVIDER, HookChannelPrefs.get(context))
    }

    @Test
    fun `manual lsposed is preserved`() {
        HookChannelPrefs.set(context, HookChannel.LSPOSED)
        assertEquals(HookChannel.LSPOSED, HookChannelPrefs.get(context))
    }

    @Test
    fun `cycle never returns auto`() {
        assertEquals(HookChannel.LSPOSED, HookChannelPrefs.next(HookChannel.PROVIDER))
        assertEquals(HookChannel.PROVIDER, HookChannelPrefs.next(HookChannel.LSPOSED))
    }
}
