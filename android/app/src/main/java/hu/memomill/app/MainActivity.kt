package hu.memomill.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.os.Environment
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.Manifest
import android.content.pm.PackageManager
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
        const val MIC = 43
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
        setInAppBack(true)
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

    /* prediktív vissza (Android 13+): ha az app kezeli a visszát, saját visszahívás; a kezdőoldalon a rendszer mutatja a kilépés előnézetét */
    private var backCb: Any? = null
    private fun setInAppBack(on: Boolean) {
        if (Build.VERSION.SDK_INT < 33) return
        val d = onBackInvokedDispatcher
        if (on && backCb == null) {
            val cb = OnBackInvokedCallback { handleBack() }
            d.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb)
            backCb = cb
        } else if (!on && backCb != null) {
            d.unregisterOnBackInvokedCallback(backCb as OnBackInvokedCallback)
            backCb = null
        }
    }
    private fun handleBack() {
        web.evaluateJavascript("(window.memoMillBack && window.memoMillBack()) ? 'y' : 'n'") { r ->
            if (r != "\"y\"") moveTaskToBack(true)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        web.evaluateJavascript("(window.memoMillBack && window.memoMillBack()) ? 'y' : 'n'") { r ->
            if (r != "\"y\"") moveTaskToBack(true)
        }
    }

    /* folyamatos beszédfelismerés a Hangos olvasás szinthez: részeredmények a webes részbe */
    private var sr: SpeechRecognizer? = null
    private var listening = false
    private var listenLang = "hu-HU"
    private fun js(kind: String, text: String) {
        web.evaluateJavascript("window.memoSpeech && window.memoSpeech(" + JSONObject.quote(kind) + "," + JSONObject.quote(text) + ")", null)
    }
    private fun startRec() {
        if (!listening) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MIC); return
        }
        val r = sr ?: SpeechRecognizer.createSpeechRecognizer(this).also { rec ->
            rec.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onPartialResults(b: Bundle?) {
                    val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                    js("partial", t)
                }
                override fun onResults(b: Bundle?) {
                    js("final", b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: "")
                    web.postDelayed({ startRec() }, 120)
                }
                override fun onError(error: Int) {
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { listening = false; js("error", "perm"); return }
                    if (listening) web.postDelayed({ startRec() }, if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 600L else 200L)
                }
            })
            sr = rec
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, listenLang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        }
        try { r.startListening(i) } catch (_: Exception) { js("error", "start") }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MIC) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startRec() else { listening = false; js("error", "perm") }
        }
    }

    override fun onDestroy() {
        sr?.destroy()
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
        fun setCanBack(on: Boolean) {
            runOnUiThread { setInAppBack(on) }
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
        fun canListen(): Boolean = SpeechRecognizer.isRecognitionAvailable(this@MainActivity)

        @JavascriptInterface
        fun startListen(lang: String) {
            runOnUiThread { listenLang = lang; listening = true; startRec() }
        }

        @JavascriptInterface
        fun stopListen() {
            runOnUiThread { listening = false; try { sr?.cancel() } catch (_: Exception) {} }
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
