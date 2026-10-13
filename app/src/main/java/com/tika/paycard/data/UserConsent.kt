package com.tika.paycard.data

import android.content.Context
import androidx.core.content.edit

object UserConsent {

    const val CURRENT_VERSION = 20261012

    private const val PREFS = "user_consent"
    private const val KEY_VERSION = "accepted_version"

    fun hasAccepted(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_VERSION, 0) == CURRENT_VERSION

    fun accept(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putInt(KEY_VERSION, CURRENT_VERSION) }
    }

    internal fun hasAcceptedVersion(context: Context, version: Int): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_VERSION, 0) == version
}
