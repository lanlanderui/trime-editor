/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.data.theme.ThemeLoader.ThemeLoadError
import com.osfans.trime.data.theme.ThemeLoader.ThemeLoadResult
import com.osfans.trime.editor.databinding.ActivityThemeEditorBinding
import com.osfans.trime.editor.ui.DiskBackedEditor
import com.osfans.trime.editor.ui.preview.ThemeBoard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * A theme editor screen: the keyboard it affects on top, a form for one part of
 * the theme below.
 *
 * Both editors -- style values and colour schemes -- need the same three steps
 * in the same order (read the file, make it the process's active theme, build
 * the board at the width it was given) and the same answer to "the theme could
 * not be read". Doing that once is what keeps the two screens from disagreeing
 * about what a preview is.
 *
 * The form is attached only after the theme loaded: its rows are built from the
 * same file the board draws, and an editor for a theme that will not load can
 * only produce a file the preview cannot show.
 */
abstract class ThemeEditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityThemeEditorBinding
    protected lateinit var board: ThemeBoard
    protected var themeId: String = ""
    protected var themeName: String = ""

    private var editorAttached = false

    /** The in-flight "did the file move" check; cancelled on pause. */
    private var staleCheck: Job? = null

    /** What this screen edits, shown under the theme name. */
    @get:StringRes
    protected abstract val subtitleRes: Int

    /** The form for this screen; called once, after the theme loaded. */
    protected abstract fun createEditor(): Fragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThemeEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        themeId = intent.getStringExtra(EXTRA_THEME_ID).orEmpty()
        themeName = intent.getStringExtra(EXTRA_THEME_NAME) ?: getString(R.string.app_name)
        supportActionBar?.title = themeName
        supportActionBar?.subtitle = getString(subtitleRes)
        if (themeId.isEmpty()) {
            finish()
            return
        }
        board = ThemeBoard.attach(binding.boardHost)
        binding.staleBanner.setOnClickListener { adoptFileFromDisk() }
        loadTheme()
    }

    /**
     * Takes the file the other app wrote and starts over from it.
     *
     * Both halves of the screen are rebuilt: the rows, and the keyboard above
     * them. The form asks the host to redo the board as part of the reload, so
     * this is not a second load — a screen that reloaded twice would parse the
     * same file twice and show the second result.
     */
    private fun adoptFileFromDisk() {
        val editor = currentEditor() ?: return
        binding.staleBanner.visibility = View.GONE
        editor.reloadFromDisk()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }

            R.id.action_back_to_ime -> {
                InputMethodApp.launch(this)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.editor, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // A phone without the keyboard has nothing to go back to; the item
        // would only ever answer "not installed".
        menu.findItem(R.id.action_back_to_ime)?.isVisible = InputMethodApp.isInstalled(this)
        return super.onPrepareOptionsMenu(menu)
    }

    /**
     * Re-checks the file every time the screen comes back to the front.
     *
     * The other app can be the one that wrote the file while this screen was
     * behind it, and neither the timestamp nor the size is trustworthy there
     * (a FUSE mount keeps the mtime of a replaced file). So the only sound
     * answer is to compare the bytes — off the main thread, once per resume.
     */
    override fun onResume() {
        super.onResume()
        checkFileChanged()
    }

    override fun onPause() {
        staleCheck?.cancel()
        staleCheck = null
        super.onPause()
    }

    private fun checkFileChanged() {
        val editor = currentEditor() ?: return
        staleCheck?.cancel()
        staleCheck =
            lifecycleScope.launch {
                val changed = withContext(Dispatchers.IO) { editor.isFileChanged() }
                if (isFinishing || isDestroyed) return@launch
                binding.staleBanner.visibility = if (changed) View.VISIBLE else View.GONE
            }
    }

    private fun currentEditor(): DiskBackedEditor? = supportFragmentManager.findFragmentById(R.id.settings) as? DiskBackedEditor

    /**
     * Re-reads the theme and redraws the board.
     *
     * Not a repaint: a saved value can move key sizes, heights and labels, and
     * those live in the `Keyboard` the board built. Only a fresh load picks them
     * up.
     */
    protected fun reloadTheme() {
        lifecycleScope.launch { board.load(themeId) }
    }

    private fun loadTheme() {
        lifecycleScope.launch {
            val result = board.load(themeId)
            if (isFinishing || isDestroyed) return@launch
            when (result) {
                is ThemeLoadResult.Success -> attachEditor()
                is ThemeLoadResult.Failure -> showFailure(themeId, result.error)
            }
        }
    }

    private fun attachEditor() {
        binding.boardHost.visibility = View.VISIBLE
        if (editorAttached) return
        editorAttached = true
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings, createEditor())
            .commit()
    }

    /**
     * Swaps the form for another one without reloading the theme.
     *
     * Used when the screen edits one item out of a list (one colour scheme, one
     * keyboard) and the user picks a different one: the theme itself did not
     * change, so re-reading it would only cost a pass over the file.
     */
    protected fun replaceEditor(fragment: Fragment) {
        editorAttached = true
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings, fragment)
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_THEME_ID = "theme_id"
        private const val EXTRA_THEME_NAME = "theme_name"

        /** The extras every editor screen reads. */
        fun putExtras(
            intent: Intent,
            themeId: String,
            themeName: String,
        ): Intent = intent
            .putExtra(EXTRA_THEME_ID, themeId)
            .putExtra(EXTRA_THEME_NAME, themeName)

        fun intentOf(
            context: Context,
            activity: Class<out ThemeEditorActivity>,
            themeId: String,
            themeName: String,
        ): Intent = Intent(context, activity).also { putExtras(it, themeId, themeName) }
    }
}
