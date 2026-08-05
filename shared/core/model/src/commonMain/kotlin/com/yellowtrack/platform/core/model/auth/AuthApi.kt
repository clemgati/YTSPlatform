package com.yellowtrack.platform.core.model.auth

import kotlinx.serialization.Serializable

/**
 * The authentication contract, defined once and compiled into both sides.
 *
 * Same reasoning as `core:model/sync/SyncApi.kt`: the server defined these locally and a
 * client would have had to write its own copies, which agree until one of them is edited.
 * A renamed field here would be a silent 400 on a phone rather than a build failure.
 *
 * These are not domain types. Nothing in a photography business is a `SignInRequest`, and
 * they live in their own package for the same reason the sync envelopes do.
 */
@Serializable
data class SignUpRequest(
    val email: String,
    val password: String,
    val name: String,
    val studioName: String,
)

@Serializable
data class SignInRequest(
    val email: String,
    val password: String,
)

/**
 * What a successful sign-up or sign-in returns.
 *
 * The token appears here and nowhere else — the server stores only its digest, so this is
 * the one moment the value exists in a form anybody can keep.
 */
@Serializable
data class SessionResponse(
    val token: String,
    val expiresAt: Long,
    val accountId: String,
    val email: String,
    val name: String,
    val studioId: String,
    val studioName: String,
)

@Serializable
data class AccountResponse(
    val accountId: String,
    val email: String,
    val name: String,
    val studioId: String,
    val studioName: String,
)

/** Asks for a reset code. Answered identically whether or not the address has an account. */
@Serializable
data class ForgotPasswordRequest(
    val email: String,
)

/**
 * Sets a new password with a code from the email.
 *
 * The address is sent as well as the code, so a code alone is not enough — see ADR 0010.
 */
@Serializable
data class ResetPasswordRequest(
    val email: String,
    val code: String,
    val newPassword: String,
)

/**
 * Why a request was refused, in words meant for a person.
 *
 * Deliberately uninformative about *which* half of a credential was wrong: telling a wrong
 * password from an unknown address turns sign-in into a way to ask who has an account here.
 */
@Serializable
data class ErrorResponse(
    val error: String,
)

/**
 * Asks for the studio and everything in it to be deleted.
 *
 * The password is asked for again even though the request is already authenticated. A token
 * is what a borrowed laptop already has, and this is the one action nobody can undo for
 * themselves afterwards.
 */
@Serializable
data class DeleteAccountRequest(
    val password: String,
)

/**
 * What was done, and until when it can still be undone.
 *
 * [purgeAfter] is given so the answer to "how long have I got" comes from the server that
 * will act on it rather than from a sentence in a screen that could drift from the setting.
 */
@Serializable
data class DeleteAccountResponse(
    val purgeAfter: Long,
)

/**
 * Undoes a deletion that has not been purged yet.
 *
 * Takes the credentials rather than a token because there is no session to present: every
 * one was revoked the moment the studio asked to go, which is the whole reason this cannot
 * be a button inside the application.
 */
@Serializable
data class RestoreAccountRequest(
    val email: String,
    val password: String,
)

/**
 * The answer to a sign-in for a studio that is waiting to be purged.
 *
 * Sent only when the password was right. It carries the date rather than a sentence so the
 * screen can say it in the reader's own format, and so the number comes from the server that
 * will act on it rather than from a string that could drift from the setting.
 */
@Serializable
data class PendingDeletionResponse(
    val purgeAfter: Long,
    val error: String,
)

/**
 * A rendered document, on its way to a studio's client.
 *
 * The body is rendered on the device rather than the server, because the device already
 * renders it — the same HTML that is saved to a file or handed to a share sheet. Rendering it
 * a second time on the server would be a second renderer to keep in step with the first.
 *
 * Neither the sender nor the reply address is here. Both are the server's to decide, from the
 * studio the token belongs to — see `ADR 0011`. A caller that could name its own sender would
 * be a caller that could send as anybody.
 */
@Serializable
data class SendDocumentRequest(
    val to: String,
    val subject: String,
    val html: String,
    val text: String,
)
