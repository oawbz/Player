package cn.awbz.davplayer.data

import okhttp3.HttpUrl
import java.util.Locale

// Credentials never enter intents, media IDs, cache keys or display strings.
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
data class DavEntry(val url: HttpUrl, val name: String, val directory: Boolean)
data class Episode(val path: String, val name: String, val season: String = "")
data class Season(val name: String, val episodes: List<Episode>)
data class Work(val connectionId: String, val path: String, val name: String, val coverPath: String? = null)
data class WorkDetail(val work: Work, val seasons: List<Season>, val description: String, val notices: List<String> = emptyList()) {
    val episodes: List<Episode> get() = seasons.flatMap { it.episodes }
}
data class History(val connectionId: String, val workPath: String, val workName: String,
    val coverPath: String?, val videoPath: String, val videoName: String, val season: String,
    val position: Long, val duration: Long, val updatedAt: Long)

enum class Core { SYSTEM, EXO }

object MediaRules {
    private val videos = setOf("mp4", "mkv", "avi", "mov", "m4v", "ts", "m2ts", "webm", "wmv", "flv", "3gp", "mpg", "mpeg", "vob")
    private val images = setOf("jpg", "jpeg", "png", "webp")
    fun isVideo(name: String) = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in videos
    fun isImage(name: String) = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in images
    fun cover(entries: List<DavEntry>): DavEntry? = entries.filter { !it.directory && isImage(it.name) }
        .sortedWith(Comparator { a, b ->
            val pa = priority(a.name); val pb = priority(b.name)
            if (pa != pb) pa.compareTo(pb) else NaturalOrder.compare(a.name, b.name)
        }).firstOrNull()
    private fun priority(name: String): Int = when (name.substringBeforeLast('.').lowercase(Locale.ROOT)) {
        "poster" -> 0; "cover" -> 1; "folder" -> 2; else -> 3
    }
    fun episodes(entries: List<DavEntry>, season: String = "") = entries.filter { !it.directory && isVideo(it.name) }
        .sortedWith(Comparator { a, b -> NaturalOrder.compare(a.name, b.name) })
        .map { Episode(it.url.encodedPath(), it.name, season) }
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

class DavFailure(val userMessage: String) : java.io.IOException(userMessage)

object DavUrls {
    fun base(input: String): HttpUrl {
        val url = HttpUrl.parse(input.trim()) ?: throw DavFailure("请输入有效的 HTTP 或 HTTPS 地址")
        if (url.username().isNotEmpty() || url.password().isNotEmpty() || url.query() != null || url.fragment() != null)
            throw DavFailure("服务地址不能包含账号、密码、查询参数或片段，请单独填写账号密码")
        return directory(url)
    }
    fun directory(url: HttpUrl): HttpUrl = if (url.encodedPath().endsWith('/')) url else
        url.newBuilder().addPathSegment("").build()
    fun sameOrigin(a: HttpUrl, b: HttpUrl) = a.scheme() == b.scheme() && a.host() == b.host() && a.port() == b.port()
    fun inside(base: HttpUrl, target: HttpUrl): Boolean {
        if (!sameOrigin(base, target) || target.username().isNotEmpty() || target.password().isNotEmpty() ||
            target.query() != null || target.fragment() != null) return false
        val root = base.pathSegments().filter { it.isNotEmpty() }
        val path = target.pathSegments().filter { it.isNotEmpty() }
        // Encoded separators/dot segments can be interpreted differently by server/proxy.
        if (path.any { it.contains('/') || it.contains('\\') || it == "." || it == ".." }) return false
        return path.size >= root.size && path.take(root.size) == root
    }
    fun fromPath(connection: Connection, path: String): HttpUrl {
        val base = base(connection.baseUrl)
        if (!path.startsWith('/') || path.startsWith("//")) throw DavFailure("目录路径无效")
        val url = base.resolve(path) ?: throw DavFailure("目录路径无效")
        if (!inside(base, url)) throw DavFailure("目录不在当前 WebDAV 连接范围内")
        return url
    }
    fun directChild(parent: HttpUrl, candidate: HttpUrl): Boolean {
        if (!inside(directory(parent), candidate)) return false
        val a = parent.pathSegments().filter { it.isNotEmpty() }
        val b = candidate.pathSegments().filter { it.isNotEmpty() }
        return b.size == a.size + 1
    }
}
