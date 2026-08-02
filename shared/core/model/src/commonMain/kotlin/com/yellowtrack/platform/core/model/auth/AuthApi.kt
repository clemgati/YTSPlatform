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
