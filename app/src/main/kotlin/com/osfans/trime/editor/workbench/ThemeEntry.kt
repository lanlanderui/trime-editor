/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.workbench

import androidx.annotation.ColorInt
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeFilesManager
import com.osfans.trime.data.theme.ThemeLoader
import com.osfans.trime.data.theme.ThemeLoader.ThemeLoadResult
import com.osfans.trime.util.ColorUtils
import java.io.File

/**
 * One row of the workbench: what a theme file is called, and what happened when
 * it was read.
 *
 * Both halves are kept because both are useful before anything is opened: a
 * theme that fails to load is still a file the user may want to fix, and the
 * reason it failed is more useful on the row than behind a tap.
 */
data class ThemeEntry(
    val id: String,
    /** The `name:` the theme declares, or its file name when it declares none. */
    val name: String,
    val result: ThemeLoadResult,
) {
    val theme: Theme? get() = (result as? ThemeLoadResult.Success)?.theme

    /** Why this theme could not be read, in the loader's own words. */
    val failure: String? get() = (result as? ThemeLoadResult.Failure)?.error?.message

    /**
     * A few of the theme's own colours, in the order the keyboard shows them.
     *
     * Read from the scheme map as text, not through `ColorManager`: that is a
     * process-wide singleton over *one* theme, so asking it for a band of
     * colours per row would paint every row with the last theme loaded.
     */
    val swatches: List<Int> get() = theme?.let(::swatchesOf).orEmpty()

    private companion object {
        val SWATCH_KEYS =
            listOf(
                "keyboard_back_color",
                "key_back_color",
                "key_text_color",
                "hilited_key_back_color",
                "hilited_key_text_color",
            )

        const val DEFAULT_SCHEME = "default"

        fun swatchesOf(theme: Theme): List<Int> {
            val schemes = theme.presetColorSchemes
            val scheme = schemes[DEFAULT_SCHEME] ?: schemes.values.firstOrNull() ?: return emptyList()
            return SWATCH_KEYS
                .mapNotNull { key -> scheme[key]?.let(::parseOrNull) }
                .ifEmpty { scheme.values.mapNotNull(::parseOrNull).take(SWATCH_KEYS.size) }
        }

        @ColorInt
        fun parseOrNull(value: String): Int? = runCatching { ColorUtils.parseColor(value) }.getOrNull()
    }
}

/**
 * Reads every theme in [dir], newest file first.
 *
 * Loading happens here rather than in the adapter because it is file I/O plus a
 * YAML parse per theme; the caller runs it off the main thread and hands the
 * finished list over in one go.
 */
fun readThemes(dir: File?): List<ThemeEntry> {
    if (dir == null) return emptyList()
    return ThemeFilesManager.listThemes(dir).map { item ->
        ThemeEntry(item.configId, item.name, ThemeLoader.loadTheme(item.configId))
    }
}
