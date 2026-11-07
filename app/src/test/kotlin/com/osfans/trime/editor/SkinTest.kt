/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor

import com.osfans.trime.editor.ui.skin.Skin
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

/**
 * 皮肤表本身的行为。
 *
 * 这些用例不碰 Android 框架：皮肤表是「id → overlay + 能力位」的纯数据，能用
 * JVM 单测钉住的部分就都在这里钉住。真机才验得到的东西（换肤后整页重建、
 * 快捷键入口不闪白屏）不写成单测，写成验收清单。
 */
class SkinTest :
    BehaviorSpec({
        Given("皮肤表") {
            Then("默认皮肤是层叠（打底方案）") {
                Skin.DEFAULT shouldBe Skin.TONAL
            }

            Then("三套皮肤的 id 互不相同且非空") {
                val ids = Skin.entries.map { it.id }
                ids.toSet().size shouldBe ids.size
                ids.forEach { it.isNotBlank() shouldBe true }
            }

            Then("每套皮肤都指向一张真实存在的 overlay") {
                // R.style.* 是编译期常量；为 0 说明忘了写 <style>，运行时会静默不换肤。
                Skin.entries.forEach { (it.overlay == 0) shouldBe false }
            }
        }

        Given("按 id 找皮肤") {
            Then("空值回退默认") {
                Skin.of(null) shouldBe Skin.DEFAULT
            }

            Then("未知 id 回退默认，不抛异常") {
                Skin.of("m2-purple-from-the-future") shouldBe Skin.DEFAULT
            }

            Then("已知 id 原样返回") {
                Skin.of("proof") shouldBe Skin.PROOF
                Skin.of("m3-tonal") shouldBe Skin.TONAL
                Skin.of("m3-expressive") shouldBe Skin.EXPRESSIVE
            }
        }

        Given("能力位") {
            Then("校样与层叠都不改结构") {
                Skin.PROOF.caps.stageHeader shouldBe false
                Skin.PROOF.caps.floatingDock shouldBe false
                Skin.TONAL.caps.stageHeader shouldBe false
                Skin.TONAL.caps.floatingDock shouldBe false
            }

            Then("舞台同时开三处结构差异") {
                Skin.EXPRESSIVE.caps.stageHeader shouldBe true
                Skin.EXPRESSIVE.caps.floatingDock shouldBe true
                Skin.EXPRESSIVE.caps.segmented shouldBe true
            }
        }

        Given("同一套皮肤取两次") {
            Then("拿到的是同一个枚举实例（缓存/比较都靠它）") {
                Skin.of("m3-tonal").shouldBeSameInstanceAs(Skin.TONAL)
            }
        }
    })
