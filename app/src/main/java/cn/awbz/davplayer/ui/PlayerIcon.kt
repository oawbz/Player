package cn.awbz.davplayer.ui

import android.graphics.*
import android.graphics.drawable.Drawable

/** Shared Canvas icons for phone and TV controls. */
class PlayerIcon(private val kind: Kind) : Drawable() {
    enum class Kind { BACK, PLAY, PAUSE, REPLAY, REWIND, FORWARD, SETTINGS, EPISODES, AUDIO, PREVIOUS, NEXT }
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
            Kind.PREVIOUS, Kind.NEXT -> {
                if (kind == Kind.PREVIOUS) { canvas.translate(24f, 0f); canvas.scale(-1f, 1f) }
                paint.style = Paint.Style.FILL
                val p = Path(); p.moveTo(5f, 5f); p.lineTo(17f, 12f); p.lineTo(5f, 19f); p.close(); canvas.drawPath(p, paint)
                canvas.drawRect(18f, 5f, 21f, 19f, paint)
            }
            Kind.AUDIO -> {
                path(4f, 9f, 8f, 9f, 13f, 5f, 13f, 19f, 8f, 15f, 4f, 15f, 4f, 9f)
                canvas.drawArc(RectF(11f, 7f, 21f, 17f), -60f, 120f, false, paint)
            }
            Kind.EPISODES -> {
                for (y in listOf(6f, 12f, 18f)) { canvas.drawCircle(4f, y, .7f, paint); canvas.drawLine(9f, y, 21f, y, paint) }
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
