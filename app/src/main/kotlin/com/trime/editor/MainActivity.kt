/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.trime.editor

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.trime.editor.databinding.ActivityMainBinding
import com.trime.editor.ui.AppearanceActivity
import com.trime.editor.ui.EditorActivity
import com.trime.editor.ui.skin.UiMode
import com.trime.editor.workbench.DirectoryPickerDialog
import com.trime.editor.workbench.StorageAccess
import com.trime.editor.workbench.ThemeCardAdapter
import com.trime.editor.workbench.ThemeEntry
import com.trime.editor.workbench.readThemes
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
class MainActivity : EditorActivity() {
    private lateinit var binding: ActivityMainBinding

    private val adapter = ThemeCardAdapter(::openTheme)

    /** 英雄区当前展示的那一份；英雄区自己也要能点进编辑，所以得留住它。 */
    private var currentHero: ThemeEntry? = null

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
        applyContentWidth()
        setSupportActionBar(binding.toolbar)

        // 舞台皮肤没有顶栏：导航交给英雄区里的 ⋯ 和底部 dock（设计稿 B1）。
        binding.toolbar.visibility = if (UiMode.caps.stageHeader) View.GONE else View.VISIBLE
        binding.heroSwatches.cornerRadiusPx = dp(10).toFloat()
        binding.themes.layoutManager = LinearLayoutManager(this)
        binding.themes.adapter = adapter
        binding.changeDir.setOnClickListener { chooseDirectory() }
        binding.dockDir.setOnClickListener { chooseDirectory() }
        binding.dockRefresh.setOnClickListener { refresh() }
        binding.dockAppearance.setOnClickListener { startActivity(Intent(this, AppearanceActivity::class.java)) }
        binding.dockIme.setOnClickListener { InputMethodApp.launch(this) }
        binding.heroMenu.setOnClickListener { showHeroMenu(it) }
        // 英雄区是这一页的主语，点它就要能进编辑；原来只有下面列表的行能点，
        // 而页面提示写的却是「点击主题进入编辑」——主语自己点不动。
        binding.heroBlock.setOnClickListener { currentHero?.let(::openTheme) }
        // 崩溃提示优先于首启引导：它解释「上次为什么没了」，引导讲「怎么用」。
        // 两者同时需要，只有「第一次启动就崩过」这一种情况，那时先解释崩溃。
        if (!reportLastCrash()) FirstRunGuide.showIfFirstRun(this)
    }

    /**
     * Says what happened last time, once per start.
     *
     * There is nothing this app restores on launch — no last-opened theme, no
     * unsaved draft — so "safe mode" has nothing to skip. What is worth doing is
     * the part a user cannot get anywhere else: naming the crash and offering the
     * log, instead of letting the app disappear quietly.
     *
     * @return whether a dialog was shown; the caller uses it to avoid stacking
     *   this on top of the first-run guide.
     */
    private fun reportLastCrash(): Boolean {
        if (!CrashLog.hasReport(this)) return false
        AlertDialog.Builder(this)
            .setTitle(R.string.crash_title)
            .setMessage(R.string.crash_body)
            .setNeutralButton(R.string.crash_view) { _, _ -> showCrashLog() }
            .setNegativeButton(R.string.crash_later, null)
            .setPositiveButton(R.string.crash_clear) { _, _ -> CrashLog.clear(this) }
            .show()
        return true
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

    /**
     * 平板上的内容列限宽。
     *
     * 848dp 一行铺满，卡片会被拉成三条横杠，M3 的「卡片」概念就不成立了。
     * 限到 720dp 并居中，和「外观」页用的是同一个尺子。
     */
    private fun applyContentWidth() {
        val side = ((resources.displayMetrics.widthPixels - dp(720)) / 2).coerceAtLeast(0)
        if (side == 0) return
        binding.content.setPadding(side, 0, side, binding.content.paddingBottom)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        OutlineMenu.addTo(menu, this)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_back_to_ime)?.isVisible = InputMethodApp.isInstalled(this)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (OutlineMenu.handle(this, item, "", "")) return true
        when (item.itemId) {
            R.id.action_refresh -> refresh()
            R.id.action_back_to_ime -> InputMethodApp.launch(this)
            R.id.action_appearance -> startActivity(Intent(this, AppearanceActivity::class.java))
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
                present(entries)
            }
    }

    /**
     * 把「当前主题」放上英雄区，其余的交给列表。
     *
     * 当前主题若不在目录里（换过目录、文件被删），退回第一份——英雄区不能空着，
     * 它是这一页的主语；退回时标题仍然是那份主题自己的名字，不假装是「当前」。
     */
    private fun present(entries: List<ThemeEntry>) {
        if (entries.isEmpty()) {
            currentHero = null
            adapter.submit(emptyList())
            showPlaceholder(
                title = getString(R.string.state_empty_title),
                body = getString(R.string.state_empty_body),
                action = getString(R.string.action_change_dir),
            ) { chooseDirectory() }
            return
        }
        val hero = entries.firstOrNull { it.id == EditorPrefs.selectedTheme } ?: entries.first()
        currentHero = hero
        val others = entries.filter { it.id != hero.id }
        binding.heroName.text = hero.name
        binding.heroMeta.text = hero.id
        binding.heroSwatches.setColors(hero.swatches.take(5))
        binding.restLabel.text =
            if (others.isEmpty()) {
                getString(R.string.hero_no_other_theme)
            } else {
                getString(R.string.hero_other_themes, others.size)
            }
        adapter.submit(others)
        showList()
    }

    /** 舞台没有 ActionBar，⋯ 用 PopupMenu 打开同一份菜单，功能不丢。 */
    private fun showHeroMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.main, popup.menu)
        OutlineMenu.addTo(popup.menu, this)
        popup.menu.findItem(R.id.action_back_to_ime)?.isVisible = InputMethodApp.isInstalled(this)
        popup.setOnMenuItemClickListener { onOptionsItemSelected(it) }
        popup.show()
    }

    private fun openTheme(entry: ThemeEntry) {
        // 快捷入口（输入法按键）没有 extra 可用，靠这个记住「你正在编辑哪一份」。
        EditorPrefs.selectedTheme = entry.id
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
        // 列表不粘了，英雄区也不该留着上一份主题还能点进去。
        currentHero = null
        binding.themes.visibility = View.GONE
        binding.placeholder.visibility = View.VISIBLE
        binding.placeholderTitle.text = title
        binding.placeholderBody.text = body
        binding.placeholderAction.text = action
        binding.placeholderAction.setOnClickListener { onAction() }
    }
}
