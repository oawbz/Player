package cn.awbz.davplayer.ui

internal object InlinePlotText {
    fun prefix(text: String, end: Int): String {
        val safe = if (end in 1 until text.length && Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end - 1 else end
        return text.substring(0, safe).trimEnd()
    }
    fun fit(text: String, suffix: String, fits: (String) -> Boolean): String {
        var low = 0; var high = text.length
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (fits(prefix(text, mid) + suffix)) low = mid else high = mid - 1
        }
        return prefix(text, low) + suffix
    }
}
