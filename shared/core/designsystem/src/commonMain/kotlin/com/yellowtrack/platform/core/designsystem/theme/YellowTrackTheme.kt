package com.yellowtrack.platform.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalYTSpacing =
    staticCompositionLocalOf {
        DefaultYTSpacing
    }

@Composable
fun YellowTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme =
        if (darkTheme) {
            YellowTrackDarkColorScheme
        } else {
            YellowTrackLightColorScheme
        }

    CompositionLocalProvider(
        LocalYTSpacing provides DefaultYTSpacing,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = YellowTrackTypography,
            shapes = YellowTrackShapes,
            content = content,
        )
    }
}

object YTTheme {
    val colors
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme

    val typography
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography

    val shapes
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.shapes

    val spacing: YTSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalYTSpacing.current
}
