package com.rootdeck.floaty.app.service

import android.view.View
import android.os.Build
import android.widget.ImageView
import android.widget.LinearLayout
import android.view.ViewGroup
import android.util.TypedValue
import com.rootdeck.floaty.R

class OverlayBarViewController(
    private val rootView: View,
    private val listener: OverlayActionListener,
    iconSize: String = "medium"
) {
    interface OverlayActionListener {
        fun onTakeScreenshot()
        fun onStartRecording()
        fun onPauseRecording()
        fun onResumeRecording()
        fun onStopRecording()
    }

    private val btnScreenshot: ImageView = rootView.findViewById(R.id.btnScreenshot)
    private val btnStartRecord: ImageView = rootView.findViewById(R.id.btnStartRecord)
    private val recordingControlsGroup: LinearLayout = rootView.findViewById(R.id.recordingControlsGroup)
    private val btnPauseResumeRecord: ImageView = rootView.findViewById(R.id.btnPauseResumeRecord)
    private val btnStopRecord: ImageView = rootView.findViewById(R.id.btnStopRecord)

    private var isRecording = false
    private var isPaused = false

    init {
        val scale = when (iconSize.lowercase()) {
            "small" -> 0.8f
            "large" -> 1.25f
            else -> 1f
        }
        val density = rootView.resources.displayMetrics.density
        fun setSize(view: View, widthDp: Int, heightDp: Int) {
            view.layoutParams = view.layoutParams.apply {
                width = (widthDp * scale * density).toInt()
                height = (heightDp * scale * density).toInt()
            }
        }
        setSize(btnScreenshot, 32, 32)
        setSize(btnStartRecord, 32, 32)
        setSize(btnPauseResumeRecord, 32, 32)
        setSize(btnStopRecord, 32, 32)
        // MediaRecorder pause/resume is only supported on Android 7.0+.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            btnPauseResumeRecord.visibility = View.GONE
        }
        btnScreenshot.setOnClickListener { listener.onTakeScreenshot() }
        btnStartRecord.setOnClickListener { listener.onStartRecording() }
        btnPauseResumeRecord.setOnClickListener {
            if (isPaused) {
                listener.onResumeRecording()
                setPausedState(false)
            } else {
                listener.onPauseRecording()
                setPausedState(true)
            }
        }
        btnStopRecord.setOnClickListener { listener.onStopRecording() }
    }

    fun setRecordingState(active: Boolean) {
        isRecording = active
        isPaused = false

        if (active) {
            btnStartRecord.visibility = View.GONE
            recordingControlsGroup.visibility = View.VISIBLE
            btnPauseResumeRecord.setImageResource(R.drawable.ic_pause)
        } else {
            recordingControlsGroup.visibility = View.GONE
            btnStartRecord.visibility = View.VISIBLE
        }
    }

    private fun setPausedState(paused: Boolean) {
        isPaused = paused
        if (paused) {
            btnPauseResumeRecord.setImageResource(R.drawable.ic_play)
        } else {
            btnPauseResumeRecord.setImageResource(R.drawable.ic_pause)
        }
    }
}
