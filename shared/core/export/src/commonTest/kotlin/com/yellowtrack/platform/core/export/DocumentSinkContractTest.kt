package com.yellowtrack.platform.core.export

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The promise every sink has to keep, whatever the platform does.
 *
 * Presenting a share sheet touches window hierarchies and content providers, which fail in
 * ways that compile perfectly and cannot be exercised from a unit test. What *can* be
 * pinned is the ordering: the document is written first and unconditionally, so a failure
 * to present costs the studio nothing.
 */
class DocumentSinkContractTest {
    private val document = Document("call-sheet", DocumentFormat.Html, "<html></html>")

    /** A sink whose sharing always fails, standing in for a platform misconfiguration. */
    private class SinkWithBrokenSharing : DocumentSink {
        var saves = 0

        override val canShare: Boolean = true

        override suspend fun save(document: Document): SavedDocument {
            saves++

            return SavedDocument(fileName = document.fileName, location = "saved/${document.fileName}")
        }

        override suspend fun share(document: Document): SavedDocument {
            val saved = save(document)
            runCatching { error("no share sheet on this device") }

            return saved
        }
    }

    @Test
    fun `a sink that cannot share still saves when asked to share`() =
        runTest {
            // The default implementation is the fallback: a platform that has said it
            // cannot share is not asked to do anything clever.
            val sink =
                object : DocumentSink {
                    var saved: Document? = null

                    override suspend fun save(document: Document): SavedDocument {
                        saved = document

                        return SavedDocument(document.fileName, "saved/${document.fileName}")
                    }
                }

            val result = sink.share(document)

            assertEquals(document, sink.saved)
            assertEquals("call-sheet.html", result.fileName)
            assertFalse(sink.canShare)
        }

    @Test
    fun `a share sheet that fails to appear still leaves the document on disk`() =
        runTest {
            val sink = SinkWithBrokenSharing()

            val result = sink.share(document)

            assertEquals(1, sink.saves, "the write happens before anything that can fail")
            assertEquals("saved/call-sheet.html", result.location)
            assertTrue(
                sink.canShare,
                "the platform still claims it can share — it failed, which is a different thing",
            )
        }
}
