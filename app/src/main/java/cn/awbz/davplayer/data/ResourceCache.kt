package cn.awbz.davplayer.data

import java.io.*
import java.security.MessageDigest

/** Persistent app-private resources. Explicit refreshes must not evict earlier downloads. */
class ResourceCache(private val root: File) {
    data class Entry(val data: ByteArray, val etag: String, val modified: String, val checkedAt: Long)
    fun key(connection: Connection, path: String): String {
        val identity = listOf(connection.id, connection.baseUrl, connection.authMode.name, connection.username, connection.password, path).joinToString("\u0000")
        val key = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
        return key
    }
    private fun file(connection: Connection, path: String) = File(root, key(connection, path) + ".bin")
    @Synchronized fun read(connection: Connection, path: String, limit: Int): Entry? = runCatching {
        val source = file(connection, path)
        if (!source.isFile || source.length() > limit + 32768L) return@runCatching null
        DataInputStream(BufferedInputStream(FileInputStream(source))).use { input ->
            require(input.readInt() == 1)
            val at = input.readLong(); val etag = input.readUTF(); val modified = input.readUTF()
            val size = input.readInt(); require(size in 0..limit)
            val bytes = ByteArray(size); input.readFully(bytes); require(input.read() == -1)
            Entry(bytes, etag, modified, at)
        }
    }.getOrNull()
    @Synchronized fun write(connection: Connection, path: String, entry: Entry) {
        require(entry.data.size <= 8 * 1024 * 1024)
        if (!root.isDirectory && !root.mkdirs()) throw IOException("资料缓存目录不可用")
        val target = file(connection, path); val temp = File(root, target.name + ".tmp")
        try {
            FileOutputStream(temp).use { stream ->
                val out = DataOutputStream(stream)
                out.writeInt(1); out.writeLong(entry.checkedAt)
                out.writeUTF(entry.etag.take(4096)); out.writeUTF(entry.modified.take(4096))
                out.writeInt(entry.data.size); out.write(entry.data); out.flush(); stream.fd.sync()
            }
            if (!temp.renameTo(target)) throw IOException("资料缓存写入失败")
        } finally { temp.delete() }
    }
    @Synchronized fun remove(connection: Connection, path: String) { file(connection, path).delete() }
}

/** Persist parsed NFO fields, not arbitrary external-resource links. */
object MetadataCodec {
    private fun DataOutputStream.text(value: String) { val b = value.toByteArray(Charsets.UTF_8); writeInt(b.size); write(b) }
    private fun DataInputStream.text(): String { val size = readInt(); require(size in 0..Nfo.LIMIT); return ByteArray(size).also { readFully(it) }.toString(Charsets.UTF_8) }
    fun encode(meta: MediaMetadata): ByteArray = ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use { out ->
        out.writeInt(1); listOf(meta.title, meta.plot, meta.year, meta.premiered, meta.kind).forEach { out.text(it) }
        out.writeInt(meta.runtimeMinutes ?: -1); out.writeDouble(meta.rating ?: Double.NaN)
        out.writeInt(meta.genres.size); meta.genres.forEach { out.text(it) }
    } }.toByteArray()
    fun decode(bytes: ByteArray): MediaMetadata = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        require(input.readInt() == 1)
        val title = input.text(); val plot = input.text(); val year = input.text(); val date = input.text(); val kind = input.text()
        val runtime = input.readInt().takeIf { it > 0 }; val rating = input.readDouble().takeIf { it.isFinite() && it in 0.0..10.0 }
        val count = input.readInt(); require(count in 0..1024)
        val genres = List(count) { input.text() }; require(input.read() == -1)
        MediaMetadata(title, plot, year, date, runtime, rating, genres, kind)
    }
}
