package com.tika.paycard.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机后按当前档位恢复保活。稳妥档需重新拉起前台服务,省心档的 WorkManager 会自行恢复。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            KeepAlive.apply(context)
        }
    }
}
