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
import com.yellowtrack.platform.core.data.event.IngestStatus
import com.yellowtrack.platform.core.designsystem.component.YTBadge
import com.yellowtrack.platform.core.designsystem.component.YTButton
import com.yellowtrack.platform.core.designsystem.component.YTCard
import com.yellowtrack.platform.core.designsystem.component.YTSectionCard
import com.yellowtrack.platform.core.designsystem.component.YTStatus
import com.yellowtrack.platform.core.designsystem.component.YTStatusIndicator
import com.yellowtrack.platform.core.designsystem.component.YTTextButton
import com.yellowtrack.platform.core.designsystem.component.YTTextField
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
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
    onWatchFolder: (eventId: String, sourceKey: String) -> Unit,
    onStopWatching: (sourceKey: String) -> Unit,
    onSeat: (eventId: String, stationId: String, registrationId: String) -> Unit,
    onDeliver: (eventId: String, slotId: String) -> Unit,
    onDismissProblem: () -> Unit,
    onDismissNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatefulContent(
        state = uiState.content,
        modifier = modifier.fillMaxSize(),
        onRetry = onRetry,
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
            uiState.note?.let { note ->
                YTCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(note, style = YTTheme.typography.bodyMedium)
                        YTTextButton(text = "Dismiss", onClick = onDismissNote)
                    }
                }
            }

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
                        ingest = uiState.ingest,
                        canWatchFolders = uiState.canWatchFolders,
                        onBack = onCloseEvent,
                        onOpenStation = onOpenStation,
                        onCloseStation = onCloseStation,
                        onWatchFolder = onWatchFolder,
                        onStopWatching = onStopWatching,
                        onSeat = onSeat,
                        onDeliver = onDeliver,
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

    if (content.events.isEmpty()) {
        Text(
            "No events yet. An event is a day you hand photographs to the people in them — " +
                "name one above to start.",
            style = YTTheme.typography.bodyMedium,
            color = YTTheme.colors.onSurfaceVariant,
        )
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
    ingest: Map<String, IngestStatus>,
    canWatchFolders: Boolean,
    onBack: () -> Unit,
    onOpenStation: (eventId: String, name: String, sourceKey: String) -> Unit,
    onCloseStation: (eventId: String, stationId: String) -> Unit,
    onWatchFolder: (eventId: String, sourceKey: String) -> Unit,
    onStopWatching: (sourceKey: String) -> Unit,
    onSeat: (eventId: String, stationId: String, registrationId: String) -> Unit,
    onDeliver: (eventId: String, slotId: String) -> Unit,
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

            if (station.isOpen) {
                Seated(
                    seated = event.seated[station.name],
                    people = event.registrations,
                    isBusy = isBusy,
                    onSeat = { onSeat(event.id, station.id, it) },
                )
            }

            if (station.isOpen && canWatchFolders) {
                Ingest(
                    status = ingest[station.sourceKey],
                    onWatch = { onWatchFolder(event.id, station.sourceKey) },
                    onStop = { onStopWatching(station.sourceKey) },
                )
            }
        }
    }

    // After the stations, deliberately. Mid-event the live camera is what a photographer
    // looks at; the backlog is what somebody works down once the queue has gone.
    Sittings(event = event, isBusy = isBusy, onDeliver = onDeliver)
}

/**
 * Who is in front of this camera, and how to change it.
 *
 * The name and the address both, because a mistap here sends one guest's photographs to
 * another and "Ada" is not enough to tell two Adas apart. The list is filtered rather than
 * scrolled: an event has hundreds of sign-ups and a photographer has somebody standing in
 * front of them.
 */
@Composable
private fun Seated(
    seated: SittingRow?,
    people: List<PersonRow>,
    isBusy: Boolean,
    onSeat: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var choosing by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    seated?.let { "Now: ${it.label}" } ?: "Nobody seated — photographs go to the gallery",
                    style = YTTheme.typography.bodyMedium,
                )
                seated?.takeIf { it.name != null }?.let {
                    Text(it.email, style = YTTheme.typography.bodySmall, color = YTTheme.colors.onSurfaceVariant)
                }
            }

            YTTextButton(
                text = if (choosing) "Cancel" else "Next person",
                onClick = { choosing = !choosing },
            )
        }

        if (choosing) {
            YTTextField(
                value = query,
                onValueChange = { query = it },
                label = "Find somebody",
                placeholder = "name or email",
            )

            val matches =
                people
                    .filter {
                        query.isBlank() ||
                            query.trim().lowercase() in "${it.name.orEmpty()} ${it.email}".lowercase()
                    }.take(SEARCH_RESULTS)

            if (matches.isEmpty()) {
                Text(
                    "Nobody matches. They may not have scanned the code yet.",
                    style = YTTheme.typography.bodySmall,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            }

            matches.forEach { person ->
                YTCard(
                    modifier =
                        Modifier.fillMaxWidth().clickable(enabled = !isBusy) {
                            onSeat(person.id)
                            choosing = false
                            query = ""
                        },
                ) {
                    Column {
                        Text(person.label, style = YTTheme.typography.bodyMedium)
                        // Always, even when a name is shown. Two people called Ada is not an
                        // edge case at a conference, and the address is what distinguishes
                        // them — and what the photographs will be sent to.
                        Text(
                            person.email,
                            style = YTTheme.typography.bodySmall,
                            color = YTTheme.colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The sittings, and the ones still owed a delivery.
 *
 * Ordered so the work is at the top: closed, holding photographs, not yet sent. A sitting
 * nobody sends is a person who was photographed and got nothing, and the only place that is
 * visible is here.
 */
@Composable
private fun Sittings(
    event: OpenEvent,
    isBusy: Boolean,
    onDeliver: (eventId: String, slotId: String) -> Unit,
) {
    if (event.sittings.isEmpty()) return

    val waiting = event.awaitingDelivery

    YTSectionCard(
        title =
            if (waiting.isEmpty()) {
                "Sittings"
            } else {
                "Sittings — ${waiting.size} to send"
            },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small)) {
            (waiting + (event.sittings - waiting.toSet())).forEach { sitting ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(sitting.label, style = YTTheme.typography.bodyMedium)
                        Text(
                            "${sitting.stationName} · ${photographs(sitting.photographs)}" +
                                (sitting.blockedBecause?.let { " · $it" } ?: ""),
                            style = YTTheme.typography.bodySmall,
                            color = YTTheme.colors.onSurfaceVariant,
                        )
                    }

                    when {
                        sitting.isDelivered -> YTBadge(text = "Sent")
                        sitting.canDeliver ->
                            YTButton(
                                text = "Send",
                                enabled = !isBusy,
                                onClick = { onDeliver(event.id, sitting.id) },
                            )
                        else -> Unit
                    }
                }
            }
        }
    }
}

/**
 * What this station's folder is doing, or an offer to point it at one.
 *
 * The counts are worth the space because ingest is otherwise invisible: a watch that stopped
 * looks exactly like one with nothing to send, and the difference is only discovered when
 * somebody opens the gallery after the guests have gone.
 */
@Composable
private fun Ingest(
    status: IngestStatus?,
    onWatch: () -> Unit,
    onStop: () -> Unit,
) {
    if (status == null) {
        YTTextButton(text = "Watch a folder…", onClick = onWatch)

        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The folder name is usually the source key — a photographer names the folder
            // after the camera and the source is taken from it — so naming it again under
            // the station reads as a stutter. Shown only when they have drifted apart, which
            // is exactly when somebody needs to know which folder this actually is.
            val folder =
                status.folderName
                    .takeIf { it != status.sourceKey }
                    ?.let { "$it — " }
                    .orEmpty()

            Text(
                folder + "${status.sent} sent" +
                    if (status.waiting > 0) ", ${status.waiting} waiting" else "",
                style = YTTheme.typography.bodySmall,
                color = YTTheme.colors.onSurfaceVariant,
            )
            YTTextButton(text = "Stop watching", onClick = onStop)
        }

        // Each of these is a photograph somebody will not receive, or a reason none will
        // arrive at all. Said here rather than counted, because a number is something to
        // scroll past.
        status.lastSweepFailed?.let { Problem("That folder could not be read: $it") }

        if (status.refused.isNotEmpty()) {
            Problem(
                "${status.refused.size} refused and will not be sent: ${status.refused.take(
                    3,
                ).joinToString { it.path }}",
            )
        }

        if (status.stuck.isNotEmpty()) {
            Problem("${status.stuck.size} stuck: ${status.stuck.take(3).joinToString()}")
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

/** Enough to choose from without turning the screen into a directory. */
private const val SEARCH_RESULTS = 6

/** "1 photograph", "4 photographs" — a screen a person reads, not a log line. */
private fun photographs(count: Int): String = if (count == 1) "1 photograph" else "$count photographs"
