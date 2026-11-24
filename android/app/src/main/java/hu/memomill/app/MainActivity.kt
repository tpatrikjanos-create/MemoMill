package hu.memomill.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import org.json.JSONObject
import java.util.Locale
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.webkit.WebViewAssetLoader
import java.io.File

/** A MemoMill webes app, az APK-ba csomagolva (net nélkül is indul). */
class MainActivity : Activity() {
    private lateinit var web: WebView
    private var fileCb: ValueCallback<Array<Uri>>? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    companion object {
        const val BASE = "https://appassets.androidplatform.net/assets/www/index.html"
        const val PICK = 41
        const val DICTATE = 42
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        web = WebView(this)
        web.setBackgroundColor(Ui.BG)
        setContentView(web)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.host == "appassets.androidplatform.net") return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, u))
                } catch (_: Exception) {
                }
                return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCb?.onReceiveValue(null)
                fileCb = cb
                return try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(params.createIntent(), PICK)
                    true
                } catch (_: Exception) {
                    fileCb = null
                    false
                }
            }
        }
        web.addJavascriptInterface(Bridge(), "MemoMillAndroid")
        web.loadUrl(BASE + if (intent.getBooleanExtra("review", false)) "#review" else "")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("review", false)) {
            web.evaluateJavascript("window.memoMillOpenReview && window.memoMillOpenReview()", null)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK) {
            fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            fileCb = null
        }
        if (requestCode == DICTATE) {
            val text = if (resultCode == RESULT_OK) data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: "" else ""
            web.evaluateJavascript("window.memoMillDictation && window.memoMillDictation(" + JSONObject.quote(text) + ")", null)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        web.evaluateJavascript("(window.memoMillBack && window.memoMillBack()) ? 'y' : 'n'") { r ->
            if (r != "\"y\"") moveTaskToBack(true)
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    private fun saveDownload(name: String, content: String): String? = try {
        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: throw IllegalStateException()
            contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) } ?: throw IllegalStateException()
            "Letöltések/$name"
        } else {
            val f = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name)
            f.writeText(content)
            f.absolutePath
        }
    } catch (_: Exception) {
        null
    }

    inner class Bridge {
        @JavascriptInterface
        fun reportStatus(json: String) {
            LockStore.saveStatus(this@MainActivity, json)
        }

        @JavascriptInterface
        fun reportCards(json: String) {
            LockStore.saveCards(this@MainActivity, json)
        }

        @JavascriptInterface
        fun openLockSettings() {
            runOnUiThread { startActivity(Intent(this@MainActivity, LockSettingsActivity::class.java)) }
        }

        @JavascriptInterface
        fun canDictate(): Boolean =
            packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty()

        @JavascriptInterface
        fun startDictation(lang: String) {
            runOnUiThread {
                val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Mondd a szöveget")
                }
                try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(i, DICTATE)
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "Nincs beszédfelismerő a telefonon", Toast.LENGTH_LONG).show()
                    web.evaluateJavascript("window.memoMillDictation && window.memoMillDictation('')", null)
                }
            }
        }

        @JavascriptInterface
        fun speak(text: String) {
            runOnUiThread {
                val t = tts
                if (t != null && ttsReady) { t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mm"); return@runOnUiThread }
                tts = TextToSpeech(this@MainActivity) { st ->
                    ttsReady = st == TextToSpeech.SUCCESS
                    if (ttsReady) {
                        tts?.language = Locale("hu", "HU")
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mm")
                    }
                }
            }
        }

        @JavascriptInterface
        fun saveFile(name: String, content: String) {
            runOnUiThread {
                val where = saveDownload(name, content)
                Toast.makeText(this@MainActivity, if (where != null) "Mentve: $where" else "Nem sikerült menteni", Toast.LENGTH_LONG).show()
            }
        }
    }
}
