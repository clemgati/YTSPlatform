package com.yellowtrack.platform.feature.clients.presentation.details.mapper

import com.yellowtrack.platform.core.ui.removal.Removal
import com.yellowtrack.platform.core.ui.removal.heldBy

/**
 * Works out whether an account can be removed.
 *
 * A client is the opposite shape to a booking: it holds nothing itself. Only `Project` and
 * `ClientContactLink` refer to one, the links are retired with it, and `Session.projectId`
 * and `Invoice.projectId` cannot be null — so shoot days, invoices and payments hang off a
 * booking, never off the client.
 *
 * That is what makes one question sufficient here. An account with no bookings genuinely
 * has nothing behind it but contacts and notes; one with bookings is holding money, and
 * removing it with them would be the destruction of a year's accounts by one press.
 *
 * Cancelled bookings count. A job that fell through still carries costs and possibly a
 * deposit, so it is measured against the bookings themselves rather than the ones worth
 * showing.
 */
internal fun clientRemoval(bookings: Int): Removal = heldBy(Removal.Hold("booking", "bookings", bookings))
