package com.yellowtrack.platform.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.yellowtrack.platform.core.designsystem.theme.YTTheme

/**
 * A labelled single-line input with optional help and error text.
 *
 * Help text sits below the field rather than inside a tooltip, because several of the
 * figures this application asks for are ones people routinely get wrong, and an
 * explanation nobody hovers over is an explanation nobody reads.
 */
@Composable
fun YTTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    help: String? = null,
    errorMessage: String? = null,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    singleLine: Boolean = true,
    enabled: Boolean = true,
) {
    val isError = errorMessage != null

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            singleLine = singleLine,
            isError = isError,
            enabled = enabled,
            shape = YTTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        )

        val supporting = errorMessage ?: help

        if (supporting != null) {
            Text(
                text = supporting,
                style = YTTheme.typography.bodySmall,
                color = if (isError) YTTheme.colors.error else YTTheme.colors.onSurfaceVariant,
            )
        }
    }
}
