package com.yellowtrack.platform.feature.display.presentation

import com.yellowtrack.platform.core.model.event.QrMatrix
import com.yellowtrack.platform.core.ui.state.UiState

/**
 * A device on a table, showing one event's sign-up code.
 *
 * The whole screen is the product here. There is no navigation bar, no back gesture worth
 * honouring and nobody signed in looking at it — it is furniture at an event, and the person
 * closest to it is a stranger holding a phone.
 */
internal data class DisplayUiState(
    val content: UiState<DisplayContent>,
    /**
     * A refusal that leaves the screen correct — a poll that failed, a code that could not be
     * fetched. Sits on top rather than replacing what is shown, because a device on a table
     * that blanks itself over one failed request has stopped doing its job.
     */
    val problem: String? = null,
)

internal data class DisplayContent(
    val studioName: String,
    /**
     * The events somebody could still sign up to.
     *
     * Only these. An event with no live code cannot be displayed, because the code would be
     * one the server refuses — and asking for a code is what *creates* one, so a list of all
     * events would open sign-ups on every one of them just by being drawn.
     */
    val events: List<DisplayableEvent>,
    /** The one on the table, or null while somebody is choosing. */
    val showing: Showing? = null,
)

internal data class DisplayableEvent(
    val id: String,
    val name: String,
    val startsAt: Long?,
)

internal data class Showing(
    val event: DisplayableEvent,
    val code: QrMatrix,
    /** The same link the code carries, printed underneath for anybody whose camera will not. */
    val link: String,
    /**
     * The invite's own token, which is what the walk-up form signs somebody up with.
     *
     * Held rather than picked back out of [link]: the link is for a person to read, and
     * parsing it to recover something we were given would be inventing a second source of
     * truth for the same value.
     */
    val token: String,
    /**
     * Set when the studio has withdrawn this event's code from somewhere else.
     *
     * The device keeps showing the event and stops showing the code. A code that no longer
     * works is worse than no code: somebody scans it, is refused, and walks away believing
     * they signed up.
     */
    val withdrawn: Boolean = false,
    /** Non-null while somebody is being asked for the password. */
    val unlock: Unlock? = null,
    /** Non-null while somebody is filling the form in on this device. */
    val walkUp: WalkUp? = null,
)

/**
 * Somebody registering on the device itself, because they have no phone on them.
 *
 * The same questions the sign-up page asks, in the same order, submitted through the same
 * public endpoint with the same token. A guest at a table without a phone is doing exactly
 * what a guest with one does.
 */
internal data class WalkUp(
    val email: String = "",
    val givenName: String = "",
    val familyName: String = "",
    val phone: String = "",
    val isSubmitting: Boolean = false,
    /** What went wrong, in words the person at the table can act on. */
    val problem: String? = null,
    /** Set once they are registered, so the form can say so before it clears itself. */
    val done: Boolean = false,
    /**
     * Bumped by anything the person does.
     *
     * The form clears itself after a few minutes of nothing, because it is sitting on a table
     * in a room full of strangers with somebody's address half typed into it. Every keystroke
     * has to push that back, and a counter is what the screen can watch to know to start the
     * wait again.
     */
    val interactions: Int = 0,
) {
    /** The same rule the page enforces: an address and both halves of a name. */
    val canSubmit: Boolean
        get() =
            !isSubmitting &&
                email.isNotBlank() &&
                givenName.isNotBlank() &&
                familyName.isNotBlank()
}

/**
 * The password prompt that stands between the table and the event list.
 *
 * The device is unattended in a room full of strangers, and changing which event it shows is
 * the one thing on it that matters — a guest who wandered into the picker and tapped another
 * event would silently route the next hour of sign-ups to the wrong place.
 */
internal data class Unlock(
    val password: String = "",
    val isChecking: Boolean = false,
    /** What to say about the last attempt. Null before there has been one. */
    val problem: String? = null,
) {
    val canSubmit: Boolean get() = !isChecking && password.isNotBlank()
}
