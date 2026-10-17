package cn.awbz.davplayer.data

interface MediaCatalog {
    fun list(connection: Connection, path: String, requests: RequestScope): List<MediaEntry>
}

/** OpenList owns storage selection; the library retains stable directory identities. */
class OpenListCatalog : MediaCatalog {
    override fun list(connection: Connection, path: String, requests: RequestScope): List<MediaEntry> =
        cn.awbz.davplayer.data.source.OpenListSource.list(connection, path, requests)
}
