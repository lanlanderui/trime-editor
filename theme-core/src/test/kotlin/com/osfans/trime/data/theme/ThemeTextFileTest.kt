/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Pins the safety net shared by all theme editors: the previous version is
 * kept, the author's line endings survive, and a stale editor cannot overwrite
 * a file that somebody else changed.
 */
class ThemeTextFileTest :
    StringSpec({
        fun tempFile(
            text: String,
            block: (File) -> Unit,
        ) {
            val file = File.createTempFile("trime-theme-", ".yaml")
            try {
                file.writeText(text)
                block(file)
            } finally {
                listOf("", ".bak", ".tmp").forEach { File(file.parentFile, file.name + it).delete() }
            }
        }

        "a successful write keeps an exact backup and preserves CRLF" {
            tempFile("style:\r\n  horizontal_gap: 1\r\n") { file ->
                val document = ThemeTextFile.read(file) ?: error("cannot read fixture")
                document.lines[1] = "  horizontal_gap: 2"

                ThemeTextFile.write(file, document, document.lines) shouldBe true

                file.readText() shouldBe "style:\r\n  horizontal_gap: 2\r\n"
                ThemeTextFile.backupOf(file).readText() shouldBe "style:\r\n  horizontal_gap: 1\r\n"
            }
        }

        "a file changed outside the app is not overwritten" {
            tempFile("style:\n  horizontal_gap: 1\n") { file ->
                val document = ThemeTextFile.read(file) ?: error("cannot read fixture")
                document.lines[1] = "  horizontal_gap: 2"
                file.writeText("style:\n  horizontal_gap: 9\n")

                ThemeTextFile.write(file, document, document.lines) shouldBe false
                file.readText() shouldBe "style:\n  horizontal_gap: 9\n"
            }
        }

        "a failed temp write leaves the original and its backup intact" {
            tempFile("style:\n  horizontal_gap: 1\n") { file ->
                val original = file.readText()
                val document = ThemeTextFile.read(file) ?: error("cannot read fixture")
                document.lines[1] = "  horizontal_gap: 2"
                // Make the temp path unwritable by turning it into a directory.
                File(file.parentFile, file.name + ".tmp").mkdirs()

                ThemeTextFile.write(file, document, document.lines) shouldBe false
                file.readText() shouldBe original
                ThemeTextFile.backupOf(file).readText() shouldBe original
            }
        }

        "the style writer uses the same backup and CRLF rules" {
            tempFile("style:\r\n  horizontal_gap: 1 # gap\r\n") { file ->
                ThemeStyleWriter.writeEntry(file, "horizontal_gap", "2") shouldBe true

                file.readText() shouldBe "style:\r\n  horizontal_gap: 2 # gap\r\n"
                ThemeTextFile.backupOf(file).readText() shouldBe "style:\r\n  horizontal_gap: 1 # gap\r\n"
            }
        }

        "a save bumps the generation a theme cache watches, a failed one does not" {
            tempFile("style:\n  horizontal_gap: 1\n") { file ->
                fun save(value: String) {
                    val document = ThemeTextFile.read(file) ?: error("cannot read fixture")
                    document.lines[1] = "  horizontal_gap: $value"
                    ThemeTextFile.write(file, document, document.lines) shouldBe true
                }

                val before = ThemeTextFile.writeGeneration
                save("2")
                val afterSave = ThemeTextFile.writeGeneration
                (afterSave > before) shouldBe true

                // Same byte count as the previous save: a cache watching only
                // length and timestamp could keep serving the old theme.
                save("1")
                val afterSecondSave = ThemeTextFile.writeGeneration
                (afterSecondSave > afterSave) shouldBe true

                val stale = ThemeTextFile.read(file) ?: error("cannot read fixture")
                file.writeText("style:\n  horizontal_gap: 9\n")
                ThemeTextFile.write(file, stale, stale.lines) shouldBe false
                ThemeTextFile.writeGeneration shouldBe afterSecondSave
            }
        }
    })
