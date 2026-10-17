package cn.awbz.davplayer.playback

import org.junit.Assert.*
import org.junit.Test

class TransferMeterTest {
    @Test fun combinesRangesAndDropsIdleTraffic() {
        var now = 1000L; val meter = TransferMeter { now }
        now = 1500; meter.progress(512000); now = 2000; meter.progress(512000)
        assertEquals(1024000L, meter.speed())
        now = 5500; assertEquals(0L, meter.speed())
    }
    @Test fun switchingMediaResetsSpeedWindow() {
        var now = 1000L; val meter = TransferMeter { now }
        now = 1500; meter.progress(512000); meter.reset()
        assertEquals(0L, meter.speed())
        now = 2000; meter.progress(1000); assertEquals(2000L, meter.speed())
    }
}
