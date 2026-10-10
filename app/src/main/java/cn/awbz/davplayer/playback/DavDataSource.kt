package cn.awbz.davplayer.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import cn.awbz.davplayer.data.*
import okhttp3.Call
import okhttp3.Response
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/** Small progressive-file source using the API-18-compatible OkHttp branch. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class DavDataSource(private val connection: Connection, private val dav: DavClient) : BaseDataSource(true) {
    private var response: Response? = null
    private var stream: InputStream? = null
    private var uri: Uri? = null
    private var remaining = C.LENGTH_UNSET.toLong()
    private var opened = false
    private var readBytes = 0L
    private var lastTrace = 0L
    @Volatile private var activeCall: Call? = null
    override fun open(dataSpec: DataSpec): Long {
        readBytes = 0; lastTrace = android.os.SystemClock.elapsedRealtime()
        PlaybackTrace.write("open position=${dataSpec.position} length=${dataSpec.length}")
        transferInitializing(dataSpec)
        val url = okhttp3.HttpUrl.parse(dataSpec.uri.toString()) ?: throw DavFailure("媒体地址无效")
        if (!DavUrls.inside(DavUrls.base(connection.baseUrl), url)) throw DavFailure("媒体不在当前 WebDAV 连接范围内")
        if (dataSpec.httpMethod != DataSpec.HTTP_METHOD_GET) throw DavFailure("仅支持文件 GET 播放")
        try {
            val result = MediaRequests.open(dav, connection, url, ByteRanges.header(dataSpec.position, dataSpec.length)) { activeCall = it }
            response = result
            if (!result.isSuccessful) throw DavFailure("媒体请求失败（HTTP ${result.code()}）")
            ByteRanges.validate(dataSpec.position, result.code(), result.header("Content-Range"))
            val body = result.body() ?: throw DavFailure("媒体响应为空")
            stream = body.byteStream(); uri = Uri.parse(url.toString())
            remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else body.contentLength()
            opened = true; transferStarted(dataSpec)
            return remaining
        } catch (e: IOException) { close(); throw e }
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val count = stream?.read(buffer, offset, if (remaining < 0) length else minOf(length.toLong(), remaining).toInt())
            ?: return C.RESULT_END_OF_INPUT
        if (count < 0) {
            if (remaining > 0) throw EOFException("媒体响应提前结束")
            return C.RESULT_END_OF_INPUT
        }
        if (remaining >= 0) remaining -= count
        readBytes += count
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastTrace >= 5000) { PlaybackTrace.write("read bytes=$readBytes remaining=$remaining"); lastTrace = now }
        bytesTransferred(count); return count
    }
    override fun getUri(): Uri? = uri
    override fun getResponseHeaders(): Map<String, List<String>> = response?.headers()?.toMultimap() ?: emptyMap()
    override fun close() {
        PlaybackTrace.write("close bytes=$readBytes remaining=$remaining")
        activeCall?.cancel(); activeCall = null
        runCatching { stream?.close() }; stream = null
        response?.close(); response = null; uri = null
        if (opened) { opened = false; transferEnded() }
    }
    class Factory(private val connection: Connection, private val dav: DavClient) : DataSource.Factory {
        override fun createDataSource(): DataSource = DavDataSource(connection, dav)
    }
}
