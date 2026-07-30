package com.yellowtrack.platform.core.common.storage

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Reading a real folder, because the point of this class is that it touches a disk.
 *
 * A fake would prove only that the interface compiles. What has to be right is the walk:
 * a shoot is filed in folders — by card, by camera, by day — and counting only the top
 * level would report a wedding as a handful of items.
 */
class JvmVolumeInspectorTest {
    private val inspector = JvmVolumeInspector()

    private fun tempTree(): java.io.File {
        val root = Files.createTempDirectory("yellowtrack-volume").toFile()
        root.deleteOnExit()

        // Two cards, each with frames, plus a stray file at the top — the shape a copied
        // shoot actually has.
        listOf("CARD-A", "CARD-B").forEach { card ->
            val folder = root.resolve(card).apply { mkdirs() }
            repeat(3) { index -> folder.resolve("DSC_000$index.CR3").writeBytes(ByteArray(1_000)) }
        }
        root.resolve("notes.txt").writeBytes(ByteArray(500))

        return root
    }

    @Test
    fun `every file is counted, however deep it is filed`() =
        runTest {
            val found = inspector.inspect(tempTree().absolutePath)

            assertTrue(found.exists)
            assertEquals(7, found.fileCount, "six frames across two cards, plus the note")
            assertEquals(6_500L, found.totalBytes)
            assertTrue(found.holdsFiles)
        }

    @Test
    fun `a drive that is not plugged in reads as missing rather than as empty`() =
        runTest {
            val found = inspector.inspect("/Volumes/A Drive That Is Not There/2026")

            assertFalse(found.exists)
            assertFalse(found.holdsFiles)
            assertEquals(0, found.fileCount)
        }

    @Test
    fun `an empty folder exists but holds nothing`() =
        runTest {
            val empty = Files.createTempDirectory("yellowtrack-empty").toFile().also { it.deleteOnExit() }

            val found = inspector.inspect(empty.absolutePath)

            assertTrue(found.exists, "the folder is there — that is a different problem from a missing drive")
            assertFalse(found.holdsFiles, "and an empty folder is not a backup")
        }

    @Test
    fun `a path to one file counts that file`() =
        runTest {
            val file =
                Files.createTempFile("yellowtrack-single", ".zip").toFile().also {
                    it.deleteOnExit()
                    it.writeBytes(ByteArray(2_048))
                }

            val found = inspector.inspect(file.absolutePath)

            assertEquals(1, found.fileCount)
            assertEquals(2_048L, found.totalBytes)
        }

    @Test
    fun `folders themselves are not counted as files`() =
        runTest {
            val root = Files.createTempDirectory("yellowtrack-folders").toFile().also { it.deleteOnExit() }
            root.resolve("a/b/c").mkdirs()

            val found = inspector.inspect(root.absolutePath)

            assertEquals(0, found.fileCount, "three nested folders and no files is still no files")
        }
}
