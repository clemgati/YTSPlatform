package com.yellowtrack.platform.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTLoadingIndicator

@Composable
fun LoadingContent(modifier: Modifier = Modifier) {
    YTLoadingIndicator(
        modifier = modifier,
    )
}
