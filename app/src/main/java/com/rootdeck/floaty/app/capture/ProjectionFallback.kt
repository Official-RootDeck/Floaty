package com.rootdeck.floaty.app.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.rootdeck.floaty.app.prefs.SettingsRepo
import com.rootdeck.floaty.app.storage.MediaWriter
import com.rootdeck.floaty.app.service.FloatyService

class ProjectionFallback : ComponentActivity() {

    private lateinit var settings: SettingsRepo
    private val mainHandler = Handler(Looper.getMainLooper())
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaProjection: MediaProjection? = null
    private var captureDone = false
    private var finishing = false

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            // Android 10+ requires MediaProjection work to be hosted by a foreground
            // service declaring the mediaProjection type. The service obtains the token
            // only after it has entered that foreground state.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceIntent = Intent(this, FloatyService::class.java).apply {
                    action = FloatyService.ACTION_ATTACH_PROJECTION
                    putExtra(FloatyService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(FloatyService.EXTRA_RESULT_INTENT, result.data)
                    putExtra(FloatyService.EXTRA_TARGET_ACTION, FloatyService.ACTION_CAPTURE_SCREENSHOT)
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                    finishCleanly(restoreOverlay = false)
                } catch (error: Exception) {
                    Log.e(TAG, "Could not start projection foreground service", error)
                    Toast.makeText(this, "Screenshot capture could not start", Toast.LENGTH_SHORT).show()
                    finishCleanly(restoreOverlay = true)
                }
                return@registerForActivityResult
            }

            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            try {
                val projection = mpManager.getMediaProjection(result.resultCode, result.data!!)
                if (projection != null) {
                    this.mediaProjection = projection
                    startCapture(projection)
                    return@registerForActivityResult
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting media projection", e)
            }
        }
        finishCleanly()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        settings = SettingsRepo(this)
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureLauncher.launch(mpManager.createScreenCaptureIntent())
    }

    private fun startCapture(projection: MediaProjection) {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        this.imageReader = reader

        reader.setOnImageAvailableListener({ r ->
            if (captureDone) return@setOnImageAvailableListener
            val image = try {
                r.acquireLatestImage()
            } catch (e: Exception) {
                null
            } ?: return@setOnImageAvailableListener

            captureDone = true
            val bmp = try {
                processImageToBitmap(image, width, height)
            } finally {
                image.close()
            }

            try {
                if (bmp != null) {
                    val saveResult = MediaWriter.saveScreenshotResult(applicationContext, bmp, settings)
                    if (saveResult.success) {
                        Toast.makeText(this@ProjectionFallback, "Screenshot captured", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ProjectionFallback, "Screenshot failed: ${saveResult.errorMessage}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@ProjectionFallback, "Could not process the screenshot", Toast.LENGTH_SHORT).show()
                }
            } catch (error: Exception) {
                Log.e(TAG, "Could not save fallback screenshot", error)
                Toast.makeText(this@ProjectionFallback, "Screenshot could not be saved", Toast.LENGTH_SHORT).show()
            } finally {
                bmp?.recycle()
                finishCleanly(restoreOverlay = true)
            }
        }, mainHandler)

        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                mediaProjection = null
                mainHandler.post { finishCleanly(restoreOverlay = true) }
            }
        }, mainHandler)

        try {
            virtualDisplay = projection.createVirtualDisplay(
                "ProjectionFallback",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                mainHandler
            )
        } catch (e: Exception) {
            Log.e(TAG, "VirtualDisplay creation failed", e)
            finishCleanly()
        }

        // Safety timeout
        mainHandler.postDelayed({
            if (!captureDone) {
                finishCleanly()
            }
        }, 2000L)
    }

    private fun processImageToBitmap(image: Image, width: Int, height: Int): Bitmap? {
        return try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width

            val bmp = Bitmap.createBitmap(
                width + (rowPadding / pixelStride),
                height,
                Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(buffer)

            if (rowPadding > 0) {
                val cropped = Bitmap.createBitmap(bmp, 0, 0, width, height)
                bmp.recycle()
                cropped
            } else {
                bmp
            }
        } catch (e: Exception) {
            Log.e(TAG, "processImageToBitmap error", e)
            null
        }
    }

    private fun cleanup() {
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        try { mediaProjection?.stop() } catch (_: Exception) {}
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }

    private fun finishCleanly(restoreOverlay: Boolean = true) {
        if (finishing) return
        finishing = true
        cleanup()
        if (restoreOverlay) {
            val restoreIntent = Intent(this, FloatyService::class.java).apply {
                action = FloatyService.ACTION_RESTORE_OVERLAY
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(restoreIntent)
                } else {
                    startService(restoreIntent)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Could not restore floating controls", error)
            }
        }
        finishAndRemoveTask()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        private const val TAG = "ProjectionFallback"
    }
}
