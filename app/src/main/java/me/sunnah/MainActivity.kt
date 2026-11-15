package me.sunnah

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.RenderProcessGoneDetail
import android.widget.Toast
import android.view.ViewGroup
import androidx.webkit.WebViewAssetLoader
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class Bridge(private val activity: Activity) {
    private val netExecutor = Executors.newFixedThreadPool(3)

    // Fetch remote Quran/text/image/audio resources natively to avoid WebView CORS failures.
    @JavascriptInterface fun nativeFetch(url: String, requestId: String) {
        netExecutor.execute {
            var conn: HttpURLConnection? = null
            try {
                val parsed = Uri.parse(url)
                require(parsed.scheme == "https" && parsed.host in setOf(
                    "api.alquran.cloud", "cdn.islamic.network", "everyayah.com",
                    "commons.wikimedia.org", "upload.wikimedia.org", "fonts.googleapis.com", "fonts.gstatic.com"
                )) { "عنوان مصدر غير مسموح" }
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 45000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "HasanahAndroid/1.2")
                }
                conn = connection
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val bytes = stream?.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(64 * 1024)
                    var total = 0
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        total += n
                        if (total > 30 * 1024 * 1024) throw IllegalStateException("الملف أكبر من الحد المسموح")
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                } ?: ByteArray(0)
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val mime = connection.contentType ?: "application/octet-stream"
                val js = "window.__nativeFetchDone(${org.json.JSONObject.quote(requestId)},$status,${org.json.JSONObject.quote(b64)},${org.json.JSONObject.quote(mime)},null)"
                activity.runOnUiThread { if (!activity.isFinishing && !activity.isDestroyed) (activity as? MainActivity)?.runNativeJs(js) }
            } catch (e: Exception) {
                val msg = org.json.JSONObject.quote(e.message ?: "فشل الاتصال بالمصدر")
                val js = "window.__nativeFetchDone(${org.json.JSONObject.quote(requestId)},0,'','application/octet-stream',$msg)"
                activity.runOnUiThread { if (!activity.isFinishing && !activity.isDestroyed) (activity as? MainActivity)?.runNativeJs(js) }
            } finally { conn?.disconnect() }
        }
    }
    private var output: OutputStream? = null
    private var lastUri: Uri? = null
    private var mimeType: String = "video/mp4"

    @Synchronized @JavascriptInterface
    fun start(name: String, mime: String): Boolean {
        try {
            mimeType = mime.ifBlank { "video/mp4" }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name.replace(Regex("[^\\p{L}\\p{N}._-]"), "_"))
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Hasanah")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = activity.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("تعذر إنشاء ملف التنزيل")
            lastUri = uri
            output = BufferedOutputStream(activity.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("تعذر فتح ملف التنزيل"), 256 * 1024)
            return true
        } catch (e: Exception) {
            output = null
            activity.runOnUiThread { Toast.makeText(activity, e.message ?: "فشل بدء الحفظ", Toast.LENGTH_LONG).show() }
            return false
        }
    }

    @Synchronized @JavascriptInterface
    fun chunk(base64: String) {
        try { output?.write(Base64.decode(base64, Base64.DEFAULT)) }
        catch (e: Exception) { activity.runOnUiThread { Toast.makeText(activity, "حدث خطأ أثناء حفظ الملف", Toast.LENGTH_LONG).show() } }
    }

    @JavascriptInterface fun share() {
        val uri = lastUri ?: return
        activity.runOnUiThread {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(intent, "مشاركة المقطع"))
        }
    }

    @Synchronized @JavascriptInterface
    fun end() {
        try { output?.flush(); output?.close() } catch (_: Exception) { }
        output = null
        lastUri?.let { uri ->
            try {
                val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                activity.contentResolver.update(uri, values, null, null)
            } catch (_: Exception) { }
        }
        activity.runOnUiThread { Toast.makeText(activity, "تم الحفظ في Downloads/Hasanah", Toast.LENGTH_LONG).show() }
    }

    @JavascriptInterface fun playBackground(url: String, title: String, artist: String) {
        if (!url.startsWith("https://")) return
        val intent = Intent(activity, QuranPlaybackService::class.java).apply {
            action = QuranPlaybackService.ACTION_PLAY
            putExtra(QuranPlaybackService.EXTRA_URL, url)
            putExtra(QuranPlaybackService.EXTRA_TITLE, title)
            putExtra(QuranPlaybackService.EXTRA_ARTIST, artist)
        }
        androidx.core.content.ContextCompat.startForegroundService(activity, intent)
    }
    @JavascriptInterface fun openExternal(url: String) {
        if (!url.startsWith("https://")) return
        activity.runOnUiThread { try { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { } }
    }
    @JavascriptInterface fun restoreBackground() = sendPlayback(QuranPlaybackService.ACTION_RESTORE)
    @JavascriptInterface fun pauseBackground() = sendPlayback(QuranPlaybackService.ACTION_PAUSE)
    @JavascriptInterface fun resumeBackground() = sendPlayback(QuranPlaybackService.ACTION_RESUME)
    @JavascriptInterface fun stopBackground() = sendPlayback(QuranPlaybackService.ACTION_STOP)
    @JavascriptInterface fun seekBackground(seconds: Double) {
        val intent = Intent(activity, QuranPlaybackService::class.java).apply {
            action = QuranPlaybackService.ACTION_SEEK
            putExtra(QuranPlaybackService.EXTRA_POSITION, (seconds.coerceAtLeast(0.0) * 1000).toLong())
        }
        activity.startService(intent)
    }
    private fun sendPlayback(action: String) { activity.startService(Intent(activity, QuranPlaybackService::class.java).setAction(action)) }
}

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var assetLoader: WebViewAssetLoader

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = Color.rgb(20, 23, 31)
        window.navigationBarColor = Color.rgb(20, 23, 31)
        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        web = createWebView()
        setContentView(web)
        if (state == null || !web.restoreState(state).let { it != null }) {
            web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
        }
    }

    fun runNativeJs(script: String) { if (::web.isInitialized) web.evaluateJavascript(script, null) }

    private fun createWebView(): WebView = WebView(this).apply {
        setBackgroundColor(Color.rgb(7, 25, 18))
        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            // Keep local app assets and DOM storage enabled; avoid file URL access.
            loadsImagesAutomatically = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        if (android.os.Build.VERSION.SDK_INT >= 26) setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true)
        addJavascriptInterface(Bridge(this@MainActivity), "Android")
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: android.webkit.WebResourceRequest) =
                assetLoader.shouldInterceptRequest(request.url)
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (isFinishing || isDestroyed) return true
                val parent = view.parent as? ViewGroup
                parent?.removeView(view)
                view.stopLoading()
                view.removeJavascriptInterface("Android")
                view.removeAllViews()
                view.destroy()
                web = createWebView()
                val params = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                if (parent != null) parent.addView(web, params) else setContentView(web)
                web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
                return true
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(v: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                }
                return try {
                    startActivityForResult(Intent.createChooser(intent, "اختر صورة أو فيديو"), 1)
                    true
                } catch (_: Exception) { fileCallback = null; false }
            }
        }
    }

    @Deprecated("") override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1) {
            fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCallback = null
        } else super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onPause() {
        if (::web.isInitialized) web.onPause()
        // Do not call WebView.pauseTimers(): it pauses timers for every WebView and can break
        // audio/background playback while the Activity is not visible.
        super.onPause()
    }
    override fun onResume() { super.onResume(); if (::web.isInitialized) web.onResume() }
    override fun onSaveInstanceState(outState: Bundle) { if (::web.isInitialized) web.saveState(outState); super.onSaveInstanceState(outState) }
    override fun onDestroy() {
        if (::web.isInitialized) { web.stopLoading(); web.removeJavascriptInterface("Android"); web.removeAllViews(); web.destroy() }
        fileCallback?.onReceiveValue(null); fileCallback = null
        super.onDestroy()
    }
    @Deprecated("") override fun onBackPressed() {
        if (!::web.isInitialized || isFinishing || isDestroyed) { super.onBackPressed(); return }
        web.evaluateJavascript("window.goBack&&window.goBack()") { result ->
            if (result != "true" && !isFinishing && !isDestroyed) super.onBackPressed()
        }
    }
}
