package cn.awbz.davplayer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.security.MessageDigest

class Posters(context: Context, private val library: Library) {
    private val folder = File(context.cacheDir, "posters").apply { mkdirs() }
    private val memory = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    fun load(connection: Connection, work: Work, requests: Requests): Pair<Work, Bitmap?> {
        val resolved = if (work.coverPath != null) work else library.cover(connection, work, requests)
        val path = resolved.coverPath ?: return resolved to null
        val key = MessageDigest.getInstance("SHA-256").digest((connection.id + ":" + path).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        synchronized(memory) { memory.get(key)?.let { return resolved to it } }
        val file = File(folder, key)
        val bytes = if (file.exists() && System.currentTimeMillis() - file.lastModified() < 24 * 60 * 60 * 1000L) file.readBytes()
        else library.dav.read(connection, path, requests, 4 * 1024 * 1024).also { data ->
            synchronized(folder) {
                file.writeBytes(data)
                var size = folder.listFiles()?.sumOf { it.length() } ?: 0L
                folder.listFiles()?.sortedBy { it.lastModified() }?.forEach { old ->
                    if (size > 32 * 1024 * 1024 && old != file) { size -= old.length(); old.delete() }
                }
            }
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0 || options.outWidth.toLong() * options.outHeight > 100_000_000)
            throw DavFailure("封面尺寸无效或过大")
        var sample = 1
        while (options.outWidth / sample > 600 || options.outHeight / sample > 900) sample *= 2
        options.inJustDecodeBounds = false; options.inSampleSize = sample
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (bitmap != null) synchronized(memory) { memory.put(key, bitmap) }
        return resolved to bitmap
    }
    fun clear() { synchronized(memory) { memory.evictAll() }; synchronized(folder) { folder.listFiles()?.forEach { it.delete() } } }
}
