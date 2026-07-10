package com.tika.paycard.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.tika.paycard.R
import com.tika.paycard.ui.ColorManager
import com.tika.paycard.ui.PayActivity
import com.tika.paycard.data.AccountStore
import com.tika.paycard.data.PayCodeManager
import com.tika.paycard.qr.QrGenerator
import com.tika.paycard.work.RefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 桌面组件:外层圆角卡 + 顶部姓名圆角条 + 下方大圆角二维码方块。
 * 点姓名条 = 切到下一个用户;点二维码 = 打开全屏付款页强制刷新。
 */
class PayWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        RefreshWorker.schedule(context)
        fetchAndRender(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { renderWidget(context, manager, it) }
        fetchAndRender(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_RENDER -> renderAll(context, intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS))
            ACTION_SWITCH -> {
                AccountStore.get(context).switchToNext()
                renderAll(context)
                fetchAndRender(context)
            }
            ACTION_REFRESH -> fetchAndRender(context)
        }
    }

    /** 拉当前账号最新码再刷组件。切号与点刷新共用。 */
    private fun fetchAndRender(
        context: Context,
        manager: AppWidgetManager = AppWidgetManager.getInstance(context),
        ids: IntArray = widgetIds(context, manager)
    ) {
        if (ids.isEmpty()) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PayCodeManager.refreshCurrent(context)
                refreshAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    private fun renderAll(context: Context, ids: IntArray? = null) {
        val manager = AppWidgetManager.getInstance(context)
        (ids ?: widgetIds(context, manager)).forEach { renderWidget(context, manager, it) }
    }

    private fun renderWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_paycard)
        val scheme = ColorManager.getScheme(context)
        views.setInt(R.id.widget_name_bar, "setBackgroundResource", scheme.widgetName)
        views.setInt(R.id.widget_refresh, "setBackgroundResource", scheme.widgetRefresh)
        val account = AccountStore.get(context).current()

        if (account == null) {
            views.setTextViewText(R.id.widget_name, context.getString(R.string.widget_add_card))
            views.setViewVisibility(R.id.widget_qr, android.view.View.GONE)
            views.setViewVisibility(R.id.widget_hint, android.view.View.VISIBLE)
            views.setTextViewText(R.id.widget_hint, context.getString(R.string.widget_no_account))
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        } else {
            views.setTextViewText(R.id.widget_name, account.displayName())
            if (account.hasFreshCode()) {
                val bmp = QrGenerator.encode(account.cachedCode, QrGenerator.SIZE_WIDGET)
                views.setImageViewBitmap(R.id.widget_qr, bmp)
                views.setViewVisibility(R.id.widget_qr, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_hint, android.view.View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_qr, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_hint, android.view.View.VISIBLE)
                views.setTextViewText(R.id.widget_hint, context.getString(R.string.widget_tap_refresh))
            }
            // 点姓名条切换用户
            views.setOnClickPendingIntent(R.id.widget_name, switchIntent(context))
            // 点二维码/整体打开全屏付款页
            views.setOnClickPendingIntent(R.id.widget_qr, openPayIntent(context))
            views.setOnClickPendingIntent(R.id.widget_hint, openPayIntent(context))
            // 点刷新按钮就地拉最新码
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context))
        }
        // RemoteViews 经 Binder 传桌面进程,事务超限等异常会让这次更新无声丢失,记下来便于定位
        runCatching { manager.updateAppWidget(widgetId, views) }
            .onFailure { Log.w(TAG, "updateAppWidget 失败,组件本轮未刷新", it) }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, PayActivity::class.java)
        return PendingIntent.getActivity(context, 0, intent, flags())
    }

    private fun openPayIntent(context: Context): PendingIntent {
        val intent = Intent(context, PayActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, 1, intent, flags())
    }

    private fun switchIntent(context: Context): PendingIntent {
        val intent = Intent(context, PayWidgetProvider::class.java).setAction(ACTION_SWITCH)
        return PendingIntent.getBroadcast(context, 2, intent, flags())
    }

    private fun refreshIntent(context: Context): PendingIntent {
        val intent = Intent(context, PayWidgetProvider::class.java).setAction(ACTION_REFRESH)
        return PendingIntent.getBroadcast(context, 3, intent, flags())
    }

    private fun flags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    companion object {
        private const val TAG = "PayWidget"
        private const val ACTION_RENDER = "com.tika.paycard.WIDGET_RENDER"
        private const val ACTION_SWITCH = "com.tika.paycard.WIDGET_SWITCH"
        private const val ACTION_REFRESH = "com.tika.paycard.WIDGET_REFRESH"

        private fun widgetIds(context: Context, manager: AppWidgetManager): IntArray {
            val cn = ComponentName(context, PayWidgetProvider::class.java)
            return manager.getAppWidgetIds(cn)
        }

        /** 刷新所有已放置的组件 */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = widgetIds(context, manager)
            if (ids.isNotEmpty()) {
                val intent = Intent(context, PayWidgetProvider::class.java).apply {
                    action = ACTION_RENDER
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
