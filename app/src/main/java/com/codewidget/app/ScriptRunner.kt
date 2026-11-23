package com.codewidget.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.Locale

/** スクリプト実行時の条件 */
data class RunContext(
    val scriptName: String,
    val code: String,
    val runsInWidget: Boolean,
    val widgetFamily: String,
    val widgetParameter: String?,
)

/** スクリプト実行の結果 */
data class RunResult(
    val widgetJson: String?,
    val error: String?,
    val logs: List<String>,
)

/**
 * WebView の JavaScript エンジンでユーザースクリプトを実行する。
 * WebView はメインスレッドでしか扱えないので、生成・破棄はメインスレッドで行う。
 */
object ScriptRunner {
    private const val TIMEOUT_MS = 60_000L
    private val main = Handler(Looper.getMainLooper())

    fun isDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    suspend fun run(context: Context, rc: RunContext): RunResult {
        val app = context.applicationContext
        val result = CompletableDeferred<RunResult>()
        val bridge = NativeBridge(app, rc, buildContextJson(app, rc), result)
        var webView: WebView? = null

        withContext(Dispatchers.Main) {
            webView = createWebView(app, bridge)
        }

        val out = withTimeoutOrNull(TIMEOUT_MS) { result.await() }
            ?: RunResult(null, "タイムアウトしました（${TIMEOUT_MS / 1000}秒）", bridge.logs.toList())

        withContext(Dispatchers.Main) {
            webView?.let {
                it.stopLoading()
                it.removeJavascriptInterface("__native")
                it.destroy()
            }
        }
        return out
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun createWebView(context: Context, bridge: NativeBridge): WebView {
        val wv = WebView(context)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.blockNetworkLoads = true // 通信は必ず Request(ネイティブ) 経由
        wv.addJavascriptInterface(bridge, "__native")
        val runtime = context.assets.open("runtime.js").bufferedReader().use { it.readText() }
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                if (bridge.started) return
                bridge.started = true
                view.evaluateJavascript(runtime + "\n;window.__runUserScript();", null)
            }
        }
        bridge.poll = { cb ->
            main.post { wv.evaluateJavascript("window.__pollFinish && window.__pollFinish()") { cb(it == "true") } }
        }
        wv.loadDataWithBaseURL(
            "https://codewidget.local/",
            "<!doctype html><html><head><meta charset=\"utf-8\"></head><body></body></html>",
            "text/html", "utf-8", null,
        )
        return wv
    }

    private fun buildContextJson(context: Context, rc: RunContext): String {
        val dm = context.resources.displayMetrics
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val battery = (bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100) / 100.0
        val charging = bm?.isCharging ?: false
        val files = FileAccess.dirs(context)
        return JSONObject().apply {
            put("scriptName", rc.scriptName)
            put("runsInWidget", rc.runsInWidget)
            put("widgetFamily", rc.widgetFamily)
            put("widgetParameter", rc.widgetParameter ?: JSONObject.NULL)
            put("dark", isDark(context))
            put("locale", Locale.getDefault().toLanguageTag())
            put("dirs", JSONObject().apply {
                put("documents", files.documents.absolutePath)
                put("library", files.library.absolutePath)
                put("cache", files.cache.absolutePath)
            })
            put("device", JSONObject().apply {
                put("model", Build.MODEL)
                put("version", Build.VERSION.RELEASE)
                put("screenW", dm.widthPixels / dm.density)
                put("screenH", dm.heightPixels / dm.density)
                put("scale", dm.density)
                put("battery", battery)
                put("charging", charging)
            })
        }.toString()
    }
}
