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
import com.yellowtrack.platform.core.model.media.MediaCopyId
import com.yellowtrack.platform.feature.sessions.presentation.details.model.BackupSummary
import com.yellowtrack.platform.feature.sessions.presentation.details.model.MediaCopyItem

/**
 * Where this shoot's files are, and whether that is enough.
 *
 * Three copies, on two kinds of storage, one away from the building. The shortfalls are
 * the point: telling a studio it is unsafe is easy, and telling it which single thing to
 * do next is what gets the files copied.
 */
@Composable
internal fun BackupSection(
    summary: BackupSummary,
    onAddCopy: () -> Unit,
    onVerifyCopy: (MediaCopyId) -> Unit,
    onRemoveCopy: (MediaCopyId) -> Unit,
    modifier: Modifier = Modifier,
) {
    YTDetailSection(
        title = "Files",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small)) {
            Text(
                text = summary.verdict,
                style = YTTheme.typography.titleSmall,
                color = if (summary.isSatisfied) YTTheme.colors.primary else YTTheme.colors.error,
            )

            summary.shortfalls.forEach { shortfall ->
                Text(
                    text = shortfall,
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.error,
                )
            }

            if (summary.isSatisfied && summary.unverified > 0) {
                Text(
                    text =
                        "${summary.unverified} not opened since being copied — " +
                            "a drive can fail without saying so.",
                    style = YTTheme.typography.bodySmall,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            }

            summary.copies.forEach { copy ->
                CopyRow(copy, onVerifyCopy, onRemoveCopy)
            }

            TextButton(onClick = onAddCopy) {
                Text(
                    text = "Record a copy",
                    style = YTTheme.typography.labelLarge,
                    color = YTTheme.colors.primary,
                )
            }
        }
    }
}

@Composable
private fun CopyRow(
    copy: MediaCopyItem,
    onVerify: (MediaCopyId) -> Unit,
    onRemove: (MediaCopyId) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = copy.volumeName,
                style = YTTheme.typography.bodyLarge,
                color = YTTheme.colors.onSurface,
            )
            Text(
                text =
                    listOfNotNull(
                        copy.kind,
                        "away from the studio".takeIf { copy.isOffsite },
                        // First, because it is why this row no longer counts.
                        "on a drive that has failed".takeIf { copy.isUnreachable },
                        "not checked".takeUnless { copy.isVerified || copy.isUnreachable },
                    ).joinToString(" • "),
                style = YTTheme.typography.bodySmall,
                color =
                    if (copy.isVerified && !copy.isUnreachable) {
                        YTTheme.colors.onSurfaceVariant
                    } else {
                        YTTheme.colors.error
                    },
            )
        }

        Row {
            TextButton(onClick = { onRemove(copy.id) }) {
                Text(
                    text = "Remove",
                    style = YTTheme.typography.labelMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            }

            // Nothing to verify on a drive that has failed; offering it would invite a
            // studio to tick a box that cannot be true.
            if (!copy.isVerified && !copy.isUnreachable) {
                TextButton(onClick = { onVerify(copy.id) }) {
                    Text(
                        text = "Checked",
                        style = YTTheme.typography.labelLarge,
                        color = YTTheme.colors.primary,
                    )
                }
            }
        }
    }
}
