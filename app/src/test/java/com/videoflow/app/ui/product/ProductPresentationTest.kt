package com.videoflow.app.ui.product

import com.videoflow.app.domain.export.ExportFailureCode
import com.videoflow.app.domain.export.ExportJobStatus
import com.videoflow.app.domain.export.ExportQuality
import com.videoflow.app.domain.export.ExportResolutionPreset
import com.videoflow.app.domain.export.HdrPolicy
import com.videoflow.app.domain.export.VideoCodec
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
        assertFalse(sanitizeExportFileName("bad\nname").contains('\n'))
    }

    @Test
    fun fileNameSanitization_hasSafeFallbackAndDoesNotDuplicateExtension() {
        assertEquals("VideoFlow Export.mp4", sanitizeExportFileName("   ...   "))
        assertEquals("movie.MP4", sanitizeExportFileName("movie.MP4"))
    }

    @Test
    fun projectAspectPresets_areExactProductContract() {
        assertEquals(1920 to 1080, ProjectAspectPreset.LANDSCAPE.width to ProjectAspectPreset.LANDSCAPE.height)
        assertEquals(1080 to 1920, ProjectAspectPreset.PORTRAIT.width to ProjectAspectPreset.PORTRAIT.height)
        assertEquals(1080 to 1080, ProjectAspectPreset.SQUARE.width to ProjectAspectPreset.SQUARE.height)
        assertEquals(1080 to 1350, ProjectAspectPreset.SOCIAL.width to ProjectAspectPreset.SOCIAL.height)
        assertEquals(listOf("16:9", "9:16", "1:1", "4:5"), ProjectAspectPreset.entries.map { it.label })
    }

    @Test
    fun appearanceOptions_matchPersistedProductChoices() {
        assertEquals(listOf(AppAppearance.SYSTEM, AppAppearance.LIGHT, AppAppearance.DARK), AppAppearance.entries)
    }

    @Test
    fun qualityLabels_useUserLanguage() {
        assertEquals("Smaller File", qualityLabel(ExportQuality.SMALL))
        assertEquals("Recommended", qualityLabel(ExportQuality.BALANCED))
        assertEquals("High Quality", qualityLabel(ExportQuality.HIGH))
        assertEquals("Maximum", qualityLabel(ExportQuality.MAXIMUM))
    }

    @Test
    fun resolutionLabels_coverEverySupportedPreset() {
        val expected = mapOf(
            ExportResolutionPreset.SOURCE to "Match Project",
            ExportResolutionPreset.P480 to "480p",
            ExportResolutionPreset.P720 to "720p",
            ExportResolutionPreset.P1080 to "1080p",
            ExportResolutionPreset.P1440 to "1440p / QHD",
            ExportResolutionPreset.DCI_2K to "2K DCI",
            ExportResolutionPreset.UHD_4K to "4K UHD",
            ExportResolutionPreset.DCI_4K to "4K DCI",
            ExportResolutionPreset.CUSTOM to "Custom"
        )
        assertEquals(ExportResolutionPreset.entries.toSet(), expected.keys)
        expected.forEach { (preset, label) -> assertEquals(label, resolutionLabel(preset)) }
    }

    @Test
    fun codecLabelsAndSupportingText_areExplicit() {
        assertEquals("H.264 / AVC", codecLabel(VideoCodec.H264))
        assertEquals("Best compatibility", codecSupporting(VideoCodec.H264))
        assertEquals("HEVC / H.265", codecLabel(VideoCodec.HEVC))
        assertTrue(codecSupporting(VideoCodec.HEVC).contains("supported devices"))
    }

    @Test
    fun hdrLabels_neverHideConversionIntent() {
        assertTrue(hdrPolicyLabel(HdrPolicy.PRESERVE_WHEN_COMPATIBLE).contains("Preserve"))
        assertTrue(hdrPolicyLabel(HdrPolicy.REQUIRE_PRESERVE).contains("Require"))
        assertEquals("Convert HDR to SDR", hdrPolicyLabel(HdrPolicy.CONVERT_TO_SDR))
    }

    @Test
    fun exportStatuses_coverEveryDomainStateWithUserText() {
        ExportJobStatus.entries.forEach { status ->
            val label = exportStatusLabel(status)
            assertTrue(label.isNotBlank())
            assertFalse(label.contains('_'))
        }
        assertEquals("Preparing", exportStatusLabel(ExportJobStatus.QUEUED))
        assertEquals("Finalizing", exportStatusLabel(ExportJobStatus.FINALIZING))
        assertEquals("Cancelled", exportStatusLabel(ExportJobStatus.CANCELLED))
    }

    @Test
    fun everyKnownFailureHasReadableTitleAndMessage() {
        ExportFailureCode.entries.forEach { code ->
            val presentation = exportFailurePresentation(code)
            assertTrue("Missing title for $code", presentation.title.isNotBlank())
            assertTrue("Missing message for $code", presentation.message.isNotBlank())
            assertFalse(presentation.title.contains('_'))
        }
    }

    @Test
    fun sourceRecoveryFailuresGiveActionableSuggestions() {
        listOf(
            ExportFailureCode.SOURCE_MISSING,
            ExportFailureCode.SOURCE_CHANGED,
            ExportFailureCode.PERMISSION_LOST
        ).forEach { code ->
            assertTrue(exportFailurePresentation(code).suggestions.isNotEmpty())
        }
    }

    @Test
    fun capabilityFailuresRecommendSaferAlternatives() {
        val codec = exportFailurePresentation(ExportFailureCode.UNSUPPORTED_CODEC)
        assertTrue(codec.suggestions.any { it.contains("H.264") })

        val resolution = exportFailurePresentation(ExportFailureCode.UNSUPPORTED_RESOLUTION)
        assertTrue(resolution.suggestions.any { it.contains("1080p") })

        val frameRate = exportFailurePresentation(ExportFailureCode.UNSUPPORTED_FRAME_RATE)
        assertTrue(frameRate.suggestions.any { it.contains("30 fps") })
    }

    @Test
    fun storageAndDestinationFailuresExposeRecovery() {
        val storage = exportFailurePresentation(ExportFailureCode.STORAGE_FULL)
        assertEquals("Not enough storage", storage.title)
        assertTrue(storage.suggestions.any { it.contains("storage", ignoreCase = true) })

        val destination = exportFailurePresentation(ExportFailureCode.DESTINATION_IO)
        assertTrue(destination.suggestions.any { it.contains("destination", ignoreCase = true) })
    }

    @Test
    fun unknownFailureUsesProvidedFallbackWithoutLeakingEnumFormatting() {
        val fallback = "Encoder returned an unexpected result."
        val presentation = exportFailurePresentation(ExportFailureCode.UNKNOWN, fallback)
        assertEquals("Export couldn't finish", presentation.title)
        assertEquals(fallback, presentation.message)
        assertTrue(presentation.suggestions.isNotEmpty())
    }
}
