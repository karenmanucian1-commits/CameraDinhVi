package com.cameradinhvi.app

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.max

/**
 * Vẽ watermark lên Canvas trong khung [r]. Kích thước tính theo chiều rộng khung
 * nên ảnh chụp và khung xem trước có tỉ lệ y hệt nhau.
 */
class WatermarkRenderer {
    private val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); typeface = bold }
    private val smallPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val linePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E1E1E.toInt(); typeface = bold }
    private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC107.toInt() }

    fun draw(c: Canvas, r: RectF, d: WmData) {
        if (r.width() <= 0f || r.height() <= 0f) return
        val u = r.width() / 360f * d.sizePct / 100f
        val m = 12f * u
        val shadow = 0x99000000.toInt()

        timePaint.textSize = 34f * u
        smallPaint.textSize = 13f * u
        linePaint.textSize = 12.5f * u
        titlePaint.textSize = 13f * u
        timePaint.setShadowLayer(3f * u, 0f, u, shadow)
        smallPaint.setShadowLayer(2.5f * u, 0f, u, shadow)
        linePaint.setShadowLayer(2.5f * u, 0f, u, shadow)

        val maxW = max(1, (r.width() - 2 * m).toInt())

        // Tiêu đề
        val hasTitle = d.title.isNotBlank()
        val tfm = titlePaint.fontMetrics
        val titleTextH = tfm.descent - tfm.ascent
        val titleH = titleTextH + 8f * u
        val titleStr = if (hasTitle)
            TextUtils.ellipsize(d.title, titlePaint, maxW - 12f * u, TextUtils.TruncateAt.END).toString()
        else ""

        // Hàng giờ
        val tm = timePaint.fontMetrics
        val timeH = tm.descent - tm.ascent
        val sm = smallPaint.fontMetrics
        val sLineH = sm.descent - sm.ascent
        val rowH = max(timeH, 2 * sLineH)

        // Các dòng phụ
        val layouts = d.lines.filter { it.isNotBlank() }.map {
            StaticLayout.Builder.obtain(it, 0, it.length, linePaint, maxW)
                .setLineSpacing(0f, 1.05f)
                .build()
        }
        var linesH = 0f
        layouts.forEach { linesH += it.height + 3f * u }

        var total = rowH
        if (hasTitle) total += titleH + 6f * u
        if (layouts.isNotEmpty()) total += 4f * u + linesH

        val x = r.left + m
        var y = if (d.atTop) r.top + m else r.bottom - m - total

        if (hasTitle) {
            val tw = titlePaint.measureText(titleStr)
            c.drawRoundRect(x, y, x + tw + 12f * u, y + titleH, 4f * u, 4f * u, accent)
            c.drawText(titleStr, x + 6f * u, y + 4f * u - tfm.ascent, titlePaint)
            y += titleH + 6f * u
        }

        // Giờ to + vạch vàng + ngày/thứ
        c.drawText(d.time, x, y + (rowH - timeH) / 2f - tm.ascent, timePaint)
        val barX = x + timePaint.measureText(d.time) + 8f * u
        c.drawRect(barX, y + rowH * 0.14f, barX + 3f * u, y + rowH * 0.86f, accent)
        val colX = barX + 11f * u
        val colTop = y + (rowH - 2 * sLineH) / 2f
        c.drawText(d.date, colX, colTop - sm.ascent, smallPaint)
        c.drawText(d.weekday, colX, colTop + sLineH - sm.ascent, smallPaint)
        y += rowH + 4f * u

        for (l in layouts) {
            c.save()
            c.translate(x, y)
            l.draw(c)
            c.restore()
            y += l.height + 3f * u
        }
    }
}
