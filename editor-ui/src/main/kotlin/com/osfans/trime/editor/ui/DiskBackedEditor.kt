/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.ui

/**
 * A form built from a theme file that another app can change underneath it.
 *
 * The keyboard and the editor are separate apps editing the same file, so
 * "the file moved" is the normal case, not an error. A form that quietly kept
 * its old rows would then write over the other app's change on the next tap —
 * which the writer would report as a conflict, leaving the user with a dialog
 * and no idea what happened.
 *
 * So each form answers two questions: whether the file still holds the text it
 * was built from, and how to start over from the file as it is now. The screen
 * that hosts the form shows the banner and calls [reloadFromDisk] when tapped;
 * nothing reloads on its own, because a form that rebuilds while the user is
 * mid-edit loses the edit.
 */
interface DiskBackedEditor {
    /** Whether the file on disk no longer holds the text this form was built from. */
    fun isFileChanged(): Boolean

    /** Rebuilds the form, and its conflict baseline, from the file as it is now. */
    fun reloadFromDisk()
}
