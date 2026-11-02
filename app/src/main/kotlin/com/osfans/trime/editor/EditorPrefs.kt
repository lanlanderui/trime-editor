/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor

import android.content.Context
import android.content.SharedPreferences
import com.osfans.trime.data.theme.ThemePreferenceStore
import java.io.File

/**
 * The editor's own settings, which are also the library's four theme prefs.
 *
 * The two are the same object on purpose: `ColorManager` resolves the active
 * scheme through `ThemeManager.prefs`, and the editor's preview has to be able
 * to say "show me this scheme" without a second, competing store. Implementing
 * the library interface here means there is exactly one answer to "which
 * scheme is the preview using".
 *
 * The directory is stored by absolute path rather than as a tree `Uri`: every
 * writer in `:theme-core` edits files by `File`, so a SAF picker would produce
 * a path this app cannot open.
 */
object EditorPrefs : ThemePreferenceStore {
    private const val STORE = "editor"
    private const val KEY_THEME_DIR = "theme_dir"
    private const val KEY_SELECTED_THEME = "selected_theme"
    private const val KEY_SCHEME = "normal_mode_color"
    private const val KEY_FOLLOW_SYSTEM = "follow_system_day_night"
    private const val KEY_GUIDE_SEEN = "guide_seen"

    private const val DEFAULT_SCHEME = "default"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun install(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        }
    }

    private fun requirePrefs(): SharedPreferences = requireNotNull(prefs) { "EditorPrefs.install() has not been called" }

    /**
     * The directory the editor reads and writes themes in.
     *
     * Re-read on every access rather than captured once: a theme directory can
     * be changed while the process lives, and a copy taken at startup would
     * keep resolving files from the old one.
     */
    val themeDir: File?
        get() = externalThemeDir.takeIf { it.isNotBlank() }?.let(::File)

    fun setThemeDir(dir: File?) {
        externalThemeDir = dir?.absolutePath.orEmpty()
    }

    override var externalThemeDir: String
        get() = requirePrefs().getString(KEY_THEME_DIR, "").orEmpty()
        set(value) = requirePrefs().edit().putString(KEY_THEME_DIR, value).apply()

    override var selectedTheme: String
        get() = requirePrefs().getString(KEY_SELECTED_THEME, "").orEmpty()
        set(value) = requirePrefs().edit().putString(KEY_SELECTED_THEME, value).apply()

    override var normalModeColor: String
        get() = requirePrefs().getString(KEY_SCHEME, DEFAULT_SCHEME).orEmpty()
        set(value) = requirePrefs().edit().putString(KEY_SCHEME, value).apply()

    override var followSystemDayNight: Boolean
        get() = requirePrefs().getBoolean(KEY_FOLLOW_SYSTEM, false)
        set(value) = requirePrefs().edit().putBoolean(KEY_FOLLOW_SYSTEM, value).apply()

    /**
     * Whether the three-step introduction has been read.
     *
     * Not a `ThemePreferenceStore` member: it says nothing about the theme, only
     * that this device has seen the explanation once. Kept in the same store
     * because a second preferences file for one boolean is worse.
     */
    var guideSeen: Boolean
        get() = requirePrefs().getBoolean(KEY_GUIDE_SEEN, false)
        set(value) = requirePrefs().edit().putBoolean(KEY_GUIDE_SEEN, value).apply()
}
