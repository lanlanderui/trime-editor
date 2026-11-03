/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor

import android.Manifest
import android.content.ActivityNotFoundException
import android.graphics.Typeface
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.osfans.trime.editor.databinding.ActivityMainBinding
import com.osfans.trime.editor.workbench.DirectoryPickerDialog
import com.osfans.trime.editor.workbench.StorageAccess
import com.osfans.trime.editor.workbench.ThemeCardAdapter
import com.osfans.trime.editor.workbench.ThemeEntry
import com.osfans.trime.editor.workbench.readThemes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The workbench: which directory the editor works in, and what is in it.
 *
 * Everything here is read-only. The screens that write come later, and they
 * will be opened from a row — the point of this screen is that a theme can be
 * looked at before anything is changed.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val adapter = ThemeCardAdapter(::openTheme)

    private var loadJob: Job? = null

    /** Coming back from the "all files access" screen: re-check, then load. */
    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        setSupportActionBar(binding.toolbar)

        binding.themes.layoutManager = LinearLayoutManager(this)
        binding.themes.adapter = adapter
        binding.themes.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        binding.changeDir.setOnClickListener { chooseDirectory() }
        FirstRunGuide.showIfFirstRun(this)
    }

    /**
     * Says what happened last time, once per start.
     *
     * There is nothing this app restores on launch — no last-opened theme, no
     * unsaved draft — so "safe mode" has nothing to skip. What is worth doing is
     * the part a user cannot get anywhere else: naming the crash and offering the
     * log, instead of letting the app disappear quietly.
     */
    private fun reportLastCrash() {
        if (!CrashLog.hasReport(this)) return
        AlertDialog.Builder(this)
            .setTitle(R.string.crash_title)
            .setMessage(R.string.crash_body)
            .setNeutralButton(R.string.crash_view) { _, _ -> showCrashLog() }
            .setNegativeButton(R.string.crash_later, null)
            .setPositiveButton(R.string.crash_clear) { _, _ -> CrashLog.clear(this) }
            .show()
    }

    private fun showCrashLog() {
        val body = CrashLog.read(this) ?: return
        val view =
            TextView(this).apply {
                text = body
                textSize = 11f
                setPadding(dp(16), dp(8), dp(16), dp(8))
                typeface = Typeface.MONOSPACE
            }
        val scroll = ScrollView(this).apply { addView(view) }
        AlertDialog.Builder(this)
            .setTitle(R.string.crash_log_title)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(R.string.crash_clear) { _, _ -> CrashLog.clear(this) }
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_back_to_ime)?.isVisible = InputMethodApp.isInstalled(this)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_refresh -> refresh()
            R.id.action_back_to_ime -> InputMethodApp.launch(this)
            R.id.action_help -> FirstRunGuide.show(this)
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    /** Access can be granted while the app is in the background, so re-check here. */
    override fun onStart() {
        super.onStart()
        refresh()
    }

    override fun onStop() {
        loadJob?.cancel()
        loadJob = null
        super.onStop()
    }

    private fun refresh() {
        loadJob?.cancel()
        loadJob = null
        if (!StorageAccess.hasAccess(this)) {
            showPlaceholder(
                title = getString(R.string.state_no_access_title),
                body = getString(R.string.state_no_access_body),
                action = getString(R.string.action_grant),
            ) { requestAccess() }
            return
        }
        val dir = EditorPrefs.themeDir
        binding.path.text = dir?.absolutePath ?: getString(R.string.dir_unset)
        if (dir == null) {
            showPlaceholder(
                title = getString(R.string.state_no_dir_title),
                body = getString(R.string.state_no_dir_body),
                action = getString(R.string.action_pick_dir),
            ) { chooseDirectory() }
            return
        }
        loadJob =
            lifecycleScope.launch {
                val entries = withContext(Dispatchers.IO) { readThemes(dir) }
                if (isFinishing || isDestroyed) return@launch
                adapter.submit(entries)
                if (entries.isEmpty()) {
                    showPlaceholder(
                        title = getString(R.string.state_empty_title),
                        body = getString(R.string.state_empty_body),
                        action = getString(R.string.action_change_dir),
                    ) { chooseDirectory() }
                } else {
                    showList()
                }
            }
    }

    private fun openTheme(entry: ThemeEntry) {
        startActivity(ThemePreviewActivity.intent(this, entry.id, entry.name))
    }

    private fun chooseDirectory() {
        DirectoryPickerDialog(this, EditorPrefs.themeDir) { dir ->
            EditorPrefs.setThemeDir(dir)
            refresh()
        }.show()
    }

    private fun requestAccess() {
        val settings = StorageAccess.settingsIntent(this)
        if (settings != null) {
            // Whether the app-specific screen exists is only knowable by
            // launching it; the global list is the fallback.
            try {
                settingsLauncher.launch(settings)
            } catch (_: ActivityNotFoundException) {
                try {
                    settingsLauncher.launch(StorageAccess.fallbackSettingsIntent())
                } catch (_: ActivityNotFoundException) {
                    askRuntimePermission()
                }
            }
        } else {
            askRuntimePermission()
        }
    }

    private fun askRuntimePermission() {
        if (StorageAccess.asksRuntimePermission()) {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun showList() {
        binding.themes.visibility = View.VISIBLE
        binding.placeholder.visibility = View.GONE
    }

    private fun showPlaceholder(
        title: String,
        body: String,
        action: String,
        onAction: () -> Unit,
    ) {
        binding.themes.visibility = View.GONE
        binding.placeholder.visibility = View.VISIBLE
        binding.placeholderTitle.text = title
        binding.placeholderBody.text = body
        binding.placeholderAction.text = action
        binding.placeholderAction.setOnClickListener { onAction() }
    }
}
