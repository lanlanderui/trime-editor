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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.data.theme.ThemeDiagnostics
import com.osfans.trime.data.theme.ThemeLoader
import com.osfans.trime.editor.databinding.ActivityDiagnosticsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Everything the theme linter found, grouped by how much it matters.
 *
 * The check itself is not new — the preview page has been running it and dumping
 * the findings into a text block. What this screen adds is the only thing that
 * makes a finding useful: a way to the place that causes it. A path like
 * `preset_keys/Keyboard_letter` is useless on its own; the row it belongs to is
 * one tap away.
 */
class DiagnosticsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDiagnosticsBinding

    private var themeId: String = ""
    private var themeName: String = ""
    private var findings: List<ThemeDiagnostics.Finding>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiagnosticsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        themeId = intent.getStringExtra(EXTRA_THEME_ID).orEmpty()
        themeName = intent.getStringExtra(EXTRA_THEME_NAME).orEmpty()
        supportActionBar?.title = themeName.ifEmpty { getString(R.string.app_name) }
        supportActionBar?.subtitle = getString(R.string.dg_title)
        if (themeId.isEmpty()) {
            finish()
            return
        }
        load()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.editor, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_back_to_ime)?.isVisible = InputMethodApp.isInstalled(this)
        return super.onPrepareOptionsMenu(menu)
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

    private fun load() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                when (val result = ThemeLoader.loadTheme(themeId)) {
                    is ThemeLoader.ThemeLoadResult.Success -> result.findings
                    else -> null
                }
            }
            if (isFinishing || isDestroyed) return@launch
            findings = loaded
            render()
        }
    }

    private fun render() {
        val all = findings
        binding.summary.text =
            when {
                all == null -> getString(R.string.preview_findings_unknown)

                all.isEmpty() -> getString(R.string.dg_clean)

                else -> {
                    val errors = all.count { it.severity == ThemeDiagnostics.Severity.ERROR }
                    val warnings = all.count { it.severity == ThemeDiagnostics.Severity.WARNING }
                    getString(R.string.dg_summary, all.size, errors, warnings)
                }
            }

        val column =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(4), dp(20), dp(24))
            }
        val scroll = ScrollView(this).apply { addView(column) }
        binding.content.removeAllViews()
        binding.content.addView(
            scroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT),
        )

        if (all.isNullOrEmpty()) {
            column.addView(text(getString(R.string.dg_clean), secondary = true))
            return
        }

        // Errors first: the list is read top-down and the first rows should be the
        // ones worth acting on.
        all.sortedBy { it.severity.ordinal }.forEach { finding ->
            column.addView(row(finding))
        }
    }

    private fun row(finding: ThemeDiagnostics.Finding): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(10), 0, dp(10))
        isClickable = true
        setOnClickListener { jump(finding) }

        addView(
            TextView(context).apply {
                text = severityLabel(finding.severity)
                textSize = 11f
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (finding.severity == ThemeDiagnostics.Severity.ERROR) R.color.warn else R.color.chip_text,
                    ),
                )
            },
        )
        addView(
            TextView(context).apply {
                text = finding.path.orEmpty()
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.chip_text))
                visibility = if (finding.path.isNullOrEmpty()) View.GONE else View.VISIBLE
            },
        )
        addView(
            TextView(context).apply {
                text = finding.message
                textSize = 13f
                setLineSpacing(0f, 1.2f)
            },
        )
    }

    private fun text(
        value: String,
        secondary: Boolean,
    ): TextView = TextView(this).apply {
        text = value
        textSize = 13f
        setTextColor(ContextCompat.getColor(context, R.color.chip_text))
        if (secondary) setPadding(0, dp(6), 0, dp(6))
    }

    private fun severityLabel(severity: ThemeDiagnostics.Severity): String = when (severity) {
        ThemeDiagnostics.Severity.ERROR -> getString(R.string.dg_severity_error)
        ThemeDiagnostics.Severity.WARNING -> getString(R.string.dg_severity_warning)
        else -> getString(R.string.dg_severity_info)
    }

    /**
     * Opens the screen that owns [finding], via [DiagnosticsRouter].
     *
     * The `when` has no `else`: [EditorScreen] lists the screens that exist, so
     * a new one added without an intent here stops the build rather than
     * turning its rows into taps that do nothing.
     */
    private fun jump(finding: ThemeDiagnostics.Finding) {
        val route = DiagnosticsRouter.route(finding.path)
        val intent =
            when (route.screen) {
                EditorScreen.STYLE -> StyleEditorActivity.intent(this, themeId, themeName)

                EditorScreen.COLORS -> ColorEditorActivity.intent(this, themeId, themeName)

                EditorScreen.KEYBOARD_LIST -> KeyboardListActivity.intent(this, themeId, themeName)

                EditorScreen.KEYBOARD_EDITOR ->
                    KeyboardEditorActivity.intent(this, themeId, themeName, route.id, route.id)

                EditorScreen.PRESET_KEYS ->
                    PresetKeyListActivity.intent(this, themeId, themeName, query = route.id)

                EditorScreen.TOOL_BAR -> ToolBarActivity.intent(this, themeId, themeName)

                EditorScreen.CANDIDATE_WINDOW -> CandidateWindowActivity.intent(this, themeId, themeName)

                EditorScreen.NONE -> null
            }
        if (intent == null) {
            Toast.makeText(this, R.string.dg_no_jump, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(intent)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_THEME_ID = "theme_id"
        private const val EXTRA_THEME_NAME = "theme_name"

        fun intent(
            context: Context,
            themeId: String,
            themeName: String,
        ): Intent = Intent(context, DiagnosticsActivity::class.java)
            .putExtra(EXTRA_THEME_ID, themeId)
            .putExtra(EXTRA_THEME_NAME, themeName)
    }
}
