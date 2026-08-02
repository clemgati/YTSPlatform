package com.yellowtrack.platform.server.auth

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Argon2id hashing, in the PHC string format.
 *
 * The format carries its own parameters — `$argon2id$v=19$m=65536,t=3,p=1$salt$hash` — so
 * [verify] uses the parameters the hash was *made* with rather than the ones configured
 * now. That is what lets [Parameters.CURRENT] be raised later without invalidating
 * everybody's password: existing hashes keep verifying against their own settings, and
 * `needsRehash` says which ones are behind.
 *
 * BouncyCastle rather than the usual JVM binding, which is JNI over a native library and
 * would put a per-platform native artefact into CI. See ADR 0009 decision 3.
 */
object Passwords {
    private const val ALGORITHM = "argon2id"
    private const val VERSION = Argon2Parameters.ARGON2_VERSION_13
    private const val SALT_LENGTH = 16
    private const val HASH_LENGTH = 32

    private val random = SecureRandom()
    private val encoder: Base64.Encoder = Base64.getEncoder().withoutPadding()
    private val decoder: Base64.Decoder = Base64.getDecoder()

    /**
     * Cost settings.
     *
     * [CURRENT] follows OWASP's Argon2id guidance: 64 MiB of memory, three passes, one
     * lane. Memory is the parameter that matters — it is what stops an attacker trading
     * cheap parallel hardware for time — so it is the one to raise first.
     */
    data class Parameters(
        val memoryKb: Int,
        val iterations: Int,
        val parallelism: Int,
    ) {
        companion object {
            val CURRENT = Parameters(memoryKb = 65_536, iterations = 3, parallelism = 1)
        }
    }

    /** Hashes [password] with a fresh random salt. */
    fun hash(
        password: String,
        parameters: Parameters = Parameters.CURRENT,
    ): String {
        val salt = ByteArray(SALT_LENGTH).also(random::nextBytes)
        val hash = derive(password, salt, parameters)

        return buildString {
            append('$').append(ALGORITHM)
            append("\$v=").append(VERSION)
            append("\$m=").append(parameters.memoryKb)
            append(",t=").append(parameters.iterations)
            append(",p=").append(parameters.parallelism)
            append('$').append(encoder.encodeToString(salt))
            append('$').append(encoder.encodeToString(hash))
        }
    }

    /**
     * Whether [password] produced [encoded].
     *
     * Returns false rather than throwing on a malformed hash: a corrupt row should fail to
     * authenticate, not take the endpoint down. Comparison is constant-time, so the
     * failure reveals nothing about how far it got.
     */
    fun verify(
        password: String,
        encoded: String,
    ): Boolean {
        val parsed = parse(encoded) ?: return false
        val recomputed = derive(password, parsed.salt, parsed.parameters)
        return MessageDigest.isEqual(recomputed, parsed.hash)
    }

    /** Whether [encoded] was made with weaker settings than [parameters], and should be redone. */
    fun needsRehash(
        encoded: String,
        parameters: Parameters = Parameters.CURRENT,
    ): Boolean {
        val parsed = parse(encoded) ?: return true
        return parsed.parameters.memoryKb < parameters.memoryKb ||
            parsed.parameters.iterations < parameters.iterations
    }

    private fun derive(
        password: String,
        salt: ByteArray,
        parameters: Parameters,
    ): ByteArray {
        val generator =
            Argon2BytesGenerator().apply {
                init(
                    Argon2Parameters
                        .Builder(Argon2Parameters.ARGON2_id)
                        .withVersion(VERSION)
                        .withMemoryAsKB(parameters.memoryKb)
                        .withIterations(parameters.iterations)
                        .withParallelism(parameters.parallelism)
                        .withSalt(salt)
                        .build(),
                )
            }

        return ByteArray(HASH_LENGTH).also { generator.generateBytes(password.toCharArray(), it) }
    }

    private data class Parsed(
        val parameters: Parameters,
        val salt: ByteArray,
        val hash: ByteArray,
    )

    private fun parse(encoded: String): Parsed? =
        runCatching {
            // ["", "argon2id", "v=19", "m=65536,t=3,p=1", salt, hash]
            val fields = encoded.split('$')
            if (fields.size != 6 || fields[1] != ALGORITHM) return null

            val costs =
                fields[3]
                    .split(',')
                    .associate { field -> field.substringBefore('=') to field.substringAfter('=').toInt() }

            Parsed(
                parameters =
                    Parameters(
                        memoryKb = costs.getValue("m"),
                        iterations = costs.getValue("t"),
                        parallelism = costs.getValue("p"),
                    ),
                salt = decoder.decode(fields[4]),
                hash = decoder.decode(fields[5]),
            )
        }.getOrNull()
}
