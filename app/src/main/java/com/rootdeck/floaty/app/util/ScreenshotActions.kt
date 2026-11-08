package com.rootdeck.floaty.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaActionSound
import android.net.Uri
import com.rootdeck.floaty.app.capture.NativeScreenshotEngine
import com.rootdeck.floaty.app.prefs.SettingsRepo

fun tryNativeScreenshot(context: Context, onDone: (Boolean) -> Unit = {}): Boolean {
    return NativeScreenshotEngine.tryNativeCapture(context, onDone)
}

fun handlePostScreenshot(
    context: Context,
    uri: Uri,
    mimeType: String = "image/png",
    bitmap: Bitmap? = null,
    fileTitle: String? = null
) {
    val settings = SettingsRepo(context)
    if (settings.captureSound) {
        try {
            MediaActionSound().apply {
                load(MediaActionSound.SHUTTER_CLICK)
                play(MediaActionSound.SHUTTER_CLICK)
            }
        } catch (_: Exception) {}
    }

    if (settings.autoCopyClipboard) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newUri(context.contentResolver, fileTitle ?: "Screenshot", uri)
            clipboard?.setPrimaryClip(clip)
        } catch (_: Exception) {}
    }

    settings.incrementScreenshots()
}
