package com.rootdeck.floaty.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.rootdeck.floaty.app.service.FloatyService

class ProjectionRequestActivity : ComponentActivity() {

    private var targetAction: String? = null

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, FloatyService::class.java).apply {
                action = FloatyService.ACTION_ATTACH_PROJECTION
                putExtra(FloatyService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(FloatyService.EXTRA_RESULT_INTENT, result.data)
                putExtra(FloatyService.EXTRA_TARGET_ACTION, targetAction)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        targetAction = intent.getStringExtra(FloatyService.EXTRA_TARGET_ACTION)

        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = mpManager.createScreenCaptureIntent()
        captureLauncher.launch(captureIntent)
    }
}
