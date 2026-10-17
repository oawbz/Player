package cn.awbz.davplayer.data

/** Fixed artwork convention: a path calculation, never a directory lookup. */
object PosterFiles {
    const val NAME = "poster.jpg"
    fun path(connection: Connection, workPath: String): String =
        MediaPaths.child(MediaPaths.directory(MediaPaths.fromPath(connection, workPath)), NAME, false).rawPath
    fun shouldFetch(entry: ResourceCache.Entry?, force: Boolean): Boolean =
        force || entry == null || entry.data.isEmpty()
}
