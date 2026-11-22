package com.rootdeck.floaty.app.capture

import android.content.Context

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

        // Native/Auto use the direct screenshot callback on Android 11+.
        // System-default screenshots are reserved for explicit legacy mode.
        val success = svc.simulateScreenshotButton(
            onCaptured = onCaptured,
            autoHideButton = true,
            autoUnHideButton = true,
            useTakeScreenshotMethod = true,
            useSystemDefaults = false
        )
        onDone(success)
        return success
    }
}
