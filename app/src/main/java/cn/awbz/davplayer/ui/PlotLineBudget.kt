package cn.awbz.davplayer.ui

internal object PlotLineBudget {
    inline fun fitting(count: Int, height: Int, bottom: (Int) -> Int): Int {
        var fitting = 0
        for (line in 0 until count) {
            if (bottom(line) > height) break
            fitting++
        }
        return fitting.coerceAtLeast(1)
    }
}
