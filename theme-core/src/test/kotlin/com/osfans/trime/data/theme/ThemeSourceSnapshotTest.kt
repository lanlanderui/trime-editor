/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.io.File

class ThemeSourceSnapshotTest :
    BehaviorSpec({
        fun tempDir(): File = File.createTempFile("snapshot", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }

        fun file(
            dir: File,
            name: String,
            text: String,
        ): File = File(dir, name).apply { writeText(text) }

        val themeDir = tempDir()
        val sharedDir = tempDir()

        // Mirrors the real lookup: the user dir shadows the shared one.
        val resolve: (String) -> File? = { id ->
            File(themeDir, "$id.yaml").takeIf { it.isFile }
                ?: File(sharedDir, "$id.yaml").takeIf { it.isFile }
        }

        Given("a snapshot of the files a theme load read") {
            Then("it is current while every file is unchanged") {
                val root = file(sharedDir, "root.yaml", "name: r\n")
                val inc = file(sharedDir, "inc.yaml", "a: 1\n")
                val snapshot =
                    ThemeSourceSnapshot(
                        listOf(ThemeFileStamp.of(root, "root"), ThemeFileStamp.of(inc, "inc")),
                    )
                snapshot.isCurrent(resolve) shouldBe true
            }

            Then("a change to any of them makes it stale, not just the first") {
                val root = file(sharedDir, "root2.yaml", "name: r\n")
                val inc = file(sharedDir, "inc2.yaml", "a: 1\n")
                val snapshot =
                    ThemeSourceSnapshot(
                        listOf(ThemeFileStamp.of(root, "root2"), ThemeFileStamp.of(inc, "inc2")),
                    )
                // An __include target edited on its own used to leave the cached
                // theme in place, so the edit never showed up.
                file(sharedDir, "inc2.yaml", "a: 22222\n")
                snapshot.isCurrent(resolve) shouldBe false
            }

            Then("a resource that did not exist when it was looked for makes it stale") {
                val snapshot = ThemeSourceSnapshot(absent = listOf("later"))
                snapshot.isCurrent(resolve) shouldBe true
                file(sharedDir, "later.yaml", "b: 1\n")
                snapshot.isCurrent(resolve) shouldBe false
            }

            Then("a user copy appearing over the shared file it read makes it stale") {
                val shared = file(sharedDir, "shadow.yaml", "a: 1\n")
                val snapshot = ThemeSourceSnapshot(listOf(ThemeFileStamp.of(shared, "shadow")))
                snapshot.isCurrent(resolve) shouldBe true
                file(themeDir, "shadow.yaml", "a: 1\n")
                snapshot.isCurrent(resolve) shouldBe false
            }

            Then("a deleted file makes it stale") {
                val only = file(sharedDir, "gone.yaml", "a: 1\n")
                val snapshot = ThemeSourceSnapshot(listOf(ThemeFileStamp.of(only, "gone")))
                only.delete() shouldBe true
                snapshot.isCurrent(resolve) shouldBe false
            }
        }

        Given("a snapshot combining the source files and the deployed artifact") {
            Then("it keeps both, once each") {
                val root = file(sharedDir, "merged.yaml", "name: m\n")
                val deployed = file(sharedDir, "merged.deployed", "name: m\n")
                val source = ThemeSourceSnapshot(listOf(ThemeFileStamp.of(root, "merged")), listOf("merged.custom"))
                val artifact = ThemeSourceSnapshot(listOf(ThemeFileStamp.of(deployed)))
                val merged = source.merge(artifact)

                merged.files.map { it.path } shouldBe listOf(root.absolutePath, deployed.absolutePath)
                merged.absent shouldContain "merged.custom"
                merged.merge(artifact).files.size shouldBe 2
            }
        }
    })
