package com.videoflow.app.ui.product

import com.videoflow.app.domain.export.ExportFailureCode
import com.videoflow.app.domain.export.ExportJobStatus
import com.videoflow.app.domain.export.ExportQuality
import com.videoflow.app.domain.export.ExportResolutionPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductPresentationTest {
    @Test
    fun fileNameSanitization_keepsMp4AndRemovesUnsafeCharacters() {
        assertEquals("Summer_Trip_2026.mp4", sanitizeExportFileName(" Summer/Trip:2026.mp4 "))
        assertTrue(sanitizeExportFileName("video").endsWith(".mp4"))
        assertFalse(sanitizeExportFileName("bad|name").contains('|'))
    }

    @Test
    fun qualityLabels_useUserLanguage() {
        assertEquals("Smaller File", qualityLabel(ExportQuality.SMALL))
        assertEquals("Recommended", qualityLabel(ExportQuality.BALANCED))
        assertEquals("High Quality", qualityLabel(ExportQuality.HIGH))
        assertEquals("Maximum", qualityLabel(ExportQuality.MAXIMUM))
    }

    @Test
    fun resolutionSource_isMatchProject() {
        assertEquals("Match Project", resolutionLabel(ExportResolutionPreset.SOURCE))
    }

    @Test
    fun exportStatuses_doNotExposeEnumFormatting() {
        assertEquals("Preparing", exportStatusLabel(ExportJobStatus.QUEUED))
        assertEquals("Finalizing", exportStatusLabel(ExportJobStatus.FINALIZING))
        assertEquals("Cancelled", exportStatusLabel(ExportJobStatus.CANCELLED))
    }

    @Test
    fun knownFailures_haveHumanReadableRecovery() {
        val missing = exportFailurePresentation(ExportFailureCode.SOURCE_MISSING)
        assertEquals("Original file needed", missing.title)
        assertTrue(missing.suggestions.isNotEmpty())

        val storage = exportFailurePresentation(ExportFailureCode.STORAGE_FULL)
        assertEquals("Not enough storage", storage.title)
        assertTrue(storage.suggestions.any { it.contains("storage", ignoreCase = true) })
    }
}
