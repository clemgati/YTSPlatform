package com.yellowtrack.platform.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.ui.state.UiState

@Composable
fun <T> StatefulContent(
    state: UiState<T>,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    emptyContent: @Composable (Modifier) -> Unit = {
        EmptyContent(modifier = it)
    },
    content: @Composable (T, Modifier) -> Unit,
) {
    when (state) {
        UiState.Loading ->
            LoadingContent(modifier = modifier)

        UiState.Empty ->
            emptyContent(modifier)

        is UiState.Error ->
            ErrorContent(
                message = state.message,
                onRetry = onRetry,
                modifier = modifier,
            )

        is UiState.Success ->
            content(
                state.data,
                modifier,
            )
    }
}
