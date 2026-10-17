package cn.awbz.davplayer.data

/** Directory browsing only; selected resource paths are reused by detail-only loading. */
class Library(val catalog: MediaCatalog) {
    fun list(connection: Connection, path: String, requests: RequestScope): List<MediaEntry> = catalog.list(connection, path, requests).filterNot { MediaRules.isHidden(it.name) }
    fun works(connection: Connection, category: Category, requests: RequestScope): List<Work> =
        list(connection, category.path, requests).filter { it.directory }.sortedWith(Comparator { a, b ->
            val time = when {
                a.modifiedAt == null && b.modifiedAt == null -> 0
                a.modifiedAt == null -> 1
                b.modifiedAt == null -> -1
                else -> b.modifiedAt.compareTo(a.modifiedAt)
            }
            if (time != 0) time else NaturalOrder.compare(a.name, b.name).takeIf { it != 0 } ?: a.path.compareTo(b.path)
        }).map { Work(connection.id, it.path, it.name) }
    fun detail(connection: Connection, work: Work, requests: RequestScope): WorkDetail {
        val entries = list(connection, work.path, requests)
        val seasons = mutableListOf<Season>()
        val notices = mutableListOf<String>()
        val direct = MediaRules.episodes(entries)
        if (direct.isNotEmpty()) seasons.add(Season("", direct))
        val directories = entries.filter { it.directory && !MediaRules.isHidden(it.name) }.sortedWith(Comparator { a, b -> NaturalOrder.compare(a.name, b.name) })
        if (directories.size > 100) throw MediaFailure("作品分季目录超过 100 个，请整理目录")
        for (directory in directories) {
            try {
                val episodes = MediaRules.episodes(list(connection, directory.path, requests), directory.name)
                if (episodes.isNotEmpty()) seasons.add(Season(directory.name, episodes))
            } catch (_: Exception) {
                if (requests.isCancelled || Thread.currentThread().isInterrupted) throw MediaFailure("请求已取消")
                notices.add("部分分季目录无法加载，可刷新重试")
            }
        }
        if (seasons.size > 1 && direct.isNotEmpty()) seasons[0] = Season("未分季", direct)
        return WorkDetail(work.copy(coverPath = null), seasons, "", notices.distinct(),
            assets = DetailAssets(Nfo.select(entries, work.name, direct)?.path, PosterFiles.path(connection, work.path)))
    }
}
