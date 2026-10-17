package cn.awbz.davplayer.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.widget.TextView
import cn.awbz.davplayer.R

/** Fits the expand action into the last visible line, using the paragraph's own font. */
class InlinePlotView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle) : TextView(context, attrs, defStyleAttr) {
    private var body = ""
    private var expand: (() -> Unit)? = null
    private var heightBudget: Int? = null
    private var renderedLines = 1
    private var dirty = true
    private var lastWidth = -1
    private var lastLines = -1
    private var lastSize = -1f
    private var lastScale = -1f
    private var lastTypeface: android.graphics.Typeface? = null
    private var lastSpacing = -1f
    private var lastMultiplier = -1f
    private val markerColor = ForegroundColorSpan(resources.getColor(R.color.accent))
    private var truncated = false
    init {
        ellipsize = null
        configureLineBreaking()
        setBackgroundResource(R.drawable.inline_plot_focus)
        setOnClickListener { if (truncated) expand?.invoke() }
        isClickable = false; isFocusable = false
    }
    @SuppressLint("InlinedApi")
    private fun configureLineBreaking() {
        // SDK36 names this inlined integer through LineBreaker (API29); the setter exists
        // since API23 and the compiler emits the integer, never a LineBreaker reference.
        if (Build.VERSION.SDK_INT >= 23) {
            breakStrategy = android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE
            hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        }
    }
    fun setPlot(value: String, onExpand: (() -> Unit)?) {
        val unchanged = body == value && (expand != null) == (onExpand != null)
        expand = onExpand
        if (unchanged) return
        body = value; dirty = true; requestLayout()
    }
    fun fitHeight(heightPx: Int) {
        val budget = (heightPx - compoundPaddingTop - compoundPaddingBottom).coerceAtLeast(1)
        if (heightBudget != budget) { heightBudget = budget; dirty = true; requestLayout() }
    }
    @Suppress("DEPRECATION")
    private fun measureText(value: String, width: Int) = StaticLayout(value, paint, width,
        Layout.Alignment.ALIGN_NORMAL, lineSpacingMultiplier, lineSpacingExtra, includeFontPadding)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = (MeasureSpec.getSize(widthMeasureSpec) - compoundPaddingLeft - compoundPaddingRight).coerceAtLeast(0)
        val lines = if (heightBudget != null) 20 else maxLines.coerceIn(1, 20)
        if (width > 0 && (dirty || lastWidth != width || lastLines != lines || lastSize != textSize ||
                lastScale != paint.textScaleX || lastTypeface != typeface || lastSpacing != lineSpacingExtra || lastMultiplier != lineSpacingMultiplier)) {
            dirty = false; lastWidth = width; lastLines = lines; lastSize = textSize
            lastScale = paint.textScaleX; lastTypeface = typeface; lastSpacing = lineSpacingExtra; lastMultiplier = lineSpacingMultiplier
            rebuild(width, lines)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // The TextView's own Layout is authoritative, including device font fallback spacing.
        // Corrections only reduce this pass's line count, so measurement cannot oscillate.
        val budget = heightBudget
        if (budget != null && width > 0) {
            while (renderedLines > 1) {
                val actual = layout ?: break
                val corrected = PlotLineBudget.fitting(minOf(actual.lineCount, renderedLines), budget) { actual.getLineBottom(it) }
                if (corrected >= renderedLines) break
                rebuild(width, corrected)
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
    }
    /** Layout and spans are rebuilt only when content, width, line budget or font changes. */
    private fun rebuild(width: Int, requestedLines: Int) {
        val lines = requestedLines
        if (heightBudget != null) maxLines = lines
        renderedLines = lines
        // Probe only enough source to overflow the visible lines, even for very large NFOs.
        var end = body.length; var next = 0
        for (line in 0..lines) {
            val newline = body.indexOf('\n', next)
            if (newline < 0) break
            next = newline + 1
            if (line == lines) end = next
        }
        val count = paint.breakText(body, 0, end, true, width * (lines + 1f), null)
        end = minOf(end, count + 2)
        val probe = InlinePlotText.prefix(body, end)
        val layout = measureText(probe, width)
        truncated = expand != null && (end < body.length || layout.lineCount > lines)
        if (truncated) {
            val label = context.getString(R.string.view_plot)
            val suffix = "…$label"
            val visibleEnd = layout.getLineEnd(minOf(lines, layout.lineCount) - 1)
            var compact = InlinePlotText.fit(InlinePlotText.prefix(probe, visibleEnd), suffix) { measureText(it, width).lineCount <= lines }
            // Keep the entire marker, ellipsis and last body character together on the last line.
            while (compact.length > suffix.length) {
                val candidate = measureText(compact, width)
                val bodyEnd = compact.length - suffix.length
                if (candidate.getLineForOffset(bodyEnd - 1) == candidate.getLineForOffset(compact.lastIndex)) break
                compact = InlinePlotText.prefix(compact.substring(0, bodyEnd), bodyEnd - 1) + suffix
            }
            text = SpannableString(compact).apply {
                setSpan(markerColor, length - label.length, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else text = body
        isClickable = truncated; isFocusable = truncated
    }
}
