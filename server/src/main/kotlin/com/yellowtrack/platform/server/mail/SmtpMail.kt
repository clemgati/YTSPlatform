package com.yellowtrack.platform.server.mail

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.Properties

/**
 * Mail over SMTP.
 *
 * Deliberately plain. Everything that decides whether a reset is *safe* — how the code is
 * made, how long it lives, what it reveals — is in `PasswordResets`; this only has to put
 * a message on a socket, which is the part a network can get wrong.
 *
 * Constructed only when [MailConfig.fromEnvironment] found a host, so an unconfigured
 * deployment fails at boot with something readable rather than at the first reset with a
 * null.
 */
class SmtpMail(
    private val config: MailConfig,
) : Mailer {
    private val session: Session by lazy {
        val properties =
            Properties().apply {
                put("mail.smtp.host", config.host)
                put("mail.smtp.port", config.port.toString())
                put("mail.smtp.auth", (config.username != null).toString())
                put("mail.smtp.starttls.enable", config.useTls.toString())
                // Without this a dead host hangs the request thread until the OS gives up.
                put("mail.smtp.connectiontimeout", TIMEOUT_MILLIS)
                put("mail.smtp.timeout", TIMEOUT_MILLIS)
                put("mail.smtp.writetimeout", TIMEOUT_MILLIS)
            }

        if (config.username != null) {
            Session.getInstance(
                properties,
                object : Authenticator() {
                    override fun getPasswordAuthentication() =
                        PasswordAuthentication(config.username, config.password.orEmpty())
                },
            )
        } else {
            Session.getInstance(properties)
        }
    }

    override fun send(email: Email) {
        val message =
            MimeMessage(session).apply {
                setFrom(InternetAddress(config.fromAddress, config.fromName))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(email.to))
                subject = email.subject
                setText(email.body, "UTF-8")
            }

        Transport.send(message)
    }

    private companion object {
        const val TIMEOUT_MILLIS = "10000"
    }
}
