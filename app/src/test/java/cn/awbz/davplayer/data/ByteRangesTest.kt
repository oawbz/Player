package cn.awbz.davplayer.data

import org.junit.Assert.*
import org.junit.Test

class ByteRangesTest {
    @Test fun buildsOpenAndBoundedRanges() {
        assertNull(ByteRanges.header(0, -1))
        assertEquals("bytes=1024-", ByteRanges.header(1024, -1))
        assertEquals("bytes=0-0", ByteRanges.header(0, 1))
        assertEquals("bytes=1024-2047", ByteRanges.header(1024, 1024))
    }
    @Test fun rejectsUnsupportedSeekAndWrongResponseOffsets() {
        listOf(200 to null, 206 to "bytes 0-100/200", 206 to "bytes 1024-100/200", 206 to "bytes 1024-2048/2048", 206 to null).forEach { (code, range) ->
            try { ByteRanges.validate(1024, code, range); fail("must reject") } catch (_: DavFailure) {}
        }
        ByteRanges.validate(1024, 206, "bytes 1024-2047/4096")
        ByteRanges.validate(0, 200, null)
    }
    @Test fun rejectsOverflowAndInvalidLength() {
        listOf(Long.MAX_VALUE to 2L, -1L to 10L, 0L to 0L, 0L to -2L).forEach { (position, length) ->
            try { ByteRanges.header(position, length); fail("must reject") } catch (_: DavFailure) {}
        }
    }
}
