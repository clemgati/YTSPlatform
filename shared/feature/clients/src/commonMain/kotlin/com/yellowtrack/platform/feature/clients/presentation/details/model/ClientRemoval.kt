package com.yellowtrack.platform.feature.clients.presentation.details.model

/**
 * Whether this account can be removed, and what is holding it if not.
 *
 * Two things in the application refer to a client: its bookings, and the links to the
 * people on it. The links are retired with the account, individually, so peers learn each
 * one is gone. Everything else a studio would grieve — sessions, invoices, payments —
 * reaches a client only *through* a booking, because `Session.projectId` and
 * `Invoice.projectId` cannot be null.
 *
 * That is what makes the rule below safe rather than merely cautious: a client with no
 * bookings genuinely has nothing behind it but contacts and notes, and a client with
 * bookings is holding money.
 */
internal sealed interface ClientRemoval {
    /** Nothing is attached, so removing this account loses only what is on this screen. */
    data object Available : ClientRemoval

    /**
     * Held by its bookings, and deliberately not offered as a cascade.
     *
     * Removing an account and taking its jobs, shoot days, invoices and payments with it is
     * not a correction — it is the destruction of a year's accounts by one press, and no
     * amount of confirming makes that a reasonable thing to offer. A studio that means it
     * can remove the bookings first, and will see what it is losing on the way.
     */
    data class HeldByBookings(
        val count: Int,
    ) : ClientRemoval
}
