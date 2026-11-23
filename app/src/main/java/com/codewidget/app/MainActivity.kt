package com.codewidget.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var list: ListView
    private lateinit var empty: TextView
    private var names: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSamplesOnce()
        WidgetUpdater.schedulePeriodic(this)

        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        fun button(text: String, onClick: () -> Unit) = Button(this).apply {
            this.text = text
            isAllCaps = false
            setOnClickListener { onClick() }
        }

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(button("＋ 新規作成") { openEditor(null, "") }, LinearLayout.LayoutParams(0, -2, 1f))
        row1.addView(button("📋 貼り付けて作成") { createFromClipboard() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(row1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(button("🔄 ウィジェット更新") {
            WidgetUpdater.refreshNow(this)
            Toast.makeText(this, "ホーム画面のウィジェットを更新しています", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row2.addView(button("⚙️ 設定") { showSettings() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(row2)

        root.addView(TextView(this).apply {
            text = "スクリプト（タップで編集・長押しで削除）"
            setPadding(0, dp(12), 0, dp(4))
            textSize = 13f
        })

        empty = TextView(this).apply {
            text = "スクリプトがありません"
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(24))
        }
        root.addView(empty)

        list = ListView(this)
        list.setOnItemClickListener { _, _, pos, _ -> openEditor(names[pos], null) }
        list.setOnItemLongClickListener { _, _, pos, _ -> confirmDelete(names[pos]); true }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(TextView(this).apply {
            text = "ホーム画面を長押し →「ウィジェット」→「コードウィジェット」を追加し、表示するスクリプトを選びます。" +
                "ウィジェットをタップすると、その場で更新されます（約15分ごとに自動更新）。"
            textSize = 12f
            setPadding(0, dp(8), 0, 0)
        })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refreshList()
        cacheLocation()
    }

    private fun refreshList() {
        names = ScriptStore.list(this)
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        empty.visibility = if (names.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun openEditor(name: String?, initialCode: String?) {
        startActivity(Intent(this, EditorActivity::class.java).apply {
            putExtra(EditorActivity.EXTRA_NAME, name)
            putExtra(EditorActivity.EXTRA_CODE, initialCode)
        })
    }

    private fun createFromClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text.isNullOrBlank()) {
            Toast.makeText(this, "クリップボードが空です", Toast.LENGTH_SHORT).show()
            return
        }
        openEditor(null, text)
    }

    private fun confirmDelete(name: String) {
        AlertDialog.Builder(this)
            .setTitle("「$name」を削除しますか？")
            .setPositiveButton("削除") { _, _ -> ScriptStore.delete(this, name); refreshList() }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showSettings() {
        val items = arrayOf(
            "📍 位置情報を許可する（Location.current() 用）",
            "🔋 電池の最適化を解除する（自動更新が止まる場合）",
            "🔑 Keychain に値を保存する（APIキーなど）",
        )
        AlertDialog.Builder(this)
            .setTitle("設定")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), 1)
                    1 -> startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    2 -> KeychainDialog.show(this)
                }
            }
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        cacheLocation()
    }

    /** アプリを開いたときに現在地を保存しておく（ウィジェット更新時はこれを使う） */
    @SuppressLint("MissingPermission")
    private fun cacheLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }?.let {
                LocationCache.save(this, it.latitude, it.longitude, it.altitude, it.accuracy.toDouble())
            }
            val provider = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
            @Suppress("DEPRECATION")
            lm.requestSingleUpdate(provider, { loc ->
                LocationCache.save(this, loc.latitude, loc.longitude, loc.altitude, loc.accuracy.toDouble())
            }, mainLooper)
        } catch (e: Exception) {
            // 位置情報が使えない端末・設定では何もしない
        }
    }

    private fun installSamplesOnce() {
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        if (prefs.getBoolean("samples_installed", false)) return
        listOf("サンプル 天気" to "sample_weather.js", "サンプル メモ一覧" to "sample_memo.js").forEach { (name, file) ->
            val code = assets.open(file).bufferedReader().use { it.readText() }
            ScriptStore.write(this, name, code)
        }
        prefs.edit().putBoolean("samples_installed", true).apply()
    }
}
