package com.tika.paycard.ui

import android.app.Activity
import android.content.res.Configuration
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

object SystemBars {

    fun apply(activity: Activity, root: View) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)

        val isLight = root.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK !=
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(activity.window, root).apply {
            isAppearanceLightStatusBars = isLight
            isAppearanceLightNavigationBars = isLight
        }

        val initialLeft = root.paddingLeft
        val initialTop = root.paddingTop
        val initialRight = root.paddingRight
        val initialBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(
                initialLeft + bars.left,
                initialTop + bars.top,
                initialRight + bars.right,
                initialBottom + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
