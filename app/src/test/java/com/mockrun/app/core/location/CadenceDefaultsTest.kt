package com.mockrun.app.core.location

import org.junit.Assert.assertEquals
import org.junit.Test

/** 默认步频只能有一个事实源，避免 165/172 分裂。 */
class CadenceDefaultsTest {

    @Test
    fun `sensor state uses persisted default custom spm`() {
        assertEquals(CadencePrefs.DEFAULT_CUSTOM_SPM, SensorMockData().customCadence)
    }
}
