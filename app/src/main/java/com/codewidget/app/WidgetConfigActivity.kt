package com.codewidget.app

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** ウィジェットを置いたとき（または長押し→設定）に、表示するスクリプトを選ぶ画面 */
class WidgetConfigActivity : AppCompatActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        title = "表示するスクリプトを選択"

        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val names = ScriptStore.list(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        if (names.isEmpty()) {
            root.addView(TextView(this).apply { text = "スクリプトがありません。先にアプリでスクリプトを作成してください。" })
            root.addView(Button(this).apply {
                text = "アプリを開く"
                setOnClickListener { startActivity(Intent(this@WidgetConfigActivity, MainActivity::class.java)); finish() }
            })
            setContentView(root)
            return
        }

        val list = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_SINGLE
            adapter = ArrayAdapter(this@WidgetConfigActivity, android.R.layout.simple_list_item_single_choice, names)
        }
        val current = WidgetPrefs.script(this, widgetId)
        list.setItemChecked(names.indexOf(current).takeIf { it >= 0 } ?: 0, true)
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(TextView(this).apply {
            text = "パラメータ（任意・スクリプト内では args.widgetParameter）"
            textSize = 12f
            setPadding(0, dp(8), 0, 0)
        })
        val param = EditText(this).apply {
            setSingleLine()
            setText(WidgetPrefs.param(this@WidgetConfigActivity, widgetId) ?: "")
        }
        root.addView(param)

        root.addView(Button(this).apply {
            text = "決定"
            setOnClickListener {
                val name = names[list.checkedItemPosition.coerceAtLeast(0)]
                WidgetPrefs.setScript(this@WidgetConfigActivity, widgetId, name, param.text.toString().trim())
                WidgetUpdater.schedulePeriodic(this@WidgetConfigActivity)
                WidgetUpdater.showMessage(this@WidgetConfigActivity, widgetId, "読み込み中…")
                WidgetUpdater.refreshNow(this@WidgetConfigActivity, intArrayOf(widgetId))
                setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                finish()
            }
        })
        setContentView(root)
    }
}

/** Keychain（APIキーなどの保存場所）を編集するダイアログ */
object KeychainDialog {
    fun show(c: Context) {
        val prefs = c.getSharedPreferences("keychain", Context.MODE_PRIVATE)
        val keys = prefs.all.keys.sorted()
        val items = (listOf("＋ 新しい値を追加") + keys.map { "🔑 $it（タップで削除）" }).toTypedArray()
        AlertDialog.Builder(c).setTitle("Keychain").setItems(items) { _, which ->
            if (which == 0) add(c) else {
                val k = keys[which - 1]
                AlertDialog.Builder(c).setTitle("「$k」を削除しますか？")
                    .setPositiveButton("削除") { _, _ -> prefs.edit().remove(k).apply() }
                    .setNegativeButton("キャンセル", null).show()
            }
        }.show()
    }

    private fun add(c: Context) {
        val d = c.resources.displayMetrics.density
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (8 * d).toInt(), (20 * d).toInt(), 0)
        }
        val key = EditText(c).apply { hint = "キー（例: openai_key）"; setSingleLine() }
        val value = EditText(c).apply { hint = "値"; setSingleLine() }
        box.addView(key); box.addView(value)
        AlertDialog.Builder(c).setTitle("Keychain に保存").setView(box)
            .setPositiveButton("保存") { _, _ ->
                if (key.text.isNotBlank()) {
                    c.getSharedPreferences("keychain", Context.MODE_PRIVATE).edit()
                        .putString(key.text.toString().trim(), value.text.toString()).apply()
                }
            }
            .setNegativeButton("キャンセル", null).show()
    }
}
