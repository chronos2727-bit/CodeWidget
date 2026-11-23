package com.codewidget.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_NAME = "name"
        const val EXTRA_CODE = "code"
    }

    private var originalName: String? = null
    private lateinit var nameField: EditText
    private lateinit var codeField: EditText
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        originalName = intent.getStringExtra(EXTRA_NAME)
        val initial = intent.getStringExtra(EXTRA_CODE)
            ?: originalName?.let { ScriptStore.read(this, it) } ?: ""
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        nameField = EditText(this).apply {
            hint = "スクリプト名"
            setSingleLine()
            setText(originalName ?: ScriptStore.uniqueName(this@EditorActivity, "新しいスクリプト"))
        }
        root.addView(nameField)

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; isAllCaps = false; setOnClickListener { f() } }
        buttons.addView(btn("▶ 実行") { choosePreviewSize() }, LinearLayout.LayoutParams(0, -2, 1f))
        buttons.addView(btn("💾 保存") { save(true) }, LinearLayout.LayoutParams(0, -2, 1f))
        buttons.addView(btn("📋 全部貼り付け") { pasteAll() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(buttons)

        codeField = EditText(this).apply {
            setText(initial)
            typeface = Typeface.MONOSPACE
            textSize = 13f
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setHorizontallyScrolling(true)
            hint = "ここに Scriptable のコードを貼り付けてください"
        }
        root.addView(codeField, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        title = originalName ?: "新しいスクリプト"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "コードをコピー")
        menu.add(0, 2, 0, "使える機能の一覧")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> { finish(); return true }
            1 -> {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("script", codeField.text.toString()))
                Toast.makeText(this, "コピーしました", Toast.LENGTH_SHORT).show()
            }
            2 -> AlertDialog.Builder(this).setTitle("使える機能").setMessage(HELP).setPositiveButton("OK", null).show()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPause() {
        super.onPause()
        if (codeField.text.isNotBlank()) save(false)
    }

    private fun save(showToast: Boolean): String {
        val newName = ScriptStore.sanitize(nameField.text.toString())
        val old = originalName
        if (old != null && old != newName) {
            if (!ScriptStore.rename(this, old, newName)) {
                if (showToast) Toast.makeText(this, "同じ名前のスクリプトがあります", Toast.LENGTH_SHORT).show()
                nameField.setText(old)
                ScriptStore.write(this, old, codeField.text.toString())
                return old
            }
        } else if (old == null && ScriptStore.exists(this, newName)) {
            val unique = ScriptStore.uniqueName(this, newName)
            nameField.setText(unique)
            ScriptStore.write(this, unique, codeField.text.toString())
            originalName = unique
            title = unique
            return unique
        }
        ScriptStore.write(this, newName, codeField.text.toString())
        originalName = newName
        title = newName
        if (showToast) {
            Toast.makeText(this, "保存しました。このスクリプトを使っているウィジェットも更新します", Toast.LENGTH_SHORT).show()
            val ids = WidgetUpdater.allIds(this).filter { WidgetPrefs.script(this, it) == newName }.toIntArray()
            if (ids.isNotEmpty()) WidgetUpdater.refreshNow(this, ids)
        }
        return newName
    }

    private fun pasteAll() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text.isNullOrBlank()) {
            Toast.makeText(this, "クリップボードが空です", Toast.LENGTH_SHORT).show()
            return
        }
        codeField.setText(text)
        Toast.makeText(this, "貼り付けました", Toast.LENGTH_SHORT).show()
    }

    private fun choosePreviewSize() {
        val labels = arrayOf("小（2×2）", "中（4×2）", "大（4×4）")
        AlertDialog.Builder(this).setTitle("プレビューのサイズ").setItems(labels) { _, which ->
            val sw = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
            val wide = minOf(sw - 48, 360)
            when (which) {
                0 -> preview(170, 170, "small")
                1 -> preview(wide, 170, "medium")
                else -> preview(wide, 370, "large")
            }
        }.show()
    }

    private fun preview(wDp: Int, hDp: Int, family: String) {
        if (running) return
        running = true
        val name = save(false)
        val code = codeField.text.toString()
        val progress = AlertDialog.Builder(this).setMessage("実行中…").setCancelable(false).show()
        lifecycleScope.launch {
            val r = ScriptRunner.run(this@EditorActivity, RunContext(name, code, true, family, null))
            val renderer = WidgetRenderer(this@EditorActivity, ScriptRunner.isDark(this@EditorActivity))
            val bmp = withContext(Dispatchers.Main) {
                try {
                    renderer.render(r.widgetJson ?: renderer.errorJson(r.error ?: "不明なエラー"), wDp, hDp)
                } catch (e: Exception) {
                    renderer.render(renderer.errorJson("描画エラー: ${e.message}"), wDp, hDp)
                }
            }
            progress.dismiss()
            running = false
            showPreview(bmp, r)
        }
    }

    private fun showPreview(bmp: android.graphics.Bitmap, r: RunResult) {
        val d = resources.displayMetrics.density
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), 0)
        }
        box.addView(ImageView(this).apply {
            setImageBitmap(bmp)
            adjustViewBounds = true
        }, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL })
        val log = buildString {
            if (r.error != null) append("❌ エラー:\n").append(r.error).append("\n\n")
            if (r.logs.isNotEmpty()) append("ログ:\n").append(r.logs.joinToString("\n"))
            if (r.error == null && r.logs.isEmpty()) append("✅ 正常に実行されました")
        }
        box.addView(HorizontalScrollView(this).apply {
            addView(TextView(this@EditorActivity).apply {
                text = log
                typeface = Typeface.MONOSPACE
                textSize = 12f
                setTextIsSelectable(true)
                setPadding(0, (12 * d).toInt(), 0, 0)
            })
        })
        AlertDialog.Builder(this)
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("閉じる", null)
            .show()
    }
}

private const val HELP = """Scriptable と同じ書き方が使えます。

● ウィジェット
ListWidget / addText / addStack / addImage / addSpacer / addDate
WidgetStack（layoutHorizontally・layoutVertically・cornerRadius・size・setPadding など）
Color / Color.dynamic / Font / LinearGradient / DrawContext / SFSymbol（絵文字で代用）

● データ取得
Request（loadJSON・loadString・loadImage・load）、fetch も使えます

● 保存
Keychain（APIキーの保存に）/ FileManager.local()・iCloud()（どちらも端末内に保存）

● その他
Location.current（設定で位置情報を許可）/ Device / DateFormatter / RelativeDateTimeFormatter
args.widgetParameter（ウィジェット設定で入力）/ config.widgetFamily / importModule

● 未対応
Calendar・Reminder・Photos・Alert（表示されません）・Notification・WebView"""
