package com.videoflow.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeValueTest {
    @Test
    fun fileSizesRemainLong() {
        val sizes = listOf(
            500L * 1024L * 1024L,
            2L * 1024L * 1024L * 1024L,
            3L * 1024L * 1024L * 1024L,
            5L * 1024L * 1024L * 1024L,
            10L * 1024L * 1024L * 1024L,
            100L * 1024L * 1024L * 1024L
        )
        sizes.forEach {
            assertTrue(it > 0L)
            assertFalse(formatBytes(it).startsWith("-"))
        }
    }

    @Test
    fun offsetsBeyondIntegerMaxRemainPositive() {
        val size = 100L * 1024L * 1024L * 1024L
        val sample = 4L * 1024L * 1024L
        val starts = listOf(0L, (size - sample) / 2L, size - sample)
        assertTrue(starts[1] > Int.MAX_VALUE.toLong())
        assertTrue(starts[2] > Int.MAX_VALUE.toLong())
        assertTrue(starts.all { it >= 0L })
    }
}
