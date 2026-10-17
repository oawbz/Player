package cn.awbz.davplayer.data

/** Bound view creation while keeping original episode numbers and every episode reachable. */
data class EpisodePage(val page: Int, val pages: Int, val start: Int, val end: Int) {
    companion object {
        const val SIZE = 24
        fun of(count: Int, requested: Int): EpisodePage {
            val pages = ((count.coerceAtLeast(0) + SIZE - 1) / SIZE).coerceAtLeast(1)
            val page = requested.coerceIn(0, pages - 1)
            return EpisodePage(page, pages, page * SIZE, minOf(count.coerceAtLeast(0), (page + 1) * SIZE))
        }
    }
}
