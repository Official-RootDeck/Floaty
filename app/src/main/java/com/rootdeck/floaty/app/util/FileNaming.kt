package com.rootdeck.floaty.app.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.rootdeck.floaty.app.capture.ScreenshotAccessibilityService
import com.rootdeck.floaty.app.prefs.SettingsRepo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.random.Random

fun mimeFromFileExtension(fileExtension: String): String {
    return when (fileExtension.lowercase(Locale.ROOT)) {
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> "image/png"
    }
}

fun formatFileName(context: Context, fileNamePattern: String, date: Date = Date()): String {
    val timeStamp: String = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(date)
    val counter = SettingsRepo(context).screenshotCount.toString()

    var fileName = fileNamePattern.replace("%timestamp%", timeStamp)
    fileName = fileName.replace("%counter%", counter.padStart(max(5, counter.length), '0'))
    while (fileName.contains("%randint%")) {
        val randInt = Random.nextInt(0, Int.MAX_VALUE).toString()
            .padStart(Int.MAX_VALUE.toString().length, '0')
        fileName = fileName.replaceFirst("%randint%", randInt)
    }
    while (fileName.contains("%random%")) {
        fileName = fileName.replaceFirst("%random%", UUID.randomUUID().toString())
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && (fileName.contains("%app%") || fileName.contains("%package%"))) {
        val service = ScreenshotAccessibilityService.instance
        val packageName = service?.getForegroundPackageName() ?: ""
        fileName = fileName.replace("%package%", sanitizeFilename(packageName))
        if (fileName.contains("%app%")) {
            val appLabel = resolveAppLabel(context, packageName)
            fileName = fileName.replace("%app%", sanitizeFilename(appLabel))
        }
    }
    return sanitizeFilename(fileName)
}

private fun resolveAppLabel(context: Context, packageName: String): String {
    if (packageName.isBlank()) return ""
    val pm = context.packageManager
    try {
        val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0)
        }
        return pm.getApplicationLabel(appInfo).toString()
    } catch (_: Exception) {}
    return packageName.split('.').takeLast(2).joinToString(".")
}

fun sanitizeFilename(name: String): String {
    val safe = name
        .replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_")
        .trim()
        .trim('.')
        .take(120)
    return safe.ifBlank { "floaty_capture" }
}
