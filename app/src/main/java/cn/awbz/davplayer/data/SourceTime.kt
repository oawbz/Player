package cn.awbz.davplayer.data

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** OpenList modified/created are nullable ISO date-time strings. */
object SourceTime {
    private val iso = Regex("^(\\d{4}-\\d{2}-\\d{2})(?:[Tt ](\\d{2}:\\d{2}:\\d{2})(?:[.,](\\d{1,9}))?([Zz]|[+-]\\d{2}:?\\d{2})?)?$")
    fun modified(updated: String?, created: String?): Long? = parse(updated) ?: parse(created)
    fun parse(value: String?): Long? {
        val match = value?.trim()?.let { iso.matchEntire(it) } ?: return null
        val time = match.groupValues[2].ifEmpty { "00:00:00" }
        val fraction = match.groupValues[3].take(3).padEnd(3, '0')
        val zone = match.groupValues[4].let { if (it.isEmpty() || it.equals("z", true)) "+0000" else it.replace(":", "") }
        val normalized = "${match.groupValues[1]}T$time.$fraction$zone"
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.ROOT).apply { isLenient = false; timeZone = TimeZone.getTimeZone("UTC") }
        val position = ParsePosition(0)
        return parser.parse(normalized, position)?.time?.takeIf { position.index == normalized.length }
    }
}
