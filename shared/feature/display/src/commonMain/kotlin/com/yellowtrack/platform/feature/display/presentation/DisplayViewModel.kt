package com.yellowtrack.platform.feature.display.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yellowtrack.platform.core.data.auth.AuthRepository
import com.yellowtrack.platform.core.data.auth.SessionState
import com.yellowtrack.platform.core.data.event.EventsApi
import com.yellowtrack.platform.core.model.event.EventSummary
import com.yellowtrack.platform.core.ui.state.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The companion display: a device propped on a table showing one event's sign-up code.
 *
 * It replaces a printed card, and everything here follows from that being its whole job.
 *
 * ## The code shown must be a code that works
 *
 * A printed card has one failure mode — the studio withdraws the invite and the paper does
 * not know. A screen has no excuse for the same failure, so this keeps asking, and stops
 * showing the code the moment the server says sign-up has closed. A dead code is worse than
 * no code: somebody scans it, is refused, and walks away believing they signed up.
 *
 * ## Listing must not open anything
 *
 * The list is filtered on [EventSummary.signUpOpen], which the server reports. The obvious
 * alternative — ask each event for its invite and see — *issues* an invite, so a device would
 * open sign-ups on every event a studio had ever run simply by being switched on.
 *
 * ## Leaving the code costs the password
 *
 * The device is unattended among strangers. Changing which event it shows is the only thing
 * on it worth protecting, and [confirmUnlock] is the sole route back to the list — a guest who
 * finds the picker and taps another event would route the next hour of sign-ups elsewhere,
 * and nobody would notice until the photographs went to the wrong people.
 */
internal class DisplayViewModel(
    private val api: EventsApi,
    private val auth: AuthRepository,
) : ViewModel() {
    private val state = MutableStateFlow(DisplayUiState(content = UiState.Loading))

    val uiState: StateFlow<DisplayUiState> = state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        try {
            val open = api.events().filter { it.signUpOpen }

            state.update { current ->
                val shown = current.showing()

                current.copy(
                    content =
                        when {
                            // A reload while an event is on the table keeps it there, even
                            // when the reload came back with nothing. Falling to Empty here
                            // would take the device off the event without the password —
                            // which is the one thing the lock exists to stop — and it would
                            // happen precisely when the studio withdrew the last live code,
                            // so the screen would go blank at the moment it most needed to
                            // explain itself.
                            shown != null ->
                                UiState.Success(
                                    DisplayContent(
                                        studioName = studioName(),
                                        events = open.map { it.displayable() },
                                        showing = shown.copy(withdrawn = open.none { it.id == shown.event.id }),
                                    ),
                                )
                            open.isEmpty() -> UiState.Empty
                            else ->
                                UiState.Success(
                                    DisplayContent(
                                        studioName = studioName(),
                                        events = open.map { it.displayable() },
                                    ),
                                )
                        },
                    problem = null,
                )
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            // A device that has something on screen keeps it. Only a device with nothing to
            // show says so, because a table with a blank screen on it is a table with no
            // sign-up on it.
            state.update { current ->
                if (current.content is UiState.Success) {
                    current.copy(problem = failure.readable())
                } else {
                    current.copy(content = UiState.Error(failure.readable()))
                }
            }
        }
    }

    /**
     * Puts an event on the table.
     *
     * Asks for the invite and its geometry together — the invite is idempotent and this event
     * already has a live one, so this returns the code that is already printed on whatever
     * else the studio has produced rather than a second one.
     */
    fun show(eventId: String) {
        viewModelScope.launch {
            val event = state.value.events().firstOrNull { it.id == eventId } ?: return@launch

            try {
                val invite = api.invite(eventId)
                val code = api.inviteCode(eventId)

                state.update { current ->
                    current
                        .mapContent { content ->
                            content.copy(
                                showing = Showing(event = event, code = code, link = invite.url),
                            )
                        }.copy(problem = null)
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                state.update { it.copy(problem = failure.readable()) }
            }
        }
    }

    /**
     * Follows the server while the code is on the table.
     *
     * Two things change under the device without anybody touching it: the studio withdraws
     * the code from the laptop, and the studio opens sign-ups on a new event. The first has to
     * take the code off the screen; the second has to appear in the list somebody sees after
     * unlocking.
     */
    fun poll() {
        viewModelScope.launch {
            val shown = state.value.showing()

            if (shown == null) {
                reload()
                return@launch
            }

            try {
                val open = api.events().filter { it.signUpOpen }

                state.update { current ->
                    current
                        .mapContent { content ->
                            content.copy(
                                events = open.map { it.displayable() },
                                showing =
                                    content.showing?.let { showing ->
                                        val stillOpen = open.any { it.id == showing.event.id }
                                        // Named rather than the event being dropped from the
                                        // list: the studio needs to see *which* event went quiet,
                                        // and a screen that empties itself says only that
                                        // something broke.
                                        showing.copy(withdrawn = !stillOpen)
                                    },
                            )
                        }.copy(problem = null)
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                // Deliberately not marking the code withdrawn. A poll that failed says
                // nothing about the invite — the venue's wifi dropping must not take a
                // working code off the table.
                state.update { it.copy(problem = failure.readable()) }
            }
        }
    }

    // -- Getting back to the list ---------------------------------------------------------

    fun askToLeave() {
        state.update { it.mapShowing { showing -> showing.copy(unlock = Unlock()) } }
    }

    fun cancelLeaving() {
        state.update { it.mapShowing { showing -> showing.copy(unlock = null) } }
    }

    fun typePassword(password: String) {
        state.update {
            it.mapShowing { showing ->
                showing.copy(unlock = showing.unlock?.copy(password = password, problem = null))
            }
        }
    }

    /**
     * The password, checked against the server rather than against anything held here.
     *
     * Signing in again is the check. There is no "verify this password" endpoint and adding
     * one would be a second way to test a credential; this reuses the one that already exists,
     * with the rate limiting and the lockout behaviour it already has.
     *
     * It also refreshes the token, which a device left on a table through a ten-hour event
     * needs anyway.
     */
    fun confirmUnlock() {
        val email = (auth.session.value as? SessionState.SignedIn)?.session?.email

        if (email == null) {
            state.update {
                it.mapUnlock { unlock -> unlock.copy(problem = "This device is not signed in.") }
            }
            return
        }

        val password =
            state.value
                .showing()
                ?.unlock
                ?.password
                .orEmpty()

        if (password.isBlank()) return

        state.update { it.mapUnlock { unlock -> unlock.copy(isChecking = true, problem = null) } }

        viewModelScope.launch {
            try {
                auth.signIn(email, password)

                // Only here. Every other path leaves `showing` exactly as it was.
                state.update { current -> current.mapContent { it.copy(showing = null) } }
                reload()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                state.update {
                    it.mapUnlock { unlock ->
                        unlock.copy(
                            isChecking = false,
                            password = "",
                            // Not the server's words. A wrong password here is somebody
                            // holding the device, and "401" or "Invalid credentials" reads as
                            // a fault in the application rather than an answer.
                            problem = "That is not the password for this studio.",
                        )
                    }
                }
            }
        }
    }

    fun dismissProblem() {
        state.update { it.copy(problem = null) }
    }

    // -- Reading the state ------------------------------------------------------------------

    private fun studioName(): String = (auth.session.value as? SessionState.SignedIn)?.session?.studioName.orEmpty()

    private fun DisplayUiState.events(): List<DisplayableEvent> = (content as? UiState.Success)?.data?.events.orEmpty()

    private fun DisplayUiState.showing(): Showing? = (content as? UiState.Success)?.data?.showing

    private fun DisplayUiState.mapContent(block: (DisplayContent) -> DisplayContent): DisplayUiState =
        when (val current = content) {
            is UiState.Success -> copy(content = UiState.Success(block(current.data)))
            else -> this
        }

    private fun DisplayUiState.mapShowing(block: (Showing) -> Showing): DisplayUiState =
        mapContent { content -> content.copy(showing = content.showing?.let(block)) }

    private fun DisplayUiState.mapUnlock(block: (Unlock) -> Unlock): DisplayUiState =
        mapShowing { showing -> showing.copy(unlock = showing.unlock?.let(block)) }

    private fun EventSummary.displayable() = DisplayableEvent(id = id, name = name, startsAt = startsAt)

    /**
     * Nobody is reading this screen who can act on a stack trace.
     *
     * The person nearest the device is a guest. The studio is across the room. So the message
     * is short and says what to do, and the exception's own text is only used when it was
     * written for a person.
     */
    private fun Throwable.readable(): String = message?.takeIf { it.isNotBlank() } ?: "Could not reach the server."
}
