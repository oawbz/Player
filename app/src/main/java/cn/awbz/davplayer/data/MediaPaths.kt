package cn.awbz.davplayer.data

import java.net.URI

/** Only non-secret source paths live in models and caches; playback URLs stay in memory. */
object MediaPaths {
    private fun parse(value: String): URI = try {
        URI(URI(value.replace(" ", "%20")).toASCIIString())
    } catch (_: Exception) { throw MediaFailure("目录地址无效") }
    fun base(input: String): URI {
        val url = parse(input.trim())
        if (url.scheme?.lowercase() !in listOf("http", "https") || url.host.isNullOrEmpty())
            throw MediaFailure("请输入有效的 HTTP 或 HTTPS 地址")
        if (url.rawUserInfo != null || url.rawQuery != null || url.rawFragment != null || !safe(url))
            throw MediaFailure("服务地址不能包含账号、密码或其他参数，请单独填写账号密码")
        return directory(if (url.rawPath.isNullOrEmpty()) parse(url.toASCIIString() + "/") else url)
    }
    fun directory(url: URI): URI = if (url.rawPath.endsWith('/')) url else parse(url.toASCIIString() + "/")
    fun segments(url: URI): List<String> = url.path.split('/').filter { it.isNotEmpty() }
    private fun port(url: URI) = if (url.port >= 0) url.port else if (url.scheme.equals("https", true)) 443 else 80
    private fun safe(url: URI) = segments(url).none { '/' in it || '\\' in it || it == "." || it == ".." || '\u0000' in it } &&
        url.rawPath.split('/').none { part ->
            val decoded = parse("/$part").path.removePrefix("/")
            '/' in decoded || '\\' in decoded || decoded == "." || decoded == ".."
        }
    fun inside(root: URI, target: URI): Boolean =
        root.scheme.equals(target.scheme, true) && root.host.equals(target.host, true) && port(root) == port(target) &&
            target.rawUserInfo == null && target.rawQuery == null && target.rawFragment == null && safe(target) &&
            segments(target).let { it.size >= segments(root).size && it.take(segments(root).size) == segments(root) }
    fun fromPath(connection: Connection, path: String): URI {
        val root = base(connection.baseUrl)
        if (!path.startsWith('/') || path.startsWith("//")) throw MediaFailure("目录路径无效")
        val value = parse(path)
        if (!safe(value)) throw MediaFailure("目录路径无效")
        val url = root.resolve(value)
        if (!inside(root, url)) throw MediaFailure("目录不在当前连接范围内")
        return url
    }
    fun child(parent: URI, name: String, directory: Boolean): URI {
        if (name.isEmpty() || name in listOf(".", "..") || '/' in name || '\\' in name || '\u0000' in name)
            throw MediaFailure("返回的文件名无效")
        val encoded = URI(null, null, "/$name", null).toASCIIString().removePrefix("/")
        return parse(MediaPaths.directory(parent).toASCIIString() + encoded + if (directory) "/" else "")
    }
    fun parent(connection: Connection, path: String): String {
        val root = base(connection.baseUrl)
        val current = fromPath(connection, path)
        val next = current.rawPath.trimEnd('/').substringBeforeLast('/', "") + "/"
        return if (inside(root, root.resolve(next))) next else root.rawPath
    }
}
