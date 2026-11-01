/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Pins the text-level editing of `preset_color_schemes`: the writer must touch
 * exactly the edited line and leave comments, ordering and every other scheme
 * alone, because the theme file is what the user carries between devices.
 */
class ThemeSchemeWriterTest :
    BehaviorSpec({
        Given("a theme with two schemes") {
            Then("listSchemes reports ids in file order with their display names") {
                withTheme { file ->
                    ThemeSchemeWriter.listSchemes(file) shouldContainExactly
                        listOf(
                            ThemeSchemeWriter.Scheme("default", "預設／default"),
                            ThemeSchemeWriter.Scheme("ink", "墨池／Ink"),
                        )
                }
            }

            Then("a scheme without a name falls back to its id") {
                withTheme("preset_color_schemes:\n  plain:\n    back_color: 0xffffff\n") { file ->
                    ThemeSchemeWriter.listSchemes(file) shouldContainExactly
                        listOf(ThemeSchemeWriter.Scheme("plain", "plain"))
                }
            }

            Then("readScheme returns every property without its trailing comment") {
                withTheme { file ->
                    val values = ThemeSchemeWriter.readScheme(file, "default")
                    values["name"] shouldBe "預設／default"
                    values["back_color"] shouldBe "0xe4e7e9"
                    values["border_color"] shouldBe "0xc1c7ca"
                }
            }

            Then("a tab-aligned comment is not read as part of the value") {
                val source =
                    "preset_color_schemes:\n  tabbed:\n    name: 制表符\t#名稱\n    back_color: 0xffffff\t#背景\n"
                withTheme(source) { file ->
                    val values = ThemeSchemeWriter.readScheme(file, "tabbed")
                    values["name"] shouldBe "制表符"
                    values["back_color"] shouldBe "0xffffff"
                }
            }
        }

        Given("a scheme being renamed") {
            Then("only the name line changes and its trailing comment survives") {
                withTheme { file ->
                    ThemeSchemeWriter.renameScheme(file, "default", "我的配色") shouldBe true
                    file.readText() shouldBe
                        SAMPLE.replace(
                            "    name: 預設／default #方案名稱",
                            "    name: 我的配色 #方案名稱",
                        )
                }
            }

            Then("a scheme without a name line gets one inserted first") {
                val source = "preset_color_schemes:\n  plain:\n    back_color: 0xffffff\n"
                withTheme(source) { file ->
                    ThemeSchemeWriter.renameScheme(file, "plain", "朴素") shouldBe true
                    file.readText() shouldBe
                        "preset_color_schemes:\n  plain:\n    name: 朴素\n    back_color: 0xffffff\n"
                }
            }

            Then("an unknown id is reported instead of silently doing nothing") {
                withTheme { file ->
                    ThemeSchemeWriter.renameScheme(file, "no_such", "x") shouldBe false
                    file.readText() shouldBe SAMPLE
                }
            }

            Then("the spacing between the name and its comment is kept") {
                // Themes are hand-written: some align comments with two spaces
                // or a tab, and an edit must not re-flow the document.
                val source = "preset_color_schemes:\n  wide:\n    name: 旧  #方案名稱\n    back_color: 0xffffff\n"
                withTheme(source) { file ->
                    ThemeSchemeWriter.renameScheme(file, "wide", "新") shouldBe true
                    file.readText() shouldBe
                        "preset_color_schemes:\n  wide:\n    name: 新  #方案名稱\n    back_color: 0xffffff\n"
                }
            }
        }

        Given("a scheme being deleted") {
            Then("its whole block goes and the blank separator with it") {
                withTheme { file ->
                    ThemeSchemeWriter.deleteScheme(file, "default") shouldBe true
                    val text = file.readText()
                    text.contains("default") shouldBe false
                    text.contains("墨池／Ink") shouldBe true
                    text.contains("\n\n\n") shouldBe false
                }
            }

            Then("the last remaining scheme is protected") {
                val source = "preset_color_schemes:\n  only:\n    back_color: 0xffffff\n"
                withTheme(source) { file ->
                    ThemeSchemeWriter.deleteScheme(file, "only") shouldBe false
                    file.readText() shouldBe source
                }
            }
        }

        Given("a single colour being edited") {
            Then("the value changes and the trailing comment is kept") {
                withTheme { file ->
                    ThemeSchemeWriter.setColor(file, "default", "back_color", "0xff4444") shouldBe true
                    file.readText() shouldBe
                        SAMPLE.replace(
                            "    back_color: 0xe4e7e9 #候選區背景",
                            "    back_color: 0xff4444 #候選區背景",
                        )
                }
            }

            Then("a missing key is appended to the scheme body") {
                withTheme { file ->
                    ThemeSchemeWriter.setColor(file, "ink", "shadow_color", "0x000000") shouldBe true
                    val text = file.readText()
                    text.contains("    border_color: 0x000000\n    hilited_candidate_back_color: 0x000000\n    shadow_color: 0x000000\n")
                        .shouldBe(true)
                }
            }

            Then("a colour value keeps the spacing before its comment") {
                val source = "preset_color_schemes:\n  src:\n    back_color: 0xffffff\t#背景\n"
                withTheme(source) { file ->
                    ThemeSchemeWriter.setColor(file, "src", "back_color", "0x000000") shouldBe true
                    file.readText() shouldBe
                        "preset_color_schemes:\n  src:\n    back_color: 0x000000\t#背景\n"
                }
            }

            Then("the version before the edit is kept as a backup") {
                withTheme { file ->
                    ThemeSchemeWriter.setColor(file, "default", "back_color", "0xff4444") shouldBe true
                    ThemeTextFile.backupOf(file).readText() shouldBe SAMPLE
                }
            }
        }

        Given("schemes linked through light_scheme / dark_scheme") {
            Then("referencingSchemes names every scheme pointing at the id") {
                withTheme(LINKED) { file ->
                    ThemeSchemeWriter.referencingSchemes(file, "day") shouldContainExactly listOf("auto")
                    ThemeSchemeWriter.referencingSchemes(file, "night") shouldContainExactly listOf("auto")
                }
            }

            Then("an unreferenced scheme reports nothing") {
                withTheme(LINKED) { file ->
                    ThemeSchemeWriter.referencingSchemes(file, "auto").shouldBeEmpty()
                }
            }
        }
    })

/** Writes [content] to a scratch file (the sample theme when omitted) and cleans up after. */
private fun withTheme(
    content: String = SAMPLE,
    block: (File) -> Unit,
) {
    val file = File.createTempFile("trime-scheme-", ".yaml")
    try {
        file.writeText(content)
        block(file)
    } finally {
        listOf("", ".bak", ".tmp").forEach { File(file.parentFile, file.name + it).delete() }
    }
}

/** Mirrors the shape of the shipped theme: 2-space ids, 4-space properties. */
private val SAMPLE =
    """
    style:
      horizontal_gap: 1 #鍵水平間距

    preset_color_schemes:
      default:
        name: 預設／default #方案名稱
        author: osfans #作者資訊
        back_color: 0xe4e7e9 #候選區背景
        border_color: 0xc1c7ca #邊框

      ink:
        name: 墨池／Ink
        back_color: 0xffffff
        border_color: 0x000000
        hilited_candidate_back_color: 0x000000

    preset_keys:
      Return: {label: enter_labels, send: Return}
    """.trimIndent() + "\n"

private val LINKED =
    """
    preset_color_schemes:
      day:
        name: 日间
        back_color: 0xffffff
      night:
        name: 夜间
        back_color: 0x000000
      auto:
        name: 自动
        light_scheme: day
        dark_scheme: night
    """.trimIndent() + "\n"
