package com.nahrahviing.lecteurnovel.util

import android.content.Context
import android.content.SharedPreferences

object UpdatePreferences {
    private const val PREFS_NAME = "novel_update_preferences"
    private const val KEY_AUTO_UPDATE_ENABLED = "key_auto_update_enabled"
    private const val KEY_LAST_CHECK_TIMESTAMP = "key_last_check_timestamp"
    private const val KEY_LAST_NEW_CHAPTERS_COUNT = "key_last_new_chapters_count"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isAutoUpdateEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_UPDATE_ENABLED, true)
    }

    fun setAutoUpdateEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_UPDATE_ENABLED, enabled).apply()
    }

    fun getLastCheckTimestamp(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_CHECK_TIMESTAMP, 0L)
    }

    fun getLastNewChaptersCount(context: Context): Int {
        return getPrefs(context).getInt(KEY_LAST_NEW_CHAPTERS_COUNT, 0)
    }

    fun setLastCheckInfo(context: Context, timestamp: Long, newChaptersCount: Int) {
        getPrefs(context).edit()
            .putLong(KEY_LAST_CHECK_TIMESTAMP, timestamp)
            .putInt(KEY_LAST_NEW_CHAPTERS_COUNT, newChaptersCount)
            .apply()
    }
}
