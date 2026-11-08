package com.rootdeck.floaty.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.rootdeck.floaty.R
import com.rootdeck.floaty.app.FloatyApp
import com.rootdeck.floaty.app.capture.NativeScreenshotEngine
import com.rootdeck.floaty.app.capture.ProjectionFallback
import com.rootdeck.floaty.app.storage.MediaWriter
import com.rootdeck.floaty.app.prefs.SettingsRepo
import com.rootdeck.floaty.app.ui.MainActivity
import com.rootdeck.floaty.app.ui.ProjectionRequestActivity

class FloatyService : Service() {

    private lateinit var settings: SettingsRepo
    private lateinit var overlayController: OverlayController
    private lateinit var recorderEngine: RecorderEngine
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentProjection: MediaProjection? = null
    private var screenshotImageReader: ImageReader? = null
    private var screenshotVirtualDisplay: VirtualDisplay? = null
    private var projectionScreenshotActive = false
    private val projectionScreenshotTimeout = Runnable {
        if (projectionScreenshotActive) {
            Toast.makeText(this, "Screenshot capture timed out", Toast.LENGTH_SHORT).show()
            finishProjectionScreenshot()
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepo(this)
        recorderEngine = RecorderEngine(this)

        overlayController = OverlayController(
            context = this,
            onScreenshotClick = { requestScreenshot() },
            onStartRecording = { startRecording() },
            onPauseRecording = { pauseRecording() },
            onResumeRecording = { resumeRecording() },
            onStopRecording = { stopRecording() }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val projectionStart = intent?.action == ACTION_ATTACH_PROJECTION
        val requestedAction = intent?.getStringExtra(EXTRA_TARGET_ACTION)
        val requestMicrophone = projectionStart && requestedAction == ACTION_START_RECORDING &&
            settingsSafeRecordAudio()
        startInForeground(
            isProjectionActive = projectionStart,
            isMicrophoneActive = requestMicrophone,
            statusText = if (projectionStart) "Preparing screen capture" else "Floaty active"
        )

        if (intent == null) {
            overlayController.show()
            return START_STICKY
        }

        when (intent.action) {
            ACTION_START_OVERLAY_ONLY -> {
                overlayController.show()
            }
            ACTION_RESTORE_OVERLAY -> {
                overlayController.unhide()
            }
            ACTION_ATTACH_PROJECTION -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_INTENT)
                val targetAction = intent.getStringExtra(EXTRA_TARGET_ACTION)

                if (resultCode != 0 && data != null) {
                    initProjectionAndExecute(resultCode, data, targetAction)
                } else {
                    Toast.makeText(this, "Screen capture permission was not granted", Toast.LENGTH_SHORT).show()
                    startInForeground(isProjectionActive = false, statusText = "Floaty active")
                }
            }
            ACTION_TAKE_SCREENSHOT -> {
                requestScreenshot()
            }
            ACTION_START_RECORDING -> {
                startRecording()
            }
            ACTION_STOP_RECORDING -> {
                stopRecording()
            }
            ACTION_STOP_SERVICE -> {
                stopSelf()
            }
            else -> {
                overlayController.show()
            }
        }

        return START_STICKY
    }

    private fun settingsSafeRecordAudio(): Boolean {
        return try {
            settings.recordAudio &&
                android.content.pm.PackageManager.PERMISSION_GRANTED ==
                androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
        } catch (_: Exception) {
            false
        }
    }

    private fun startInForeground(
        isProjectionActive: Boolean,
        statusText: String,
        isMicrophoneActive: Boolean = false
    ) {
        val notif = buildNotification(statusText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val type = when {
                isProjectionActive && isMicrophoneActive ->
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                isProjectionActive -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                else -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            startForeground(FloatyApp.NOTIFICATION_ID, notif, type)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (isProjectionActive) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            } else {
                0
            }
            // Pass an explicit type (including NONE) so the OS does not implicitly
            // activate every type declared by the manifest for the general overlay service.
            startForeground(FloatyApp.NOTIFICATION_ID, notif, type)
        } else {
            startForeground(FloatyApp.NOTIFICATION_ID, notif)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            pendingIntentFlags()
        )

        val shotIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatyService::class.java).setAction(ACTION_TAKE_SCREENSHOT),
            pendingIntentFlags()
        )

        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, FloatyService::class.java).setAction(ACTION_STOP_SERVICE),
            pendingIntentFlags()
        )

        return NotificationCompat.Builder(this, FloatyApp.CHANNEL_ID)
            .setContentTitle("Floaty")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentIntent)
            .setOngoing(settings.persistentNotification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Screenshot", shotIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    private fun initProjectionAndExecute(resultCode: Int, data: Intent, targetAction: String?) {
        startInForeground(isProjectionActive = true, statusText = "Capture ready")

        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = try {
            mpManager.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            null
        }

        if (projection == null) {
            Toast.makeText(this, "Capture permission denied", Toast.LENGTH_SHORT).show()
            overlayController.unhide()
            startInForeground(isProjectionActive = false, statusText = "Floaty active")
            return
        }

        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                if (recorderEngine.isRecording) {
                    stopRecording()
                } else if (projectionScreenshotActive) {
                    finishProjectionScreenshot(stopProjection = false)
                }
            }
        }, mainHandler)

        this.currentProjection = projection

        when (targetAction) {
            ACTION_CAPTURE_SCREENSHOT -> executeProjectionScreenshot(projection)
            else -> executeRecording(projection)
        }
    }

    private fun executeProjectionScreenshot(projection: MediaProjection) {
        if (projectionScreenshotActive) return
        projectionScreenshotActive = true
        overlayController.hide()

        try {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)

            val width = metrics.widthPixels.coerceAtLeast(1)
            val height = metrics.heightPixels.coerceAtLeast(1)
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            screenshotImageReader = reader

            reader.setOnImageAvailableListener({ source ->
                if (!projectionScreenshotActive) return@setOnImageAvailableListener
                val image = try {
                    source.acquireLatestImage()
                } catch (error: Exception) {
                    Log.e(TAG, "Could not acquire projection screenshot", error)
                    null
                } ?: return@setOnImageAvailableListener

                val bitmap = try {
                    imageToBitmap(image, width, height)
                } finally {
                    image.close()
                }

                try {
                    if (bitmap != null) {
                        val result = MediaWriter.saveScreenshotResult(applicationContext, bitmap, settings)
                        if (result.success) {
                            Toast.makeText(this, "Screenshot captured", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Screenshot failed: ${result.errorMessage}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "Could not process the screenshot", Toast.LENGTH_SHORT).show()
                    }
                } catch (error: Exception) {
                    Log.e(TAG, "Could not save projection screenshot", error)
                    Toast.makeText(this, "Screenshot could not be saved", Toast.LENGTH_SHORT).show()
                } finally {
                    bitmap?.recycle()
                    finishProjectionScreenshot()
                }
            }, mainHandler)

            val display = projection.createVirtualDisplay(
                "FloatyProjectionScreenshot",
                width,
                height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                mainHandler
            ) ?: throw IllegalStateException("Could not create the screenshot display.")

            if (projectionScreenshotActive) {
                screenshotVirtualDisplay = display
                mainHandler.removeCallbacks(projectionScreenshotTimeout)
                mainHandler.postDelayed(projectionScreenshotTimeout, 4_000L)
            } else {
                display.release()
            }
        } catch (error: Exception) {
            Log.e(TAG, "Projection screenshot setup failed", error)
            Toast.makeText(this, "Screenshot failed: ${error.message}", Toast.LENGTH_SHORT).show()
            finishProjectionScreenshot()
        }
    }

    private fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap? {
        return try {
            val plane = image.planes.firstOrNull() ?: return null
            val pixelStride = plane.pixelStride
            if (pixelStride <= 0) return null
            val rowPadding = plane.rowStride - pixelStride * width
            val paddedWidth = (width + (rowPadding / pixelStride)).coerceAtLeast(width)
            val paddedBitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
            paddedBitmap.copyPixelsFromBuffer(plane.buffer)
            if (paddedWidth == width) {
                paddedBitmap
            } else {
                Bitmap.createBitmap(paddedBitmap, 0, 0, width, height).also {
                    paddedBitmap.recycle()
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Could not convert projection image to bitmap", error)
            null
        }
    }

    private fun finishProjectionScreenshot(
        stopProjection: Boolean = true,
        restoreOverlay: Boolean = true,
        updateForeground: Boolean = true
    ) {
        if (!projectionScreenshotActive && screenshotImageReader == null && screenshotVirtualDisplay == null) return
        projectionScreenshotActive = false
        mainHandler.removeCallbacks(projectionScreenshotTimeout)

        try { screenshotVirtualDisplay?.release() } catch (_: Exception) {}
        try { screenshotImageReader?.close() } catch (_: Exception) {}
        screenshotVirtualDisplay = null
        screenshotImageReader = null

        val projection = currentProjection
        currentProjection = null
        if (stopProjection) {
            try { projection?.stop() } catch (_: Exception) {}
        }

        if (restoreOverlay) overlayController.unhide()
        if (updateForeground) {
            startInForeground(isProjectionActive = false, statusText = "Floaty active")
        }
    }

    fun requestScreenshot() {
        executeScreenshot()
    }

    private fun executeScreenshot() {
        overlayController.hide()
        // Wait 80ms for WindowManager and SurfaceFlinger to remove the overlay window from the display buffer
        mainHandler.postDelayed({
            val dispatched = NativeScreenshotEngine.tryNativeCapture(
                context = this,
                onCaptured = {
                    mainHandler.post {
                        overlayController.unhide()
                    }
                },
                onDone = { ok ->
                    if (!ok) {
                        mainHandler.post {
                            fallbackToProjection()
                        }
                    }
                }
            )
            if (!dispatched) {
                fallbackToProjection()
            }
        }, 80L)
    }

    private fun fallbackToProjection() {
        val intent = Intent(this, ProjectionFallback::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        startActivity(intent)
        // ProjectionFallback or the projection foreground service restores the overlay
        // when the capture flow ends; do not reveal it while the permission dialog is open.
    }

    fun startRecording() {
        if (recorderEngine.isRecording) return

        val intent = Intent(this, ProjectionRequestActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_TARGET_ACTION, ACTION_START_RECORDING)
        }
        startActivity(intent)
    }

    private fun executeRecording(projection: MediaProjection) {
        recorderEngine.startRecording(projection, settings) { success ->
            if (success) {
                overlayController.setRecordingState(true)
                startInForeground(
                    isProjectionActive = true,
                    statusText = "Screen recording active",
                    isMicrophoneActive = recorderEngine.isRecordingAudio
                )
                Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Recording failed to initialize", Toast.LENGTH_SHORT).show()
                startInForeground(isProjectionActive = false, statusText = "Floaty active")
            }
        }
    }

    fun pauseRecording() {
        recorderEngine.pauseRecording()
        Toast.makeText(this, "Recording paused", Toast.LENGTH_SHORT).show()
    }

    fun resumeRecording() {
        recorderEngine.resumeRecording()
        Toast.makeText(this, "Recording resumed", Toast.LENGTH_SHORT).show()
    }

    fun stopRecording() {
        if (!recorderEngine.isRecording) return
        recorderEngine.stopRecording()
        overlayController.setRecordingState(false)
        overlayController.unhide()

        try {
            currentProjection?.stop()
        } catch (_: Exception) {}
        currentProjection = null

        startInForeground(isProjectionActive = false, statusText = "Floaty active")
        Toast.makeText(this, "Recording saved", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(projectionScreenshotTimeout)
        finishProjectionScreenshot(restoreOverlay = false, updateForeground = false)
        overlayController.remove()
        recorderEngine.stopRecording()
        try {
            currentProjection?.stop()
        } catch (_: Exception) {}
        currentProjection = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_OVERLAY_ONLY = "com.rootdeck.floaty.action.START_OVERLAY_ONLY"
        const val ACTION_ATTACH_PROJECTION = "com.rootdeck.floaty.action.ATTACH_PROJECTION"
        const val ACTION_TAKE_SCREENSHOT = "com.rootdeck.floaty.action.TAKE_SCREENSHOT"
        const val ACTION_START_RECORDING = "com.rootdeck.floaty.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.rootdeck.floaty.action.STOP_RECORDING"
        const val ACTION_STOP_SERVICE = "com.rootdeck.floaty.action.STOP_SERVICE"
        const val ACTION_CAPTURE_SCREENSHOT = "com.rootdeck.floaty.action.CAPTURE_SCREENSHOT"
        const val ACTION_RESTORE_OVERLAY = "com.rootdeck.floaty.action.RESTORE_OVERLAY"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_INTENT = "extra_result_intent"
        const val EXTRA_TARGET_ACTION = "extra_target_action"

        private const val TAG = "FloatyService"

        private fun pendingIntentFlags(): Int = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

        fun startFloaty(context: Context) {
            val intent = Intent(context, FloatyService::class.java).apply {
                action = ACTION_START_OVERLAY_ONLY
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
