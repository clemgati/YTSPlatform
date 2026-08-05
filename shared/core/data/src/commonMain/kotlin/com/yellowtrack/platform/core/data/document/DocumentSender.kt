package com.yellowtrack.platform.core.data.document

/** Why a document did not reach a client, in words the studio should read. */
class SendFailed(
    message: String,
) : Exception(message)

/**
 * Sends a rendered document to a client, as the studio.
 *
 * Only the body travels. The sender, the reply address and the studio's own copy are all
 * decided by the server from the token — see `ADR 0011`. A client that could name its own
 * sender would be a client that could send as anybody, from a domain every studio shares.
 */
interface DocumentSender {
    /** Sends, or throws [SendFailed] with something worth showing. */
    suspend fun send(
        to: String,
        subject: String,
        html: String,
        text: String,
    )
}
