package cn.awbz.davplayer.data

import java.io.Closeable
import java.net.ServerSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class ResourceHttpTest {
    private data class Reply(val status: Int, val body: ByteArray = ByteArray(0), val headers: Map<String, String> = emptyMap())
    /** Real loopback HTTP fixture; no Android stubs or extra network dependency. */
    private class Server(val handler: (String, Map<String, String>) -> Reply) : Closeable {
        private val socket = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        val port get() = socket.localPort
        private val thread = Thread {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                client.use {
                    runCatching {
                        val input = client.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                        val first = input.readLine() ?: return@runCatching
                        val headers = mutableMapOf<String, String>()
                        while (true) { val line = input.readLine(); if (line.isNullOrEmpty()) break; headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim() }
                        val reply = handler(first.split(' ')[1], headers)
                        val output = client.getOutputStream()
                        val head = "HTTP/1.1 ${reply.status} Status\r\nConnection: close\r\nContent-Length: ${reply.body.size}\r\n" +
                            reply.headers.entries.joinToString("") { "${it.key}: ${it.value}\r\n" } + "\r\n"
                        output.write(head.toByteArray(Charsets.ISO_8859_1)); output.write(reply.body); output.flush()
                    }
                }
            }
        }.apply { isDaemon = true; start() }
        override fun close() { socket.close(); thread.join(2000) }
    }
    @Test fun signedDownloadsNeverSendAccountCredentialsAndReportMissingResources() {
        val sentAuth = AtomicReference<String>(); val redirectedAuth = AtomicReference<String>("unset")
        Server { _, headers ->
            redirectedAuth.set(headers["authorization"])
            Reply(200, byteArrayOf(1, 2, 3), mapOf("ETag" to "image-v1"))
        }.use { target ->
            Server { path, headers ->
                if (path.endsWith("poster.jpg")) {
                    sentAuth.set(headers["authorization"])
                    Reply(302, headers = mapOf("Location" to "http://127.0.0.1:${target.port}/poster"))
                } else Reply(404)
            }.use { origin ->
                val connection = Connection("fixture", "fixture", "http://127.0.0.1:${origin.port}/dav/", "fixture", "fixture", AuthMode.BASIC)
                val http = ResourceHttp { c, p, _ -> MediaPaths.fromPath(c, p).toString() }
                assertArrayEquals(byteArrayOf(1, 2, 3), http.get(connection, "/dav/poster.jpg", 10, RequestScope()).data)
                assertNull(sentAuth.get()); assertNull(redirectedAuth.get())
                assertEquals(404, http.get(connection, "/dav/movie.nfo", 10, RequestScope()).status)
            }
        }
    }
    @Test fun downloadLimitAndCancellationPreventUnboundedReads() {
        Server { _, _ -> Reply(200, ByteArray(100)) }.use { server ->
            val connection = Connection("fixture", "fixture", "http://127.0.0.1:${server.port}/dav/", "", "")
            try { ResourceHttp { c, p, _ -> MediaPaths.fromPath(c, p).toString() }.get(connection, "/dav/large.nfo", 10, RequestScope()); fail() } catch (_: MediaFailure) { }
            val scope = RequestScope().apply { cancel() }
            try { ResourceHttp { c, p, _ -> MediaPaths.fromPath(c, p).toString() }.get(connection, "/dav/large.nfo", 1000, scope); fail() } catch (_: MediaFailure) { }
        }
    }
}
