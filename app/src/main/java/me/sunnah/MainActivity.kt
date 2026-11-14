package me.sunnah
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.net.Uri

class MainActivity : AppCompatActivity() {
    private lateinit var wv: WebView
    private var cb: ValueCallback<Array<Uri>>? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wv = WebView(this)
        setContentView(wv)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.allowFileAccess = true
        wv.settings.allowContentAccess = true
        wv.webViewClient = WebViewClient()
        wv.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(w: WebView?, c: ValueCallback<Array<Uri>>?, p: FileChooserParams?): Boolean {
                if (cb != null) { cb?.onReceiveValue(null); cb = null }
                cb = c
                try { startActivityForResult(p?.createIntent(), 1001) } catch (e: Exception) { cb = null; return false }
                return true
            }
        }
        wv.loadUrl("file:///android_asset/index.html")
    }
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 1001) { cb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data)); cb = null }
    }
}