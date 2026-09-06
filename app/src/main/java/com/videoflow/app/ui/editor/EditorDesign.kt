package com.videoflow.app.ui.editor

import androidx.compose.ui.graphics.Color

object VideoFlowEditorColors {
    val EditorBackground = Color(0xFF0D0F12)
    val EditorSurface = Color(0xFF15191E)
    val EditorSurfaceElevated = Color(0xFF1D232A)
    val EditorDivider = Color(0xFF303841)
    val TimelineBackground = Color(0xFF101419)
    val TimelineTrackHeader = Color(0xFF181E24)
    val TimelineVideoClip = Color(0xFF263746)
    val TimelineAudioClip = Color(0xFF263D36)
    val TimelineOverlayClip = Color(0xFF3A3044)
    val PlayheadAccent = Color(0xFF5AB0FF)
    val SelectionAccent = Color(0xFF84C7FF)
    val WarningColor = Color(0xFFF0B35A)
    val ErrorColor = Color(0xFFFF7B7B)
    val SuccessColor = Color(0xFF6FD39A)

    // Explicit editor foreground tokens. Custom dark editor surfaces must never depend on an
    // unrelated Material LocalContentColor inherited from outside the workspace.
    val PrimaryText = Color(0xFFF4F7FA)
    val SecondaryText = Color(0xFFC0CAD4)
    val DisabledText = Color(0xFF8D98A4)
    val TextOnAccent = Color(0xFF08131D)
    val InputText = Color(0xFFF4F7FA)
    val InputHint = Color(0xFFA9B4BF)
}
