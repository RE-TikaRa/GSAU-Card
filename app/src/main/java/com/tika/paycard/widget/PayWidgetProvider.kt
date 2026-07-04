package com.tika.paycard.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.tika.paycard.R
import com.tika.paycard.ui.PayActivity
import com.tika.paycard.data.AccountStore
import com.tika.paycard.qr.QrGenerator

/**
 * 桌面组件:外层圆角卡 + 顶部姓名圆角条 + 下方大圆角二维码方块。
 * 点姓名条 = 切到下一个用户;点二维码 = 打开全屏付款页强制刷新。
 */
class PayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { renderWidget(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_SWITCH) {
            AccountStore.get(context).switchToNext()
            refreshAll(context)
        }
    }

    private fun renderWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_paycard)
        val account = AccountStore.get(context).current()

        if (account == null) {
            views.setTextViewText(R.id.widget_name, "点击添加校园卡")
            views.setViewVisibility(R.id.widget_qr, android.view.View.GONE)
            views.setViewVisibility(R.id.widget_hint, android.view.View.VISIBLE)
            views.setTextViewText(R.id.widget_hint, "未添加账号")
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        } else {
            views.setTextViewText(R.id.widget_name, account.displayName())
            if (account.cachedCode.isNotBlank()) {
                val bmp = QrGenerator.encode(account.cachedCode, QrGenerator.SIZE_WIDGET)
                views.setImageViewBitmap(R.id.widget_qr, bmp)
                views.setViewVisibility(R.id.widget_qr, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_hint, android.view.View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_qr, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_hint, android.view.View.VISIBLE)
                views.setTextViewText(R.id.widget_hint, "点击刷新付款码")
            }
            // 点姓名条切换用户
            views.setOnClickPendingIntent(R.id.widget_name, switchIntent(context))
            // 点二维码/整体打开全屏付款页
            views.setOnClickPendingIntent(R.id.widget_qr, openPayIntent(context))
            views.setOnClickPendingIntent(R.id.widget_hint, openPayIntent(context))
        }
        manager.updateAppWidget(widgetId, views)
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

    private fun flags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    companion object {
        private const val ACTION_SWITCH = "com.tika.paycard.WIDGET_SWITCH"

        /** 刷新所有已放置的组件 */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val cn = ComponentName(context, PayWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(cn)
            if (ids.isNotEmpty()) {
                val intent = Intent(context, PayWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
