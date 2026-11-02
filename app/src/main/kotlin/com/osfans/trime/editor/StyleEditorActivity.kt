/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.data.theme.ThemeLoader.ThemeLoadError
import com.osfans.trime.data.theme.ThemeLoader.ThemeLoadResult
import com.osfans.trime.editor.databinding.ActivityStyleEditorBinding
import com.osfans.trime.editor.ui.preview.ThemeBoard
import com.osfans.trime.editor.ui.style.StyleEditorFragment
import com.osfans.trime.editor.ui.style.StyleEditorHost
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The style form with the keyboard it affects directly above it.
 *
 * Both on one screen on purpose: a style field is a number in a file until the
 * keyboard is rebuilt from it, so "saved" and "visible" have to be the same
 * moment. Every save therefore reloads the theme and redraws the board, which
 * is also the only honest way to see that `key_height` did what it says.
 *
 * The board is [ThemeBoard], the same helper the read-only preview uses, so the
 * two screens cannot drift in how they load, activate and size a theme.
 */
class StyleEditorActivity :
    AppCompatActivity(),
    StyleEditorHost {
    private lateinit var binding: ActivityStyleEditorBinding
    private lateinit var board: ThemeBoard
    private var themeId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStyleEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = intent.getStringExtra(EXTRA_THEME_NAME) ?: getString(R.string.app_name)
        supportActionBar?.subtitle = getString(R.string.style_editor_title)

        themeId = intent.getStringExtra(EXTRA_THEME_ID).orEmpty()
        if (themeId.isEmpty()) {
            finish()
            return
        }
        board = ThemeBoard.attach(binding.boardHost)
        loadTheme()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Reads the theme and attaches the form.
     *
     * The form is attached only when the theme could be read: its rows are
     * built from the same file the board draws, so a theme that failed to load
     * would give an editor that writes into a document the preview cannot show.
     */
    private fun loadTheme() {
        lifecycleScope.launch {
            val result = board.load(themeId)
            if (isFinishing || isDestroyed) return@launch
            when (result) {
                is ThemeLoadResult.Success -> attachSettings()
                is ThemeLoadResult.Failure -> showFailure(themeId, result.error)
            }
        }
    }

    private fun attachSettings() {
        binding.boardHost.visibility = View.VISIBLE
        if (supportFragmentManager.findFragmentById(R.id.settings) != null) return
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings, StyleEditorFragment.newInstance(themeId))
            .commit()
    }

    private fun showFailure(
        themeId: String,
        error: ThemeLoadError,
    ) {
        Timber.w("Theme %s failed to load for editing: %s", themeId, error.message)
        binding.boardHost.visibility = View.GONE
        val message =
            TextView(this).apply {
                text =
                    buildString {
                        append(getString(R.string.preview_load_failed))
                        append("\n\n")
                        append(error.message.orEmpty())
                    }
                setPadding(dp(20), dp(20), dp(20), dp(20))
            }
        binding.settings.addView(
            message,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    /**
     * Re-reads the theme and redraws the board.
     *
     * Not a repaint: a style change can move key sizes, heights and labels, and
     * those live in the `Keyboard` the board built. Only a fresh load picks them
     * up — the same reason the colour scheme switch *is* just a repaint.
     */
    override fun reloadBoard() {
        lifecycleScope.launch { board.load(themeId) }
    }

    /** Colours are resolved while drawing, so the board only needs a repaint. */
    override fun applyScheme(schemeId: String) {
        board.refresh()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_THEME_ID = "theme_id"
        private const val EXTRA_THEME_NAME = "theme_name"

        fun intent(
            context: Context,
            themeId: String,
            themeName: String,
        ): Intent = Intent(context, StyleEditorActivity::class.java)
            .putExtra(EXTRA_THEME_ID, themeId)
            .putExtra(EXTRA_THEME_NAME, themeName)
    }
}
