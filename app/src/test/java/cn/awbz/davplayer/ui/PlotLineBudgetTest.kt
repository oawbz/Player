package cn.awbz.davplayer.ui

import org.junit.Assert.*
import org.junit.Test

class PlotLineBudgetTest {
    @Test fun actualTallFontRemovesOverflowEvenWhenSampleAllowedTenLines() {
        val actualBottoms = List(10) { (it + 1) * 48 }
        assertEquals(8, PlotLineBudget.fitting(10, 396) { actualBottoms[it] })
    }
    @Test fun exactBottomIsIncludedAndShortDescriptionsAreNotPadded() {
        assertEquals(2, PlotLineBudget.fitting(3, 96) { (it + 1) * 48 })
        assertEquals(2, PlotLineBudget.fitting(2, 400) { (it + 1) * 48 })
    }
    @Test fun repeatedCorrectionDoesNotRestoreEstimatedOverflowLines() {
        var lines = 10
        repeat(4) { lines = PlotLineBudget.fitting(lines, 396) { (it + 1) * 48 } }
        assertEquals(8, lines)
        assertEquals(1, PlotLineBudget.fitting(1, 20) { 48 })
    }
}
