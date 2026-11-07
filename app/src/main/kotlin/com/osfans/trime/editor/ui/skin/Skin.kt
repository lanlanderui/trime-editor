/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.ui.skin

import android.app.Activity
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import com.osfans.trime.editor.EditorPrefs
import com.osfans.trime.editor.R
import com.osfans.trime.editor.ui.R as UiR

/**
 * 三套界面皮肤。
 *
 * 「皮肤」在这里是一张 ThemeOverlay（覆盖 `ui_*` token）+ 一组能力位（允许改结构
 * 的那几个开关）。它不含任何行为，所以：
 * - 换皮肤 = 换一层资源，页面代码不动；
 * - 皮肤之间不共享改动风险，第三张 overlay 写错只影响它自己。
 *
 * 校样（[PROOF]）是改造前的观感，保留它是为了「能退回去」：任何一次改版都可能
 * 有用户更习惯旧样子，而它的维护成本在 token 化之后接近零。
 */
enum class Skin(
    val id: String,
    @param:StringRes val label: Int,
    @param:StringRes val tagline: Int,
    @param:StyleRes val overlay: Int,
    /** 预览用的三个色点：主色 / 卡片 / 墨。外观页拿它画「这套皮肤长什么样」。 */
    @param:ColorRes val dotPrimary: Int,
    @param:ColorRes val dotCard: Int,
    @param:ColorRes val dotInk: Int,
    val caps: SkinCaps,
) {
    PROOF(
        id = "proof",
        label = R.string.skin_proof,
        tagline = R.string.skin_proof_tagline,
        overlay = R.style.Skin_Proof,
        dotPrimary = R.color.brand_primary,
        dotCard = UiR.color.proof_sheet,
        dotInk = UiR.color.proof_ink,
        caps = SkinCaps(),
    ),
    TONAL(
        id = "m3-tonal",
        label = R.string.skin_tonal,
        tagline = R.string.skin_tonal_tagline,
        overlay = R.style.Skin_Tonal,
        dotPrimary = R.color.tonal_primary,
        dotCard = R.color.tonal_card,
        dotInk = R.color.tonal_ink,
        caps = SkinCaps(),
    ),
    EXPRESSIVE(
        id = "m3-expressive",
        label = R.string.skin_expressive,
        tagline = R.string.skin_expressive_tagline,
        overlay = R.style.Skin_Expressive,
        dotPrimary = R.color.expressive_primary,
        dotCard = R.color.expressive_card,
        dotInk = R.color.expressive_ink,
        caps = SkinCaps(stageHeader = true, floatingDock = true, segmented = true),
    ),
    ;

    /** 把这张皮肤盖到 `activity` 现有的主题上。 */
    fun applyTo(activity: Activity) {
        activity.theme.applyStyle(overlay, true)
        UiMode.caps = caps
    }

    companion object {
        val DEFAULT: Skin = TONAL

        /** 按 id 找皮肤；未知 id 回退默认，不抛异常（旧版本存下来的值可能是任何串）。 */
        fun of(id: String?): Skin = entries.firstOrNull { it.id == id } ?: DEFAULT

        fun current(): Skin = of(EditorPrefs.skinId)
    }
}
