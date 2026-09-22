package com.nahrahviing.lecteurnovel.util

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var readerFontSize: Float
        get() = prefs.getFloat(KEY_FONT_SIZE, 17f)
        set(value) = prefs.edit().putFloat(KEY_FONT_SIZE, value).apply()

    var readerLineHeight: Float
        get() = prefs.getFloat(KEY_LINE_HEIGHT, 1.6f)
        set(value) = prefs.edit().putFloat(KEY_LINE_HEIGHT, value).apply()

    var readerHorizontalPadding: Int
        get() = prefs.getInt(KEY_HORIZ_PADDING, 16)
        set(value) = prefs.edit().putInt(KEY_HORIZ_PADDING, value).apply()

    var readerVerticalPadding: Int
        get() = prefs.getInt(KEY_VERT_PADDING, 12)
        set(value) = prefs.edit().putInt(KEY_VERT_PADDING, value).apply()

    var readerFontMode: String
        get() = prefs.getString(KEY_FONT_MODE, "SYSTEM") ?: "SYSTEM"
        set(value) = prefs.edit().putString(KEY_FONT_MODE, value).apply()

    var readerThemeMode: String
        get() = prefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM"
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()

    var autoScrollSpeed: Int
        get() = prefs.getInt(KEY_AUTO_SCROLL_SPEED, 3)
        set(value) = prefs.edit().putInt(KEY_AUTO_SCROLL_SPEED, value).apply()

    var downloadChapterDelayMs: Long
        get() = prefs.getLong(KEY_DOWNLOAD_CHAPTER_DELAY_MS, 400L)
        set(value) = prefs.edit().putLong(KEY_DOWNLOAD_CHAPTER_DELAY_MS, value).apply()

    var customCategories: Set<String>
        get() = prefs.getStringSet(KEY_CUSTOM_CATEGORIES, setOf("Général", "Isekai", "Favoris absolus", "En pause")) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_CUSTOM_CATEGORIES, value).apply()

    var librarySortType: String
        get() = prefs.getString(KEY_LIBRARY_SORT_TYPE, "LAST_READ") ?: "LAST_READ"
        set(value) = prefs.edit().putString(KEY_LIBRARY_SORT_TYPE, value).apply()

    var librarySortAscending: Boolean
        get() = prefs.getBoolean(KEY_LIBRARY_SORT_ASC, false)
        set(value) = prefs.edit().putBoolean(KEY_LIBRARY_SORT_ASC, value).apply()

    var libraryStatusFilter: String
        get() = prefs.getString(KEY_LIBRARY_STATUS_FILTER, "ALL") ?: "ALL"
        set(value) = prefs.edit().putString(KEY_LIBRARY_STATUS_FILTER, value).apply()

    var autoDownloadOnRestore: Boolean
        get() = prefs.getBoolean(KEY_AUTO_DOWNLOAD_ON_RESTORE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_DOWNLOAD_ON_RESTORE, value).apply()

    companion object {
        private const val PREFS_NAME = "chireads_app_prefs"
        private const val KEY_FONT_SIZE = "reader_font_size"
        private const val KEY_LINE_HEIGHT = "reader_line_height"
        private const val KEY_HORIZ_PADDING = "reader_horiz_padding"
        private const val KEY_VERT_PADDING = "reader_vert_padding"
        private const val KEY_FONT_MODE = "reader_font_mode"
        private const val KEY_THEME_MODE = "reader_theme_mode"
        private const val KEY_KEEP_SCREEN_ON = "reader_keep_screen_on"
        private const val KEY_AUTO_SCROLL_SPEED = "reader_auto_scroll_speed"
        private const val KEY_DOWNLOAD_CHAPTER_DELAY_MS = "download_chapter_delay_ms"
        private const val KEY_CUSTOM_CATEGORIES = "library_custom_categories"
        private const val KEY_LIBRARY_SORT_TYPE = "library_sort_type"
        private const val KEY_LIBRARY_SORT_ASC = "library_sort_asc"
        private const val KEY_LIBRARY_STATUS_FILTER = "library_status_filter"
        private const val KEY_AUTO_DOWNLOAD_ON_RESTORE = "auto_download_on_restore"
    }
}
