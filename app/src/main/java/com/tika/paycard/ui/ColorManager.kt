package com.tika.paycard.ui

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StyleRes
import com.tika.paycard.R

/**
 * 配色档位:五套莫兰迪。持久化选择,映射到主题叠加层与组件预置 drawable。
 */
object ColorManager {

    enum class Scheme(
        @StyleRes val overlay: Int,
        @DrawableRes val widgetName: Int,
        @DrawableRes val widgetRefresh: Int
    ) {
        GRAY_GREEN(R.style.ThemeOverlay_PayCard_GrayGreen, R.drawable.widget_name_gg, R.drawable.widget_refresh_gg),
        GRAY_BLUE(R.style.ThemeOverlay_PayCard_GrayBlue, R.drawable.widget_name_gb, R.drawable.widget_refresh_gb),
        LOTUS_PINK(R.style.ThemeOverlay_PayCard_LotusPink, R.drawable.widget_name_lp, R.drawable.widget_refresh_lp),
        TERRACOTTA(R.style.ThemeOverlay_PayCard_Terracotta, R.drawable.widget_name_tc, R.drawable.widget_refresh_tc),
        GRAY_MAUVE(R.style.ThemeOverlay_PayCard_GrayMauve, R.drawable.widget_name_gm, R.drawable.widget_refresh_gm)
    }

    private const val PREFS = "appearance"
    private const val KEY_SCHEME = "color_scheme"

    fun getScheme(context: Context): Scheme {
        val v = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SCHEME, Scheme.GRAY_GREEN.name)
        return runCatching { Scheme.valueOf(v!!) }.getOrDefault(Scheme.GRAY_GREEN)
    }

    fun setScheme(context: Context, scheme: Scheme) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SCHEME, scheme.name).apply()
    }

    /** 在 setContentView 之前调用,把选中配色叠加到当前主题上 */
    fun applyOverlay(context: android.app.Activity) {
        context.theme.applyStyle(getScheme(context).overlay, true)
    }
}
