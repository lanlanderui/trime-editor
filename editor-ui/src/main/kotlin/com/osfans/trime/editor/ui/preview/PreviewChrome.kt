/*
 * SPDX-FileCopyrightText: 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.ui.preview

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.View
import android.widget.LinearLayout
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.editor.ui.R
import com.osfans.trime.editor.ui.dp

/**
 * 一整块输入区：候选栏 + 键盘 + 底部内边距。
 *
 * 只画键盘会误导人。候选栏不在 [com.osfans.trime.ime.keyboard.Keyboard] 里，
 * `keyboard_padding_bottom` 那一条更是空的——两者都由输入法的 `InputView` 摆。
 * 调「键盘高度」时看不见它们在总高里占多少，就判断不了自己改出来的是不是想要的样子。
 *
 * 两条带子是**示意**，不是候选栏的真实长相：填充加名字加高度，好让人一眼看出
 * 「这条是空的，占 12」；真去画候选项反而会被当成预览的一部分。
 */
class PreviewChrome(context: Context) : LinearLayout(context) {
    private val candidateBar = Band(context, context.getString(R.string.preview_band_candidate))

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
     * 按主题的样式摆两条带子。
     *
     * 候选栏高度取 `candidate_view_height + comment_height`：输入法的
     * `InputBarDelegate` 就是这两个数相加当整条的高度。
     */
    fun applyStyle(style: GeneralStyle) {
        val bar = style.candidateViewHeight + style.commentHeight
        candidateBar.show(context.dp(bar), bar)
        bottomPad.show(context.dp(style.keyboardPaddingBottom), style.keyboardPaddingBottom)
    }

    /**
     * 一条示意图带子。
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
