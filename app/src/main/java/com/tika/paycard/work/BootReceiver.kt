package com.tika.paycard.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机后恢复周期刷新。稳妥档服务由组件操作或 Activity 退到后台时恢复,
 * 开机只调度 WorkManager。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            RefreshWorker.schedule(context)
        }
    }
}
