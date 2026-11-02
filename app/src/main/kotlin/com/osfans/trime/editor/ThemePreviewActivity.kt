/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeDiagnostics
import com.osfans.trime.data.theme.ThemeLoader.ThemeLoadError
import com.osfans.trime.data.theme.ThemeLoader.ThemeLoadResult
import com.osfans.trime.editor.databinding.ActivityThemePreviewBinding
import com.osfans.trime.editor.ui.preview.ThemeBoard
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * A theme, drawn by the same code the keyboard uses.
 *
 * The board is not a mock and not a thumbnail: `KeyColorBoardView` builds a real
 * `Keyboard` at the width it is given and paints it with `KeyFaceRenderer`, so
 * what appears here is what the IME will draw — provided the width matches. That
 * is why this screen has no horizontal padding around the board.
 *
 * Only one theme can be drawn per process: `ColorManager` holds the active
 * scope, and every `Key` resolves its colours through it. So the screen attaches
 * the theme first and rebuilds on every scheme change.
 */
class ThemePreviewActivity : AppCompatActivity() {
    private lateinit var binding: ActivityThemePreviewBinding
    private lateinit var board: ThemeBoard

    private var themeId: String = ""
    private var themeName: String = ""
    private var theme: Theme? = null

    /** True once a theme has been drawn; a failed load must not offer editing. */
    private var hasLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThemePreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        themeId = intent.getStringExtra(EXTRA_THEME_ID).orEmpty()
        themeName = intent.getStringExtra(EXTRA_THEME_NAME) ?: getString(R.string.app_name)
        supportActionBar?.title = themeName
        if (themeId.isEmpty()) {
            finish()
            return
        }
        board = ThemeBoard.attach(binding.boardHost)
        loadTheme()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.preview, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_edit_style)?.isVisible = hasLoaded
        menu.findItem(R.id.action_edit_colors)?.isVisible = hasLoaded
        menu.findItem(R.id.action_back_to_ime)?.isVisible = InputMethodApp.isInstalled(this)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the style editor: the file may have been rewritten,
        // so the board is rebuilt from disk rather than assumed unchanged.
        if (hasLoaded) loadTheme()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }

            R.id.action_edit_style -> {
                startActivity(StyleEditorActivity.intent(this, themeId, themeName))
                return true
            }

            R.id.action_edit_colors -> {
                startActivity(ColorEditorActivity.intent(this, themeId, themeName))
                return true
            }

            R.id.action_edit_layout -> {
                startActivity(KeyboardListActivity.intent(this, themeId, themeName))
                return true
            }

            R.id.action_back_to_ime -> {
                InputMethodApp.launch(this)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadTheme() {
        lifecycleScope.launch {
            val result = board.load(themeId)
            if (isFinishing || isDestroyed) return@launch
            when (result) {
                is ThemeLoadResult.Success -> showTheme(result)
                is ThemeLoadResult.Failure -> showFailure(themeId, result.error)
            }
        }
    }

    private fun showTheme(success: ThemeLoadResult.Success) {
        val loaded = success.theme
        theme = loaded
        hasLoaded = true
        binding.boardHost.visibility = View.VISIBLE
        buildSchemeChips(loaded)
        binding.detail.text = findingsText(success.findings)
        invalidateOptionsMenu()
    }

    private fun showFailure(
        themeId: String,
        error: ThemeLoadError,
    ) {
        Timber.w("Theme %s failed to load: %s", themeId, error.message)
        hasLoaded = false
        binding.boardHost.visibility = View.GONE
        binding.schemeBar.visibility = View.GONE
        binding.detail.text = buildString {
            append(getString(R.string.preview_load_failed))
            append("\n\n")
            append(error.message.orEmpty())
        }
        invalidateOptionsMenu()
    }

    private fun buildSchemeChips(loaded: Theme) {
        val ids = loaded.presetColorSchemes.keys
        if (ids.isEmpty()) {
            binding.schemeBar.visibility = View.GONE
            return
        }
        binding.schemeBar.visibility = View.VISIBLE
        val container = binding.schemes
        container.removeAllViews()
        // `default` first, then the rest in the order the file declares them:
        // this is the light/dark pair most themes ship.
        val ordered = listOf(DEFAULT_SCHEME).filter { it in ids } + ids.filter { it != DEFAULT_SCHEME }
        addChip(container, getString(R.string.preview_scheme_follow_system), followSystem = true)
        ordered.forEach { addChip(container, it, followSystem = false) }
        refreshChipState()
    }

    private fun addChip(
        container: LinearLayout,
        label: String,
        followSystem: Boolean,
    ) {
        val chip =
            TextView(this).apply {
                text = label
                textSize = 13f
                setPadding(dp(14), dp(6), dp(14), dp(6))
                setOnClickListener {
                    if (followSystem) {
                        EditorPrefs.followSystemDayNight = true
                        // Re-attaching resolves the scheme from the system again.
                        theme?.let { ColorManager.attachTheme(it) }
                    } else {
                        // Forcing one scheme: stop following the system, or the
                        // resolver would immediately override the choice.
                        EditorPrefs.followSystemDayNight = false
                        ColorManager.setColorScheme(label)
                    }
                    refreshChipState()
                    board.refresh()
                }
                tag = if (followSystem) FOLLOW_SYSTEM_TAG else label
            }
        val params =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        params.marginEnd = dp(8)
        container.addView(chip, params)
    }

    /**
     * Marks the chip the colours are actually coming from.
     *
     * Resolution, not the stored preference, decides: with "follow system" set
     * the resolver decides which of the theme's schemes is in use, so the chip
     * that lights up has to be read back from the active scheme.
     */
    private fun refreshChipState() {
        val active = runCatching { ColorManager.activeColorScheme }.getOrNull()
        val following = EditorPrefs.followSystemDayNight
        // The resolver hands back one of the theme's own scheme maps, so the id
        // in use is whichever entry compares equal to it.
        val activeId = theme?.presetColorSchemes?.entries?.firstOrNull { it.value == active }?.key
        binding.schemes.children().forEach { chip ->
            val view = chip as TextView
            val selected =
                if (view.tag == FOLLOW_SYSTEM_TAG) {
                    following
                } else {
                    !following && view.tag == activeId
                }
            view.setBackgroundResource(if (selected) R.drawable.bg_chip_selected else R.drawable.bg_chip)
            view.setTextColor(ContextCompat.getColor(this, if (selected) R.color.chip_text_selected else R.color.chip_text))
        }
    }

    private fun findingsText(findings: List<ThemeDiagnostics.Finding>?): String {
        if (findings == null) return getString(R.string.preview_findings_unknown)
        if (findings.isEmpty()) return getString(R.string.preview_findings_clean)
        return buildString {
            append(getString(R.string.preview_findings, findings.size))
            findings.take(MAX_FINDINGS).forEach { finding ->
                append("\n\n")
                append(finding.severity.name)
                append("  ")
                append(finding.path.orEmpty())
                append("\n")
                append(finding.message)
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun android.view.ViewGroup.children(): Sequence<View> = (0 until childCount).asSequence().map { getChildAt(it) }

    companion object {
        private const val EXTRA_THEME_ID = "theme_id"
        private const val EXTRA_THEME_NAME = "theme_name"
        private const val DEFAULT_SCHEME = "default"
        private const val FOLLOW_SYSTEM_TAG = "\u0000system"
        private const val MAX_FINDINGS = 12

        fun intent(
            context: Context,
            themeId: String,
            themeName: String,
        ): Intent = Intent(context, ThemePreviewActivity::class.java)
            .putExtra(EXTRA_THEME_ID, themeId)
            .putExtra(EXTRA_THEME_NAME, themeName)
    }
}
