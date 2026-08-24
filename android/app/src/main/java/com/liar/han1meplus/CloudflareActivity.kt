package com.liar.han1meplus

import androidx.annotation.Keep;
import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse

@Keep
class CloudflareActivity : Activity() {
    companion object {
        const val requestUrlKey = "request_url"
        const val autoCompleteKey = "auto_complete"
        var onFinished: ((String) -> Unit)? = null
    }

    private val userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36"
    private val handler = Handler(Looper.getMainLooper())
    private var completed = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(requestUrlKey) ?: return finish()
        val webView = WebView(this)
        setContentView(webView)
        val cookies = CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        val loginMode = intent.getBooleanExtra(autoCompleteKey, false)
        val initialClearance = clearanceCookie(cookies.getCookie(url).orEmpty())
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            javaScriptCanOpenWindowsAutomatically = true
            userAgentString = userAgent
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (request.url.scheme != "https") return super.shouldInterceptRequest(view, request)
                return MainActivity.interceptActiveWebViewRequest(request)
                    ?: WebResourceResponse("text/plain", "UTF-8", 503, "ECH unavailable", emptyMap(), "ECH unavailable".byteInputStream())
            }
        }
        handler.post(object : Runnable {
            override fun run() {
                if (isFinishing || completed) return
                if (loginMode) {
                    handler.postDelayed(this, 500)
                    return
                }
                val cookie = cookies.getCookie(url).orEmpty()
                if (clearanceCookie(cookie)?.let { it != initialClearance } == true) {
                    completed = true
                    cookies.flush()
                    MainActivity.saveCookies(this@CloudflareActivity, cookie, url)
                    onFinished?.invoke(cookie)
                    onFinished = null
                    finish()
                    return
                }
                handler.postDelayed(this, 500)
            }
        })
        webView.loadUrl(url)
    }

    private fun clearanceCookie(cookies: String): String? = cookies
        .split(';')
        .map { it.trim() }
        .firstOrNull { it.startsWith("cf_clearance=", ignoreCase = true) }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (!completed) {
            val url = intent.getStringExtra(requestUrlKey).orEmpty()
            val cookie = CookieManager.getInstance().getCookie(url).orEmpty()
            onFinished?.invoke(cookie)
        }
        onFinished = null
        super.onDestroy()
    }
}
