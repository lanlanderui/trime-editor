/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.scalar

import com.osfans.trime.data.theme.model.Preedit
import com.osfans.trime.data.theme.model.Window

/**
 * A field of a scalar section, as the screen offers it.
 *
 * [defaultValue] is read off the model instance rather than typed in: the theme
 * leaves most of these out, so "what you get when the line is missing" and "what
 * the runtime uses" have to be the same number. Deriving it means the screen
 * cannot drift from the model it is describing.
 */
data class ScalarField(
    val path: List<String>,
    val kind: Kind,
    val defaultValue: String,
) {
    enum class Kind {
        INT,
        FLOAT,
    }

    /** Stable id for label lookup; `window/insets/vertical`. */
    val id: String get() = path.joinToString("/")
}

/** One section of the theme that is a plain tree of scalars. */
data class ScalarSectionSpec(
    val name: String,
    val fields: List<ScalarField>,
)

/**
 * The two sections that configure how typing looks: the candidate window and the
 * preedit line.
 *
 * Both are optional. A theme that does not write them gets libime's defaults, and
 * that is the normal case — `tongwenfeng.trime.yaml` writes neither. So the screen
 * has to be able to say "not set here, defaults apply" and offer to add the block,
 * rather than pretend the theme has values it does not.
 */
object ScalarCatalog {
    private val WINDOW = Window()

    private val PREEDIT = Preedit()

    val window: ScalarSectionSpec =
        ScalarSectionSpec(
            name = "window",
            fields =
            listOf(
                ScalarField(listOf("insets", "vertical"), ScalarField.Kind.INT, "${WINDOW.insets.vertical}"),
                ScalarField(listOf("insets", "horizontal"), ScalarField.Kind.INT, "${WINDOW.insets.horizontal}"),
                ScalarField(listOf("item_padding", "vertical"), ScalarField.Kind.INT, "${WINDOW.itemPadding.vertical}"),
                ScalarField(listOf("item_padding", "horizontal"), ScalarField.Kind.INT, "${WINDOW.itemPadding.horizontal}"),
                ScalarField(listOf("min_width"), ScalarField.Kind.INT, "${WINDOW.minWidth}"),
                ScalarField(listOf("corner_radius"), ScalarField.Kind.FLOAT, "${WINDOW.cornerRadius}"),
                ScalarField(listOf("border"), ScalarField.Kind.INT, "${WINDOW.border}"),
                ScalarField(listOf("shadow"), ScalarField.Kind.FLOAT, "${WINDOW.shadow}"),
                ScalarField(listOf("alpha"), ScalarField.Kind.FLOAT, "${WINDOW.alpha}"),
                ScalarField(listOf("foreground", "label_font_size"), ScalarField.Kind.FLOAT, "${WINDOW.foreground.labelFontSize}"),
                ScalarField(listOf("foreground", "text_font_size"), ScalarField.Kind.FLOAT, "${WINDOW.foreground.textFontSize}"),
                ScalarField(listOf("foreground", "comment_font_size"), ScalarField.Kind.FLOAT, "${WINDOW.foreground.commentFontSize}"),
            ),
        )

    val preedit: ScalarSectionSpec =
        ScalarSectionSpec(
            name = "preedit",
            fields =
            listOf(
                ScalarField(listOf("horizontal_padding"), ScalarField.Kind.INT, "${PREEDIT.horizontalPadding}"),
                ScalarField(listOf("top_start_radius"), ScalarField.Kind.FLOAT, "${PREEDIT.topStartRadius}"),
                ScalarField(listOf("top_end_radius"), ScalarField.Kind.FLOAT, "${PREEDIT.topEndRadius}"),
                ScalarField(listOf("alpha"), ScalarField.Kind.FLOAT, "${PREEDIT.alpha}"),
                ScalarField(listOf("foreground", "font_size"), ScalarField.Kind.FLOAT, "${PREEDIT.foreground.fontSize}"),
            ),
        )

    val sections: List<ScalarSectionSpec> = listOf(window, preedit)

    /**
     * What the file says for [field], or null when the line is missing (or
     * commented out) and the runtime will use [ScalarField.defaultValue].
     */
    fun valueOf(
        section: RawScalarSection,
        field: ScalarField,
    ): String? = section.leaf(field.path)?.takeIf { !it.commented }?.value

    /** Leaves the file has that this screen does not offer, so it can say so. */
    fun unlisted(
        section: RawScalarSection,
        spec: ScalarSectionSpec,
    ): List<List<String>> {
        val known = spec.fields.map { it.path }.toSet()
        return section.leaves.map { it.path }.filter { it !in known }
    }
}
