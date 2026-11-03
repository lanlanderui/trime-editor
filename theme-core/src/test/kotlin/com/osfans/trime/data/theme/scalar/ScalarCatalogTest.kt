/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.scalar

import com.osfans.trime.data.theme.ThemeTextFile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * What the candidate-window / preedit screen is allowed to claim.
 *
 * Two facts matter and neither can be guessed at runtime: the default a theme
 * falls back to when it omits a line, and whether "absent" means "unset" rather
 * than "default spelled out". Both are checked here so the screen can say
 * "using the default" without inventing a number.
 */
class ScalarCatalogTest :
    BehaviorSpec({
        fun fileWith(text: String): File = File.createTempFile("scalar", ".yaml").apply {
            writeText(text)
            deleteOnExit()
            File(parentFile, name + ".bak").deleteOnExit()
        }

        val sample =
            """
            # 候选窗
            window:
              insets:
                vertical: 4
                horizontal: 6
              item_padding:
                vertical: 2
                horizontal: 4
              corner_radius: 6
              alpha: 1.0
              foreground:
                label_font_size: 18
                text_font_size: 22
                comment_font_size: 12
              some_future_key: 7

            preedit:
              horizontal_padding: 8
              alpha: 0.8
              foreground:
                font_size: 16
            """.trimIndent()

        Given("the field catalogue") {
            Then("defaults come from the model, so a missing line and the runtime agree") {
                // Spot-check the ones a reader would expect to differ.
                ScalarCatalog.window.fields.first { it.id == "insets/vertical" }.defaultValue shouldBe "4"
                ScalarCatalog.window.fields.first { it.id == "item_padding/vertical" }.defaultValue shouldBe "2"
                ScalarCatalog.window.fields.first { it.id == "alpha" }.defaultValue shouldBe "1.0"
                ScalarCatalog.preedit.fields.first { it.id == "horizontal_padding" }.defaultValue shouldBe "8"
                ScalarCatalog.preedit.fields.first { it.id == "foreground/font_size" }.defaultValue shouldBe "16.0"
            }
        }

        Given("a theme that writes both sections") {
            Then("each field reads the value the file wrote") {
                val file = fileWith(sample)
                val window = ThemeScalarReader.read(file, "window")
                val preedit = ThemeScalarReader.read(file, "preedit")

                ScalarCatalog.valueOf(window, ScalarCatalog.window.fields.first { it.id == "insets/vertical" }) shouldBe "4"
                ScalarCatalog.valueOf(window, ScalarCatalog.window.fields.first { it.id == "insets/horizontal" }) shouldBe "6"
                ScalarCatalog.valueOf(window, ScalarCatalog.window.fields.first { it.id == "alpha" }) shouldBe "1.0"
                ScalarCatalog.valueOf(preedit, ScalarCatalog.preedit.fields.first { it.id == "alpha" }) shouldBe "0.8"
            }

            Then("a field the file omits reads as null, so the screen can say「默认」") {
                val file = fileWith(sample)
                val window = ThemeScalarReader.read(file, "window")
                // `border` and `shadow` are not written in the sample.
                ScalarCatalog.valueOf(window, ScalarCatalog.window.fields.first { it.id == "border" }) shouldBe null
                ScalarCatalog.valueOf(window, ScalarCatalog.window.fields.first { it.id == "shadow" }) shouldBe null
            }

            Then("leaves the screen does not offer are reported, not hidden") {
                val file = fileWith(sample)
                val window = ThemeScalarReader.read(file, "window")
                ScalarCatalog.unlisted(window, ScalarCatalog.window).map { it.joinToString(".") } shouldBe
                    listOf("some_future_key")
            }

            Then("writing each field back with its own value leaves the file untouched") {
                val file = fileWith(sample)
                val before = file.readText()
                listOf(ScalarCatalog.window, ScalarCatalog.preedit).forEach { spec ->
                    val section = ThemeScalarReader.read(file, spec.name)
                    spec.fields.forEach { field ->
                        val value = ScalarCatalog.valueOf(section, field) ?: return@forEach
                        ThemeScalarWriter.set(file, spec.name, field.path, value) shouldBe true
                    }
                }
                file.readText() shouldBe before
            }
        }

        Given("a theme that writes neither section") {
            Then("both read as absent, which is the normal case") {
                val file = fileWith("style:\n  key_text_size: 18\n")
                ThemeScalarReader.read(file, "window").range shouldBe null
                ThemeScalarReader.read(file, "preedit").range shouldBe null
            }

            Then("writing one field creates the section with only that line") {
                val file = fileWith("style:\n  key_text_size: 18\n")
                ThemeScalarWriter.set(file, "window", listOf("insets", "vertical"), "6") shouldBe true
                val text = file.readText()
                text.contains("window:") shouldBe true
                val section = ThemeScalarReader.read(file, "window")
                ScalarCatalog.valueOf(
                    section,
                    ScalarCatalog.window.fields.first { it.id == "insets/vertical" },
                ) shouldBe "6"
                // The rest of the theme is untouched.
                text.contains("key_text_size: 18") shouldBe true
            }
        }

        Given("a commented-out line") {
            Then("it counts as「not set」 rather than as a value") {
                // Themes keep defaults as comments; the runtime never sees them.
                val file = fileWith("window:\n  insets:\n#    vertical: 9\n    horizontal: 6\n")
                val section = ThemeScalarReader.read(file, "window")
                ScalarCatalog.valueOf(
                    section,
                    ScalarCatalog.window.fields.first { it.id == "insets/vertical" },
                ) shouldBe null
            }
        }
    })
