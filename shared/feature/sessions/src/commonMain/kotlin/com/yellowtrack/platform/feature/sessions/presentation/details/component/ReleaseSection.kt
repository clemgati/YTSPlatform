package com.yellowtrack.platform.feature.sessions.presentation.details.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTDetailSection
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.release.ReleaseStatus
import com.yellowtrack.platform.core.model.release.TalentReleaseId
import com.yellowtrack.platform.feature.sessions.presentation.details.model.ReleaseItem
import com.yellowtrack.platform.feature.sessions.presentation.details.model.ReleaseSummary

/**
 * Permission from the people in the photographs.
 *
 * The outstanding count leads because it is the number of photographs that cannot lawfully
 * be delivered — a studio can have signed a contract granting worldwide rights and still
 * have no way to honour it. The licence is the promise; this is whether it can be kept.
 */
@Composable
internal fun ReleaseSection(
    summary: ReleaseSummary,
    onSetStatus: (TalentReleaseId, ReleaseStatus) -> Unit,
    onAddRelease: () -> Unit,
    onRemoveRelease: (TalentReleaseId) -> Unit,
    modifier: Modifier = Modifier,
) {
    YTDetailSection(
        title = "Releases",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small)) {
            if (summary.releases.isEmpty()) {
                Text(
                    text =
                        "Nobody recorded yet. Commercial work cannot be licensed to a client " +
                            "without permission from the people in the frame.",
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            } else {
                Text(
                    text = summary.headline,
                    style = YTTheme.typography.bodyMedium,
                    color = if (summary.hasProblem) YTTheme.colors.error else YTTheme.colors.primary,
                )

                summary.releases.forEach { release ->
                    ReleaseRow(release, onSetStatus, onRemoveRelease)
                }
            }

            TextButton(onClick = onAddRelease) {
                Text(
                    text = "Add someone",
                    style = YTTheme.typography.labelLarge,
                    color = YTTheme.colors.primary,
                )
            }
        }
    }
}

private val ReleaseSummary.headline: String
    get() =
        when {
            refused > 0 && outstanding > 0 ->
                "$outstanding still to sign, and $refused ${refused.personWord} who said no"

            refused > 0 -> "$refused ${refused.personWord} said no — those photographs cannot be used"
            outstanding > 0 -> "$outstanding still to sign"
            else -> "Everyone has signed"
        }

private val Int.personWord: String get() = if (this == 1) "person" else "people"

@Composable
private fun ReleaseRow(
    release: ReleaseItem,
    onSetStatus: (TalentReleaseId, ReleaseStatus) -> Unit,
    onRemove: (TalentReleaseId) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = release.personName,
                style = YTTheme.typography.bodyLarge,
                color = YTTheme.colors.onSurface,
            )

            Text(
                text = release.statusLabel,
                style = YTTheme.typography.titleSmall,
                color = if (release.isSigned) YTTheme.colors.primary else YTTheme.colors.onSurfaceVariant,
            )
        }

        // A release marked signed that would not stand up says so, rather than sitting in
        // the list looking like permission the studio does not actually hold.
        release.problem?.let { problem ->
            Text(
                text = problem,
                style = YTTheme.typography.bodySmall,
                color = YTTheme.colors.error,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = release.kind,
                modifier = Modifier.weight(1f),
                style = YTTheme.typography.bodyMedium,
                color = YTTheme.colors.onSurfaceVariant,
            )

            Row {
                TextButton(onClick = { onRemove(release.id) }) {
                    Text(
                        text = "Remove",
                        style = YTTheme.typography.labelMedium,
                        color = YTTheme.colors.onSurfaceVariant,
                    )
                }

                TextButton(onClick = { onSetStatus(release.id, ReleaseStatus.Refused) }) {
                    Text(
                        text = "Refused",
                        style = YTTheme.typography.labelMedium,
                        color = YTTheme.colors.onSurfaceVariant,
                    )
                }

                TextButton(onClick = { onSetStatus(release.id, ReleaseStatus.Signed) }) {
                    Text(
                        text = "Signed",
                        style = YTTheme.typography.labelLarge,
                        color = YTTheme.colors.primary,
                    )
                }
            }
        }
    }
}
