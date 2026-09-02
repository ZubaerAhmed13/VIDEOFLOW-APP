package com.videoflow.app.device

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.videoflow.app.data.device.DeviceCapabilityRepository
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceCapabilityInstrumentationTest {
    @Test
    fun actualCodecListIsInterrogated() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val profile = DeviceCapabilityRepository(context).read()
        assertTrue(profile.apiLevel >= 26)
        assertTrue(profile.totalRamBytes > 0L)
        assertTrue(profile.codecs.any { it.mime == "video/avc" })
    }
}
