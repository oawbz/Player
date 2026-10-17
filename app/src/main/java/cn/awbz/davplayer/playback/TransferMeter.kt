package cn.awbz.davplayer.playback

/** Bounded three-second byte window shared by all file ranges in one playback. */
class TransferMeter(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val epochs = LongArray(6) { -1 }
    private val bytes = LongArray(6)
    private var started = clock()
    @Synchronized fun progress(count: Int = 0) {
        val now = clock()
        if (count <= 0) return
        val epoch = now / 500; val slot = (epoch % 6).toInt()
        if (epochs[slot] != epoch) { epochs[slot] = epoch; bytes[slot] = 0 }
        bytes[slot] = bytes[slot] + count.toLong()
    }
    @Synchronized fun speed(): Long {
        val now = clock(); val epoch = now / 500
        val total = bytes.indices.filter { epochs[it] >= 0 && epoch - epochs[it] in 0..5 }.sumOf { bytes[it] }
        return total * 1000 / (now - started).coerceIn(500, 3000)
    }
    @Synchronized fun reset() { epochs.fill(-1); bytes.fill(0); started = clock() }
}
