package com.liar.han1meplus

import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

internal object HlsEchProxy {
    private const val maxRequestLine = 8192
    private val executor = Executors.newCachedThreadPool()
    private val upstream: OkHttpClient by lazy { MainActivity.createHlsClient() }
    @Volatile private var server: ServerSocket? = null
    @Volatile private var port = 0
    fun start(): Int {
        server?.let { return it.localPort }
        return synchronized(this) {
            server?.let { return@synchronized it.localPort }
            ServerSocket(0, 20, java.net.InetAddress.getByName("127.0.0.1")).also { socket ->
                server = socket
                port = socket.localPort
                executor.execute {
                    while (!socket.isClosed) {
                        try {
                            val client = socket.accept()
                            executor.execute { runCatching { handle(client) } }
                        } catch (_: java.net.SocketTimeoutException) {
                            // Retry transient accept timeouts while the proxy is alive.
                        } catch (error: java.io.IOException) {
                            if (socket.isClosed) break
                            val message = error.message.orEmpty()
                            if (!message.contains("temporarily unavailable", true) && !message.contains("EAGAIN", true)) break
                        }
                    }
                }
            }.localPort
        }
    }

    fun proxyUrl(source: String, referer: String, cookie: String?): String = url(port, source, referer, cookie)

    fun url(port: Int, source: String, referer: String, cookie: String?): String =
        "http://127.0.0.1:$port/hls?url=${enc(source)}&referer=${enc(referer)}${if (cookie.isNullOrEmpty()) "" else "&cookie=${enc(cookie)}"}"

    private fun enc(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    private fun dec(value: String) = URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    private fun handle(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 30000
            val input = BufferedInputStream(client.getInputStream())
            val output = BufferedOutputStream(client.getOutputStream())
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(' ', limit = 3)
            if (parts.size < 2 || parts[0] != "GET" || parts[1].length > maxRequestLine) return
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(input) ?: return
                if (line.isEmpty()) break
                line.indexOf(':').takeIf { it > 0 }?.let { index -> headers[line.substring(0, index).lowercase()] = line.substring(index + 1).trim() }
            }
            val query = Uri.parse("http://localhost${parts[1]}")
            val source = query.getQueryParameter("url") ?: return response(output, 400, "text/plain", "missing url".toByteArray())
            val referer = query.getQueryParameter("referer").orEmpty()
            val cookie = query.getQueryParameter("cookie")
            val request = Request.Builder().url(source)
                .header("User-Agent", MainActivity.userAgentStatic)
                .apply {
                    if (referer.isNotEmpty()) header("Referer", referer)
                    if (!cookie.isNullOrEmpty()) header("Cookie", cookie)
                }
                .build()
            runCatching {
                upstream.newCall(request).execute().use { upstreamResponse ->
                    val body = upstreamResponse.body?.bytes().orEmpty()
                    if (!upstreamResponse.isSuccessful) return response(output, upstreamResponse.code, "text/plain", body)
                    val contentType = upstreamResponse.header("Content-Type") ?: "application/octet-stream"
                    if (contentType.contains("mpegurl", true) || source.contains(".m3u8", true)) {
                        val playlist = body.toString(StandardCharsets.UTF_8)
                        val rewritten = rewritePlaylist(playlist, source, referer, cookie)
                        response(output, 200, "application/vnd.apple.mpegurl", rewritten.toByteArray(StandardCharsets.UTF_8))
                    } else response(output, 200, contentType, body)
                }
            }.getOrElse { response(output, 502, "text/plain", (it.message ?: "upstream request failed").toByteArray()) }
        }
    }

    private fun rewritePlaylist(text: String, base: String, referer: String, cookie: String?): String {
        fun proxy(value: String): String {
            val target = URI(base).resolve(value).toString()
            return url(server?.localPort ?: 0, target, referer, cookie)
        }
        return text.lineSequence().joinToString("\n") { line ->
            when {
                line.startsWith("#") -> Regex("URI=\"([^\"]+)\"").replace(line) { "URI=\"${proxy(it.groupValues[1])}\"" }
                line.isBlank() -> line
                else -> proxy(line.trim())
            }
        }
    }


    private fun readLine(input: BufferedInputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (buffer.size() <= maxRequestLine) {
            val value = input.read()
            if (value < 0) return if (buffer.size() == 0) null else buffer.toString(StandardCharsets.ISO_8859_1.name())
            if (value == '\n'.code) break
            if (value != '\r'.code) buffer.write(value)
        }
        return buffer.toString(StandardCharsets.ISO_8859_1.name())
    }

    private fun response(output: BufferedOutputStream, code: Int, type: String, body: ByteArray) {
        val reason = if (code == 200) "OK" else "Error"
        output.write("HTTP/1.1 $code $reason\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n".toByteArray())
        output.write(body)
        output.flush()
    }
}
