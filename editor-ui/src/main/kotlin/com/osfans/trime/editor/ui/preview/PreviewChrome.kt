/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.ui.preview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.LinearLayout
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.ToolBar
import com.osfans.trime.editor.ui.R
import com.osfans.trime.editor.ui.dp
import kotlin.math.roundToInt

/**
 * 一整块输入区：候选栏 + 键盘 + 底部内边距。
 *
 * 只画键盘会误导人。候选栏不在 [com.osfans.trime.ime.keyboard.Keyboard] 里，
 * `keyboard_padding_bottom` 那一条更是空的——两者都由输入法的 `InputView` 摆。
 * 调「键盘高度」时看不见它们在总高里占多少，就判断不了自己改出来的是不是想要的样子。
 *
 * 候选栏按主题真实画：底色 `candidate_background`、边框 `candidate_border_color`、
 * 圆角 `candidate_border_round`、边宽 `candidate_border`，与输入法的 `InputBarDelegate`
 * 用的是同一组数。工具条按 `tool_bar` 的按钮数量与尺寸画出占位框——图标是主题自带的
 * 图片或字体，编辑器这边画不出来，但按钮有几个、占多宽，是能看见的。
 *
 * 底部那条带子是纯示意：淡填充 + 名字 + 高度，高度为 0 时只留一条虚线。
 */
class PreviewChrome(context: Context) : LinearLayout(context) {
    private val candidateBar = CandidateBar(context)

    private val bottomPad = Band(context, context.getString(R.string.preview_band_bottom))

    init {
        orientation = VERTICAL
        addView(candidateBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(bottomPad, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** 把键盘放进两条带子之间；再调用只替换中间那个。 */
    fun wrap(board: View) {
        if (childCount == 3) removeViewAt(1)
        addView(board, 1, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /**
     * 按 [theme] 摆两条带子。
     *
     * 候选栏高度取 `candidate_view_height + comment_height`：输入法的
     * `InputBarDelegate` 就是这两个数相加当整条的高度。
     */
    fun applyStyle(theme: Theme) {
        candidateBar.show(theme)
        bottomPad.show(context.dp(theme.style.keyboardPaddingBottom), theme.style.keyboardPaddingBottom)
    }

    /** 候选栏：真实底色 + 真实边框 + 工具条占位。 */
    private class CandidateBar(context: Context) : View(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        private val line =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = context.dp(1).toFloat()
            }

        private val label =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = context.dp(9).toFloat()
                textAlign = Paint.Align.CENTER
            }

        private val rect = RectF()
        private val box = RectF()

        private var heightPx = 0
        private var background = 0xFFF2F2F2.toInt()
        private var borderColor = 0
        private var glyphColor = 0xFF333333.toInt()
        private var borderWidth = 0f
        private var radius = 0f
        private var title = ""

        private var primary: ToolBar.Button? = null
        private var buttons: List<ToolBar.Button> = emptyList()
        private var spacing = 0
        private var buttonSize = 0

        fun show(theme: Theme) {
            val style = theme.style
            val bar = style.candidateViewHeight + style.commentHeight
            heightPx = context.dp(bar)
            title = context.getString(R.string.preview_band_candidate) + " " + bar
            background = color("candidate_background", 0xFFF2F2F2.toInt())
            borderColor = color("candidate_border_color", 0x22000000)
            glyphColor = color("candidate_text_color", 0xFF333333.toInt())
            borderWidth = context.dp(style.candidateBorder).toFloat()
            radius = context.dp(style.candidateBorderRound.roundToInt()).toFloat()

            primary = theme.toolBar.primaryButton
            buttons = theme.toolBar.buttons
            spacing = context.dp(theme.toolBar.buttonSpacing)
            buttonSize = context.dp(bar)
            requestLayout()
            invalidate()
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), heightPx.coerceAtLeast(context.dp(2)))
        }

        override fun onDraw(canvas: Canvas) {
            val inset = borderWidth / 2f
            rect.set(inset, inset, width - inset, height - inset)
            fill.color = background
            canvas.drawRoundRect(rect, radius, radius, fill)
            if (borderWidth > 0f) {
                line.strokeWidth = borderWidth
                line.color = borderColor
                canvas.drawRoundRect(rect, radius, radius, line)
            }

            // 右侧按钮从右往左排（输入法的 ButtonsBarUi 就是 ROW_REVERSE）。
            var right = width - spacing
            buttons.forEach { config ->
                val size = sizeOf(config)
                drawChip(canvas, (right - size).toFloat(), size, config)
                right -= size + spacing
            }
            val primaryButton = primary
            val primarySize = primaryButton?.let { sizeOf(it) }
            if (primaryButton != null && primarySize != null) {
                drawChip(canvas, spacing.toFloat(), primarySize, primaryButton)
            }

            // 高度写在中缝：没有候选项时这里本来就是空的。
            val leftEdge = spacing + (primarySize?.plus(spacing) ?: 0)
            val rightEdge = right + spacing
            if (rightEdge - leftEdge > context.dp(70)) {
                label.color = (glyphColor and 0x00FFFFFF) or 0x70000000
                val baseline = height / 2f - (label.descent() + label.ascent()) / 2f
                canvas.drawText(title, (leftEdge + rightEdge) / 2f, baseline, label)
            }
        }

        private fun drawChip(
            canvas: Canvas,
            left: Float,
            size: Int,
            config: ToolBar.Button,
        ) {
            val top = (height - size) / 2f
            box.set(left, top, left + size, top + size)
            val circle = config.background.type == ToolBar.Button.Background.Type.CIRCLE
            val chip = config.background.normal.takeIf { it.isNotEmpty() }?.let { color(it, Color.TRANSPARENT) }
            if (chip != null && Color.alpha(chip) > 0) {
                fill.color = chip
                if (circle) canvas.drawCircle(box.centerX(), box.centerY(), size / 2f, fill) else canvas.drawRoundRect(box, cornerOf(config), cornerOf(config), fill)
            }
            // 图标画不出来（主题自带的图片/字体），但按钮在哪儿、有多大要看得见。
            line.color = (glyphColor and 0x00FFFFFF) or 0x59000000
            if (circle) canvas.drawCircle(box.centerX(), box.centerY(), size / 2f, line) else canvas.drawRoundRect(box, cornerOf(config), cornerOf(config), line)
        }

        private fun cornerOf(config: ToolBar.Button): Float = context.dp(config.background.cornerRadius.toInt()).toFloat()

        /** 与 `ButtonsBarUi.getButtonSize` 同规则：写了 `size` 就用写的，否则正方形。 */
        private fun sizeOf(config: ToolBar.Button): Int {
            val declared = config.size.takeIf { it.size == 2 } ?: return buttonSize
            return context.dp(declared[0].coerceAtLeast(0))
        }

        private fun color(
            key: String,
            fallback: Int,
        ): Int = runCatching { ColorManager.getColor(key) }.getOrDefault(fallback)
    }

    /**
     * 底部内边距的示意带子。
     *
     * 高度为 0 时仍留 [line] 高的一条虚线：主题没写 `keyboard_padding_bottom` 时
     * 「这里没有东西」本身就是要看见的信息。
     */
    private class Band(
        context: Context,
        private val title: String,
    ) : View(context) {
        private val fill = Paint().apply { color = 0x14808080 }

        private val rule =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = context.dp(1).toFloat()
                color = 0x40808080
                pathEffect = DashPathEffect(floatArrayOf(context.dp(4).toFloat(), context.dp(4).toFloat()), 0f)
            }

        private val label =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = context.dp(9).toFloat()
                color = 0xFF6B6B6B.toInt()
            }

        private val line = context.dp(2)
        private val labelMin = context.dp(16)
        private val inset = context.dp(6).toFloat()

        private var heightPx = 0
        private var valueDp = 0

        fun show(
            px: Int,
            dp: Int,
        ) {
            heightPx = px
            valueDp = dp
            requestLayout()
            invalidate()
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), heightPx.coerceAtLeast(line))
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
            canvas.drawLine(0f, height - 1f, width.toFloat(), height - 1f, rule)
            if (height >= labelMin) {
                val baseline = height / 2f - (label.descent() + label.ascent()) / 2f
                canvas.drawText("$title $valueDp", inset, baseline, label)
            }
        }
    }
}
