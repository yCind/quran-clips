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
import android.widget.Toast
import java.io.OutputStream

class Bridge(private val a: Activity) {
    private var os: OutputStream? = null
    private var last: Uri? = null
    private var mime: String = "video/mp4"
    @JavascriptInterface fun start(name: String, m: String) {
        mime = m
        val v = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Hasanah")
        }
        val u = a.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)!!
        last = u
        os = a.contentResolver.openOutputStream(u)
    }
    @JavascriptInterface fun chunk(b64: String) { os?.write(Base64.decode(b64, Base64.DEFAULT)) }
    @JavascriptInterface fun share() {
        val u = last ?: return
        a.runOnUiThread {
            val i = Intent(Intent.ACTION_SEND).apply {
                type = mime; putExtra(Intent.EXTRA_STREAM, u); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            a.startActivity(Intent.createChooser(i, "مشاركة المقطع"))
        }
    }
    @JavascriptInterface fun end() {
        os?.close(); os = null
        a.runOnUiThread { Toast.makeText(a, "تم الحفظ في Downloads/Hasanah", Toast.LENGTH_LONG).show() }
    }
}

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var cb: ValueCallback<Array<Uri>>? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = Color.parseColor("#14171f")
        window.navigationBarColor = Color.parseColor("#14171f")
        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#14171f"))
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        @Suppress("DEPRECATION") run { web.settings.allowUniversalAccessFromFileURLs = true }
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.addJavascriptInterface(Bridge(this), "Android")
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(w: WebView, callback: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                cb?.onReceiveValue(null)
                cb = callback
                val i = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                }
                startActivityForResult(Intent.createChooser(i, "اختر صورة أو فيديو"), 1)
                return true
            }
        }
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
    }

    @Deprecated("") override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        if (req == 1) {
            cb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data))
            cb = null
        } else super.onActivityResult(req, res, data)
    }

    @Deprecated("") override fun onBackPressed() {
        web.evaluateJavascript("window.goBack&&window.goBack()") { r -> if (r != "true") super.onBackPressed() }
    }
}
