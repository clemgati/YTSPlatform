package com.yellowtrack.platform.core.designsystem.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.theme.YTTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YTTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                style = YTTheme.typography.titleLarge,
            )
        },
        modifier = modifier,
        navigationIcon = {
            navigationIcon?.invoke()
        },
        actions = actions,
        colors =
            TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = YTTheme.colors.surface,
                titleContentColor = YTTheme.colors.onSurface,
                navigationIconContentColor = YTTheme.colors.onSurface,
                actionIconContentColor = YTTheme.colors.onSurface,
            ),
    )
}
