/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import timber.log.Timber

/**
 * The keyboard app this editor writes themes for.
 *
 * The editor and the keyboard are separate apps sharing one theme directory, so
 * every round trip — change a colour, look at the real keyboard, come back and
 * change another — means switching apps by hand. "回到输入法" does that switch,
 * which is the only way the loop can be short enough to be worth doing.
 *
 * The package is looked up from the system's own list of enabled input methods
 * rather than hardcoded, because two builds of the same keyboard can be
 * installed side by side (`com.osfans.trime03` and its `.debug` twin) and the
 * one that matters is the one the system would actually show.
 */
object InputMethodApp {
    /** Release and debug application ids of the keyboard, most likely first. */
    private val KNOWN_PACKAGES = listOf("com.osfans.trime03", "com.osfans.trime03.debug")

    /**
     * The keyboard package the system would show, or null when it is not one of
     * [KNOWN_PACKAGES].
     *
     * The default input method wins over merely-enabled ones: with both builds
     * installed and enabled, the user still only ever sees one of them.
     *
     * Read through `InputMethodManager` rather than `Settings.Secure`: from
     * Android 15 on, `enabled_input_methods` is only readable by apps targeting
     * API 33 or lower, and reading it throws instead of returning null.
     */
    fun enabledPackage(context: Context): String? {
        val enabled =
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.enabledInputMethodList
                ?.map { it.packageName }
                ?.distinct()
                ?.filter { it in KNOWN_PACKAGES }
                .orEmpty()
        if (enabled.isEmpty()) return null
        // Best effort: the default is still a Secure setting, and a build that
        // refuses to say which one is in use is not an error here.
        val default =
            runCatching { Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) }
                .getOrNull()
                ?.substringBefore('/')
        return enabled.firstOrNull { it == default } ?: enabled.first()
    }

    /**
     * Whether the keyboard app can be opened at all.
     *
     * Checked before the menu item is shown: an item that only ever reports
     * "not installed" is noise on a phone that does not have the keyboard.
     */
    fun isInstalled(context: Context): Boolean = packageToOpen(context) != null

    /** Opens the keyboard app; false when there is nothing to open. */
    fun launch(context: Context): Boolean {
        val pkg = packageToOpen(context)
        if (pkg == null) {
            Toast.makeText(context, R.string.back_to_ime_missing, Toast.LENGTH_LONG).show()
            return false
        }
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            Timber.w("Input method %s has no launcher activity", pkg)
            Toast.makeText(context, R.string.back_to_ime_missing, Toast.LENGTH_LONG).show()
            return false
        }
        // The editor stays in the back stack: the point of the trip is to come
        // straight back and change one more thing.
        return runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrElse {
            Timber.w(it, "Cannot open input method %s", pkg)
            Toast.makeText(context, R.string.back_to_ime_missing, Toast.LENGTH_LONG).show()
            false
        }
    }

    /** The enabled keyboard if it can be opened, otherwise any installed one. */
    private fun packageToOpen(context: Context): String? {
        val enabled = enabledPackage(context)
        if (enabled != null && context.packageManager.getLaunchIntentForPackage(enabled) != null) return enabled
        return KNOWN_PACKAGES.firstOrNull { context.packageManager.getLaunchIntentForPackage(it) != null }
    }
}
