package com.nahrahviing.lecteurnovel.util

import android.content.Context
import android.content.SharedPreferences

object DrivePreferences {
    private const val PREFS_NAME = "drive_sync_preferences"
    private const val KEY_AUTO_BACKUP_ENABLED = "key_auto_backup_enabled"
    private const val KEY_AUTO_EPUB_SYNC_ENABLED = "key_auto_epub_sync_enabled"
    private const val KEY_LAST_BACKUP_TIMESTAMP = "key_last_backup_timestamp"
    private const val KEY_LAST_BACKUP_STATUS = "key_last_backup_status"
    private const val KEY_CONNECTED_ACCOUNT_EMAIL = "key_connected_account_email"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isAutoBackupEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_BACKUP_ENABLED, false)
    }

    fun setAutoBackupEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_BACKUP_ENABLED, enabled).apply()
    }

    fun isAutoEpubSyncEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_EPUB_SYNC_ENABLED, true)
    }

    fun setAutoEpubSyncEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_EPUB_SYNC_ENABLED, enabled).apply()
    }

    fun getLastBackupTimestamp(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_BACKUP_TIMESTAMP, 0L)
    }

    fun setLastBackupInfo(context: Context, timestamp: Long, status: String) {
        getPrefs(context).edit()
            .putLong(KEY_LAST_BACKUP_TIMESTAMP, timestamp)
            .putString(KEY_LAST_BACKUP_STATUS, status)
            .apply()
    }

    fun getLastBackupStatus(context: Context): String? {
        return getPrefs(context).getString(KEY_LAST_BACKUP_STATUS, null)
    }

    fun getConnectedAccountEmail(context: Context): String? {
        return getPrefs(context).getString(KEY_CONNECTED_ACCOUNT_EMAIL, null)
    }

    fun setConnectedAccountEmail(context: Context, email: String?) {
        getPrefs(context).edit().putString(KEY_CONNECTED_ACCOUNT_EMAIL, email).apply()
    }
}
