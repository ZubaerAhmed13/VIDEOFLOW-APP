package com.videoflow.app.project

import com.videoflow.app.data.project.MediaIdentity
import com.videoflow.app.data.project.RelinkIdentity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelinkPolicyTest {
    private val original = MediaIdentity("abc", 10_000L, 3840, 2160)

    @Test
    fun matchingIdentityPasses() {
        assertTrue(RelinkIdentity.matches(original, MediaIdentity("abc", 10_000L, 3840, 2160)))
    }

    @Test
    fun fingerprintMismatchFails() {
        assertFalse(RelinkIdentity.matches(original, MediaIdentity("def", 10_000L, 3840, 2160)))
    }

    @Test
    fun missingFingerprintFails() {
        assertFalse(RelinkIdentity.matches(original, MediaIdentity(null, 10_000L, 3840, 2160)))
    }

    @Test
    fun knownSizeMismatchFails() {
        assertFalse(RelinkIdentity.matches(original, MediaIdentity("abc", 10_001L, 3840, 2160)))
    }

    @Test
    fun knownDimensionMismatchFails() {
        assertFalse(RelinkIdentity.matches(original, MediaIdentity("abc", 10_000L, 1920, 1080)))
    }
}
