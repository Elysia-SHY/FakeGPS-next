package com.mockrun.app.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientHookPolicyTest {
    @Test
    fun `ordinary application process accepts client hooks`() {
        assertTrue(ClientHookPolicy.shouldInstall("com.autonavi.minimap", 10_349))
    }

    @Test
    fun `system process is rejected regardless of package name`() {
        assertFalse(ClientHookPolicy.shouldInstall("system", 10_349))
    }

    @Test
    fun `system and shared system uids are rejected`() {
        assertFalse(ClientHookPolicy.shouldInstall("com.miui.contentcatcher", 1_000))
        assertFalse(ClientHookPolicy.shouldInstall("com.android.phone", 1_001))
    }

    @Test
    fun `MIUI auxiliary package does not install duplicate hooks`() {
        assertTrue(ClientHookPolicy.isAuxiliaryPackage("com.miui.contentcatcher"))
        assertFalse(ClientHookPolicy.isAuxiliaryPackage("com.autonavi.minimap"))
    }
}
