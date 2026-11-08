package com.rootdeck.floaty.app.prefs

import android.content.Context
import android.content.SharedPreferences

class SettingsRepo(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var screenshotCount: Int
        get() = prefs.getInt(KEY_SCREENSHOTS, 0)
        set(value) = prefs.edit().putInt(KEY_SCREENSHOTS, value).apply()

    var videoCount: Int
        get() = prefs.getInt(KEY_VIDEOS, 0)
        set(value) = prefs.edit().putInt(KEY_VIDEOS, value).apply()

    fun incrementScreenshots(): Int {
        val count = screenshotCount + 1
        screenshotCount = count
        return count
    }

    fun incrementVideos(): Int {
        val count = videoCount + 1
        videoCount = count
        return count
    }

    var opacity: Float
        get() = prefs.getFloat(KEY_OPACITY, 0.8f)
        set(value) = prefs.edit().putFloat(KEY_OPACITY, value.coerceIn(0.3f, 1.0f)).apply()

    var iconSize: String
        get() = prefs.getString(KEY_ICON_SIZE, "medium") ?: "medium"
        set(value) = prefs.edit().putString(KEY_ICON_SIZE, value).apply()

    var haptics: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    var autoHideDuringRecording: Boolean
        get() = prefs.getBoolean(KEY_AUTO_HIDE, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_HIDE, value).apply()

    var autoCopyClipboard: Boolean
        get() = prefs.getBoolean(KEY_AUTO_COPY, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_COPY, value).apply()

    var imageFormat: String
        get() = prefs.getString(KEY_IMAGE_FORMAT, "png") ?: "png"
        set(value) = prefs.edit().putString(KEY_IMAGE_FORMAT, value).apply()

    var captureSound: Boolean
        get() = prefs.getBoolean(KEY_CAPTURE_SOUND, true)
        set(value) = prefs.edit().putBoolean(KEY_CAPTURE_SOUND, value).apply()

    var enableShortcuts: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_SHORTCUTS, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_SHORTCUTS, value).apply()

    var recordResolution: String
        get() = prefs.getString(KEY_RECORD_RESOLUTION, "hd") ?: "hd"
        set(value) = prefs.edit().putString(KEY_RECORD_RESOLUTION, value).apply()

    var recordAudio: Boolean
        get() = prefs.getBoolean(KEY_RECORD_AUDIO, false)
        set(value) = prefs.edit().putBoolean(KEY_RECORD_AUDIO, value).apply()

    var recordFps: Int
        get() = prefs.getInt(KEY_RECORD_FPS, 30)
        set(value) = prefs.edit().putInt(KEY_RECORD_FPS, value).apply()

    var startOnBoot: Boolean
        get() = prefs.getBoolean(KEY_START_ON_BOOT, false)
        set(value) = prefs.edit().putBoolean(KEY_START_ON_BOOT, value).apply()

    var persistentNotification: Boolean
        get() = prefs.getBoolean(KEY_PERSISTENT_NOTIF, true)
        set(value) = prefs.edit().putBoolean(KEY_PERSISTENT_NOTIF, value).apply()

    var overlayX: Int
        get() = prefs.getInt(KEY_OVERLAY_X, 100)
        set(value) = prefs.edit().putInt(KEY_OVERLAY_X, value).apply()

    var overlayY: Int
        get() = prefs.getInt(KEY_OVERLAY_Y, 200)
        set(value) = prefs.edit().putInt(KEY_OVERLAY_Y, value).apply()

    // --- Ported / Extended from ScreenshotTile ---
    var useNative: Boolean
        get() = prefs.getBoolean(KEY_USE_NATIVE, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_NATIVE, value).apply()

    var useSystemDefaults: Boolean
        get() = prefs.getBoolean(KEY_USE_SYSTEM_DEFAULTS, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_SYSTEM_DEFAULTS, value).apply()

    var captureMethod: String
        get() = prefs.getString(KEY_CAPTURE_METHOD, "auto") ?: "auto"
        set(value) = prefs.edit().putString(KEY_CAPTURE_METHOD, value).apply()

    var preventIfLocked: Boolean
        get() = prefs.getBoolean(KEY_PREVENT_IF_LOCKED, false)
        set(value) = prefs.edit().putBoolean(KEY_PREVENT_IF_LOCKED, value).apply()

    var autoCropEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CROP_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CROP_ENABLED, value).apply()

    var autoCropLeft: Int
        get() = prefs.getInt(KEY_AUTO_CROP_LEFT, 0)
        set(value) = prefs.edit().putInt(KEY_AUTO_CROP_LEFT, value).apply()

    var autoCropTop: Int
        get() = prefs.getInt(KEY_AUTO_CROP_TOP, 0)
        set(value) = prefs.edit().putInt(KEY_AUTO_CROP_TOP, value).apply()

    var autoCropRight: Int
        get() = prefs.getInt(KEY_AUTO_CROP_RIGHT, 0)
        set(value) = prefs.edit().putInt(KEY_AUTO_CROP_RIGHT, value).apply()

    var autoCropBottom: Int
        get() = prefs.getInt(KEY_AUTO_CROP_BOTTOM, 0)
        set(value) = prefs.edit().putInt(KEY_AUTO_CROP_BOTTOM, value).apply()

    var fileFormat: String
        get() = prefs.getString(KEY_FILE_FORMAT, imageFormat) ?: imageFormat
        set(value) {
            imageFormat = value
            prefs.edit().putString(KEY_FILE_FORMAT, value).apply()
        }

    var formatQuality: Int
        get() = prefs.getInt(KEY_FORMAT_QUALITY, 100).coerceIn(0, 100)
        set(value) = prefs.edit().putInt(KEY_FORMAT_QUALITY, value.coerceIn(0, 100)).apply()

    var fileNamePattern: String
        get() = prefs.getString(KEY_FILE_NAME_PATTERN, "floaty_%timestamp%") ?: "floaty_%timestamp%"
        set(value) = prefs.edit().putString(KEY_FILE_NAME_PATTERN, value.ifBlank { "floaty_%timestamp%" }).apply()

    var screenshotHistoryKeepMax: Int
        get() = prefs.getInt(KEY_SCREENSHOT_HISTORY_KEEP_MAX, 30)
        set(value) = prefs.edit().putInt(KEY_SCREENSHOT_HISTORY_KEEP_MAX, value).apply()

    companion object {
        private const val PREFS_NAME = "floaty_settings"
        private const val KEY_SCREENSHOTS = "count_screenshots"
        private const val KEY_VIDEOS = "count_videos"
        private const val KEY_OPACITY = "pref_opacity"
        private const val KEY_ICON_SIZE = "pref_icon_size"
        private const val KEY_HAPTICS = "pref_haptics"
        private const val KEY_AUTO_HIDE = "pref_auto_hide"
        private const val KEY_AUTO_COPY = "pref_auto_copy"
        private const val KEY_IMAGE_FORMAT = "pref_image_format"
        private const val KEY_CAPTURE_SOUND = "pref_capture_sound"
        private const val KEY_ENABLE_SHORTCUTS = "pref_enable_shortcuts"
        private const val KEY_RECORD_RESOLUTION = "pref_record_resolution"
        private const val KEY_RECORD_AUDIO = "pref_record_audio"
        private const val KEY_RECORD_FPS = "pref_record_fps"
        private const val KEY_START_ON_BOOT = "pref_start_on_boot"
        private const val KEY_PERSISTENT_NOTIF = "pref_persistent_notif"
        private const val KEY_OVERLAY_X = "pref_overlay_x"
        private const val KEY_OVERLAY_Y = "pref_overlay_y"

        // Extended keys
        private const val KEY_USE_NATIVE = "pref_use_native"
        private const val KEY_USE_SYSTEM_DEFAULTS = "pref_use_system_defaults"
        private const val KEY_CAPTURE_METHOD = "pref_capture_method"
        private const val KEY_PREVENT_IF_LOCKED = "pref_prevent_if_locked"
        private const val KEY_AUTO_CROP_ENABLED = "pref_auto_crop_enabled"
        private const val KEY_AUTO_CROP_LEFT = "pref_auto_crop_left"
        private const val KEY_AUTO_CROP_TOP = "pref_auto_crop_top"
        private const val KEY_AUTO_CROP_RIGHT = "pref_auto_crop_right"
        private const val KEY_AUTO_CROP_BOTTOM = "pref_auto_crop_bottom"
        private const val KEY_FILE_FORMAT = "pref_file_format"
        private const val KEY_FORMAT_QUALITY = "pref_format_quality"
        private const val KEY_FILE_NAME_PATTERN = "pref_file_name_pattern"
        private const val KEY_SCREENSHOT_HISTORY_KEEP_MAX = "pref_keep_history_max"
    }
}
