package com.liar.han1meplus

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Temporary diagnostics bridge for the existing private R2 crash pipeline. */
object CrashReporter {
    private const val TAG = "Han1meCrash"
    private const val MAX_REPORT_BYTES = 512 * 1024
    private const val MAX_LOCAL_REPORTS = 3
    private val uploader = Executors.newSingleThreadExecutor()
    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val report = buildReport(app, thread, error)
            savePending(app, "crash", report)
            previousHandler?.uncaughtException(thread, error)
        }
    }

    fun uploadStartupDiagnostics(context: Context) {
        val app = context.applicationContext
        uploader.execute {
            uploadPending(app)
            upload("logs", "startup", buildStartupReport(app))
        }
    }

    fun reportPlaybackDiagnostic(context: Context, details: String) {
        val app = context.applicationContext
        uploader.execute {
            val report = buildString {
                append("=== Han1mePlus playback diagnostic ===\n")
                append("time: ").append(now()).append('\n')
                append("app: ").append(app.packageName).append('\n')
                append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
                append("android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n\n")
                append(redact(details).take(16 * 1024)).append('\n')
            }.take(MAX_REPORT_BYTES)
            if (!upload("crash", "playback", report)) savePending(app, "playback", report)
        }
    }

    private fun buildReport(context: Context, thread: Thread, error: Throwable): String {
        val out = StringBuilder()
        out.append("=== Han1mePlus crash ===\n")
        out.append("time: ").append(now()).append('\n')
        out.append("app: ").append(context.packageName).append('\n')
        out.append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        out.append("android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        out.append("thread: ").append(thread.name).append("\n\n")
        out.append("=== exception ===\n")
        val trace = StringWriter()
        error.printStackTrace(PrintWriter(trace))
        out.append(trace)
        out.append("\n=== all threads ===\n")
        runCatching {
            Thread.getAllStackTraces().forEach { (t, stack) ->
                out.append(t.name).append(" [").append(t.state).append("]\n")
                stack.take(24).forEach { out.append("  at ").append(it).append('\n') }
            }
        }
        appendLogcat(out, 800)
        return out.toString().take(MAX_REPORT_BYTES)
    }

    private fun buildStartupReport(context: Context): String = buildString {
        append("=== Han1mePlus startup diagnostics ===\n")
        append("time: ").append(now()).append('\n')
        append("app: ").append(context.packageName).append('\n')
        append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        append("android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        appendLogcat(this, 600)
    }.take(MAX_REPORT_BYTES)

    private fun appendLogcat(out: StringBuilder, limit: Int) {
        out.append("\n=== logcat ===\n")
        runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", limit.toString()))
            process.inputStream.bufferedReader().useLines { lines ->
                lines.take(limit).forEach { out.append(it).append('\n') }
            }
            process.destroy()
        }
    }

    private fun savePending(context: Context, type: String, report: String) {
        runCatching {
            val dir = File(context.filesDir, "diagnostics/pending").apply { mkdirs() }
            File(dir, "$type-${System.currentTimeMillis()}.txt").writeText(report)
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(MAX_LOCAL_REPORTS)?.forEach { it.delete() }
        }.onFailure { Log.w(TAG, "save failed", it) }
    }

    private fun uploadPending(context: Context) {
        val dir = File(context.filesDir, "diagnostics/pending")
        dir.listFiles()?.sortedBy { it.lastModified() }?.forEach { file ->
            val type = file.name.substringBefore('-').ifBlank { "crash" }
            if (upload("crash", type, file.readText())) file.delete()
        }
    }

    private fun upload(prefix: String, type: String, body: String): Boolean {
        val endpoint = BuildConfig.CRASH_R2_ENDPOINT.trimEnd('/')
        val bucket = BuildConfig.CRASH_R2_BUCKET
        val accessKey = BuildConfig.CRASH_R2_ACCESS_KEY
        val secretKey = BuildConfig.CRASH_R2_SECRET_KEY
        if (endpoint.isBlank() || bucket.isBlank() || accessKey.isBlank() || secretKey.isBlank()) {
            Log.w(TAG, "R2 diagnostics are not configured")
            return false
        }
        return runCatching {
            val requestTime = Date()
            val date = format(requestTime, "yyyyMMdd")
            val amzDate = format(requestTime, "yyyyMMdd'T'HHmmss'Z'")
            val objectKey = "$prefix/${type}-${format(requestTime, "yyyyMMdd-HHmmss-SSS")}-${android.os.Process.myPid()}.txt"
            val encodedObjectKey = objectKey.split('/').joinToString("/") { encodePathSegment(it) }
            val canonicalUri = "/${encodePathSegment(bucket)}/$encodedObjectKey"
            val payload = redact(body).toByteArray(StandardCharsets.UTF_8)
            val payloadHash = hex(sha256(payload))
            val host = URL(endpoint).host
            val contentType = "text/plain; charset=utf-8"
            val signedHeaders = "content-type;host;x-amz-content-sha256;x-amz-date"
            val canonicalHeaders = "content-type:$contentType\nhost:$host\nx-amz-content-sha256:$payloadHash\nx-amz-date:$amzDate\n"
            val canonicalRequest = "PUT\n$canonicalUri\n\n$canonicalHeaders\n$signedHeaders\n$payloadHash"
            val scope = "$date/auto/s3/aws4_request"
            val stringToSign = "AWS4-HMAC-SHA256\n$amzDate\n$scope\n${hex(sha256(canonicalRequest.toByteArray(StandardCharsets.UTF_8)))}"
            val signingKey = hmac(hmac(hmac(hmac("AWS4$secretKey", date), "auto"), "s3"), "aws4_request")
            val signature = hex(hmac(signingKey, stringToSign))
            val connection = (URL(endpoint + canonicalUri).openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                connectTimeout = 15_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Content-Type", contentType)
                setRequestProperty("X-Amz-Date", amzDate)
                setRequestProperty("X-Amz-Content-Sha256", payloadHash)
                setRequestProperty("Authorization", "AWS4-HMAC-SHA256 Credential=$accessKey/$scope, SignedHeaders=$signedHeaders, Signature=$signature")
            }
            connection.outputStream.use { it.write(payload) }
            val code = connection.responseCode
            connection.disconnect()
            code in 200..299
        }.onFailure { Log.w(TAG, "R2 upload failed", it) }.getOrDefault(false)
    }

    private fun redact(value: String): String = value
        .replace(Regex("(?i)(https?://[^\\s?#]+)(?:\\?[^\\s#]*)?(?:#[^\\s]*)?"), "${'$'}1?<redacted>")
        .replace(Regex("(?i)((?:cookie|set-cookie|authorization)\\s*[:=]\\s*)[^\\r\\n]+"), "${'$'}1<redacted>")
        .replace(Regex("(?i)((?:access_token|id_token|refresh_token|token|signature|sig)\\s*[=:]\\s*)[^\\s,;]+"), "${'$'}1<redacted>")

    private fun now() = format(Date(), "yyyy-MM-dd HH:mm:ss'Z'")
    private fun format(date: Date, pattern: String) = SimpleDateFormat(pattern, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(date)
    private fun encodePathSegment(value: String) = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    private fun sha256(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value)
    private fun hmac(key: String, value: String) = hmac(key.toByteArray(StandardCharsets.UTF_8), value)
    private fun hmac(key: ByteArray, value: String) = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, algorithm))
        doFinal(value.toByteArray(StandardCharsets.UTF_8))
    }
    private fun hex(value: ByteArray) = value.joinToString("") { "%02x".format(Locale.US, it) }
}
