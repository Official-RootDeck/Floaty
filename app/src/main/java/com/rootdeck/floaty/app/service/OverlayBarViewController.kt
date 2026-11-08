package com.rootdeck.floaty.app.service

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.rootdeck.floaty.R

class OverlayBarViewController(
    private val rootView: View,
    private val listener: OverlayActionListener
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
