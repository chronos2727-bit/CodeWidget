package com.codewidget.app

import android.content.Context
import java.io.File

/** スクリプト（.js ファイル）と、ウィジェットごとの設定を保存する */
object ScriptStore {
    private fun dir(c: Context) = File(c.filesDir, "scripts").apply { mkdirs() }

    fun sanitize(name: String): String =
        name.trim().replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_").take(60).ifEmpty { "無題" }

    fun list(c: Context): List<String> =
        dir(c).listFiles { f -> f.name.endsWith(".js") }
            ?.sortedByDescending { it.lastModified() }
            ?.map { it.name.removeSuffix(".js") } ?: emptyList()

    fun read(c: Context, name: String): String? =
        File(dir(c), sanitize(name) + ".js").takeIf { it.isFile }?.readText()

    fun write(c: Context, name: String, code: String) {
        File(dir(c), sanitize(name) + ".js").writeText(code)
    }

    fun exists(c: Context, name: String) = File(dir(c), sanitize(name) + ".js").isFile

    fun delete(c: Context, name: String) {
        File(dir(c), sanitize(name) + ".js").delete()
    }

    fun rename(c: Context, from: String, to: String): Boolean {
        val src = File(dir(c), sanitize(from) + ".js")
        val dst = File(dir(c), sanitize(to) + ".js")
        if (dst.exists()) return false
        val ok = src.renameTo(dst)
        if (ok) WidgetPrefs.renameScript(c, from, sanitize(to))
        return ok
    }

    fun uniqueName(c: Context, base: String): String {
        val b = sanitize(base)
        if (!exists(c, b)) return b
        var i = 2
        while (exists(c, "$b $i")) i++
        return "$b $i"
    }
}

/** ウィジェットID ↔ スクリプト の対応と、最後に描画したデータ */
object WidgetPrefs {
    private fun p(c: Context) = c.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun setScript(c: Context, id: Int, name: String, param: String?) {
        p(c).edit().putString("script_$id", name).putString("param_$id", param ?: "")
            .remove("next_$id").apply()
    }

    fun script(c: Context, id: Int): String? = p(c).getString("script_$id", null)
    fun param(c: Context, id: Int): String? = p(c).getString("param_$id", "")?.ifEmpty { null }

    fun nextRefresh(c: Context, id: Int): Long = p(c).getLong("next_$id", 0L)
    fun setNextRefresh(c: Context, id: Int, t: Long) = p(c).edit().putLong("next_$id", t).apply()

    fun remove(c: Context, id: Int) {
        p(c).edit().remove("script_$id").remove("param_$id").remove("next_$id").apply()
        cacheFile(c, id).delete()
    }

    fun renameScript(c: Context, from: String, to: String) {
        val e = p(c).edit()
        p(c).all.forEach { (k, v) -> if (k.startsWith("script_") && v == from) e.putString(k, to) }
        e.apply()
    }

    private fun cacheFile(c: Context, id: Int) = File(File(c.filesDir, "widget_cache").apply { mkdirs() }, "$id.json")
    fun saveCache(c: Context, id: Int, json: String) = cacheFile(c, id).writeText(json)
    fun loadCache(c: Context, id: Int): String? = cacheFile(c, id).takeIf { it.isFile }?.readText()
}
