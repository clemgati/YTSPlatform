package com.yellowtrack.platform.feature.studio.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.yellowtrack.platform.core.designsystem.component.YTDropdownField
import com.yellowtrack.platform.core.designsystem.component.YTFormDialog
import com.yellowtrack.platform.core.designsystem.component.YTTextField
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.gear.LightRole
import com.yellowtrack.platform.feature.studio.presentation.mapper.label
import com.yellowtrack.platform.feature.studio.presentation.model.NewLightSetup
import com.yellowtrack.platform.feature.studio.presentation.model.NewLightingRecipe

/** One light's fields, as typed, so removing a light cannot leave stale text behind. */
internal data class LightFields(
    val role: LightRole = LightRole.Key,
    val instrument: String = "",
    val modifier: String = "",
    val power: String = "",
    val position: String = "",
    val distance: String = "",
) {
    fun asNew(): NewLightSetup =
        NewLightSetup(
            role = role,
            instrument = instrument.trim(),
            modifier = modifier.ifBlank { null },
            power = power.ifBlank { null },
            position = position.ifBlank { null },
            distance = distance.ifBlank { null },
        )
}

/**
 * Writes down a lighting set-up.
 *
 * Every field but the instrument is free text on purpose: a power reading is "1/4" on one
 * light and "6.3" on another, and a position is "camera left, just above eye line" — a
 * normalised number would have to be translated back before anyone could dial it in.
 *
 * A light with no instrument is dropped rather than saved empty, because a recipe listing
 * "3 lights" where one of them is blank is worse than a recipe listing two.
 */
@Composable
internal fun RecipeFormDialog(
    onSave: (NewLightingRecipe) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val lights = remember { mutableStateListOf(LightFields()) }

    val namedLights = lights.filter { it.instrument.isNotBlank() }

    YTFormDialog(
        title = "Save a lighting set-up",
        confirmLabel = "Save",
        confirmEnabled = name.isNotBlank() && namedLights.isNotEmpty(),
        onConfirm = {
            onSave(
                NewLightingRecipe(
                    name = name,
                    lights = namedLights.map { it.asNew() },
                    notes = notes.ifBlank { null },
                ),
            )
        },
        onDismiss = onDismiss,
    ) {
        YTTextField(
            value = name,
            onValueChange = { name = it },
            label = "What do you call it?",
            placeholder = "Clamshell headshot",
        )

        lights.forEachIndexed { index, light ->
            LightRow(
                light = light,
                canRemove = lights.size > 1,
                onChange = { lights[index] = it },
                onRemove = { lights.removeAt(index) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { lights.add(LightFields()) }) {
                Text(
                    text = "Add a light",
                    style = YTTheme.typography.labelLarge,
                    color = YTTheme.colors.primary,
                )
            }

            Text(
                text =
                    when (namedLights.size) {
                        0 -> "No light named yet"
                        1 -> "1 light"
                        else -> "${namedLights.size} lights"
                    },
                style = YTTheme.typography.bodySmall,
                color = YTTheme.colors.onSurfaceVariant,
            )
        }

        YTTextField(
            value = notes,
            onValueChange = { notes = it },
            label = "Notes",
            placeholder = "Drop the fill a stop for men",
            imeAction = ImeAction.Done,
        )
    }
}

@Composable
private fun LightRow(
    light: LightFields,
    canRemove: Boolean,
    onChange: (LightFields) -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall)) {
        HorizontalDivider(color = YTTheme.colors.outlineVariant)

        YTDropdownField(
            label = "Doing what?",
            selected = light.role,
            options = LightRole.entries,
            optionLabel = { it.label },
            onSelect = { onChange(light.copy(role = it)) },
        )

        YTTextField(
            value = light.instrument,
            onValueChange = { onChange(light.copy(instrument = it)) },
            label = "Light",
            placeholder = "Profoto B10",
        )

        YTTextField(
            value = light.modifier,
            onValueChange = { onChange(light.copy(modifier = it)) },
            label = "Through what?",
            placeholder = "3ft octabox",
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
            verticalAlignment = Alignment.Top,
        ) {
            YTTextField(
                value = light.power,
                onValueChange = { onChange(light.copy(power = it)) },
                label = "Power",
                modifier = Modifier.weight(1f),
                placeholder = "1/4",
            )

            YTTextField(
                value = light.distance,
                onValueChange = { onChange(light.copy(distance = it)) },
                label = "Distance",
                modifier = Modifier.weight(1f),
                placeholder = "1m",
            )
        }

        YTTextField(
            value = light.position,
            onValueChange = { onChange(light.copy(position = it)) },
            label = "Where?",
            placeholder = "Camera left, 45°, just above eye line",
        )

        if (canRemove) {
            TextButton(onClick = onRemove) {
                Text(
                    text = "Remove this light",
                    style = YTTheme.typography.labelLarge,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}
