package cn.awbz.davplayer.ui

import android.graphics.*
import android.graphics.drawable.Drawable

/** Text-free artwork fallback for any poster size. */
class PosterPlaceholder : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val saved = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        val width = bounds.width().toFloat(); val height = bounds.height().toFloat()
        paint.shader = LinearGradient(0f, 0f, width, height, Color.rgb(36, 45, 57), Color.rgb(13, 17, 23), Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL; canvas.drawRect(0f, 0f, width, height, paint); paint.shader = null
        val scale = minOf(width / 100f, height / 150f)
        canvas.translate((width - 100f * scale) / 2f, (height - 150f * scale) / 2f)
        canvas.scale(scale, scale)
        paint.color = Color.rgb(78, 83, 91); paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.2f
        canvas.drawRoundRect(RectF(23f, 49f, 77f, 99f), 5f, 5f, paint)
        for (y in listOf(56f, 67f, 78f, 89f)) { canvas.drawRect(27f, y, 31f, y + 5f, paint); canvas.drawRect(69f, y, 73f, y + 5f, paint) }
        paint.style = Paint.Style.FILL; paint.color = Color.rgb(180, 122, 105)
        val play = Path(); play.moveTo(43f, 64f); play.lineTo(61f, 75f); play.lineTo(43f, 86f); play.close(); canvas.drawPath(play, paint)
        canvas.restoreToCount(saved)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.OPAQUE
}
