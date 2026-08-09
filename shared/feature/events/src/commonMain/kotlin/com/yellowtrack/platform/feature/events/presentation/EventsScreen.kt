package com.yellowtrack.platform.feature.events.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.yellowtrack.platform.core.designsystem.component.YTBadge
import com.yellowtrack.platform.core.designsystem.component.YTButton
import com.yellowtrack.platform.core.designsystem.component.YTCard
import com.yellowtrack.platform.core.designsystem.component.YTSectionCard
import com.yellowtrack.platform.core.designsystem.component.YTStatus
import com.yellowtrack.platform.core.designsystem.component.YTStatusIndicator
import com.yellowtrack.platform.core.designsystem.component.YTTextButton
import com.yellowtrack.platform.core.designsystem.component.YTTextField
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.ui.component.EmptyContent
import com.yellowtrack.platform.core.ui.component.StatefulContent

/**
 * Running an event.
 *
 * Laid out for where it is used rather than for where it was written: on a laptop on a
 * folding table, glanced at between frames, by somebody who cannot stop to read. So the two
 * things that go wrong in a room are the two loudest things on it — a station still open on a
 * camera nobody is using, and a refusal that says which camera is already claimed.
 */
@Composable
internal fun EventsScreen(
    uiState: EventsUiState,
    onRetry: () -> Unit,
    onOpenEvent: (String) -> Unit,
    onCloseEvent: () -> Unit,
    onCreateEvent: (String) -> Unit,
    onOpenStation: (eventId: String, name: String, sourceKey: String) -> Unit,
    onCloseStation: (eventId: String, stationId: String) -> Unit,
    onDismissProblem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatefulContent(
        state = uiState.content,
        modifier = modifier.fillMaxSize(),
        onRetry = onRetry,
        emptyContent = {
            EmptyContent(
                modifier = it,
                title = "No events yet",
                message = "An event is a day you hand photographs to the people in them.",
            )
        },
    ) { content, contentModifier ->
        Column(
            modifier =
                contentModifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(YTTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.medium),
        ) {
            // Over the screen rather than instead of it: the remedy for "already open on
            // Camera A" is the station list underneath this.
            uiState.problem?.let { problem ->
                YTCard {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
                    ) {
                        Problem(problem)
                        YTTextButton(text = "Dismiss", onClick = onDismissProblem)
                    }
                }
            }

            when (val open = content.open) {
                null -> EventList(content, uiState.isBusy, onOpenEvent, onCreateEvent)
                else ->
                    OpenEventDetail(
                        event = open,
                        isBusy = uiState.isBusy,
                        onBack = onCloseEvent,
                        onOpenStation = onOpenStation,
                        onCloseStation = onCloseStation,
                    )
            }
        }
    }
}

@Composable
private fun EventList(
    content: EventsContent,
    isBusy: Boolean,
    onOpenEvent: (String) -> Unit,
    onCreateEvent: (String) -> Unit,
) {
    var newName by remember { mutableStateOf("") }

    YTSectionCard(title = "New event") {
        Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small)) {
            YTTextField(
                value = newName,
                onValueChange = { newName = it },
                label = "Name",
                placeholder = "Harbour Awards 2026",
                imeAction = ImeAction.Done,
            )
            YTButton(
                text = "Create event",
                enabled = !isBusy && newName.isNotBlank(),
                onClick = {
                    onCreateEvent(newName)
                    newName = ""
                },
            )
        }
    }

    content.events.forEach { event ->
        YTCard(modifier = Modifier.fillMaxWidth().clickable { onOpenEvent(event.id) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall)) {
                    Text(event.name, style = YTTheme.typography.titleMedium)
                    Text(
                        "${event.photographs} photographs",
                        style = YTTheme.typography.bodySmall,
                        color = YTTheme.colors.onSurfaceVariant,
                    )
                }

                // The one thing worth seeing without opening an event. A station left open
                // after everybody has gone home keeps claiming photographs for whoever was
                // last in front of the camera.
                if (event.openStations > 0) {
                    YTBadge(text = "${event.openStations} open")
                }
            }
        }
    }
}

@Composable
private fun OpenEventDetail(
    event: OpenEvent,
    isBusy: Boolean,
    onBack: () -> Unit,
    onOpenStation: (eventId: String, name: String, sourceKey: String) -> Unit,
    onCloseStation: (eventId: String, stationId: String) -> Unit,
) {
    var stationName by remember(event.id) { mutableStateOf("") }
    var sourceKey by remember(event.id) { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(event.name, style = YTTheme.typography.headlineSmall)
        YTTextButton(text = "All events", onClick = onBack)
    }

    val alreadyClaimed = sourceKey.trim() in event.claimedSources

    YTSectionCard(title = "Open a station") {
        Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small)) {
            YTTextField(
                value = stationName,
                onValueChange = { stationName = it },
                label = "Station",
                placeholder = "Bay 1",
            )
            YTTextField(
                value = sourceKey,
                onValueChange = { sourceKey = it },
                label = "Watched folder",
                placeholder = "Camera A",
                help =
                    "The folder name tethered capture writes into. It is what binds a " +
                        "photograph to this station.",
                errorMessage = "A station is already open on ${sourceKey.trim()}.".takeIf { alreadyClaimed },
                imeAction = ImeAction.Done,
            )

            YTButton(
                text = "Open station",
                enabled = !isBusy && stationName.isNotBlank() && sourceKey.isNotBlank() && !alreadyClaimed,
                onClick = {
                    onOpenStation(event.id, stationName, sourceKey)
                    stationName = ""
                    sourceKey = ""
                },
            )
        }
    }

    if (event.stations.isEmpty()) {
        Text(
            "No stations yet. Photographs still arrive — they go to the event's gallery.",
            style = YTTheme.typography.bodyMedium,
            color = YTTheme.colors.onSurfaceVariant,
        )
    }

    event.stations.forEach { station ->
        YTCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall)) {
                    Text(station.name, style = YTTheme.typography.titleMedium)
                    Text(
                        station.sourceKey,
                        style = YTTheme.typography.bodySmall,
                        color = YTTheme.colors.onSurfaceVariant,
                    )
                }

                if (station.isOpen) {
                    YTButton(
                        text = "Close",
                        enabled = !isBusy,
                        onClick = { onCloseStation(event.id, station.id) },
                    )
                } else {
                    YTBadge(text = "Closed")
                }
            }
        }
    }
}

/**
 * A refusal, said where it can be acted on.
 *
 * An icon as well as red text, because red alone is the one thing a colour-blind
 * photographer glancing at a laptop in daylight will not see.
 */
@Composable
private fun Problem(message: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YTStatusIndicator(status = YTStatus.Error, contentDescription = null)
        Text(message, style = YTTheme.typography.bodyMedium, color = YTTheme.colors.error)
    }
}
