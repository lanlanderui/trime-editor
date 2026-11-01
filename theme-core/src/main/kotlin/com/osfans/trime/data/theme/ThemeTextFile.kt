/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import java.io.File
import java.io.FileOutputStream
import com.osfans.trime.data.theme.ThemeLog as Timber

/**
 * Reads and replaces a theme file without ever truncating the original first.
 *
 * A theme is edited by many separate line-based writers. They all need the
 * same guarantees: a failed save must leave the old file intact, the previous
 * version must stay next to it, the author's line endings must survive, and a
 * file that changed outside the app must not be overwritten from a stale
 * editor snapshot.
 */
object ThemeTextFile {
    data class Document(
        /** Exact contents the editor was opened against. */
        val text: String,
        val lines: MutableList<String>,
        val eol: String,
        val endsWithNewline: Boolean,
    )

    /**
     * Counts successful saves, so a reader caching a decoded theme can tell
     * its own app's edits apart from no edit at all.
     *
     * Timestamps alone are not enough: a save that lands in the same
     * millisecond with an unchanged byte count would look like the file the
     * cache already holds.
     */
    @Volatile
    var writeGeneration: Long = 0
        private set

    /**
     * 写盘成功后的可选钩子。
     *
     * 输入法把它接到 `ThemeExternalMirror`（把主题同步到用户用 SAF 选的镜像目录）；
     * 编辑器不设，就没有这个副作用。放在这里而不是直接调用，是因为镜像用到了
     * `Intent` / `Base64`，会把本模块拖回 Android。
     */
    var afterWrite: ((File) -> Unit)? = null

    fun read(file: File): Document? = runCatching {
        val text = file.readText()
        Document(
            text = text,
            lines = text.split('\n').map { it.trimEnd('\r') }.toMutableList(),
            eol = if (text.contains("\r\n")) "\r\n" else "\n",
            endsWithNewline = text.endsWith('\n'),
        )
    }.getOrNull()

    fun write(
        file: File,
        document: Document,
        lines: List<String>,
    ): Boolean = replaceText(
        file = file,
        expectedText = document.text,
        newText = render(lines, document.eol, document.endsWithNewline),
    )

    /**
     * Replaces [file] only when it still contains [expectedText].
     *
     * The previous contents are copied to `<name>.bak`, the new contents go to
     * `<name>.tmp`, and that file is renamed over the target. If a file system
     * refuses to rename over an existing file, the target is removed and the
     * rename retried; the backup is restored if that also fails.
     */
    @Synchronized
    fun replaceText(
        file: File,
        expectedText: String,
        newText: String,
    ): Boolean {
        var temp: File? = null
        return try {
            check(file.isFile) { "Theme file does not exist: ${file.name}" }
            check(file.readText() == expectedText) { "Theme file changed outside the app: ${file.name}" }

            val backup = backupOf(file)
            file.copyTo(backup, overwrite = true)

            val tempFile = tempOf(file)
            temp = tempFile
            FileOutputStream(tempFile).use { output ->
                output.write(newText.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }

            if (!tempFile.renameTo(file)) {
                if (!file.delete() || !tempFile.renameTo(file)) {
                    // Preserve the old version if the platform cannot replace
                    // an open file. The backup is still there either way.
                    runCatching { backup.copyTo(file, overwrite = true) }
                        .onFailure { Timber.e(it, "Cannot roll back theme %s", file.name) }
                    error("Cannot replace theme file: ${file.name}")
                }
            }

            // A successful local save must not turn into a failed save just
            // because the optional external mirror could not be reached.
            afterWrite?.let { hook ->
                runCatching { hook(file) }
                    .onFailure { Timber.w(it, "Cannot mirror saved theme %s", file.name) }
            }
            writeGeneration++
            true
        } catch (e: Exception) {
            Timber.w(e, "Cannot write theme %s", file.name)
            false
        } finally {
            // If the target could not be restored, leave the complete new file
            // next to it rather than deleting the only recoverable copy.
            if (file.isFile) temp?.delete()
        }
    }

    fun backupOf(file: File): File = File(file.parentFile, file.name + ".bak")

    private fun tempOf(file: File): File = File(file.parentFile, file.name + ".tmp")

    private fun render(
        lines: List<String>,
        eol: String,
        endsWithNewline: Boolean,
    ): String {
        // `split` leaves a final empty element for a trailing newline. Adding
        // the postfix blindly would grow the file by one blank line per save.
        val body = if (endsWithNewline && lines.lastOrNull()?.isEmpty() == true) lines.dropLast(1) else lines
        return body.joinToString(eol) + if (endsWithNewline) eol else ""
    }
}
