/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import java.io.File

/**
 * 主题所在的两个目录，由宿主在启动时注入。
 *
 * - 编辑器：`themeDir` = 用户选定的外部目录，`sharedDir` = 内置示例目录。
 * - 输入法：`themeDir` = `DataManager.themeDir`（可能就是用户切过去的外部目录），
 *   `sharedDir` = assets 镜像目录。
 *
 * 未注入时所有「找主题文件」的接口返回 null，而不是猜一个路径——猜错的症状是
 * 「改了但看不见」，比直接失败更难查（R14）。
 */
object ThemeDirectory {
    @Volatile
    var themeDir: File? = null

    @Volatile
    var sharedDir: File? = null

    /** 用户可写的那一份；存在即优先。 */
    fun userFile(themeId: String): File? = themeDir?.let { File(it, "$themeId.yaml") }

    /** 只读的随包示例。 */
    fun sharedFile(themeId: String): File? = sharedDir?.let { File(it, "$themeId.yaml") }
}
