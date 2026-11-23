package com.codewidget.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import androidx.work.workDataOf
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** ウィジェットの実行・描画・更新をまとめたもの */
object WidgetUpdater {
    const val ACTION_TAP_REFRESH = "com.codewidget.app.TAP_REFRESH"

    fun allIds(c: Context): IntArray =
        AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c, ScriptWidgetProvider::class.java))

    /** ウィジェットのサイズ(dp)と種類(small/medium/large) */
    fun sizeOf(c: Context, id: Int): Triple<Int, Int, String> {
        val o: Bundle = AppWidgetManager.getInstance(c).getAppWidgetOptions(id)
        val portrait = c.resources.configuration.orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE
        var w = if (portrait) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        var h = if (portrait) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        if (w <= 0) w = 160
        if (h <= 0) h = 160
        return Triple(w, h, familyFor(w, h))
    }

    fun familyFor(w: Int, h: Int): String = when {
        w < 230 -> "small"
        h < 260 -> "medium"
        else -> "large"
    }

    /** スクリプトを実行してウィジェットを更新する */
    suspend fun runAndUpdate(c: Context, id: Int, force: Boolean) {
        val name = WidgetPrefs.script(c, id)
        if (name == null) {
            showMessage(c, id, "長押し →「ウィジェットの設定」からスクリプトを選んでください")
            return
        }
        if (!force && System.currentTimeMillis() < WidgetPrefs.nextRefresh(c, id)) return
        val code = ScriptStore.read(c, name)
        val (w, h, family) = sizeOf(c, id)
        val renderer = WidgetRenderer(c, ScriptRunner.isDark(c))
        val json = if (code == null) {
            renderer.errorJson("スクリプト「$name」が見つかりません")
        } else {
            val r = ScriptRunner.run(c, RunContext(name, code, true, family, WidgetPrefs.param(c, id)))
            r.widgetJson ?: renderer.errorJson(r.error ?: "不明なエラー")
        }
        WidgetPrefs.saveCache(c, id, json)
        val refreshAfter = JSONObject(json).optLong("refreshAfter", 0L)
        WidgetPrefs.setNextRefresh(c, id, refreshAfter)
        draw(c, id, json, w, h, renderer)
    }

    /** 保存済みの描画データで描き直す（サイズ変更時など。スクリプトは再実行しない） */
    fun redrawFromCache(c: Context, id: Int): Boolean {
        val json = WidgetPrefs.loadCache(c, id) ?: return false
        val (w, h, _) = sizeOf(c, id)
        draw(c, id, json, w, h, WidgetRenderer(c, ScriptRunner.isDark(c)))
        return true
    }

    private fun draw(c: Context, id: Int, json: String, w: Int, h: Int, renderer: WidgetRenderer) {
        val bmp = try {
            renderer.render(json, w, h)
        } catch (e: Exception) {
            renderer.render(renderer.errorJson("描画エラー: ${e.message}"), w, h)
        }
        val views = RemoteViews(c.packageName, R.layout.widget_layout)
        views.setImageViewBitmap(R.id.widget_image, bmp)
        views.setViewVisibility(R.id.widget_loading, android.view.View.GONE)
        val url = JSONObject(json).optString("url").takeIf { it.isNotEmpty() && it != "null" }
        views.setOnClickPendingIntent(R.id.widget_root, tapIntent(c, id, url))
        AppWidgetManager.getInstance(c).updateAppWidget(id, views)
    }

    fun showMessage(c: Context, id: Int, msg: String) {
        val views = RemoteViews(c.packageName, R.layout.widget_layout)
        views.setTextViewText(R.id.widget_loading, msg)
        views.setViewVisibility(R.id.widget_loading, android.view.View.VISIBLE)
        views.setOnClickPendingIntent(R.id.widget_root, tapIntent(c, id, null))
        AppWidgetManager.getInstance(c).updateAppWidget(id, views)
    }

    /** タップ時：URL があれば開く。なければその場で更新する */
    private fun tapIntent(c: Context, id: Int, url: String?): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (url != null) {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            PendingIntent.getActivity(c, id, i, flags)
        } else {
            val i = Intent(c, ScriptWidgetProvider::class.java).setAction(ACTION_TAP_REFRESH)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            PendingIntent.getBroadcast(c, id, i, flags)
        }
    }

    // ------------------------------------------------------------ スケジュール
    fun schedulePeriodic(c: Context) {
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("periodic_refresh", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun refreshNow(c: Context, ids: IntArray? = null, force: Boolean = true) {
        val req = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setInputData(workDataOf("ids" to (ids ?: intArrayOf()), "force" to force))
            .build()
        val key = "refresh_" + (ids?.joinToString("_") ?: "all")
        WorkManager.getInstance(c).enqueueUniqueWork(key, ExistingWorkPolicy.REPLACE, req)
    }
}

class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        val requested = inputData.getIntArray("ids")
        val ids = if (requested == null || requested.isEmpty()) WidgetUpdater.allIds(c) else requested
        val force = inputData.getBoolean("force", false)
        for (id in ids) {
            try {
                WidgetUpdater.runAndUpdate(c, id, force)
            } catch (e: Exception) {
                WidgetUpdater.showMessage(c, id, "更新に失敗しました: ${e.message}")
            }
        }
        return Result.success()
    }
}
