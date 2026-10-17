package cn.awbz.davplayer.ui

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.*
import cn.awbz.davplayer.R

/** Shared app panel with consistent application styling. */
class UiDialog private constructor(context: Context) : Dialog(context) {
    private val buttons = mutableMapOf<Int, Button>()
    fun getButton(which: Int): Button = buttons.getValue(which)
    override fun show() {
        super.show()
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(minOf(context.resources.displayMetrics.widthPixels - dp(32), dp(640)), -2)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = .65f }
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window?.let {
            SystemBars.apply(it, context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
        }
    }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density + .5f).toInt()
    companion object {
        const val BUTTON_POSITIVE = DialogInterface.BUTTON_POSITIVE
        const val BUTTON_NEGATIVE = DialogInterface.BUTTON_NEGATIVE
    }
    class Builder(private val context: Context) {
        private var title = ""
        private var message = ""
        private var content: View? = null
        private var items: Array<String>? = null
        private var selected = -1
        private var choice: ((DialogInterface, Int) -> Unit)? = null
        private val actions = linkedMapOf<Int, Pair<String, ((DialogInterface, Int) -> Unit)?>>()
        fun setTitle(value: String) = apply { title = value }
        fun setMessage(value: String) = apply { message = value }
        fun setView(value: View) = apply { content = value }
        fun setItems(values: Array<String>, listener: (DialogInterface, Int) -> Unit) = apply { items = values; choice = listener }
        fun setSingleChoiceItems(values: Array<String>, checked: Int, listener: (DialogInterface, Int) -> Unit) = apply { items = values; selected = checked; choice = listener }
        fun setNegativeButton(label: String, listener: ((DialogInterface, Int) -> Unit)?) = apply { actions[BUTTON_NEGATIVE] = label to listener }
        fun setPositiveButton(label: String, listener: ((DialogInterface, Int) -> Unit)?) = apply { actions[BUTTON_POSITIVE] = label to listener }
        fun create(): UiDialog {
            val dialog = UiDialog(context)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            fun dp(v: Int) = dialog.dp(v)
            val panel = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(24), dp(24), dp(16)); setBackgroundResource(R.drawable.ui_dialog) }
            panel.addView(TextView(context).apply { text = title; textSize = 22f; setTypeface(null, Typeface.BOLD); setPadding(0, 0, 0, dp(20)) })
            val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            if (message.isNotEmpty()) body.addView(TextView(context).apply { text = message; textSize = 16f; setTextColor(context.resources.getColor(R.color.text_secondary)); setLineSpacing(dp(4).toFloat(), 1f); setPadding(0, 0, 0, dp(16)) })
            content?.let { body.addView(it) }
            items?.forEachIndexed { index, label ->
                body.addView(Button(context).apply {
                    text = if (index == selected) "✓  $label" else label
                    isSelected = index == selected; gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    setPadding(dp(16), dp(12), dp(16), dp(12)); minimumHeight = dp(52)
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
                    setOnClickListener { dialog.dismiss(); choice?.invoke(dialog, index) }
                })
            }
            // Bound long forms / lists while leaving actions visible above the keyboard.
            val scroll = object : ScrollView(context) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    val cap = (context.resources.displayMetrics.heightPixels * .55f).toInt()
                    val available = if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED) cap else minOf(cap, View.MeasureSpec.getSize(heightMeasureSpec))
                    super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(available, View.MeasureSpec.AT_MOST))
                }
            }.apply { addView(body) }
            panel.addView(scroll, LinearLayout.LayoutParams(-1, -2))
            val footer = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END; setPadding(0, dp(16), 0, 0) }
            actions.forEach { (which, action) ->
                val button = Button(context).apply {
                    text = action.first; setPadding(dp(12), dp(12), dp(12), dp(12))
                    if (which == BUTTON_POSITIVE) { setBackgroundResource(R.drawable.detail_play_button); setTextColor(Color.rgb(13, 17, 23)) }
                    setOnClickListener { action.second?.invoke(dialog, which); dialog.dismiss() }
                }
                dialog.buttons[which] = button
                footer.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply { if (footer.childCount > 0) leftMargin = dp(12) })
            }
            if (actions.isNotEmpty()) panel.addView(footer)
            dialog.setContentView(panel)
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            return dialog
        }
        fun show() = create().apply { show() }
    }
}
