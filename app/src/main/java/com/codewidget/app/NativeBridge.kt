package com.codewidget.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.location.LocationManager
import android.util.Base64
import android.webkit.JavascriptInterface
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.Locale

/** ファイル保存先（FileManager 用） */
object FileAccess {
    data class Dirs(val documents: File, val library: File, val cache: File)

    fun dirs(context: Context): Dirs {
        val root = File(context.filesDir, "fm")
        return Dirs(
            File(root, "Documents").apply { mkdirs() },
            File(root, "Library").apply { mkdirs() },
            File(context.cacheDir, "fm").apply { mkdirs() },
        )
    }

    /** アプリ専用領域の外を触らせないためのチェック */
    fun resolve(context: Context, path: String): File? {
        val f = File(path).canonicalFile
        val allowed = listOf(context.filesDir.canonicalFile, context.cacheDir.canonicalFile)
        return if (allowed.any { f.path == it.path || f.path.startsWith(it.path + File.separator) }) f else null
    }
}

/**
 * JavaScript から呼ばれるネイティブ機能。
 * @JavascriptInterface のメソッドは WebView の専用スレッドで呼ばれるので、
 * 通信などの重い処理をここで同期的に行ってよい。
 */
class NativeBridge(
    private val context: Context,
    private val rc: RunContext,
    private val contextJson: String,
    private val result: CompletableDeferred<RunResult>,
) {
    @Volatile var started = false
    var poll: ((cb: (Boolean) -> Unit) -> Unit)? = null
    val logs: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val prefs = context.getSharedPreferences("keychain", Context.MODE_PRIVATE)

    private fun complete(r: RunResult) {
        result.complete(r)
    }

    @JavascriptInterface fun getContext(): String = contextJson
    @JavascriptInterface fun getCode(): String = rc.code

    @JavascriptInterface
    fun log(level: String, msg: String) {
        val prefix = when (level) { "warn" -> "⚠️ "; "error" -> "❌ "; else -> "" }
        if (logs.size < 500) logs.add(prefix + msg)
    }

    @JavascriptInterface
    fun setWidget(json: String) {
        complete(RunResult(json, null, logs.toList()))
    }

    @JavascriptInterface
    fun fail(msg: String) {
        complete(RunResult(null, msg, logs.toList()))
    }

    /** スクリプトは終わったがウィジェットが未設定。非同期で後から設定されるかもしれないので少し待つ */
    @JavascriptInterface
    fun scriptEnded() {
        Thread {
            repeat(20) {
                if (result.isCompleted) return@Thread
                Thread.sleep(500)
                val p = poll ?: return@repeat
                val done = CompletableDeferred<Boolean>()
                p { done.complete(it) }
                var waited = 0
                while (!done.isCompleted && waited < 2000) { Thread.sleep(20); waited += 20 }
            }
            complete(RunResult(null,
                "ウィジェットが作成されませんでした。\nスクリプトの最後で Script.setWidget(widget) を呼んでください。",
                logs.toList()))
        }.start()
    }

    // ------------------------------------------------------------ 通信
    @JavascriptInterface
    fun request(json: String): String {
        val out = JSONObject()
        try {
            val req = JSONObject(json)
            val conn = URL(req.getString("url")).openConnection() as HttpURLConnection
            val timeout = (req.optDouble("timeout", 60.0) * 1000).toInt()
            conn.connectTimeout = timeout
            conn.readTimeout = timeout
            conn.requestMethod = req.optString("method", "GET").uppercase(Locale.ROOT)
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "CodeWidget/0.1 (Android)")
            req.optJSONObject("headers")?.let { h ->
                h.keys().forEach { k -> conn.setRequestProperty(k, h.get(k).toString()) }
            }
            req.optJSONObject("body")?.let { b ->
                val bytes = if (b.has("b64")) Base64.decode(b.getString("b64"), Base64.DEFAULT)
                else b.getString("text").toByteArray(Charsets.UTF_8)
                conn.doOutput = true
                conn.outputStream.use { it.write(bytes) }
            }
            val status = conn.responseCode
            val stream = if (status >= 400) conn.errorStream else conn.inputStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            out.put("status", status)
            out.put("url", conn.url.toString())
            out.put("mime", conn.contentType ?: "")
            out.put("headers", JSONObject().apply {
                conn.headerFields.forEach { (k, v) -> if (k != null) put(k, v.joinToString(", ")) }
            })
            when (req.optString("mode")) {
                "text" -> out.put("body", decodeText(bytes, conn.contentType))
                "image" -> {
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                    if (o.outWidth <= 0) throw IllegalStateException("画像を読み込めませんでした (HTTP $status)")
                    out.put("body", Base64.encodeToString(bytes, Base64.NO_WRAP))
                    out.put("w", o.outWidth)
                    out.put("h", o.outHeight)
                }
                else -> out.put("body", Base64.encodeToString(bytes, Base64.NO_WRAP))
            }
            conn.disconnect()
        } catch (e: Exception) {
            out.put("error", "通信エラー: ${e.message ?: e.javaClass.simpleName}")
        }
        return out.toString()
    }

    private fun decodeText(bytes: ByteArray, contentType: String?): String {
        val cs = contentType?.let { Regex("charset=([^;]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
        return try {
            String(bytes, charset(cs?.trim()?.trim('"') ?: "UTF-8"))
        } catch (e: Exception) {
            String(bytes, Charsets.UTF_8)
        }
    }

    @JavascriptInterface
    fun imageInfo(b64: String): String {
        return try {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            JSONObject().put("ok", o.outWidth > 0).put("w", o.outWidth).put("h", o.outHeight).toString()
        } catch (e: Exception) {
            "{\"ok\":false}"
        }
    }

    // ------------------------------------------------------------ Keychain
    @JavascriptInterface fun keychainContains(k: String): Boolean = prefs.contains(k)
    @JavascriptInterface fun keychainGet(k: String): String? = prefs.getString(k, null)
    @JavascriptInterface fun keychainSet(k: String, v: String) { prefs.edit().putString(k, v).apply() }
    @JavascriptInterface fun keychainRemove(k: String) { prefs.edit().remove(k).apply() }

    // ------------------------------------------------------------ ファイル
    private fun f(p: String) = FileAccess.resolve(context, p)

    @JavascriptInterface
    fun fileReadB64(p: String): String? = f(p)?.takeIf { it.isFile }?.let {
        Base64.encodeToString(it.readBytes(), Base64.NO_WRAP)
    }

    @JavascriptInterface
    fun fileWriteB64(p: String, b64: String): Boolean = f(p)?.let {
        it.parentFile?.mkdirs(); it.writeBytes(Base64.decode(b64, Base64.DEFAULT)); true
    } ?: false

    @JavascriptInterface fun fileExists(p: String): Boolean = f(p)?.exists() ?: false
    @JavascriptInterface fun fileIsDir(p: String): Boolean = f(p)?.isDirectory ?: false
    @JavascriptInterface fun fileMkdir(p: String): Boolean = f(p)?.let { it.isDirectory || it.mkdirs() } ?: false
    @JavascriptInterface fun fileRemove(p: String) { f(p)?.deleteRecursively() }
    @JavascriptInterface fun fileModified(p: String): Long = f(p)?.lastModified() ?: 0L
    @JavascriptInterface fun fileSize(p: String): Long = f(p)?.length() ?: 0L

    @JavascriptInterface
    fun fileMove(a: String, b: String): Boolean {
        val src = f(a) ?: return false
        val dst = f(b) ?: return false
        dst.parentFile?.mkdirs()
        return src.renameTo(dst)
    }

    @JavascriptInterface
    fun fileList(p: String): String =
        JSONArray(f(p)?.list()?.sorted() ?: emptyList<String>()).toString()

    // ------------------------------------------------------------ 他スクリプトの読み込み
    @JavascriptInterface
    fun readScript(name: String): String? = ScriptStore.read(context, name)

    // ------------------------------------------------------------ 位置情報
    @SuppressLint("MissingPermission")
    @JavascriptInterface
    fun location(): String {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return JSONObject().put("error", "位置情報が許可されていません（アプリのトップ画面から許可してください）").toString()
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = try {
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        } catch (e: SecurityException) { null }
        if (loc != null) {
            LocationCache.save(context, loc.latitude, loc.longitude, loc.altitude, loc.accuracy.toDouble())
        }
        val c = LocationCache.load(context)
            ?: return JSONObject().put("error", "現在地を取得できませんでした（一度地図アプリなどで現在地を表示してください）").toString()
        return JSONObject().apply {
            put("latitude", c[0]); put("longitude", c[1]); put("altitude", c[2])
            put("horizontalAccuracy", c[3]); put("verticalAccuracy", c[3])
        }.toString()
    }

    @JavascriptInterface
    fun reverseGeocode(lat: Double, lon: Double, locale: String): String {
        return try {
            @Suppress("DEPRECATION")
            val list = Geocoder(context, Locale.forLanguageTag(locale.replace('_', '-'))).getFromLocation(lat, lon, 1)
                ?: emptyList()
            val arr = JSONArray()
            list.forEach { a ->
                arr.put(JSONObject().apply {
                    put("name", a.featureName ?: "")
                    put("locality", a.locality ?: "")
                    put("subLocality", a.subLocality ?: "")
                    put("administrativeArea", a.adminArea ?: "")
                    put("subAdministrativeArea", a.subAdminArea ?: "")
                    put("country", a.countryName ?: "")
                    put("isoCountryCode", a.countryCode ?: "")
                    put("postalCode", a.postalCode ?: "")
                    put("thoroughfare", a.thoroughfare ?: "")
                    put("subThoroughfare", a.subThoroughfare ?: "")
                    put("postalAddress", JSONObject().apply {
                        put("city", a.locality ?: ""); put("state", a.adminArea ?: "")
                        put("country", a.countryName ?: ""); put("street", a.thoroughfare ?: "")
                        put("postalCode", a.postalCode ?: "")
                    })
                })
            }
            JSONObject().put("results", arr).toString()
        } catch (e: Exception) {
            JSONObject().put("error", "住所の取得に失敗: ${e.message}").toString()
        }
    }
}

/** バックグラウンドでは現在地が取れないことが多いので、最後に取れた位置を保存しておく */
object LocationCache {
    fun save(c: Context, lat: Double, lon: Double, alt: Double, acc: Double) {
        c.getSharedPreferences("location", Context.MODE_PRIVATE).edit()
            .putString("v", "$lat,$lon,$alt,$acc").apply()
    }

    fun load(c: Context): DoubleArray? =
        c.getSharedPreferences("location", Context.MODE_PRIVATE).getString("v", null)
            ?.split(",")?.mapNotNull { it.toDoubleOrNull() }?.takeIf { it.size == 4 }?.toDoubleArray()
}
