package me.sunnah

import android.app.Activity
import android.content.ContentValues
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import java.io.OutputStream

class Bridge(private val a: Activity) {
    private var os: OutputStream? = null
    @JavascriptInterface fun start(name: String) {
        val v = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "video/webm")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Quran")
        }
        val u = a.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)!!
        os = a.contentResolver.openOutputStream(u)
    }
    @JavascriptInterface fun chunk(b64: String) { os?.write(Base64.decode(b64, Base64.DEFAULT)) }
    @JavascriptInterface fun end() {
        os?.close(); os = null
        a.runOnUiThread { Toast.makeText(a, "تم الحفظ في Downloads/Quran", Toast.LENGTH_LONG).show() }
    }
}

class MainActivity : Activity() {
    private lateinit var web: WebView
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = Color.parseColor("#14171f")
        window.navigationBarColor = Color.parseColor("#14171f")
        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#14171f"))
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.addJavascriptInterface(Bridge(this), "Android")
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
    }
    @Deprecated("") override fun onBackPressed() {
        web.evaluateJavascript("window.goBack&&window.goBack()") { r -> if (r != "true") super.onBackPressed() }
    }
}
