package cn.awbz.davplayer.ui

import android.graphics.*
import android.graphics.drawable.Drawable

/** Canvas icons work on API 18 without vector-drawable dependencies. */
class PlayerIcon(private val kind: Kind) : Drawable() {
    enum class Kind { BACK, PLAY, PAUSE, REPLAY, REWIND, FORWARD, SETTINGS }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 2f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    override fun draw(canvas: Canvas) {
        val saved = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat()); canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        paint.style = Paint.Style.STROKE
        fun path(vararg points: Float) { val p = Path(); p.moveTo(points[0], points[1]); for (i in 2 until points.size step 2) p.lineTo(points[i], points[i + 1]); canvas.drawPath(p, paint) }
        when (kind) {
            Kind.BACK -> { path(14f, 5f, 7f, 12f, 14f, 19f); path(7f, 12f, 21f, 12f) }
            Kind.PLAY -> { paint.style = Paint.Style.FILL; val p = Path(); p.moveTo(7f, 3f); p.lineTo(21f, 12f); p.lineTo(7f, 21f); p.close(); canvas.drawPath(p, paint) }
            Kind.PAUSE -> { paint.style = Paint.Style.FILL; canvas.drawRect(6f, 4f, 10f, 20f, paint); canvas.drawRect(14f, 4f, 18f, 20f, paint) }
            Kind.REPLAY, Kind.REWIND, Kind.FORWARD -> {
                if (kind == Kind.FORWARD) { canvas.translate(24f, 0f); canvas.scale(-1f, 1f) }
                canvas.drawArc(RectF(3f, 4f, 21f, 22f), -155f, 300f, false, paint); path(3f, 3f, 3f, 9f, 9f, 9f)
                if (kind != Kind.REPLAY) {
                    if (kind == Kind.FORWARD) { canvas.scale(-1f, 1f); canvas.translate(-24f, 0f) }
                    paint.style = Paint.Style.FILL; paint.textSize = 9f; paint.typeface = Typeface.DEFAULT_BOLD; paint.textAlign = Paint.Align.CENTER
                    canvas.drawText("10", 12f, 16f, paint)
                }
            }
            Kind.SETTINGS -> {
                canvas.drawCircle(12f, 12f, 6f, paint); canvas.drawCircle(12f, 12f, 2f, paint)
                for (i in 0..7) { canvas.save(); canvas.rotate(i * 45f, 12f, 12f); canvas.drawLine(12f, 3f, 12f, 5f, paint); canvas.restore() }
            }
        }
        canvas.restoreToCount(saved)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
