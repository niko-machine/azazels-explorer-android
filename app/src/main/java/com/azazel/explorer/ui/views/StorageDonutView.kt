package com.azazel.explorer.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.azazel.explorer.R

class StorageDonutView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 40f
        strokeCap = Paint.Cap.BUTT
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 60f
        isFakeBoldText = true
    }

    private var data: List<CategoryData> = emptyList()
    private var totalBytes: Long = 0
    private var usedBytes: Long = 0
    private val rect = RectF()

    data class CategoryData(val bytes: Long, val colorRes: Int, val label: String)

    fun setData(total: Long, used: Long, categories: List<CategoryData>) {
        this.totalBytes = total
        this.usedBytes = used
        this.data = categories
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = minOf(width, height).toFloat()
        val margin = paint.strokeWidth / 2 + 20f
        rect.set(margin, margin, size - margin, size - margin)

        // Background (Available/Empty) - Make it more noticeable
        paint.color = ContextCompat.getColor(context, R.color.surface_secondary)
        paint.alpha = 128 // Semi-transparent for better depth
        canvas.drawArc(rect, 0f, 360f, false, paint)
        paint.alpha = 255 // Reset alpha for segments

        if (totalBytes <= 0) return

        var startAngle = -90f
        data.forEach { category ->
            if (category.bytes > 0) {
                val sweepAngle = (category.bytes.toFloat() / totalBytes.toFloat()) * 360f
                paint.color = ContextCompat.getColor(context, category.colorRes)
                canvas.drawArc(rect, startAngle, sweepAngle, false, paint)
                startAngle += sweepAngle
            }
        }

        // Percentage Text
        val percentage = ((usedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt()
        textPaint.color = ContextCompat.getColor(context, R.color.text_primary)
        canvas.drawText("$percentage%", width / 2f, height / 2f + 20f, textPaint)
    }
}
