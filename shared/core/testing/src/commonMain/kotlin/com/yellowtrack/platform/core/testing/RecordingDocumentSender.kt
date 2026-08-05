package com.yellowtrack.platform.core.testing

import com.yellowtrack.platform.core.data.document.DocumentSender
import com.yellowtrack.platform.core.data.document.SendFailed

/** What a screen asked to be sent, and what the server said back. */
data class SentDocument(
    val to: String,
    val subject: String,
    val html: String,
    val text: String,
)

/**
 * A [DocumentSender] that records instead of sending.
 *
 * [refusal] makes it fail the way the server does — with words worth showing — because the
 * refusals are the half a screen has to get right: a studio with no email of its own, a daily
 * limit, a mail server that would not take it.
 */
class RecordingDocumentSender(
    private val refusal: String? = null,
) : DocumentSender {
    private val recorded = mutableListOf<SentDocument>()

    val sent: List<SentDocument> get() = recorded.toList()

    val last: SentDocument? get() = recorded.lastOrNull()

    override suspend fun send(
        to: String,
        subject: String,
        html: String,
        text: String,
    ) {
        refusal?.let { throw SendFailed(it) }
        recorded += SentDocument(to, subject, html, text)
    }
}
