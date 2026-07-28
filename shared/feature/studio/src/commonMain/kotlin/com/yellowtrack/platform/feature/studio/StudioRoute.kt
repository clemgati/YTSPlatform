package com.yellowtrack.platform.feature.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.ui.component.EmptyContent

/**
 * Placeholder until the Studio milestone.
 *
 * Gear inventory, packing lists, lighting recipes, and maintenance tracking are modelled
 * in `docs/DOMAIN_MODEL.md` but not yet implemented. The screen states that plainly rather
 * than displaying invented readiness checkboxes.
 */
@Composable
fun StudioRoute(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(YTTheme.spacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.large),
    ) {
        EmptyContent(
            title = "Studio",
            message =
                "Gear inventory, packing lists, lighting recipes, and maintenance tracking " +
                    "arrive in a later milestone.",
        )
    }
}
