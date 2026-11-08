package com.rootdeck.floaty.app.capture

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Display.DEFAULT_DISPLAY
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.rootdeck.floaty.app.prefs.SettingsRepo
import com.rootdeck.floaty.app.storage.MediaWriter
import com.rootdeck.floaty.app.util.isDeviceLocked
import com.rootdeck.floaty.app.util.toastDeviceIsLocked
import com.rootdeck.floaty.app.util.toastMessage

class ScreenshotAccessibilityService : AccessibilityService() {

    private var lastPackageName: CharSequence = ""

    fun getForegroundPackageName(): String = lastPackageName.toString()

    override fun onServiceConnected() {
        instance = this
        Log.i(TAG, "ScreenshotAccessibilityService connected")

        // Explicitly clear FLAG_REQUEST_ACCESSIBILITY_BUTTON so Android does NOT attach a floating shortcut button
        val info = serviceInfo
        if (info != null) {
            info.flags = info.flags and AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON.inv()
            serviceInfo = info
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                accessibilityButtonController.registerAccessibilityButtonCallback(
                    object : AccessibilityButtonController.AccessibilityButtonCallback() {
                        override fun onClicked(controller: AccessibilityButtonController) {
                            simulateScreenshotButton()
                        }
                    }
                )
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != packageName) {
            lastPackageName = pkg
        }
    }

    override fun onInterrupt() {
        // No-op
    }

    fun simulateScreenshotButton(
        onCaptured: (() -> Unit)? = null,
        autoHideButton: Boolean = true,
        autoUnHideButton: Boolean = true,
        useTakeScreenshotMethod: Boolean = true,
        useSystemDefaults: Boolean? = null
    ): Boolean {
        val settings = SettingsRepo(this)

        if (isDeviceLocked(this) && settings.preventIfLocked) {
            toastDeviceIsLocked(this)
            onCaptured?.invoke()
            return false
        }

        val isUseSystemDefaults = useSystemDefaults ?: settings.useSystemDefaults

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && useTakeScreenshotMethod && !isUseSystemDefaults) {
            takeScreenshot(onCaptured)
            true
        } else {
            val success = try {
                performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            } catch (e: Exception) {
                Log.e(TAG, "performGlobalAction failed", e)
                false
            }
            if (success) {
                settings.incrementScreenshots()
            }
            Handler(Looper.getMainLooper()).postDelayed({
                onCaptured?.invoke()
            }, 600L)
            success
        }
    }

    fun takeScreenshot(onCaptured: (() -> Unit)? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            Handler(Looper.getMainLooper()).postDelayed({
                onCaptured?.invoke()
            }, 600L)
            return
        }

        val settings = SettingsRepo(this)

        super.takeScreenshot(
            DEFAULT_DISPLAY,
            { r -> Thread(r).start() },
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    // Screen buffer captured: unhide overlay immediately so controls return
                    Handler(Looper.getMainLooper()).post {
                        onCaptured?.invoke()
                    }

                    val bitmap = try {
                        Bitmap.wrapHardwareBuffer(
                            screenshot.hardwareBuffer,
                            screenshot.colorSpace
                        )?.copy(Bitmap.Config.ARGB_8888, false)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error wrapping hardware buffer", e)
                        null
                    } finally {
                        screenshot.hardwareBuffer.close()
                    }

                    if (bitmap == null) {
                        Log.e(TAG, "takeScreenshot: bitmap null, fallback to global action")
                        Handler(Looper.getMainLooper()).post {
                            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                        }
                    } else {
                        val saveResult = MediaWriter.saveScreenshotResult(
                            applicationContext,
                            bitmap,
                            settings
                        )
                        Handler(Looper.getMainLooper()).post {
                            onFileSaved(saveResult)
                        }
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Log.e(TAG, "takeScreenshot onFailure($errorCode), fallback to global action")
                    Handler(Looper.getMainLooper()).post {
                        onCaptured?.invoke()
                        performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                    }
                }
            }
        )
    }

    private fun onFileSaved(saveResult: SaveImageResult) {
        if (saveResult.success && saveResult is SaveImageResultSuccess) {
            toastMessage("Screenshot captured: ${saveResult.fileTitle ?: ""}", Toast.LENGTH_SHORT)
        } else {
            toastMessage("Screenshot failed: ${saveResult.errorMessage}", Toast.LENGTH_SHORT)
        }
    }

    companion object {
        const val TAG = "ScreenshotAccessService"
        var instance: ScreenshotAccessibilityService? = null

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            }
        }
    }
}
