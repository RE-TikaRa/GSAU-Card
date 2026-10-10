package com.tika.paycard

import android.app.Application
import com.tika.paycard.ui.QrShare
import com.tika.paycard.ui.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 启动即应用已保存的主题档位,保证冷启动就是用户选定的浅色/深色/跟随系统。
 */
class PayCardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.apply(ThemeManager.getMode(this))
        CoroutineScope(Dispatchers.IO).launch {
            QrShare.clearOldImages(this@PayCardApp)
        }
    }
}
