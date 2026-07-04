package com.tika.paycard.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * 主题档位:跟随系统 / 浅色 / 深色。持久化选择并映射到 AppCompatDelegate 的夜间模式。
 */
object ThemeManager {

    enum class Mode { SYSTEM, LIGHT, DARK }

    private const val PREFS = "appearance"
    private const val KEY_MODE = "theme_mode"

    fun getMode(context: Context): Mode {
        val v = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, Mode.SYSTEM.name)
        return runCatching { Mode.valueOf(v!!) }.getOrDefault(Mode.SYSTEM)
    }

    fun setMode(context: Context, mode: Mode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, mode.name).apply()
        apply(mode)
    }

    /** 把档位映射到系统夜间模式,立即生效 */
    fun apply(mode: Mode) {
        val night = when (mode) {
            Mode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            Mode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            Mode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(night)
    }
}
