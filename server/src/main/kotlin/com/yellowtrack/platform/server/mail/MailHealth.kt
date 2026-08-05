package com.yellowtrack.platform.server.mail

import java.util.concurrent.atomic.AtomicReference

/**
 * What the last attempt to send mail actually did.
 *
 * `/ready` reported `mail` from whether `MAIL_HOST` was set, which is a fact about the
 * environment read once at boot. It stays true through a wrong password, an expired
 * credential, a region still in the SES sandbox, and sending suspended over a bounce rate —
 * every way mail actually stops. It was a check that mail had been *configured*, offered
 * where a check that mail *works* was wanted.
 *
 * That mattered more here than it looks. ADR 0010 has the reset endpoint answer `202`
 * whether the send succeeded or not, deliberately, so the studio cannot be told either.
 * Between the two, a password reset could stop working for everybody and the only trace
 * would be a log line nobody reads.
 *
 * ## What this can and cannot see
 *
 * It sees what SMTP says at the moment of sending: a refusal, an authentication failure, a
 * host that cannot be reached. It does **not** see a bounce, because SES accepts the message
 * first and bounces afterwards, out of band — a delivery failure to a mistyped domain looks
 * exactly like a success from here. Watching that needs the SNS bounce topic, which nothing
 * subscribes to; `docs/DEPLOYMENT.md` says so under production access.
 *
 * Held in memory, so a restart forgets. That is not a gap being tolerated: after a restart
 * nothing has been sent, and reporting the last process's success would be describing a
 * different process. [lastSucceededAt] is null until this one has proved it for itself.
 */
class MailHealth(
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val state = AtomicReference(State())

    /** When a send last worked, or null if none has in this process. */
    val lastSucceededAt: Long? get() = state.get().lastSucceededAt

    /**
     * Why the last send failed, or null if it worked.
     *
     * Cleared by a success, so this answers "is mail broken now" rather than "has mail ever
     * broken". A single refused message with a working server either side of it is not
     * something to raise anybody at three in the morning for.
     */
    val lastFailure: String? get() = state.get().lastFailure

    fun recordSuccess() {
        state.set(State(lastSucceededAt = now(), lastFailure = null))
    }

    fun recordFailure(cause: Throwable) {
        state.updateAndGet { previous ->
            previous.copy(
                // The class name as well as the message, because the message is empty on
                // several of the exceptions that matter and "failed" on its own has never
                // helped anybody.
                lastFailure = "${cause::class.simpleName}: ${cause.message ?: "no message"}".take(MAX_DETAIL),
            )
        }
    }

    private data class State(
        val lastSucceededAt: Long? = null,
        val lastFailure: String? = null,
    )

    private companion object {
        const val MAX_DETAIL = 300
    }
}

/**
 * A [Mailer] that records what happened to [health] on the way past.
 *
 * A decorator rather than a callback threaded through `PasswordResets`, which should not
 * grow a second reason to care about mail. It also gets the null case right for free: when
 * mail is unconfigured there is no mailer to wrap, so nothing records a success that never
 * happened — the shape of the bug this whole class exists to stop.
 *
 * Rethrows. The caller logs the failure and must go on deciding what to do about it; this
 * only watches.
 */
class MonitoredMailer(
    private val delegate: Mailer,
    private val health: MailHealth,
) : Mailer {
    override fun send(email: Email) {
        try {
            delegate.send(email)
        } catch (cause: Throwable) {
            health.recordFailure(cause)
            throw cause
        }
        health.recordSuccess()
    }
}
