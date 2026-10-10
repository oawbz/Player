package cn.awbz.davplayer.data

/** HTTP byte ranges must not silently become a whole-file download during a seek. */
object ByteRanges {
    fun header(position: Long, length: Long): String? {
        if (position < 0 || length < -1 || length == 0L) throw DavFailure("媒体 Range 参数无效")
        if (position == 0L && length == -1L) return null
        if (length > 0 && position > Long.MAX_VALUE - (length - 1)) throw DavFailure("媒体 Range 参数溢出")
        return "bytes=$position-${if (length == -1L) "" else (position + length - 1).toString()}"
    }
    fun validate(position: Long, code: Int, contentRange: String?) {
        if (position > 0 && code != 206) throw DavFailure("服务器不支持视频 Range 跳转")
        if (code != 206) return
        val match = Regex("^bytes (\\d+)-(\\d+)/(\\d+|\\*)$").matchEntire(contentRange ?: "")
            ?: throw DavFailure("服务器 Range 响应无效")
        val start = match.groupValues[1].toLongOrNull()
        val end = match.groupValues[2].toLongOrNull()
        val total = if (match.groupValues[3] == "*") null else match.groupValues[3].toLongOrNull()
        if ((match.groupValues[3] != "*" && total == null) || start != position || end == null || end < position || (total != null && end >= total))
            throw DavFailure("服务器 Range 响应位置不匹配")
    }
}
