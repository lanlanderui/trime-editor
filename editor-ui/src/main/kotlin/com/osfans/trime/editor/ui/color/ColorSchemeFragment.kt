/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.ui.color

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.osfans.trime.data.theme.ColorGroup
import com.osfans.trime.data.theme.ColorKeyGroups
import com.osfans.trime.data.theme.ColorLiteral
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.ColorTable
import com.osfans.trime.data.theme.ThemeSchemeWriter
import com.osfans.trime.data.theme.ThemeStyleWriter
import com.osfans.trime.data.theme.ThemeTextFile
import com.osfans.trime.editor.ui.DiskBackedEditor
import com.osfans.trime.editor.ui.EditorSection
import com.osfans.trime.editor.ui.R
import com.osfans.trime.editor.ui.dp
import com.osfans.trime.editor.ui.proof.PreferenceOutline
import com.osfans.trime.editor.ui.proof.ProofChapter
import com.osfans.trime.editor.ui.proof.ProofFormat
import com.osfans.trime.editor.ui.proof.ProofRow
import com.osfans.trime.editor.ui.proof.ProofRowValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** What the fragment asks its activity to do with the preview board. */
interface ColorEditorHost {
    /** Re-reads the theme from disk and redraws the board. */
    fun reloadBoard()

    /**
     * A scheme was added, renamed or deleted, and [edit] is the one the screen
     * should be editing now.
     *
     * A deleted scheme cannot stay in the editor, and a freshly created one is
     * what the user wants to look at — so the screen follows the edit rather
     * than leaving the rows pointing at a block that is no longer in the file.
     */
    fun onSchemeChanged(edit: String)

    /** 写盘之后重算样张下面那行统计（未设 / 本次已改）。 */
    fun refreshCaption()

    /** Which half of a day/night pair the board is painting with. */
    fun previewState(): PreviewState

    /** Forces the board into one half, or back onto the system's choice. */
    fun setPreviewMode(mode: NightMode)
}

/**
 * Which half of a day/night pair the board should show.
 *
 * [SYSTEM] is the real behaviour — the keyboard follows the device — and is
 * what the editor starts on. The two fixed modes exist because editing the
 * light block while the board shows the dark twin looks like a bug otherwise.
 */
enum class NightMode { SYSTEM, LIGHT, DARK }

/** [mode] the board was asked for, and the scheme it actually resolved to. */
data class PreviewState(
    val mode: NightMode,
    val schemeId: String,
)

/**
 * Edits the colours of one scheme.
 *
 * The list of keys follows `preset_color_schemes.default`: that is the set the
 * theme author considers complete, so a scheme that only overrides a few
 * colours still shows every slot and marks the ones it inherits. Keys the
 * scheme defines on its own are appended after the template.
 *
 * Saving follows the same rules as the style editor: one line changes, the
 * author's comments and line endings survive, and a file that somebody else
 * rewrote is refused rather than clobbered.
 */
class ColorSchemeFragment :
    PreferenceFragmentCompat(),
    DiskBackedEditor {
    private lateinit var themeId: String
    private lateinit var schemeId: String
    private lateinit var sourceFile: File

    /** The file's contents the rows were built from; the baseline for conflicts. */
    private var expectedText: String = ""

    private val writeLock = Mutex()

    /** What this scheme defines, kept so the dialog can offer its colours. */
    private var schemeColors: Map<String, String> = emptyMap()

    /** Every scheme the file had when the rows were built, in file order. */
    private var knownSchemes: List<ThemeSchemeWriter.Scheme> = emptyList()

    /** 这一次会话里成功写盘的次数，以及本方案没写的色键数（见 [caption]）。 */
    private var changedCount = 0
    private var unsetCount = 0

    private val host: ColorEditorHost? get() = activity as? ColorEditorHost

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        themeId = arguments?.getString(ARG_THEME_ID).orEmpty()
        schemeId = arguments?.getString(ARG_SCHEME_ID).orEmpty()
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
        val file = ThemeStyleWriter.editableSourceFile(themeId)
        if (file == null || schemeId.isEmpty()) {
            addDisabledRow()
            return
        }
        sourceFile = file
        rebuild()
    }

    private fun addDisabledRow() {
        preferenceScreen?.addPreference(
            Preference(requireContext()).apply {
                title = getString(R.string.style_no_file)
                isEnabled = false
            },
        )
    }

    override fun isFileChanged(): Boolean {
        if (!::sourceFile.isInitialized) return false
        val current = ThemeTextFile.read(sourceFile)?.text ?: return false
        return current != expectedText
    }

    /** 样张下面的读数：还有多少色键没在本方案里写、这次改了几处。 */
    override fun caption(): String = ProofFormat.caption(unsetCount, changedCount)

    /** 目录栏的章节：从建好的偏好树里读，和页面上的分组一一对应。 */
    override fun sections(): List<EditorSection> = PreferenceOutline.sectionsOf(preferenceScreen)

    /** Rebuilds the whole screen from the file as it is now. */
    private fun rebuild() {
        val screen = preferenceScreen ?: return
        screen.removeAll()
        val document = ThemeTextFile.read(sourceFile)
        if (document == null) {
            addDisabledRow()
            return
        }
        expectedText = document.text

        val templateValues = ThemeSchemeWriter.readScheme(sourceFile, TEMPLATE_SCHEME)
        val ownValues = ThemeSchemeWriter.readScheme(sourceFile, schemeId)
        schemeColors = ownValues
        knownSchemes = ThemeSchemeWriter.listSchemes(document.lines)
        val keys =
            templateValues.keys.filter { it.isColorKey() } +
                ownValues.keys.filter { it.isColorKey() && it !in templateValues }
        // The scheme rows come first: adding or renaming one is what a user
        // looks for before touching colours, and every row below describes the
        // scheme those three rows say is being edited.
        buildSchemeRows(screen)
        if (keys.isEmpty()) {
            addDisabledRow()
            return
        }

        // 没写在本方案里的色键数：这些走 fallback 链，行里标「继承」。
        unsetCount = keys.count { it !in ownValues }

        // Grouped the way a keyboard is read, so a colour is found by what it
        // paints instead of by remembering its YAML name. Groups the theme does
        // not use are dropped rather than shown empty.
        for ((group, groupKeys) in ColorKeyGroups.group(keys)) {
            // 章头只写组名，项数挪到右边那一列（ProofChapter.setCount），
            // 这样「候选栏与编码区」不会因为后面挂了个括号而长短不一。
            val chapter =
                ProofChapter(requireContext()).apply {
                    title = getString(groupTitle(group))
                    setCount(groupKeys.size)
                }
            screen.addPreference(chapter)
            groupKeys.forEach { chapter.addPreference(colorRow(it, ownValues)) }
        }
    }

    /**
     * 方案与预览：两行，不是四行。
     *
     * 原来「新建 / 重命名 / 删除」三行平铺，占掉小半屏才轮到颜色，而这三件事
     * 一次只做一件。收成一行「方案」，点开是菜单。预览明暗单独留一行：它是
     * 「为什么行里的色和右边键盘不一样」的唯一解释，不能藏进菜单里。
     */
    private fun buildSchemeRows(screen: PreferenceScreen) {
        val chapter =
            ProofChapter(requireContext()).apply {
                title = getString(R.string.color_scheme_group)
            }
        // 铁律：category 必须先挂上 screen 再加子项，否则 NPE，单测也碰不到。
        screen.addPreference(chapter)
        chapter.addPreference(schemeRow())
        previewRow()?.let { chapter.addPreference(it) }
    }

    /** 一行「方案」：值是这个方案的名字，点开是它能做的三件事。 */
    private fun schemeRow(): Preference = ProofRowValue(requireContext()).apply {
        key = KEY_SCHEME
        proofName = getString(R.string.color_scheme_group)
        proofKey = schemeId
        value = displayNameOf(schemeId)
        setOnPreferenceClickListener {
            showSchemeMenu()
            true
        }
    }

    private fun showSchemeMenu() {
        val canDelete = knownSchemes.size > 1
        val items =
            arrayOf(
                getString(R.string.color_scheme_new),
                getString(R.string.color_scheme_rename),
                getString(R.string.color_scheme_delete),
            )
        AlertDialog.Builder(requireContext())
            .setTitle(displayNameOf(schemeId))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showNewSchemeDialog()

                    1 -> showRenameDialog()

                    // 只剩一个方案时菜单照样列出来，点了说清为什么不能删——
                    // 比直接把这一项藏掉更不容易让人以为界面出了问题。
                    else -> if (canDelete) showDeleteDialog() else toast(R.string.color_scheme_last)
                }
            }
            .show()
    }

    /**
     * 右边那块板现在画的是哪一半。
     *
     * 声明了 `dark_scheme` 的方案在夜间会被孪生方案画出来。没有这一行，行里的
     * 颜色和上方的键盘会像两套主题——人会去文件里找 bug，而问题在时钟上。
     */
    private fun previewRow(): Preference? {
        val host = host ?: return null
        val state = host.previewState()
        return ProofRowValue(requireContext()).apply {
            key = KEY_PREVIEW_NIGHT
            proofName = getString(R.string.color_preview_night)
            proofKey = "night"
            value = getString(modeLabel(state.mode))
            setOnPreferenceClickListener {
                showNightModeDialog(host, state.mode)
                true
            }
        }
    }

    private fun modeLabel(mode: NightMode): Int = when (mode) {
        NightMode.SYSTEM -> R.string.color_preview_night_system
        NightMode.LIGHT -> R.string.color_preview_night_light
        NightMode.DARK -> R.string.color_preview_night_dark
    }

    private fun showNightModeDialog(
        host: ColorEditorHost,
        current: NightMode,
    ) {
        val modes = NightMode.entries
        val labels = modes.map { getString(modeLabel(it)) }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.color_preview_night_title)
            .setSingleChoiceItems(labels, modes.indexOf(current)) { dialog, which ->
                dialog.dismiss()
                host.setPreviewMode(modes[which])
                rebuild()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** The label a scheme shows in the list, falling back to its id. */
    private fun displayNameOf(id: String): String = knownSchemes.firstOrNull { it.id == id }?.displayName ?: id

    /**
     * A new block starting from the one being edited.
     *
     * The id is the YAML key other settings reference, so it is asked for
     * separately from the display name and only accepts what
     * [ThemeSchemeWriter] can read back. The copy keeps the colours this scheme
     * spells out and drops its `light_scheme` / `dark_scheme` links: pointing
     * the copy at the same twin would leave two schemes claiming one partner,
     * and deleting either would strand the other.
     */
    private fun showNewSchemeDialog() {
        val context = requireContext()
        val taken = knownSchemes.mapTo(HashSet()) { it.id }
        val idField =
            textField(context, uniqueId(schemeId, taken)).apply {
                // Restricting the keys stops an IME from turning an id into
                // candidates: what lands here has to survive a round trip
                // through the theme file.
                keyListener = DigitsKeyListener.getInstance(ID_CHARS)
            }
        val nameField =
            textField(context, getString(R.string.color_scheme_new_name_copied, displayNameOf(schemeId)))
        val dialog =
            AlertDialog.Builder(context)
                .setTitle(R.string.color_scheme_new_title)
                .setView(
                    dialogBody(
                        context,
                        labelledField(context, getString(R.string.color_scheme_new_id), idField),
                        labelledField(context, getString(R.string.color_scheme_new_name), nameField),
                    ),
                )
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    createScheme(idField.text.toString().trim(), nameField.text.toString().trim(), taken)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        focusOnShow(dialog, idField)
        dialog.show()
    }

    private fun createScheme(
        id: String,
        name: String,
        taken: Set<String>,
    ) {
        if (!SCHEME_ID.matches(id)) {
            toast(R.string.color_scheme_new_id_invalid)
            return
        }
        if (id in taken) {
            toast(R.string.color_scheme_new_id_taken)
            return
        }
        if (name.isEmpty()) {
            toast(R.string.color_scheme_name_empty)
            return
        }
        val entries = LinkedHashMap<String, String>()
        entries["name"] = name
        schemeColors.forEach { (key, value) ->
            // `author` stays behind on purpose: the copy is the user's own
            // scheme now, and carrying someone else's address into a file they
            // may share would credit them with edits they never made.
            if (key !in METADATA_KEYS && value.isNotBlank()) entries[key] = value
        }
        commit(
            write = { baseline ->
                ThemeSchemeWriter.setSchemesChecked(
                    file = sourceFile,
                    schemes = mapOf(id to entries),
                    create = true,
                    expectedText = baseline,
                )
            },
            onWritten = {
                toast(getString(R.string.color_scheme_created, name))
                host?.onSchemeChanged(id)
            },
        )
    }

    /**
     * Renames the display label. The id is what `set_color_scheme` and the
     * day/night links point at, so it stays as it is — renaming it would break
     * settings the editor cannot see.
     */
    private fun showRenameDialog() {
        val context = requireContext()
        val field = textField(context, displayNameOf(schemeId))
        val dialog =
            AlertDialog.Builder(context)
                .setTitle(R.string.color_scheme_rename_title)
                .setView(dialogBody(context, labelledField(context, getString(R.string.color_scheme_new_name), field)))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val name = field.text.toString().trim()
                    if (name.isEmpty()) {
                        toast(R.string.color_scheme_name_empty)
                    } else {
                        rename(name)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        focusOnShow(dialog, field)
        dialog.show()
    }

    /**
     * Puts the cursor in [field] and brings the keyboard up once the dialog is
     * on screen.
     *
     * A rename or a new id is typed, not read: opening with nothing focused
     * costs a tap and leaves the keypresses going nowhere until the user makes
     * it. The listener is what makes it stick — the window only exists after
     * the dialog is attached.
     */
    private fun focusOnShow(
        dialog: AlertDialog,
        field: EditText,
    ) {
        dialog.setOnShowListener {
            field.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
    }

    private fun rename(name: String) = commit(
        write = { baseline -> ThemeSchemeWriter.renameSchemeChecked(sourceFile, schemeId, name, baseline) },
        onWritten = {
            toast(R.string.color_scheme_renamed)
            rebuild()
            host?.onSchemeChanged(schemeId)
        },
    )

    /**
     * Deleting asks the file who points at this scheme first.
     *
     * A `dark_scheme: steam` left behind by a deletion is not an error the user
     * will ever see: the keyboard simply stops switching at night. Naming the
     * schemes that would be stranded is the only warning they get.
     */
    private fun showDeleteDialog() {
        val referencing = ThemeSchemeWriter.referencingSchemes(sourceFile, schemeId)
        val note =
            if (referencing.isEmpty()) {
                getString(R.string.color_scheme_delete_safe)
            } else {
                getString(R.string.color_scheme_referenced, referencing.joinToString("、"))
            }
        val name = displayNameOf(schemeId)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.color_scheme_delete)
            .setMessage(getString(R.string.color_scheme_delete_confirm, name) + "\n\n" + note)
            .setPositiveButton(R.string.color_scheme_delete) { _, _ -> delete() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun delete() = commit(
        write = { baseline -> ThemeSchemeWriter.deleteSchemeChecked(sourceFile, schemeId, baseline) },
        onWritten = {
            toast(R.string.color_scheme_deleted)
            val next = ThemeSchemeWriter.listSchemes(sourceFile).firstOrNull()?.id
            host?.onSchemeChanged(next ?: TEMPLATE_SCHEME)
        },
        onFailed = { toast(R.string.color_scheme_last) },
    )

    /**
     * One text field, sized for a dialog.
     *
     * [InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS] on purpose: an id and a hex
     * value are not words, and a predictive keyboard rewriting either of them
     * is how a user ends up with a scheme nothing can read.
     */
    private fun textField(
        context: Context,
        value: String,
    ): EditText = EditText(context).apply {
        setText(value)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setSelection(text.length)
    }

    /**
     * [field] under a caption.
     *
     * The caption is a real label rather than the field's hint: both fields
     * arrive pre-filled, and a hint disappears as soon as there is text —
     * leaving the user unable to tell the id from the display name.
     */
    private fun labelledField(
        context: Context,
        caption: String,
        field: EditText,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(
            TextView(context).apply {
                text = caption
                textSize = 12f
                setPadding(0, context.dp(10), 0, 0)
            },
        )
        addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun dialogBody(
        context: Context,
        vararg fields: View,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val horizontal = context.dp(20)
        setPadding(horizontal, context.dp(4), horizontal, 0)
        fields.forEach {
            addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    /**
     * A free id close to [base]: `<base>_copy`, then `<base>_copy2` and so on.
     *
     * Prefilled rather than left blank so the common case is one tap, and
     * stripped to what the file can hold so a Chinese display name never
     * becomes an id the writer cannot find again.
     */
    private fun uniqueId(
        base: String,
        taken: Set<String>,
    ): String {
        val root = base.replace(NOT_IN_ID, "_").ifEmpty { "scheme" }
        var candidate = "${root}_copy"
        var n = 2
        while (candidate in taken) {
            candidate = "${root}_copy$n"
            n++
        }
        return candidate
    }

    /**
     * One colour row.
     *
     * The value shown is what this scheme will actually paint with. When the
     * scheme leaves a key out, the runtime walks `fallback_colors` and then the
     * built-in chains, so the row names the key the value came from — saying
     * "not set" there would be a lie the keyboard contradicts.
     */
    private fun colorRow(
        key: String,
        ownValues: Map<String, String>,
    ): Preference {
        // The active theme carries the `fallback_colors` the runtime uses; with
        // no theme attached the chain stops at the scheme's own keys, which is
        // still true, only shorter.
        val fallback = ColorManager.currentScope()?.theme?.fallbackColors.orEmpty()
        val own = ownValues[key]
        val source = if (own == null) ColorTable.resolveRawSource(key, ownValues, fallback) else null
        val shown = own ?: source?.second
        // 文件里写的是什么（可能是 `*color2`），和它实际画出来是什么颜色，是两件事：
        // 前者要能抄走，后者要能看见。锚点只有 YAML 解析之后才有颜色，所以去主题里取。
        val resolved = resolvedColor(key, shown)
        val anchor =
            when {
                own?.startsWith("*") == true -> own
                source != null -> getString(R.string.color_scheme_value_inherited, source.first)
                else -> null
            }
        return ProofRow(requireContext()).apply {
            this.key = key
            proofName = COLOR_LABELS[key]?.let { getString(it) } ?: key
            proofKey = key
            proofAnchor = anchor
            swatchColor = resolved
            hex =
                when {
                    resolved != null -> ProofFormat.hex(resolved)
                    shown == null -> ProofFormat.UNSET
                    else -> shown
                }
            setOnPreferenceClickListener {
                showColorDialog(key, shown, resolved)
                true
            }
        }
    }

    /**
     * 一个色键实际画出来的颜色。
     *
     * 主题里大量是 `*color2` 这种锚点，文本层面没有颜色，只有 YAML 解析后才有；
     * 解析拿不到就返回 null——宁可不画色块，也不画一个看起来像真的错颜色。
     */
    private fun resolvedColor(
        key: String,
        shown: String?,
    ): Int? {
        ColorLiteral.parse(shown)?.let { return it }
        val raw = ColorManager.currentScope()?.theme?.presetColorSchemes?.get(schemeId)?.get(key) ?: return null
        return ColorLiteral.parse(raw)
    }

    private fun groupTitle(group: ColorGroup): Int = when (group) {
        ColorGroup.CANDIDATE_BAR -> R.string.color_group_candidate_bar
        ColorGroup.KEYBOARD -> R.string.color_group_keyboard
        ColorGroup.KEY -> R.string.color_group_key
        ColorGroup.FUNCTION_KEY -> R.string.color_group_function_key
        ColorGroup.PRESSED_KEY -> R.string.color_group_pressed_key
        ColorGroup.POPUP -> R.string.color_group_popup
        ColorGroup.APP_CHROME -> R.string.color_group_app_chrome
        ColorGroup.OTHER -> R.string.color_group_other
    }

    /**
     * The colour dialog: a picker for the common case, with the text field kept
     * for exact values and for the keys that accept a background image file name
     * instead of a colour.
     *
     * Dragging the picker rewrites the field and typing moves the picker, so the
     * two never disagree. [syncing] breaks the loop their listeners would
     * otherwise form.
     */
    private fun showColorDialog(
        key: String,
        current: String?,
        resolved: Int? = null,
    ) {
        val context = requireContext()
        // 锚点（`*color2`）在文本里没有颜色：输入框保留原文，取色器和「改前」
        // 色块用解析出来的那个色，否则一点开就是一片白，跟行里显示的色块打架。
        val original = ColorLiteral.parse(current) ?: resolved ?: Color.WHITE

        val picker =
            ColorPickerView(context).apply {
                setColor(original)
            }
        val input =
            EditText(context).apply {
                setText(current.orEmpty())
                hint = getString(R.string.color_scheme_edit_hint)
                inputType = InputType.TYPE_CLASS_TEXT
                imeOptions = EditorInfo.IME_ACTION_DONE
                setSelection(text.length)
            }

        var syncing = false
        val newSwatch = View(context)

        fun showNew(argb: Int) {
            newSwatch.background = context.checkerSwatch(argb)
        }
        showNew(picker.color)

        picker.onColorChanged = { argb ->
            showNew(argb)
            if (!syncing) {
                syncing = true
                input.setText(ColorLiteral.format(argb, current))
                input.setSelection(input.text.length)
                syncing = false
            }
        }
        input.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (syncing) return
                    val parsed = ColorLiteral.parse(s?.toString()) ?: return
                    syncing = true
                    picker.setColor(parsed)
                    showNew(parsed)
                    syncing = false
                }

                override fun afterTextChanged(s: Editable?) = Unit
            },
        )

        val swatchHeight = context.dp(28)
        val container =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                val padding = context.dp(20)
                setPadding(padding, context.dp(12), padding, 0)
                addView(
                    buildComparisonRow(context, original, newSwatch, swatchHeight),
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                )
                addView(
                    picker,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.dp(4) },
                )
                addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                addView(buildUsedColorsRow(context, input, current), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }

        AlertDialog.Builder(context)
            .setTitle(labelWithKey(key))
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val raw = input.text.toString().trim()
                // 没改动就不写。这一点在锚点上尤其要紧：`*color2` 解析不成颜色，
                // 旧代码会先弹「颜色无效」；而如果放行，一按确定就把它写成字面量，
                // 主题里那套调色板链接从此断了，文件里再也看不出它原来指向谁。
                if (raw == current?.trim()) return@setPositiveButton
                if (ColorLiteral.parse(raw) == null) {
                    toast(R.string.color_scheme_color_invalid)
                    return@setPositiveButton
                }
                save(key, raw)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Original on the left, the picked colour on the right. */
    private fun buildComparisonRow(
        context: Context,
        original: Int,
        newSwatch: View,
        swatchHeight: Int,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(
            labelledSwatch(context, getString(R.string.color_scheme_picker_original), newSwatch, swatchHeight, original),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = context.dp(8) },
        )
        addView(
            labelledSwatch(context, getString(R.string.color_scheme_picker_new), newSwatch, swatchHeight, null),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
    }

    private fun labelledSwatch(
        context: Context,
        caption: String,
        swatch: View,
        swatchHeight: Int,
        original: Int?,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(
            TextView(context).apply {
                text = caption
                textSize = 12f
            },
        )
        if (original != null) {
            // The original is a separate, static swatch; only the new one is
            // reused by the picker callbacks.
            addView(
                View(context).apply { background = context.checkerSwatch(original) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, swatchHeight),
            )
        } else {
            addView(swatch, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, swatchHeight))
        }
    }

    /**
     * Swatches of the colours this scheme already uses, for pulling two keys
     * onto the same value without transcribing a hex string from another row.
     */
    private fun buildUsedColorsRow(
        context: Context,
        input: EditText,
        like: String?,
    ): View {
        val used = schemeColors.values.mapNotNull { ColorLiteral.parse(it) }.distinct()
        if (used.isEmpty()) return View(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, context.dp(10), 0, 0)
            addView(
                TextView(context).apply {
                    text = getString(R.string.color_scheme_picker_used)
                    textSize = 12f
                },
            )
            addView(
                HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(
                        LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            used.forEach { argb ->
                                addView(
                                    View(context).apply {
                                        background = context.checkerSwatch(argb)
                                        // Typing it into the field is enough: the
                                        // watcher moves the picker and the swatch.
                                        setOnClickListener { input.setText(ColorLiteral.format(argb, like)) }
                                    },
                                    LinearLayout.LayoutParams(context.dp(36), context.dp(28)).apply {
                                        marginEnd = context.dp(6)
                                    },
                                )
                            }
                        },
                    )
                },
            )
        }
    }

    /**
     * Writes one colour, then refreshes the list and the preview.
     */
    private fun save(
        key: String,
        value: String,
    ) = commit(
        write = { baseline -> ThemeSchemeWriter.setColorChecked(sourceFile, schemeId, key, value, baseline) },
        onWritten = {
            rebuild()
            host?.reloadBoard()
            toast(R.string.color_scheme_color_saved)
        },
    )

    /**
     * Runs one write and answers its three outcomes the same way everywhere.
     *
     * [write] is handed the baseline to compare against: [expectedText] makes
     * the writer refuse a file somebody else rewrote, `null` accepts whatever
     * is on disk now — which is exactly the retry after a conflict. The writes
     * are serialised for the reason the style editor serialises them: two saves
     * racing would make the second one's conflict check look at the first one's
     * result, and a scheme regenerated in two passes could land half-written.
     */
    private fun commit(
        write: (baseline: String?) -> ThemeSchemeWriter.SaveResult,
        onWritten: () -> Unit,
        onFailed: () -> Unit = { toast(R.string.style_write_failed) },
    ) {
        fun attempt(baseline: String?) {
            viewLifecycleOwner.lifecycleScope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        writeLock.withLock { write(baseline) }
                    }
                if (!isAdded || view == null) return@launch
                when (result.outcome) {
                    // The text the writer produced becomes the baseline for the
                    // next save, so a run of edits never reports a false conflict.
                    ThemeTextFile.WriteOutcome.WRITTEN -> {
                        expectedText = result.text.orEmpty()
                        // 「本次已改」数的是这一次会话里写成功的次数：它只回答
                        // 「我刚动过几处」，不参与任何写盘判断。
                        changedCount++
                        onWritten()
                        host?.refreshCaption()
                    }

                    ThemeTextFile.WriteOutcome.CONFLICT -> showConflictDialog { attempt(null) }

                    ThemeTextFile.WriteOutcome.FAILED -> onFailed()
                }
            }
        }
        attempt(expectedText.takeIf { it.isNotEmpty() })
    }

    /**
     * The file changed after this screen read it.
     *
     * Both answers are offered: read the file again (losing this one edit) or
     * replace it (losing whatever the other writer did). Choosing silently
     * would leave the rows showing colours that are no longer on disk.
     */
    private fun showConflictDialog(retry: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.style_conflict_title)
            .setMessage(getString(R.string.style_conflict_body, sourceFile.name))
            .setPositiveButton(R.string.style_conflict_overwrite) { _, _ -> retry() }
            .setNeutralButton(R.string.style_conflict_reload) { _, _ -> reloadFromDisk() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun reloadFromDisk() {
        rebuild()
        host?.reloadBoard()
        toast(R.string.style_reload_done)
    }

    /** `候选区背景 · back_color`, or the bare key when it has no label. */
    private fun labelWithKey(key: String): String {
        val label = COLOR_LABELS[key]?.let { getString(it) } ?: return key
        return "$label · $key"
    }

    /** `name` / `author` are metadata, not colours. */
    private fun String.isColorKey(): Boolean = this != "name" && this != "author" && this != "light_scheme" && this != "dark_scheme"

    private fun toast(resId: Int) {
        Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show()
    }

    private fun toast(text: CharSequence) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val ARG_THEME_ID = "theme_id"
        private const val ARG_SCHEME_ID = "scheme_id"

        /** Preference keys, so the three rows are findable from a test. */
        private const val KEY_SCHEME = "scheme"
        private const val KEY_NEW = "scheme_new"
        private const val KEY_RENAME = "scheme_rename"
        private const val KEY_DELETE = "scheme_delete"
        private const val KEY_PREVIEW_NIGHT = "preview_night"

        /** What [ThemeSchemeWriter] can read back as a scheme id. */
        private val SCHEME_ID = Regex("""^[A-Za-z0-9_.\-]+$""")

        /** Everything [SCHEME_ID] accepts, and nothing else. */
        private const val ID_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_.-"

        /** Characters [SCHEME_ID] would reject, for turning a name into an id. */
        private val NOT_IN_ID = Regex("""[^A-Za-z0-9_.\-]""")

        /** Entries that describe a scheme rather than paint with it. */
        private val METADATA_KEYS = setOf("name", "author", "light_scheme", "dark_scheme")

        /** The scheme whose key set defines the complete list. */
        const val TEMPLATE_SCHEME = "default"

        /** Human-readable names for the colour keys a theme uses. */
        val COLOR_LABELS: Map<String, Int> =
            mapOf(
                "back_color" to R.string.color_label_back_color,
                "border_color" to R.string.color_label_border_color,
                "candidate_background" to R.string.color_label_candidate_background,
                "candidate_border_color" to R.string.color_label_candidate_border_color,
                "candidate_separator_color" to R.string.color_label_candidate_separator_color,
                "candidate_text_color" to R.string.color_label_candidate_text_color,
                "comment_text_color" to R.string.color_label_comment_text_color,
                "hilited_back_color" to R.string.color_label_hilited_back_color,
                "hilited_candidate_back_color" to R.string.color_label_hilited_candidate_back_color,
                "hilited_candidate_button_color" to R.string.color_label_hilited_candidate_button_color,
                "hilited_candidate_text_color" to R.string.color_label_hilited_candidate_text_color,
                "hilited_comment_text_color" to R.string.color_label_hilited_comment_text_color,
                "hilited_key_back_color" to R.string.color_label_hilited_key_back_color,
                "hilited_key_border_color" to R.string.color_label_hilited_key_border_color,
                "hilited_key_symbol_color" to R.string.color_label_hilited_key_symbol_color,
                "hilited_key_text_color" to R.string.color_label_hilited_key_text_color,
                "hilited_label_color" to R.string.color_label_hilited_label_color,
                "hilited_off_key_back_color" to R.string.color_label_hilited_off_key_back_color,
                "hilited_off_key_border_color" to R.string.color_label_hilited_off_key_border_color,
                "hilited_off_key_symbol_color" to R.string.color_label_hilited_off_key_symbol_color,
                "hilited_off_key_text_color" to R.string.color_label_hilited_off_key_text_color,
                "hilited_on_key_back_color" to R.string.color_label_hilited_on_key_back_color,
                "hilited_on_key_border_color" to R.string.color_label_hilited_on_key_border_color,
                "hilited_on_key_symbol_color" to R.string.color_label_hilited_on_key_symbol_color,
                "hilited_on_key_text_color" to R.string.color_label_hilited_on_key_text_color,
                "hilited_popup_back_color" to R.string.color_label_hilited_popup_back_color,
                "hilited_popup_text_color" to R.string.color_label_hilited_popup_text_color,
                "hilited_text_color" to R.string.color_label_hilited_text_color,
                "key_back_color" to R.string.color_label_key_back_color,
                "key_border_color" to R.string.color_label_key_border_color,
                "key_symbol_color" to R.string.color_label_key_symbol_color,
                "key_text_color" to R.string.color_label_key_text_color,
                "keyboard_back_color" to R.string.color_label_keyboard_back_color,
                "keyboard_background" to R.string.color_label_keyboard_background,
                "label_color" to R.string.color_label_label_color,
                "liquid_keyboard_background" to R.string.color_label_liquid_keyboard_background,
                "long_text_back_color" to R.string.color_label_long_text_back_color,
                "long_text_color" to R.string.color_label_long_text_color,
                "navbar_background" to R.string.color_label_navbar_background,
                "normal_mode_color" to R.string.color_label_normal_mode_color,
                "off_key_back_color" to R.string.color_label_off_key_back_color,
                "off_key_border_color" to R.string.color_label_off_key_border_color,
                "off_key_symbol_color" to R.string.color_label_off_key_symbol_color,
                "off_key_text_color" to R.string.color_label_off_key_text_color,
                "on_key_back_color" to R.string.color_label_on_key_back_color,
                "on_key_border_color" to R.string.color_label_on_key_border_color,
                "on_key_symbol_color" to R.string.color_label_on_key_symbol_color,
                "on_key_text_color" to R.string.color_label_on_key_text_color,
                "popup_back_color" to R.string.color_label_popup_back_color,
                "popup_text_color" to R.string.color_label_popup_text_color,
                "root_background" to R.string.color_label_root_background,
                "shadow_color" to R.string.color_label_shadow_color,
                "text_back_color" to R.string.color_label_text_back_color,
                "text_color" to R.string.color_label_text_color,
            )

        fun newInstance(
            themeId: String,
            schemeId: String,
        ): ColorSchemeFragment = ColorSchemeFragment().apply {
            arguments =
                Bundle().apply {
                    putString(ARG_THEME_ID, themeId)
                    putString(ARG_SCHEME_ID, schemeId)
                }
        }
    }
}
