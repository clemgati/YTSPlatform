package com.yellowtrack.platform.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.yellowtrack.platform.core.designsystem.theme.YTTheme

/**
 * Single-line search input.
 *
 * The clear affordance appears only once there is something to clear, so the field stays
 * quiet when idle.
 */
@Composable
fun YTSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    contentDescription: String = placeholder,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { this.contentDescription = contentDescription },
        singleLine = true,
        shape = YTTheme.shapes.medium,
        placeholder = {
            Text(
                text = placeholder,
                style = YTTheme.typography.bodyLarge,
                color = YTTheme.colors.onSurfaceVariant,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = YTIcons.Search,
                contentDescription = null,
                tint = YTTheme.colors.onSurfaceVariant,
            )
        },
        trailingIcon =
            if (value.isEmpty()) {
                null
            } else {
                {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(
                            imageVector = YTIcons.Error,
                            contentDescription = "Clear search",
                            tint = YTTheme.colors.onSurfaceVariant,
                        )
                    }
                }
            },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}
