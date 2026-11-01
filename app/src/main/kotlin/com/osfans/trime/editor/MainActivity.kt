/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.osfans.trime.editor.ui.EditorUi
import com.osfans.trime.theme.core.ThemeCore
import com.osfans.trime.theme.editor.ThemeEditor
import com.osfans.trime.theme.runtime.ThemeRuntime

/**
 * D0 的启动页：只证明五层模块接得上、版本号打得出来。
 *
 * 工作台（主题列表 + 预览）在 D2 替换掉这个页面。
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<TextView>(R.id.version).text =
            getString(
                R.string.version_line,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE.toString(),
            )

        val modules =
            listOf(
                ThemeCore.MODULE to getString(R.string.module_core),
                ThemeRuntime.MODULE to getString(R.string.module_runtime),
                ThemeEditor.MODULE to getString(R.string.module_editor),
                EditorUi.MODULE to getString(R.string.module_ui),
                "app" to getString(R.string.module_app),
            )

        val container = findViewById<LinearLayout>(R.id.modules)
        modules.forEach { (id, role) ->
            container.addView(
                TextView(this).apply {
                    text = getString(R.string.module_row, id, role)
                    textSize = 14f
                    setLineSpacing(0f, 1.3f)
                    val v = (10 * resources.displayMetrics.density).toInt()
                    setPadding(0, v, 0, v)
                },
            )
        }
    }
}
