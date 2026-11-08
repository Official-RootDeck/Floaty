package com.rootdeck.floaty.app.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.rootdeck.floaty.R
import com.rootdeck.floaty.app.prefs.SettingsRepo
import kotlin.math.abs

class OverlayController(
    private val context: Context,
    private val onScreenshotClick: () -> Unit,
    private val onStartRecording: () -> Unit,
    private val onPauseRecording: () -> Unit,
    private val onResumeRecording: () -> Unit,
    private val onStopRecording: () -> Unit
) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val settings = SettingsRepo(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var rootView: View? = null
    private var barViewController: OverlayBarViewController? = null
    private var params: WindowManager.LayoutParams? = null

    var isVisible: Boolean = false
        private set

    fun show() {
        if (rootView != null) {
            rootView?.visibility = View.VISIBLE
            return
        }

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = settings.overlayX
            y = settings.overlayY
        }

        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.overlay_floating_bar, null)
        view.alpha = settings.opacity

        barViewController = OverlayBarViewController(
            rootView = view,
            listener = object : OverlayBarViewController.OverlayActionListener {
                override fun onTakeScreenshot() {
                    triggerHaptic()
                    this@OverlayController.onScreenshotClick.invoke()
                }

                override fun onStartRecording() {
                    triggerHaptic()
                    this@OverlayController.onStartRecording.invoke()
                }

                override fun onPauseRecording() {
                    triggerHaptic()
                    this@OverlayController.onPauseRecording.invoke()
                }

                override fun onResumeRecording() {
                    triggerHaptic()
                    this@OverlayController.onResumeRecording.invoke()
                }

                override fun onStopRecording() {
                    triggerHaptic()
                    this@OverlayController.onStopRecording.invoke()
                }
            }
        )

        setupDrag(view)

        try {
            wm.addView(view, params)
            this.rootView = view
            this.isVisible = true
        } catch (_: Exception) {
            // Keep the controller retryable after the user grants overlay permission.
            this.rootView = null
            this.barViewController = null
            this.params = null
            this.isVisible = false
        }
    }

    private fun setupDrag(view: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
            val p = params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = p.x
                    initialY = p.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 12 || abs(dy) > 12) {
                        isDragging = true
                        p.x = initialX + dx
                        p.y = initialY + dy
                        try {
                            wm.updateViewLayout(view, p)
                        } catch (_: Exception) {}
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        settings.overlayX = p.x
                        settings.overlayY = p.y
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }

    private fun triggerHaptic() {
        if (!settings.haptics) return
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(25)
                }
            }
        } catch (_: Exception) {}
    }

    fun setRecordingState(isRecording: Boolean) {
        mainHandler.post {
            barViewController?.setRecordingState(isRecording)
            if (settings.autoHideDuringRecording && isRecording) {
                hide()
            }
        }
    }

    fun hide() {
        rootView?.visibility = View.GONE
        params?.let { p ->
            try {
                rootView?.let { wm.updateViewLayout(it, p) }
            } catch (_: Exception) {}
        }
    }

    fun unhide() {
        rootView?.visibility = View.VISIBLE
        params?.let { p ->
            try {
                rootView?.let { wm.updateViewLayout(it, p) }
            } catch (_: Exception) {}
        }
    }

    fun captureWithoutOverlay(callback: () -> Unit) {
        hide()
        mainHandler.postDelayed({
            callback()
        }, 34L)
    }

    fun remove() {
        if (rootView != null) {
            try {
                wm.removeView(rootView)
            } catch (_: Exception) {}
            rootView = null
            barViewController = null
            isVisible = false
        }
    }
}
