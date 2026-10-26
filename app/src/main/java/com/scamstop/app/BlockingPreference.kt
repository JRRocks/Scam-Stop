package com.scamstop.app

import android.content.Context

internal object BlockingPreference {
    private const val PREFERENCES_NAME = "blocking_policy"
    private const val ENABLED_KEY = "enabled"

    fun isEnabled(context: Context): Boolean = preferences(context).getBoolean(ENABLED_KEY, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean(ENABLED_KEY, enabled).apply()
    }

    private fun preferences(context: Context) = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
