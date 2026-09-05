package com.videoflow.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.videoflow.app.ui.editor.PreviewContentGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewContentGeometryTest {

    @Test
    fun screenToProjectNormalized_usesProjectRectNotLetterbox() {
        val geometry = PreviewContentGeometry(
            viewportRect = Rect(0f, 0f, 1000f, 1000f),
            projectRect = Rect(0f, 218.75f, 1000f, 781.25f),
            projectWidth = 1920,
            projectHeight = 1080
        )

        val center = geometry.screenToProjectNormalized(Offset(500f, 500f))
        requireNotNull(center)
        assertEquals(0.5f, center.x, 0.0001f)
        assertEquals(0.5f, center.y, 0.0001f)
        assertNull(geometry.screenToProjectNormalized(Offset(500f, 100f)))
    }

    @Test
    fun projectToScreen_roundTripsNormalizedCoordinates() {
        val geometry = PreviewContentGeometry(
            viewportRect = Rect(0f, 0f, 800f, 600f),
            projectRect = Rect(100f, 75f, 700f, 525f),
            projectWidth = 1080,
            projectHeight = 1080
        )
        val source = Offset(0.2f, 0.8f)
        val screen = geometry.projectNormalizedToScreen(source)
        val roundTrip = geometry.screenToProjectNormalized(screen)
        requireNotNull(roundTrip)
        assertEquals(source.x, roundTrip.x, 0.0001f)
        assertEquals(source.y, roundTrip.y, 0.0001f)
    }
}
