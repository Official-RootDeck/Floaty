package com.rootdeck.floaty.app.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.rootdeck.floaty.app.capture.ScreenshotAccessibilityService
import com.rootdeck.floaty.app.storage.FolderStore

class FirstRunFlow(private val activity: Activity) {

    fun maybeRun(onDone: () -> Unit) {
        val ctx = activity.applicationContext
        // Ensure default media folders exist on device
        FolderStore.ensureDefaultFoldersCreated(ctx)

        val prefs = activity.getSharedPreferences("floaty_first_run", Context.MODE_PRIVATE)
        if (prefs.getBoolean("consent_granted", false)) return onDone()

        AlertDialog.Builder(activity)
            .setTitle("Welcome to Floaty")
            .setMessage(
                "Floaty is built for fast, lightweight capture with privacy at its core:\n\n" +
                "• All screenshots and recordings remain private on your device.\n" +
                "• No personal files, location, or contacts are ever accessed.\n" +
                "• No diagnostic or device telemetry is collected or uploaded.\n" +
                "• Popup-free screenshots use the Accessibility Service. When enabling, turn ON 'Floaty' (keep 'Floaty shortcut' off)."
            )
            .setPositiveButton("Continue") { _, _ ->
                val now = System.currentTimeMillis()
                prefs.edit()
                    .putBoolean("consent_granted", true)
                    .putLong("consent_at", now)
                    .apply()

                val canOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(activity)
                } else true

                if (canOverlay && ScreenshotAccessibilityService.instance == null) {
                    ScreenshotAccessibilityService.openAccessibilitySettings(activity)
                }

                onDone()
            }
            .setNegativeButton("Maybe Later") { _, _ ->
                onDone()
            }
            .setCancelable(false)
            .show()
    }
}
