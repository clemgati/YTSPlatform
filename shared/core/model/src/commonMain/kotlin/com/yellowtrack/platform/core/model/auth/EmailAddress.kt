package com.yellowtrack.platform.core.model.auth

import kotlin.math.abs
import kotlin.math.min

/**
 * What an address has to look like, and what it was probably meant to be.
 *
 * Here rather than in a client because both sides need it and they must not disagree: the
 * server is the authority on what it will accept, and a client that invents its own rule
 * eventually rejects something the server would have taken, or waves through something it
 * will not.
 *
 * The address matters more here than in most applications. It is the only route back into
 * an account — there is no support desk and no second factor to fall back on — so an
 * address typed wrongly at sign-up produces an account nobody can ever recover, and both
 * ends of that stay silent about it. Sign-up succeeds because any string is storable, and
 * the reset endpoint answers `202` whether or not the send worked, deliberately (ADR 0010).
 * The two halves of the fault hide each other, and the studio finds out at the only moment
 * it cannot afford to: when it is locked out.
 *
 * So this exists to be used *before* the address is committed to.
 */
object EmailAddress {
    /**
     * Whether this could be an address at all.
     *
     * Shape only, and deliberately loose. This is not RFC 5322 — that grammar admits quoted
     * local parts, nested comments and bracketed address literals, and a regular expression
     * claiming to implement it is wrong in ways that surface as a real person unable to
     * sign up. What it rejects is only what cannot be delivered to under any reading.
     *
     * It cannot tell you an address exists. `someone@gmail.com` and `nobody@gmail.com` are
     * equally plausible, and only a message actually arriving separates them.
     */
    fun isPlausible(email: String): Boolean {
        val trimmed = email.trim()
        if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return false

        val at = trimmed.indexOf('@')
        // lastIndexOf, so a second @ anywhere fails rather than being read as part of a domain.
        if (at <= 0 || at != trimmed.lastIndexOf('@')) return false

        val domain = trimmed.substring(at + 1)
        if (domain.isEmpty() || domain.startsWith('.') || domain.endsWith('.')) return false
        if (domain.startsWith('-') || domain.endsWith('-')) return false
        if (domain.contains("..")) return false

        // A dot is required. Nothing with a public MX record lacks one, and the addresses
        // that legitimately do — `root@localhost` — are not ones a studio signs up with.
        val lastDot = domain.lastIndexOf('.')
        if (lastDot <= 0) return false

        val tld = domain.substring(lastDot + 1)
        return tld.length >= MIN_TLD_LENGTH && tld.all(Char::isLetter)
    }

    /**
     * The address this was probably meant to be, or null if there is nothing to say.
     *
     * A suggestion and never a rejection, which is the whole design of it. `@gmail.ocm` is a
     * perfectly well-formed address and [isPlausible] says so correctly — the domain simply
     * does not exist, and no amount of inspecting the string proves that. Only a list of
     * what people usually mean gets close, and a list like that is wrong for anybody whose
     * domain is genuinely unusual.
     *
     * So this must be offered rather than enforced. Present it as a question the studio can
     * decline; never block the form on it, and never silently rewrite what somebody typed.
     */
    fun suggestion(email: String): String? {
        val trimmed = email.trim().lowercase()
        if (!isPlausible(trimmed)) return null

        val at = trimmed.lastIndexOf('@')
        val local = trimmed.substring(0, at)
        val domain = trimmed.substring(at + 1)

        // An exact match is the answer, not a near-miss of a different entry. Without this,
        // `mail.com` — a real provider, one edit from `gmail.com` — gets corrected to it.
        // Somebody part-way through typing the right thing is not making a mistake. The
        // field is read on every keystroke, so without this every prefix of a correct
        // answer gets questioned on the way to it.
        //
        // The test is against the whole list rather than against each candidate as it is
        // scored, which is the version that looks equivalent and is not: `@gmail.co` is a
        // prefix of `gmail.com`, but it is also two edits from `ymail.com`, so filtering
        // per candidate merely moves the interruption to a worse suggestion. If what has
        // been typed is going somewhere, nothing should be offered at all.
        if (COMMON_DOMAINS.any { it.startsWith(domain) }) return null

        val closest =
            COMMON_DOMAINS
                .asSequence()
                // Cheap reject before the expensive compare: nothing differing in length by
                // more than the tolerance can possibly be within it.
                .filter { abs(it.length - domain.length) <= MAX_DISTANCE }
                .map { it to distance(domain, it) }
                .filter { (candidate, d) -> d <= toleranceFor(candidate) }
                .minByOrNull { (_, d) -> d }
                ?.first
                ?: return null

        return "$local@$closest"
    }

    /**
     * How far wrong a domain may be before the guess is worse than silence.
     *
     * Scaled by length because a fixed tolerance misbehaves at both ends. Two edits is
     * nothing across `btinternet.com` and almost the whole of `me.com`, where it would
     * reach far enough to "correct" unrelated domains into it.
     */
    private fun toleranceFor(candidate: String): Int = if (candidate.length < SHORT_DOMAIN) 1 else MAX_DISTANCE

    /**
     * Levenshtein distance.
     *
     * Two rows rather than the full matrix: the domains compared here are short, but this
     * runs against every candidate on every keystroke that changes the field.
     */
    private fun distance(
        a: String,
        b: String,
    ): Int {
        if (a == b) return 0

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(min(current[j - 1] + 1, previous[j] + 1), substitution)
            }
            val swap = previous
            previous = current
            current = swap
        }

        return previous[b.length]
    }

    private const val MIN_TLD_LENGTH = 2
    private const val MAX_DISTANCE = 2
    private const val SHORT_DOMAIN = 8

    /**
     * What people actually type, so a near-miss of one can be spotted.
     *
     * Consumer providers only. A studio's own domain is not guessable and must never be
     * corrected towards one of these, which is what the exact-match check above protects.
     * Regional variants are listed in their own right rather than left to the distance —
     * `hotmail.co.uk` is three edits from `hotmail.com`, so without the entry it would fall
     * through as unrecognised, which is right, but with a smaller tolerance it would one day
     * be "corrected" to the wrong country.
     */
    private val COMMON_DOMAINS =
        setOf(
            "gmail.com",
            "googlemail.com",
            "outlook.com",
            "outlook.co.uk",
            "hotmail.com",
            "hotmail.co.uk",
            "live.com",
            "live.co.uk",
            "msn.com",
            "yahoo.com",
            "yahoo.co.uk",
            "ymail.com",
            "icloud.com",
            "me.com",
            "mac.com",
            "aol.com",
            "proton.me",
            "protonmail.com",
            "mail.com",
            "gmx.com",
            "zoho.com",
            "btinternet.com",
            "sky.com",
        )
}
