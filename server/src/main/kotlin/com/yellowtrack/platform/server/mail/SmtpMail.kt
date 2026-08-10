package com.yellowtrack.platform.server.mail

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
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
        Transport.send(build(email))
    }

    /**
     * The message as it will go out.
     *
     * Separated from the send so it can be read without a mail server. What is worth checking
     * here is the part `Mailer` fakes away: a recording mailer sees an [Email] and says
     * nothing about whether its headers ever reached a wire, which is where `List-Unsubscribe`
     * would have been quietly lost.
     */
    internal fun build(email: Email): MimeMessage =
        MimeMessage(session).apply {
            // The address is always this deployment's; only the name in front of it
            // changes. ADR 0011 decision 1: one verified sender, so what leaves is signed
            // by the domain it claims to come from.
            setFrom(InternetAddress(email.fromAddress ?: config.fromAddress, email.fromName ?: config.fromName))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(email.to))
            email.cc?.let { setRecipients(Message.RecipientType.CC, InternetAddress.parse(it)) }
            email.replyTo?.let { replyTo = InternetAddress.parse(it) }
            subject = email.subject

            // Before the body, because a header set after the content is still a header
            // — but the ordering here is the one a reader expects.
            email.headers.forEach { (name, value) -> setHeader(name, value) }

            when (val html = email.html) {
                // A password reset, which is one line and has no second form.
                null -> setText(email.body, "UTF-8")
                else ->
                    setContent(
                        MimeMultipart("alternative").apply {
                            // Text first. `multipart/alternative` is ordered worst to
                            // best, and a client shows the last part it can render — so
                            // reversing these sends the plain text to everybody.
                            addBodyPart(MimeBodyPart().apply { setText(email.body, "UTF-8") })
                            addBodyPart(MimeBodyPart().apply { setContent(html, "text/html; charset=UTF-8") })
                        },
                    )
            }

            // Writes the headers the parts only describe until now: without it a body part
            // set to text/html still reports text/plain, because `getContentType` reads a
            // header nothing has written yet. `Transport.send` does this itself, so calling
            // it here changes nothing about what is sent and makes what this returns true.
            saveChanges()
        }

    private companion object {
        const val TIMEOUT_MILLIS = "10000"
    }
}
