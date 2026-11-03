/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.data.theme.ThemeStyleWriter
import com.osfans.trime.data.theme.tb.RawToolBar
import com.osfans.trime.data.theme.tb.ThemeToolBarReader
import com.osfans.trime.data.theme.tb.ThemeToolBarWriter
import com.osfans.trime.data.theme.tb.ToolBarButton
import com.osfans.trime.data.theme.tb.ToolBarCatalog
import com.osfans.trime.data.theme.tb.ToolBarModel
import com.osfans.trime.data.theme.tb.ToolBarSlot
import com.osfans.trime.editor.databinding.ActivityToolBarBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The candidate bar's buttons: their order, their look and what they do.
 *
 * Two facts decide how this screen behaves, and both come from the file rather
 * than from taste:
 *
 * - `buttons:` is written **right to left** (`ButtonsBarUi` lays the middle
 *   segment out with `ROW_REVERSE`), so every move goes through [ToolBarSlot].
 *   Getting this wrong looks correct in the list and makes a button jump to the
 *   far end on the keyboard.
 * - A button is a switch only when its `option_styles` has exactly two entries.
 *   One style is decoration, three is a cycle, and neither can be drawn as on/off.
 *
 * Every edit writes immediately. The toolbar has no "save" moment to collect
 * changes into: a button's action and its position are separate lines in the
 * file, and holding them in memory would mean re-implementing merge semantics
 * for something that is one line per change.
 */
class ToolBarActivity : AppCompatActivity() {
    private lateinit var binding: ActivityToolBarBinding

    private var themeId: String = ""
    private var themeName: String = ""
    private var sourceFile: File? = null
    private var raw: RawToolBar = RawToolBar.EMPTY
    private var model: ToolBarModel? = null

    /** One edit can commit twice (IME done, then focus loss); only the first writes. */
    private var writing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityToolBarBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        themeId = intent.getStringExtra(EXTRA_THEME_ID).orEmpty()
        themeName = intent.getStringExtra(EXTRA_THEME_NAME).orEmpty()
        supportActionBar?.title = themeName.ifEmpty { getString(R.string.app_name) }
        supportActionBar?.subtitle = getString(R.string.tb_title)
        if (themeId.isEmpty()) {
            finish()
            return
        }
        load()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.editor, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_back_to_ime)?.isVisible = InputMethodApp.isInstalled(this)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }

            R.id.action_back_to_ime -> {
                InputMethodApp.launch(this)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    // ---------------------------------------------------------------- loading

    private fun load() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { read() }
            if (isFinishing || isDestroyed) return@launch
            if (!loaded) {
                toast(R.string.tb_load_failed)
                return@launch
            }
            render()
        }
    }

    private fun read(): Boolean {
        val file = ThemeStyleWriter.editableSourceFile(themeId) ?: return false
        sourceFile = file
        raw = ThemeToolBarReader.read(file) ?: return false
        model = ToolBarCatalog.build(raw)
        return true
    }

    // -------------------------------------------------------------- rendering

    private fun render() {
        val model = model ?: return
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(28))
        }
        val scroll = ScrollView(this).apply { addView(column) }
        binding.content.removeAllViews()
        binding.content.addView(
            scroll,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT),
        )

        binding.preview.removeAllViews()
        if (!model.exists) {
            column.addView(
                text(getString(R.string.tb_no_section), secondary = true),
            )
            return
        }
        if (!model.editable) {
            column.addView(text(getString(R.string.tb_unsupported, model.unsupported.orEmpty()), warn = true))
            return
        }

        drawPreview(model)

        column.addView(heading(R.string.tb_section_metrics))
        ToolBarCatalog.SCALARS.forEach { name ->
            column.addView(
                fieldRow(
                    label = getString(scalarLabel(name)),
                    value = model.scalars[name].orEmpty(),
                    numeric = name == "button_spacing",
                ) { value -> writeScalar(name, value) },
            )
        }

        column.addView(heading(R.string.tb_section_primary))
        model.primary?.let { primary ->
            ToolBarCatalog.PRIMARY_FIELDS.forEach { path ->
                column.addView(
                    fieldRow(
                        label = getString(fieldLabel(path)),
                        value = primary.value(path),
                    ) { value -> writeBlockField(RawToolBar.PRIMARY, path, value) },
                )
            }
        }

        column.addView(heading(R.string.tb_section_buttons))
        if (model.buttons.isEmpty()) column.addView(text(getString(R.string.tb_no_buttons), secondary = true))
        model.buttons.forEach { button ->
            column.addView(buttonRow(button, model.buttons.size))
        }

        if (model.unreferenced.isNotEmpty()) {
            column.addView(heading(R.string.tb_section_unreferenced))
            column.addView(text(getString(R.string.tb_unreferenced_hint), secondary = true))
            model.unreferenced.forEach { id ->
                column.addView(unreferencedRow(id))
            }
        }
    }

    /**
     * A schematic of the bar: primary pinned left, then the buttons in the order
     * they are drawn.
     *
     * Deliberately not a pixel render — the buttons are drawn with an icon font
     * the editor does not ship, so a drawn copy would only look like the theme by
     * coincidence. What it does show is the two things the file cannot: the order
     * and which buttons are switches.
     */
    private fun drawPreview(model: ToolBarModel) {
        fun chip(label: String, twoState: Boolean, primary: Boolean) {
            val chip =
                TextView(this).apply {
                    text = if (twoState) "$label ⇄" else label
                    textSize = 12f
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    setTextColor(
                        ContextCompat.getColor(
                            context,
                            if (primary) R.color.chip_text_selected else R.color.chip_text,
                        ),
                    )
                    background =
                        ContextCompat.getDrawable(
                            context,
                            if (primary) R.drawable.bg_chip_selected else R.drawable.bg_chip,
                        )
                }
            binding.preview.addView(
                chip,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = dp(6) },
            )
        }
        model.primary?.let { chip(previewLabel(it), it.twoState, primary = true) }
        model.buttons.forEach { chip(previewLabel(it), it.twoState, primary = false) }
        if (model.buttons.isEmpty() && model.primary == null) {
            binding.preview.addView(text(getString(R.string.tb_no_buttons), secondary = true))
        }
    }

    /** `ic@keyboard_close` → `keyboard_close`; a plain action stays as it is. */
    private fun previewLabel(button: ToolBarButton): String {
        val style = button.style.removePrefix("ic@")
        if (style.isNotEmpty()) return style
        return button.action.ifEmpty { button.blockId ?: button.source }
    }

    private fun heading(resId: Int): TextView = TextView(this).apply {
        text = getString(resId)
        textSize = 13f
        setPadding(0, dp(18), 0, dp(4))
        setTextColor(ContextCompat.getColor(context, R.color.chip_text))
    }

    private fun text(
        value: String,
        secondary: Boolean = false,
        warn: Boolean = false,
    ): TextView = TextView(this).apply {
        text = value
        textSize = 13f
        setPadding(0, dp(6), 0, dp(6))
        setTextColor(
            ContextCompat.getColor(
                context,
                when {
                    warn -> R.color.stale_text
                    secondary -> R.color.chip_text
                    else -> R.color.chip_text
                },
            ),
        )
    }

    /** One label + one text field that writes on focus loss or IME "done". */
    private fun fieldRow(
        label: String,
        value: String,
        numeric: Boolean = false,
        onCommit: (String) -> Unit,
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(
            TextView(context).apply {
                text = label
                textSize = 12f
                setPadding(0, dp(8), 0, dp(2))
                setTextColor(ContextCompat.getColor(context, R.color.chip_text))
            },
        )
        val field =
            EditText(context).apply {
                setText(value)
                textSize = 15f
                setSingleLine(true)
                background = null
                setPadding(0, dp(6), 0, dp(6))
                inputType =
                    if (numeric) {
                        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    } else {
                        InputType.TYPE_CLASS_TEXT
                    }
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                setOnEditorActionListener { view, _, _ ->
                    commit(view as EditText, value, onCommit)
                    true
                }
                setOnFocusChangeListener { view, focused ->
                    if (!focused && view is EditText) commit(view, value, onCommit)
                }
            }
        addView(field, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun commit(
        field: EditText,
        old: String,
        onCommit: (String) -> Unit,
    ) {
        val next = field.text.toString().trim()
        if (next != old) onCommit(next)
    }

    /** One button: what it is, what a tap does, and the two order controls. */
    private fun buttonRow(
        button: ToolBarButton,
        total: Int,
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(10), 0, dp(10))

        addView(
            TextView(context).apply {
                text =
                    buildString {
                        append(button.blockId ?: button.source)
                        if (button.twoState) append("　").append(getString(R.string.tb_two_state))
                    }
                textSize = 15f
                setTextColor(ContextCompat.getColor(context, R.color.chip_text))
            },
        )
        addView(
            TextView(context).apply {
                text =
                    getString(
                        R.string.tb_button_subtitle,
                        button.action.ifEmpty { "—" },
                        previewLabel(button),
                    )
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.chip_text))
            },
        )

        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                if (button.editable) {
                    addView(smallButton(R.string.tb_edit) { editButton(button) })
                }
                addView(
                    smallButton(R.string.tb_move_left) { move(button, -1, total) }.apply {
                        isEnabled = button.visualIndex > 0
                    },
                )
                addView(
                    smallButton(R.string.tb_move_right) { move(button, 1, total) }.apply {
                        isEnabled = button.visualIndex < total - 1
                    },
                )
                addView(smallButton(R.string.tb_remove_from_list) { removeFromList(button) })
            },
        )
    }

    private fun unreferencedRow(id: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(6), 0, dp(6))
        addView(
            TextView(context).apply {
                text = id
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.chip_text))
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        addView(smallButton(R.string.tb_add_to_list) { addToList(id) })
        addView(smallButton(R.string.tb_delete_block) { deleteBlock(id) })
    }

    private fun smallButton(
        titleRes: Int,
        onClick: () -> Unit,
    ): Button = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
        text = getString(titleRes)
        textSize = 12f
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(8), dp(2), dp(8), dp(2))
        setOnClickListener { onClick() }
    }

    // ------------------------------------------------------------------ edits

    private fun writeScalar(
        key: String,
        value: String,
    ) {
        val file = sourceFile ?: return
        write { ThemeToolBarWriter.setScalar(file, key, ThemeStyleWriter.formatScalar(value)) }
    }

    private fun writeBlockField(
        blockId: String,
        path: List<String>,
        value: String,
    ) {
        val file = sourceFile ?: return
        write { ThemeToolBarWriter.setBlockField(file, blockId, path, ThemeStyleWriter.formatScalar(value)) }
    }

    private fun writeButtons(sources: List<String>) {
        val file = sourceFile ?: return
        write { ThemeToolBarWriter.setButtons(file, sources) }
    }

    private fun move(
        button: ToolBarButton,
        delta: Int,
        total: Int,
    ) {
        val target = button.visualIndex + delta
        if (target !in 0 until total) return
        val sources = raw.buttons.map { it.source }
        writeButtons(ToolBarSlot.move(sources, button.visualIndex, target))
    }

    private fun removeFromList(button: ToolBarButton) {
        val file = sourceFile ?: return
        val sources = ToolBarSlot.remove(raw.buttons.map { it.source }, button.visualIndex)
        // The block stays in the file: the button is no longer drawn, and the
        // definition can be put back from the "unreferenced" section.
        write { ThemeToolBarWriter.setButtons(file, sources) }
    }

    private fun addToList(id: String) {
        writeButtons(raw.buttons.map { it.source } + "*$id")
    }

    private fun deleteBlock(id: String) {
        val file = sourceFile ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.tb_delete_block)
            .setMessage(getString(R.string.tb_delete_block_body, id))
            .setPositiveButton(R.string.tb_delete_block) { _, _ ->
                write { ThemeToolBarWriter.removeBlock(file, id) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** The dialog for one button's look and actions. */
    private fun editButton(button: ToolBarButton) {
        val id = button.blockId ?: return
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val fields = LinkedHashMap<List<String>, EditText>()
        ToolBarCatalog.BUTTON_FIELDS.forEach { path ->
            column.addView(
                TextView(this).apply {
                    text = getString(fieldLabel(path))
                    textSize = 12f
                    setPadding(0, dp(8), 0, dp(2))
                    setTextColor(ContextCompat.getColor(context, R.color.chip_text))
                },
            )
            val field =
                EditText(this).apply {
                    setText(button.value(path))
                    textSize = 15f
                    setSingleLine(true)
                    inputType = InputType.TYPE_CLASS_TEXT
                }
            fields[path] = field
            column.addView(field)
        }
        val scroll = ScrollView(this).apply { addView(column) }
        AlertDialog.Builder(this)
            .setTitle(id)
            .setView(scroll)
            .setPositiveButton(R.string.tb_save) { _, _ ->
                // Written one field at a time: each is its own line, and a rejected
                // value (an unparsable list) should not stop the others.
                fields.forEach { (path, field) ->
                    val next = field.text.toString().trim()
                    if (next != button.value(path)) writeBlockField(id, path, next)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun write(action: () -> ThemeToolBarWriter.Result) {
        if (sourceFile == null || writing) return
        writing = true
        lifecycleScope.launch {
            val result =
                try {
                    withContext(Dispatchers.IO) { action() }
                } finally {
                    writing = false
                }
            if (isFinishing || isDestroyed) return@launch
            when (result) {
                is ThemeToolBarWriter.Result.Success -> {
                    if (read()) render()
                    toast(R.string.tb_saved)
                }

                is ThemeToolBarWriter.Result.Failure ->
                    AlertDialog.Builder(this@ToolBarActivity)
                        .setTitle(R.string.tb_save_failed)
                        .setMessage(result.reason)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
            }
        }
    }

    private fun ToolBarButton.value(path: List<String>): String = when (path) {
        listOf("foreground", "style") -> style
        listOf("foreground", "option_styles") -> if (optionStyles.isEmpty()) "" else optionStyles.joinToString(", ")
        listOf("action") -> action
        listOf("long_press_action") -> longPressAction
        else -> ""
    }

    private fun scalarLabel(name: String): Int = when (name) {
        "button_spacing" -> R.string.tb_field_button_spacing
        "button_font" -> R.string.tb_field_button_font
        else -> R.string.tb_field_back_style
    }

    private fun fieldLabel(path: List<String>): Int = when {
        path == listOf("foreground", "style") -> R.string.tb_field_style
        path == listOf("foreground", "option_styles") -> R.string.tb_field_option_styles
        path == listOf("action") -> R.string.tb_field_action
        else -> R.string.tb_field_long_press
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    companion object {
        private const val EXTRA_THEME_ID = "theme_id"
        private const val EXTRA_THEME_NAME = "theme_name"

        fun intent(
            context: Context,
            themeId: String,
            themeName: String,
        ): Intent = Intent(context, ToolBarActivity::class.java)
            .putExtra(EXTRA_THEME_ID, themeId)
            .putExtra(EXTRA_THEME_NAME, themeName)
    }
}
