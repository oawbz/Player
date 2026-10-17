package cn.awbz.davplayer.playback

import org.junit.Assert.*
import org.junit.Test

class AudioAvailabilityTest {
    @Test fun selectedAudioIsNotRejectedBecauseAnotherTrackIsUnsupported() {
        assertNull(AudioAvailability.warning(true, true, listOf("audio/mp4a-latm", "audio/vnd.dts")))
    }
    @Test fun absentAudioIsValidForSilentVideos() { assertNull(AudioAvailability.warning(false, false, emptyList())) }
    @Test fun unavailableAudioGivesCodecSpecificWarning() {
        assertTrue(AudioAvailability.warning(true, false, listOf("audio/eac3"))!!.contains("EAC3"))
    }
}
