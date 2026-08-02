package com.yellowtrack.platform.server.mail

/** One message, in the two forms a mail client might render. */
data class Email(
    val to: String,
    val subject: String,
    val body: String,
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
