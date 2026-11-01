/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.editor.workbench

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt

/**
 * A row of colour pills: what a theme looks like before it is drawn in full.
 *
 * Not a shrunken keyboard. `ColorManager` holds one theme at a time, so a list
 * of live boards would either re-attach the theme on every row — turning a
 * scroll into a reload — or paint every row with the last theme loaded. Colour
 * pills read straight from the scheme map, so each row shows its own theme.
 */
class SwatchStripView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    @ColorInt
    private var colors: List<Int> = emptyList()

    fun setColors(@ColorInt value: List<Int>) {
        colors = value
        invalidate()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(HEIGHT_DP))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (colors.isEmpty() || width <= 0) return
        val gap = dp(GAP_DP).toFloat()
        val total = width.toFloat()
        val slot = (total - gap * (colors.size - 1)) / colors.size
        val radius = height / 2f
        colors.forEachIndexed { index, color ->
            val left = index * (slot + gap)
            rect.set(left, 0f, left + slot, height.toFloat())
            paint.color = color
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val HEIGHT_DP = 10
        const val GAP_DP = 3
    }
}
