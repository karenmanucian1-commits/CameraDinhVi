package com.cameradinhvi.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.min

/** Nút chụp tròn: trắng = chụp ảnh, đỏ = quay video, ô vuông đỏ = đang quay. */
class ShutterView(ctx: Context) : View(ctx) {
    var videoMode = false
        set(v) { field = v; invalidate() }
    var recording = false
        set(v) { field = v; invalidate() }

    private val dens = ctx.resources.displayMetrics.density
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f * dens; color = 0xFFFFFFFF.toInt()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 3f * dens
        c.drawCircle(cx, cy, r, ring)
        fill.color = if (videoMode) 0xFFE53935.toInt() else 0xFFFFFFFF.toInt()
        if (recording) {
            val h = r * 0.45f
            c.drawRoundRect(cx - h, cy - h, cx + h, cy + h, 6f * dens, 6f * dens, fill)
        } else {
            c.drawCircle(cx, cy, r - 7f * dens, fill)
        }
    }
}
