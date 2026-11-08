package com.rootdeck.floaty.app.capture

import android.content.Context
import com.rootdeck.floaty.app.prefs.SettingsRepo

object NativeScreenshotEngine {
    /**
     * Returns true if the accessibility service is running and the screenshot was dispatched.
     */
    fun tryNativeCapture(
        context: Context,
        onDone: (Boolean) -> Unit = {},
        onCaptured: () -> Unit = {}
    ): Boolean {
        val svc = ScreenshotAccessibilityService.instance ?: return false
        val settings = SettingsRepo(context)

        // For native capture, prefer takeScreenshotMethod on API 30+ so Floaty saves to its folder
        val useSystemDefaults = settings.useSystemDefaults && settings.captureMethod != "native"
        val success = svc.simulateScreenshotButton(
            onCaptured = onCaptured,
            autoHideButton = true,
            autoUnHideButton = true,
            useTakeScreenshotMethod = !useSystemDefaults,
            useSystemDefaults = useSystemDefaults
        )
        onDone(success)
        return success
    }
}
