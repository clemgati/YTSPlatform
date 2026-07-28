package com.yellowtrack.platform.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal val YellowTrackYellow = Color(0xFFFAB91D)
internal val YellowTrackBlack = Color(0xFF111111)
internal val YellowTrackNearBlack = Color(0xFF181818)
internal val YellowTrackDarkGray = Color(0xFF252525)
internal val YellowTrackLightGray = Color(0xFFF5F5F5)
internal val YellowTrackWhite = Color(0xFFFFFFFF)

internal val YellowTrackDarkColorScheme =
    darkColorScheme(
        primary = YellowTrackYellow,
        onPrimary = YellowTrackBlack,
        primaryContainer = Color(0xFF5C4300),
        onPrimaryContainer = Color(0xFFFFDF96),
        secondary = YellowTrackWhite,
        onSecondary = YellowTrackBlack,
        background = YellowTrackNearBlack,
        onBackground = YellowTrackWhite,
        surface = YellowTrackDarkGray,
        onSurface = YellowTrackWhite,
        surfaceVariant = Color(0xFF343434),
        onSurfaceVariant = Color(0xFFD6D6D6),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
    )

internal val YellowTrackLightColorScheme =
    lightColorScheme(
        primary = Color(0xFF795900),
        onPrimary = YellowTrackWhite,
        primaryContainer = YellowTrackYellow,
        onPrimaryContainer = YellowTrackBlack,
        secondary = YellowTrackBlack,
        onSecondary = YellowTrackWhite,
        background = YellowTrackLightGray,
        onBackground = YellowTrackBlack,
        surface = YellowTrackWhite,
        onSurface = YellowTrackBlack,
        surfaceVariant = Color(0xFFEAEAEA),
        onSurfaceVariant = Color(0xFF484848),
        error = Color(0xFFBA1A1A),
        onError = YellowTrackWhite,
    )
