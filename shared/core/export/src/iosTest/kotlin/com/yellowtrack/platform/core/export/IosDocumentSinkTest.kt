package com.yellowtrack.platform.core.export

import kotlinx.coroutines.runBlocking
import platform.Foundation.NSFileManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The real iOS sink, against the real Documents directory.
 *
 * What cannot be tested here is the part that matters most — whether the share sheet
 * actually appears — because presenting one needs a window, and a test host has none. That
 * is stated rather than worked around: this covers the half that is testable and the
 * behaviour when there is nothing to present onto, and the sheet itself stays unproven until
 * somebody opens the application. The Android side of the same feature has now been driven
 * on a phone; this one has not.
 *
 * Block bodies throughout, because Kotlin/Native requires a test function to return `Unit`.
 */
class IosDocumentSinkTest {
    /** The half the studio depends on: the file exists whatever the sheet does. */
    @Test
    fun `writes the document to the documents directory`() {
        runBlocking {
            val document = Document(name(), DocumentFormat.Html, "<html>a call sheet</html>")

            val saved = IosDocumentSink().save(document)

            assertTrue(
                NSFileManager.defaultManager.fileExistsAtPath(saved.location),
                "nothing was written to ${saved.location}",
            )
            assertEquals(document.fileName, saved.fileName)
        }
    }

    // There is deliberately no test here for `share`.
    //
    // The obvious one — share with no window, assert the file survives — was written, run,
    // and deleted: it takes the whole test process down with `signal 11: Segmentation
    // fault`. `UIActivityViewController` needs a live `UIApplication`, and a Kotlin/Native
    // test binary has none, so the crash is in the harness rather than in the code. No
    // `runCatching` helps: a segmentation fault is not a Kotlin exception.
    //
    // That is worth leaving written down, because the test looks reasonable and somebody
    // will write it again. The ordering it was meant to pin — write first, present second —
    // is covered for every platform by `DocumentSinkContractTest` against a fake, which is
    // where a test of ordering belongs.

    /**
     * A distinct name per run, so a passing test cannot be a leftover file from a previous
     * one. The directory is real and persists between runs on a simulator.
     */
    private fun name(): String = "test-${NSFileManager.defaultManager.hashCode()}-${counter++}"

    private companion object {
        var counter = 0
    }
}
