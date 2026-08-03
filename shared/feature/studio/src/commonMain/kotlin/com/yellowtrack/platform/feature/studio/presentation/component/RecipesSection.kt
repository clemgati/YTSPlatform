package com.yellowtrack.platform.feature.studio.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTBadge
import com.yellowtrack.platform.core.designsystem.component.YTSectionCard
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.gear.LightingRecipeId
import com.yellowtrack.platform.feature.studio.presentation.model.LightingRecipeItem

/**
 * Set-ups worth rebuilding.
 *
 * The same three-light headshot gets reconstructed from memory a hundred times and comes
 * out slightly different each time. Written down, it is a starting point that takes ten
 * minutes instead of forty.
 */
@Composable
internal fun RecipesSection(
    recipes: List<LightingRecipeItem>,
    onAddRecipe: () -> Unit,
    onDeleteRecipe: (LightingRecipeId) -> Unit,
    onEditRecipe: (LightingRecipeItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    YTSectionCard(
        title = "Lighting set-ups",
        modifier = modifier,
        actions = {
            TextButton(onClick = onAddRecipe) {
                Text(
                    text = "Save a set-up",
                    style = YTTheme.typography.labelLarge,
                    color = YTTheme.colors.primary,
                )
            }
        },
    ) {
        if (recipes.isEmpty()) {
            Text(
                text =
                    "Nothing saved yet. The set-up worth writing down is the one that worked " +
                        "last week and will be rebuilt from memory next week.",
                style = YTTheme.typography.bodyMedium,
                color = YTTheme.colors.onSurfaceVariant,
            )
            return@YTSectionCard
        }

        recipes.forEach { recipe ->
            RecipeBlock(recipe, onDeleteRecipe, onEditRecipe)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeBlock(
    recipe: LightingRecipeItem,
    onDelete: (LightingRecipeId) -> Unit,
    onEdit: (LightingRecipeItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = recipe.name,
                modifier = Modifier.weight(1f),
                style = YTTheme.typography.titleSmall,
                color = YTTheme.colors.onSurface,
            )

            YTBadge(text = recipe.lightCountLabel)
        }

        recipe.lights.forEach { light ->
            Text(
                text =
                    listOfNotNull("${light.roleLabel} — ${light.instrumentLabel}", light.settingsLabel)
                        .joinToString(" · "),
                style = YTTheme.typography.bodyMedium,
                color = YTTheme.colors.onSurfaceVariant,
            )
        }

        recipe.notes?.let { note ->
            Text(
                text = note,
                style = YTTheme.typography.bodySmall,
                color = YTTheme.colors.onSurfaceVariant,
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
        ) {
            TextButton(onClick = { onEdit(recipe) }) {
                Text(
                    text = "Edit",
                    style = YTTheme.typography.labelMedium,
                    color = YTTheme.colors.primary,
                )
            }

            TextButton(onClick = { onDelete(recipe.id) }) {
                Text(
                    text = "Remove",
                    style = YTTheme.typography.labelMedium,
                    color = YTTheme.colors.error,
                )
            }
        }
    }
}
