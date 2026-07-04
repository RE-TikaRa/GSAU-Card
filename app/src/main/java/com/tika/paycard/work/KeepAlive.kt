package com.tika.paycard.work

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * 保活档位与系统设置跳转。
 * 省心档:只用 WorkManager。稳妥档:额外开前台服务。
 * 另外提供电池白名单、各家 ROM 自启动页的跳转引导。
 */
object KeepAlive {

    enum class Mode { LITE, STEADY }

    private const val PREFS = "keepalive"
    private const val KEY_MODE = "mode"

    fun getMode(context: Context): Mode {
        val v = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, Mode.LITE.name)
        return runCatching { Mode.valueOf(v!!) }.getOrDefault(Mode.LITE)
    }

    fun setMode(context: Context, mode: Mode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, mode.name).apply()
        apply(context, mode)
    }

    /** 按当前档位启动对应保活机制 */
    fun apply(context: Context, mode: Mode = getMode(context)) {
        RefreshWorker.schedule(context)
        when (mode) {
            Mode.LITE -> RefreshService.stop(context)
            Mode.STEADY -> RefreshService.start(context)
        }
    }

    /** 是否已在电池优化白名单 */
    fun isIgnoringBattery(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 跳到「忽略电池优化」申请弹窗(系统标准,三家通用) */
    fun requestIgnoreBattery(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        runCatching {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure { openBatterySettings(context) }
    }

    private fun openBatterySettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /**
     * 跳到各家 ROM 的自启动管理页。跳不准就退回应用详情页。
     * 覆盖小米/红米(MIUI)、OPPO/realme(ColorOS)、vivo/iQOO(OriginOS/Funtouch)。
     */
    fun openAutoStartSettings(context: Context) {
        val candidates = listOf(
            // 小米 MIUI
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            // OPPO ColorOS
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            // vivo
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        )
        for (cn in candidates) {
            val intent = Intent().apply {
                component = cn
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                runCatching { context.startActivity(intent); return }
            }
        }
        // 退回应用详情页
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}
