package com.yellowtrack.platform.core.data.event

import java.io.File

/**
 * What this machine has already dealt with, on disk.
 *
 * Append-only text, one line per file, `delivered` or `refused` and then the name. A
 * database table would be tidier and would need a migration and a schema snapshot to hold a
 * list of strings that is thrown away when the event ends; this is a scratch file with the
 * lifetime of an event.
 *
 * Appended and flushed as each photograph is settled rather than written at the end, because
 * the whole point is to survive the process not reaching the end. A crash loses at most the
 * line being written, which costs one duplicate photograph — where losing the file costs the
 * studio's entire morning a second time.
 *
 * One log per event and source, so two cameras shooting the same event keep separate lists
 * and a folder reused for a later event starts empty.
 */
class FileUploadLog(
    private val file: File,
) : UploadLog {
    private var cache: MutableSet<String>? = null

    override suspend fun handled(): Set<String> = load()

    override suspend fun record(
        path: String,
        delivered: Boolean,
    ) {
        // A newline in a file name would forge an extra entry, and the file name is the key
        // this whole log turns on. macOS and Linux both allow one.
        val safe = path.replace("\n", " ").replace("\r", " ")

        load().add(safe)

        file.parentFile?.mkdirs()
        file.appendText("${if (delivered) "delivered" else "refused"} $safe\n")
    }

    private fun load(): MutableSet<String> =
        cache ?: run {
            val entries =
                if (!file.exists()) {
                    mutableSetOf()
                } else {
                    file
                        .readLines()
                        .mapNotNullTo(mutableSetOf()) { line ->
                            // A half-written final line from a crash names no complete file,
                            // and treating it as one would suppress a photograph that was
                            // never actually sent. Dropped, so it is offered again.
                            line.substringAfter(' ', "").takeIf { it.isNotBlank() && ' ' in line }
                        }
                }

            entries.also { cache = it }
        }
}
