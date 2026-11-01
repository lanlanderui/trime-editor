/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import java.io.File

/**
 * A file a theme load read, in the state the load found it.
 *
 * [resourceId] is the id it was resolved for, or null when the load was handed
 * an explicit file. Keeping the id lets the snapshot notice that a later user
 * copy now shadows the shared file the load actually read.
 */
data class ThemeFileStamp(
    val resourceId: String?,
    val path: String,
    val stamp: Long,
    val size: Long,
) {
    fun isCurrent(resolve: (String) -> File?): Boolean {
        val file = File(path)
        if (file.lastModified() != stamp || file.length() != size) return false
        if (resourceId == null) return true
        return resolve(resourceId)?.absolutePath == path
    }

    companion object {
        /** Stamps [file] as it is right now; call before reading it. */
        fun of(
            file: File,
            resourceId: String? = null,
        ) = ThemeFileStamp(resourceId, file.absolutePath, file.lastModified(), file.length())
    }
}

/**
 * Everything a theme load depended on, so a cached theme can be told apart
 * from a still-valid one.
 *
 * A theme is not a single file: `__include` pulls in other resources and
 * `<id>.custom.yaml` patches the result. Keying the cache on the root file's
 * timestamp alone serves a stale theme as soon as an included or custom file
 * changes — including the common case of fixing an include from outside.
 */
data class ThemeSourceSnapshot(
    /** Files read, stamped before each read. */
    val files: List<ThemeFileStamp> = emptyList(),
    /** Resource ids looked for and not found; a file appearing later changes the theme. */
    val absent: List<String> = emptyList(),
) {
    fun isCurrent(resolve: (String) -> File?): Boolean {
        if (!files.all { it.isCurrent(resolve) }) return false
        return absent.none { resolve(it) != null }
    }

    /**
     * Combines two snapshots of the same load — the source files it tried and
     * the deployed artifact it ended up using. Both can change independently,
     * and either change means the cached theme is stale.
     */
    fun merge(other: ThemeSourceSnapshot): ThemeSourceSnapshot {
        val paths = files.mapTo(HashSet()) { it.path }
        return ThemeSourceSnapshot(
            files = files + other.files.filterNot { it.path in paths },
            absent = (absent + other.absent).distinct(),
        )
    }
}
