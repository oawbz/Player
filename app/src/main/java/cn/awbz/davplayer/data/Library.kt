package cn.awbz.davplayer.data

import java.util.concurrent.ConcurrentHashMap

class Library(val dav: DavClient) {
    private data class Listing(val at: Long, val entries: List<DavEntry>)
    private val cache = ConcurrentHashMap<String, Listing>()
    fun clear() = cache.clear()
    fun list(connection: Connection, path: String, requests: Requests, refresh: Boolean = false): List<DavEntry> {
        val key = connection.id + ":" + path
        val old = cache[key]
        if (!refresh && old != null && System.currentTimeMillis() - old.at < 60_000) return old.entries
        val entries = dav.list(connection, path, requests)
        if (cache.size >= 128) cache.clear()
        cache[key] = Listing(System.currentTimeMillis(), entries)
        return entries
    }
    fun works(connection: Connection, category: Category, requests: Requests): List<Work> =
        list(connection, category.path, requests).filter { it.directory }
            .map { Work(connection.id, it.url.encodedPath(), it.name) }
    fun cover(connection: Connection, work: Work, requests: Requests): Work =
        work.copy(coverPath = MediaRules.cover(list(connection, work.path, requests))?.url?.encodedPath())
    fun detail(connection: Connection, work: Work, requests: Requests): WorkDetail {
        val entries = list(connection, work.path, requests)
        val seasons = mutableListOf<Season>()
        val notices = mutableListOf<String>()
        val direct = MediaRules.episodes(entries)
        if (direct.isNotEmpty()) seasons.add(Season("", direct))
        // Only direct season children. Never traverse a whole server or arbitrary deeper folders.
        val directories = entries.filter { it.directory }
        if (directories.size > 100) throw DavFailure("作品分季目录超过 100 个，请整理目录")
        for (directory in directories) {
            try {
                val episodes = MediaRules.episodes(list(connection, directory.url.encodedPath(), requests), directory.name)
                if (episodes.isNotEmpty()) seasons.add(Season(directory.name, episodes))
            } catch (e: DavFailure) { notices.add("部分分季目录无法加载，可刷新重试") }
        }
        if (seasons.size > 1 && direct.isNotEmpty()) seasons[0] = Season("未分季", direct)
        val descriptionFile = entries.firstOrNull { !it.directory && it.name.equals("description.txt", true) }
        var description = ""
        if (descriptionFile != null) {
            try {
                val bytes = dav.read(connection, descriptionFile.url.encodedPath(), requests, 64 * 1024)
                val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                description = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF").trim()
            } catch (_: Exception) { notices.add("简介无法读取，请检查 UTF-8 编码及文件大小（最多 64 KB）") }
        }
        return WorkDetail(work.copy(coverPath = MediaRules.cover(entries)?.url?.encodedPath()), seasons, description, notices.distinct())
    }
}
