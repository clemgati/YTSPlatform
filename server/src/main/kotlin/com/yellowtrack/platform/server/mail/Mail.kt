package com.yellowtrack.platform.server.mail

/**
 * One message.
 *
 * [body] is plain text and always present. The extra fields are all absent for a password
 * reset — one line of text from Yellow Track to its own account holder — and all used when a
 * studio sends a document to its client, which is a different act with different rules. ADR
 * 0011 sets out why.
 */
data class Email(
    val to: String,
    val subject: String,
    val body: String,
    /**
     * The same message as a web page, sent alongside [body] rather than instead of it.
     *
     * A client that cannot render it falls back to the text part, which is the rendering
     * that already exists for pasting into a message.
     */
    val html: String? = null,
    /**
     * What a client reads in its inbox list, in front of the sending address.
     *
     * The studio's name goes here. The address cannot be the studio's — SES signs for the
     * verified domain, and a message signed by one domain claiming to be from another is a
     * message in a spam folder.
     */
    val fromName: String? = null,
    /** Overrides the configured sender. Documents leave from `DOCUMENT_FROM`, resets from `MAIL_FROM`. */
    val fromAddress: String? = null,
    /**
     * Where a reply should go, which is the studio rather than this deployment.
     *
     * The load-bearing half of ADR 0011 decision 2. Without it a client's reply about an
     * invoice lands in the mailbox that owns the sending address, and the photographer
     * waiting to be paid never sees it.
     */
    val replyTo: String? = null,
    /** Copied, so the studio holds what its client received. Nothing else keeps the body. */
    val cc: String? = null,
    /**
     * Extra headers, for the ones a receiving provider reads rather than a person.
     *
     * `List-Unsubscribe` is why this exists. Gmail weights it, and for somebody who typed
     * their address at an event it is correct regardless of what any filter thinks: they
     * gave it for one purpose and should be able to withdraw it without composing a reply.
     *
     * Deliberately a map rather than named fields. The next one of these will be somebody
     * else's requirement, not a decision this codebase makes.
     */
    val headers: Map<String, String> = emptyMap(),
)

/**
 * Somewhere to send mail.
 *
 * An interface because the two implementations answer different questions: [SmtpMail]
 * proves the send path works, and a recording one lets the endpoints be tested without a
 * mail server. ADR 0010 decision 5 chose SMTP over a provider's API so that choosing a
 * provider later is configuration rather than code.
 */
interface Mailer {
    /**
     * Sends, or throws.
     *
     * Callers are expected to catch. ADR 0010 decision 6: a reset endpoint cannot report a
     * failed send without also reporting whether the address exists, so failures are
     * logged rather than surfaced — but they have to reach the caller to be logged, which
     * is why this throws rather than swallowing.
     */
    fun send(email: Email)
}

/**
 * Where mail comes from and how to reach the server.
 *
 * All of it from the environment. A default host would be a way to accidentally send real
 * mail from a development machine, so there is none — [fromEnvironment] returns null when
 * unconfigured and the application says so at boot rather than at the first reset.
 */
data class MailConfig(
    val host: String,
    val port: Int,
    val username: String?,
    val password: String?,
    val fromAddress: String,
    val fromName: String,
    /** STARTTLS. Off for a local capture server, on for anything real. */
    val useTls: Boolean,
) {
    companion object {
        fun fromEnvironment(): MailConfig? {
            val host = System.getenv("MAIL_HOST") ?: return null

            return MailConfig(
                host = host,
                port = System.getenv("MAIL_PORT")?.toIntOrNull() ?: DEFAULT_PORT,
                username = System.getenv("MAIL_USERNAME"),
                password = System.getenv("MAIL_PASSWORD"),
                fromAddress = System.getenv("MAIL_FROM") ?: "no-reply@yellowtrack.local",
                fromName = System.getenv("MAIL_FROM_NAME") ?: "Yellow Track",
                // Defaults off, because the only host anybody reaches without setting this
                // is a capture server on their own laptop.
                useTls = System.getenv("MAIL_TLS")?.toBooleanStrictOrNull() ?: false,
            )
        }

        private const val DEFAULT_PORT = 1025
    }
}
