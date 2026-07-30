package com.yellowtrack.platform.feature.sessions.presentation.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTBadge
import com.yellowtrack.platform.core.designsystem.component.YTButton
import com.yellowtrack.platform.core.designsystem.component.YTDetailSection
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.crew.CrewMemberId
import com.yellowtrack.platform.core.model.gear.GearItemId
import com.yellowtrack.platform.core.model.gear.PackingEntryId
import com.yellowtrack.platform.core.model.media.MediaCopyId
import com.yellowtrack.platform.core.model.release.ReleaseStatus
import com.yellowtrack.platform.core.model.release.TalentReleaseId
import com.yellowtrack.platform.core.model.shot.ShotId
import com.yellowtrack.platform.core.ui.component.EmptyContent
import com.yellowtrack.platform.core.ui.component.StatefulContent
import com.yellowtrack.platform.feature.sessions.presentation.component.SessionFormDialog
import com.yellowtrack.platform.feature.sessions.presentation.details.component.BackupSection
import com.yellowtrack.platform.feature.sessions.presentation.details.component.CallSheetSection
import com.yellowtrack.platform.feature.sessions.presentation.details.component.CrewFormDialog
import com.yellowtrack.platform.feature.sessions.presentation.details.component.CrewSection
import com.yellowtrack.platform.feature.sessions.presentation.details.component.MediaCopyFormDialog
import com.yellowtrack.platform.feature.sessions.presentation.details.component.PackingSection
import com.yellowtrack.platform.feature.sessions.presentation.details.component.ReleaseFormDialog
import com.yellowtrack.platform.feature.sessions.presentation.details.component.ReleaseSection
import com.yellowtrack.platform.feature.sessions.presentation.details.component.ShotFormDialog
import com.yellowtrack.platform.feature.sessions.presentation.details.component.ShotListSection
import com.yellowtrack.platform.feature.sessions.presentation.details.model.SessionDetailsModel
import com.yellowtrack.platform.feature.sessions.presentation.details.model.SessionLight
import com.yellowtrack.platform.feature.sessions.presentation.model.NewCrewMember
import com.yellowtrack.platform.feature.sessions.presentation.model.NewMediaCopy
import com.yellowtrack.platform.feature.sessions.presentation.model.NewRelease
import com.yellowtrack.platform.feature.sessions.presentation.model.NewSession
import com.yellowtrack.platform.feature.sessions.presentation.model.NewShot
import kotlinx.datetime.TimeZone

@Composable
internal fun SessionDetailsScreen(
    uiState: SessionDetailsUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onUpdateSession: (NewSession) -> Unit,
    onMoveSession: (NewSession) -> Unit,
    onAddShot: (NewShot) -> Unit,
    onAddCrew: (NewCrewMember) -> Unit,
    onRemoveCrew: (CrewMemberId) -> Unit,
    onAddRelease: (NewRelease) -> Unit,
    onSetReleaseStatus: (TalentReleaseId, ReleaseStatus) -> Unit,
    onRemoveRelease: (TalentReleaseId) -> Unit,
    onAddMediaCopy: (NewMediaCopy) -> Unit,
    onVerifyMediaCopy: (MediaCopyId) -> Unit,
    onCheckMediaCopy: (MediaCopyId) -> Unit,
    onRemoveMediaCopy: (MediaCopyId) -> Unit,
    onToggleShot: (ShotId, Boolean) -> Unit,
    onDeleteShot: (ShotId) -> Unit,
    onAddPackingGear: (GearItemId) -> Unit,
    onSetPacked: (PackingEntryId, Boolean) -> Unit,
    onSetReturned: (PackingEntryId, Boolean) -> Unit,
    onRemovePacking: (PackingEntryId) -> Unit,
    onCopyCallSheet: () -> Unit,
    onSaveCallSheet: () -> Unit,
    callSheetMessage: String?,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    var addingShot by remember { mutableStateOf(false) }
    var addingCrew by remember { mutableStateOf(false) }
    var addingRelease by remember { mutableStateOf(false) }
    var addingCopy by remember { mutableStateOf(false) }

    StatefulContent(
        state = uiState.session,
        modifier = modifier.fillMaxSize(),
        onRetry = onRetry,
        emptyContent = { emptyModifier ->
            EmptyContent(
                modifier = emptyModifier,
                title = "Session not found",
                message = "This shoot day could not be loaded.",
            )
        },
    ) { session, contentModifier ->
        if (editing && uiState.today != null) {
            SessionFormDialog(
                bookings = uiState.bookings,
                today = uiState.today,
                zone = TimeZone.of(session.zoneId),
                initial = session.editable,
                onSave = { edited, moved ->
                    if (moved) onMoveSession(edited) else onUpdateSession(edited)
                    editing = false
                },
                onDismiss = { editing = false },
            )
        }

        if (addingShot) {
            ShotFormDialog(
                knownGroups = session.shotGroups.map { it.name },
                onSave = {
                    onAddShot(it)
                    addingShot = false
                },
                onDismiss = { addingShot = false },
            )
        }

        if (addingCrew) {
            CrewFormDialog(
                sessionCallTime = session.callTimeLabel,
                onSave = {
                    onAddCrew(it)
                    addingCrew = false
                },
                onDismiss = { addingCrew = false },
            )
        }

        if (addingRelease) {
            ReleaseFormDialog(
                onSave = {
                    onAddRelease(it)
                    addingRelease = false
                },
                onDismiss = { addingRelease = false },
            )
        }

        if (addingCopy) {
            MediaCopyFormDialog(
                volumes = uiState.volumes,
                canReadDrives = uiState.canReadDrives,
                onSave = {
                    onAddMediaCopy(it)
                    addingCopy = false
                },
                onDismiss = { addingCopy = false },
            )
        }

        Column(
            modifier =
                contentModifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(YTTheme.spacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraLarge),
        ) {
            YTButton(text = "Back to Sessions", onClick = onBack)

            SessionHeader(session)

            YTDetailSection(title = "When") {
                Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall)) {
                    DetailLine("Day", session.dayLabel)
                    DetailLine("Time", "${session.timeRange} • ${session.durationLabel} on site")
                    session.callTimeLabel?.let { DetailLine("Crew called", it) }
                    session.timeZoneNote?.let { DetailLine("Local time in", it) }
                }
            }

            YTDetailSection(title = "Where") {
                Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall)) {
                    if (session.locationName == null && session.locationAddress == null) {
                        Text(
                            text = "No location recorded.",
                            style = YTTheme.typography.bodyMedium,
                            color = YTTheme.colors.onSurfaceVariant,
                        )
                    }

                    session.locationName?.let { DetailLine("Place", it) }
                    session.locationAddress?.let { DetailLine("Address", it) }
                    session.coordinatesLabel?.let { DetailLine("Coordinates", it) }
                }
            }

            LightPanel(session.light)

            CallSheetSection(
                message = callSheetMessage,
                canSend = uiState.canSendDocuments,
                onCopy = onCopyCallSheet,
                onSave = onSaveCallSheet,
            )

            CrewSection(
                crew = session.crew,
                sessionCallTime = session.callTimeLabel,
                onAddCrew = { addingCrew = true },
                onRemoveCrew = onRemoveCrew,
            )

            BackupSection(
                summary = session.backup,
                onAddCopy = { addingCopy = true },
                onVerifyCopy = onVerifyMediaCopy,
                onCheckCopy = onCheckMediaCopy,
                checkResult = uiState.checkResult,
                onRemoveCopy = onRemoveMediaCopy,
            )

            PackingSection(
                packing = session.packing,
                onAddGear = onAddPackingGear,
                onSetPacked = onSetPacked,
                onSetReturned = onSetReturned,
                onRemove = onRemovePacking,
            )

            ReleaseSection(
                summary = session.releases,
                onSetStatus = onSetReleaseStatus,
                onAddRelease = { addingRelease = true },
                onRemoveRelease = onRemoveRelease,
            )

            ShotListSection(
                groups = session.shotGroups,
                remaining = session.shotsRemaining,
                onToggleShot = onToggleShot,
                onDeleteShot = onDeleteShot,
                onAddShot = { addingShot = true },
            )

            if (session.notes.isNotEmpty()) {
                YTDetailSection(title = "Notes") {
                    Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall)) {
                        session.notes.forEach { note ->
                            Text(
                                text = note,
                                style = YTTheme.typography.bodyMedium,
                                color = YTTheme.colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            TextButton(onClick = { editing = true }) {
                Text(
                    text = "Edit this day",
                    style = YTTheme.typography.labelLarge,
                    color = YTTheme.colors.primary,
                )
            }
        }
    }
}

/**
 * The day's light, which is the reason this screen is worth opening on a recce.
 *
 * Absent without a coordinate, and it says so rather than showing nothing: a studio that
 * does not know the figure is available will never go looking for it.
 */
@Composable
private fun LightPanel(light: SessionLight?) {
    YTDetailSection(title = "The light") {
        Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small)) {
            if (light == null) {
                Text(
                    text =
                        "Add a latitude and longitude to this day and the sunrise, sunset, " +
                            "and golden hours are worked out for it — no signal needed.",
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )

                return@YTDetailSection
            }

            light.note?.let { note ->
                Text(
                    text = note,
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.error,
                )
            }

            light.rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.label,
                        style = YTTheme.typography.bodyMedium,
                        color =
                            if (row.isEmphasised) YTTheme.colors.primary else YTTheme.colors.onSurfaceVariant,
                    )
                    Text(
                        text = row.value,
                        style = if (row.isEmphasised) YTTheme.typography.titleSmall else YTTheme.typography.bodyMedium,
                        color = if (row.isEmphasised) YTTheme.colors.primary else YTTheme.colors.onSurface,
                    )
                }
            }

            light.sunAtStart?.let { atStart ->
                HorizontalDivider(color = YTTheme.colors.outlineVariant)

                Text(
                    text = atStart,
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SessionHeader(session: SessionDetailsModel) {
    Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small)) {
        YTBadge(text = session.status.name)

        Text(
            text = session.title,
            style = YTTheme.typography.headlineLarge,
            color = YTTheme.colors.onBackground,
        )

        Text(
            text =
                listOfNotNull(
                    session.kind.name,
                    session.projectName.ifBlank { null },
                    session.clientName.ifBlank { null },
                ).joinToString(" • "),
            style = YTTheme.typography.bodyLarge,
            color = YTTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = YTTheme.typography.bodyMedium,
            color = YTTheme.colors.onSurfaceVariant,
        )
        Text(
            text = value,
            style = YTTheme.typography.bodyMedium,
            color = YTTheme.colors.onSurface,
        )
    }
}
