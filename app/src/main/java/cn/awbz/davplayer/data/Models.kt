package cn.awbz.davplayer.data

import java.util.Locale

// Raw credentials never enter intents, media IDs, cache filenames or display strings.
enum class AuthMode { ANONYMOUS, BASIC;
    companion object {
        fun stored(value: String?, username: String, password: String): AuthMode =
            values().firstOrNull { it.name == value } ?: if (username.isEmpty() && password.isEmpty()) ANONYMOUS else BASIC
    }
}
data class Connection(val id: String, val name: String, val baseUrl: String, val username: String, val password: String,
    val authMode: AuthMode = AuthMode.stored(null, username, password)) {
    override fun toString() = "Connection(id=$id, authMode=$authMode)"
}
data class Category(val id: String, val name: String, val connectionId: String, val path: String)
data class MediaEntry(val path: String, val name: String, val directory: Boolean, val modifiedAt: Long? = null)
data class Episode(val path: String, val name: String, val season: String = "")
data class Season(val name: String, val episodes: List<Episode>)
data class Work(val connectionId: String, val path: String, val name: String, val coverPath: String? = null)
data class DetailAssets(val nfo: String? = null, val cover: String? = null)
data class WorkDetail(val work: Work, val seasons: List<Season>, val description: String, val notices: List<String> = emptyList(), val metadata: MediaMetadata = MediaMetadata(), val assets: DetailAssets = DetailAssets()) {
    val episodes: List<Episode> get() = seasons.flatMap { it.episodes }
}
data class History(val connectionId: String, val workPath: String, val workName: String,
    val coverPath: String?, val videoPath: String, val videoName: String, val season: String,
    val position: Long, val duration: Long, val updatedAt: Long)


object MediaRules {
    fun isHidden(name: String) = name.startsWith(".")
    private val videos = setOf("mp4", "mkv", "avi", "mov", "m4v", "ts", "m2ts", "webm", "wmv", "flv", "3gp", "mpg", "mpeg", "vob")
    fun isVideo(name: String) = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in videos
    fun episodes(entries: List<MediaEntry>, season: String = "") = entries.filter { !it.directory && !isHidden(it.name) && isVideo(it.name) }
        .sortedWith(Comparator { a, b -> NaturalOrder.compare(a.name, b.name) })
        .map { Episode(it.path, it.name, season) }
}

/** Compare arbitrary length numbers without Long overflow, preserving a deterministic tie-break. */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0; var j = 0
        while (i < a.length && j < b.length) {
            if (a[i] in '0'..'9' && b[j] in '0'..'9') {
                val startA = i; val startB = j
                while (i < a.length && a[i] in '0'..'9') i++
                while (j < b.length && b[j] in '0'..'9') j++
                val na = a.substring(startA, i).trimStart('0').ifEmpty { "0" }
                val nb = b.substring(startB, j).trimStart('0').ifEmpty { "0" }
                val c = if (na.length != nb.length) na.length.compareTo(nb.length) else na.compareTo(nb)
                if (c != 0) return c
            } else {
                val c = a[i].lowercaseChar().compareTo(b[j].lowercaseChar())
                if (c != 0) return c
                i++; j++
            }
        }
        val length = (a.length - i).compareTo(b.length - j)
        return if (length != 0) length else a.compareTo(b)
    }
}

class MediaFailure(val userMessage: String) : java.io.IOException(userMessage)
