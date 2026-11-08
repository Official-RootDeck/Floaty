package com.rootdeck.floaty.app.util

import android.content.Context
import com.rootdeck.floaty.app.prefs.SettingsRepo

object SettingsBridge {
    fun toPrefManagerValues(context: Context): Map<String, Any> {
        val repo = SettingsRepo(context)
        return mapOf(
            "useNative" to repo.useNative,
            "useSystemDefaults" to repo.useSystemDefaults,
            "captureMethod" to repo.captureMethod,
            "preventIfLocked" to repo.preventIfLocked,
            "fileFormat" to repo.fileFormat,
            "formatQuality" to repo.formatQuality,
            "fileNamePattern" to repo.fileNamePattern,
            "autoCropEnabled" to repo.autoCropEnabled,
            "autoCropLeft" to repo.autoCropLeft,
            "autoCropTop" to repo.autoCropTop,
            "autoCropRight" to repo.autoCropRight,
            "autoCropBottom" to repo.autoCropBottom,
            "screenshotHistoryKeepMax" to repo.screenshotHistoryKeepMax
        )
    }
}
