package com.codewidget.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

class ScriptWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetUpdater.schedulePeriodic(context)
        val configured = ids.filter { WidgetPrefs.script(context, it) != null }.toIntArray()
        if (configured.isNotEmpty()) WidgetUpdater.refreshNow(context, configured, force = false)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        // サイズが変わったら描き直し。種類(small/medium/large)が変わった場合はスクリプトも再実行
        if (WidgetPrefs.script(context, id) == null) return
        WidgetUpdater.redrawFromCache(context, id)
        WidgetUpdater.refreshNow(context, intArrayOf(id))
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { WidgetPrefs.remove(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetUpdater.ACTION_TAP_REFRESH) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                WidgetUpdater.showMessage(context, id, "更新中…")
                WidgetUpdater.refreshNow(context, intArrayOf(id))
            }
        }
    }
}
