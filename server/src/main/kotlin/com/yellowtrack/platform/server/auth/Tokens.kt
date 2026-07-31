package com.yellowtrack.platform.server.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Session tokens: opaque, random, and stored only as a digest.
 *
 * The token is handed to the device once and never written down here. What the database
 * holds is its SHA-256, so a copy of `auth_session` is a list of sessions rather than a
 * set of working keys — the same reasoning that makes storing a password hash obvious.
 *
 * A plain digest with no salt is right for this and wrong for passwords. The input is 256
 * bits of uniform randomness, so there is no dictionary to precompute and nothing for a
 * salt to defend; the slowness that protects a password would only tax the lookup that
 * happens on every request.
 *
 * Opaque rather than a JWT, because a token that cannot be revoked is a key that cannot be
 * taken back from a stolen phone — and these are long-lived, because the application is
 * offline-first. See ADR 0009 decision 4.
 */
object Tokens {
    private const val TOKEN_BYTES = 32

    private val random = SecureRandom()
    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    /** A fresh token. This value is returned to the caller once and never stored. */
    fun issue(): String = encoder.encodeToString(ByteArray(TOKEN_BYTES).also(random::nextBytes))

    /** What goes in `auth_session.token_digest`, and what a presented token is looked up by. */
    fun digest(token: String): String =
        encoder.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)),
        )
}
