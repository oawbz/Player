package cn.awbz.davplayer.playback

/** Audio capability is a warning, never a reason to tear down otherwise playable video. */
internal object AudioAvailability {
    fun codec(mime: String): String = when (mime) {
        "audio/mp4a-latm" -> "AAC"; "audio/ac3" -> "AC3"; "audio/eac3", "audio/eac3-joc" -> "EAC3"
        "audio/vnd.dts", "audio/vnd.dts.hd" -> "DTS"; "audio/true-hd" -> "TrueHD"
        "audio/mpeg" -> "MP3"; "audio/flac" -> "FLAC"; "audio/opus" -> "Opus"
        else -> mime.removePrefix("audio/")
    }
    fun warning(hasAudio: Boolean, selected: Boolean, formats: List<String>): String? {
        if (!hasAudio || selected) return null
        val names = formats.map(::codec).filter { it.isNotBlank() }.distinct().joinToString("、")
        return "音频暂不可用${if (names.isEmpty()) "" else "（$names）"}，当前没有声音"
    }
}
