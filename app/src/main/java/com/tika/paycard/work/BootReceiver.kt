package com.tika.paycard.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机后恢复周期刷新。前台服务(dataSync)不在 BOOT_COMPLETED 的启动豁免内,
 * 稳妥档的服务留待用户打开 App 时由前台交互合法拉起,开机只调度 WorkManager 兜底。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            RefreshWorker.schedule(context)
        }
    }
}
