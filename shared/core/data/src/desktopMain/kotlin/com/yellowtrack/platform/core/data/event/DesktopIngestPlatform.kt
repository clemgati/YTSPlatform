package com.yellowtrack.platform.core.data.event

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

/**
 * Watching folders on the machine a camera is actually tethered to.
 *
 * The only platform that implements this, and deliberately so — a phone is not what a body
 * shoots into. See [IngestPlatform.Unavailable].
 */
class DesktopIngestPlatform(
    private val logDirectory: File = defaultLogDirectory(),
) : IngestPlatform {
    override val canWatchFolders: Boolean = true

    /**
     * The system's own folder chooser, on the event dispatch thread.
     *
     * `invokeAndWait` rather than `invokeLater` because the answer is the return value, and
     * it is wrapped in [Dispatchers.IO] so a modal dialog does not hold whichever thread the
     * caller happened to be on — on the main thread that is the one drawing the window, and
     * the application would appear to hang behind its own dialog.
     */
    override suspend fun chooseFolder(): ChosenFolder? =
        withContext(Dispatchers.IO) {
            var chosen: File? = null

            SwingUtilities.invokeAndWait {
                val chooser =
                    JFileChooser().apply {
                        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                        dialogTitle = "Choose the folder your camera writes into"
                        isMultiSelectionEnabled = false
                    }

                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    chosen = chooser.selectedFile
                }
            }

            chosen?.let { ChosenFolder(path = it.absolutePath, name = it.name) }
        }

    override fun folderAt(path: String): WatchedFolder = NioWatchedFolder(File(path))

    /**
     * One log per folder, event and source.
     *
     * The name is derived rather than used directly: a folder path contains separators and a
     * source key is whatever somebody typed, neither of which belongs in a file name. A hash
     * would be opaque to anybody looking in the directory, so the readable part is kept and
     * the rest sanitised.
     */
    override fun logFor(
        folderPath: String,
        eventId: String,
        sourceKey: String,
    ): UploadLog = FileUploadLog(File(logDirectory, "${safe(eventId)}-${safe(sourceKey)}-${folderPath.hashCode()}.log"))

    private companion object {
        fun safe(value: String): String = value.map { if (it.isLetterOrDigit()) it else '-' }.joinToString("").take(40)

        /** Beside the session file, which is already this application's place on the disk. */
        fun defaultLogDirectory(): File {
            val home = System.getProperty("user.home").orEmpty()
            val osName = System.getProperty("os.name").orEmpty().lowercase()

            val base =
                when {
                    osName.contains("mac") -> File(home, "Library/Application Support")
                    osName.contains("win") -> File(System.getenv("APPDATA") ?: home)
                    else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share")
                }

            return File(File(base, "YellowTrack"), "ingest")
        }
    }
}
