package com.rootdeck.floaty.app.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.rootdeck.floaty.app.prefs.SettingsRepo
import com.rootdeck.floaty.app.storage.FolderStore
import java.io.File

class RecorderEngine(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var currentPfd: ParcelFileDescriptor? = null
    var isRecording: Boolean = false
        private set
    var isRecordingAudio: Boolean = false
        private set

    fun pickSize(mode: String): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)

        val maxW = minOf(dm.widthPixels, 1920)
        val maxH = minOf(dm.heightPixels, 1080)
        return when (mode.lowercase()) {
            "sd" -> 720 to 480
            "hd" -> 1280 to 720
            else -> if (maxH >= 1080) 1920 to 1080 else maxW to maxH
        }
    }

    fun startRecording(
        mediaProjection: MediaProjection,
        settings: SettingsRepo,
        onStarted: (Boolean) -> Unit
    ) {
        if (isRecording) {
            onStarted(true)
            return
        }

        var createdDocument: androidx.documentfile.provider.DocumentFile? = null
        var createdLocalFile: File? = null
        try {
            val (width, height) = pickSize(settings.recordResolution)
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)

            @Suppress("DEPRECATION")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }
            // Own the recorder immediately so every failure path releases it.
            this.mediaRecorder = recorder

            val hasMicPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            val shouldRecordAudio = settings.recordAudio && hasMicPermission

            if (shouldRecordAudio) {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (shouldRecordAudio) {
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(128_000)
                recorder.setAudioSamplingRate(44100)
            }
            recorder.setVideoSize(width, height)
            recorder.setVideoFrameRate(settings.recordFps)
            recorder.setVideoEncodingBitRate(6_000_000)

            val folder = FolderStore.folder(context, FolderStore.KEY_VIDEOS)
            var pfdAssigned = false
            if (folder != null && folder.canWrite()) {
                val baseName = "floaty_rec_${System.currentTimeMillis()}"
                var suffix = 0
                var candidate: String
                do {
                    candidate = if (suffix == 0) "$baseName.mp4" else "${baseName}_$suffix.mp4"
                    suffix++
                } while (folder.findFile(candidate) != null)

                val doc = folder.createFile("video/mp4", candidate)
                if (doc != null) {
                    createdDocument = doc
                    val pfd = context.contentResolver.openFileDescriptor(doc.uri, "w")
                    if (pfd != null) {
                        this.currentPfd = pfd
                        recorder.setOutputFile(pfd.fileDescriptor)
                        pfdAssigned = true
                    } else {
                        doc.delete()
                        createdDocument = null
                    }
                }
            }

            if (!pfdAssigned) {
                val videoDir = FolderStore.getDefaultVideosDir(context)
                val baseName = "floaty_rec_${System.currentTimeMillis()}"
                var suffix = 0
                while (true) {
                    val candidateName = if (suffix == 0) "$baseName.mp4" else "${baseName}_$suffix.mp4"
                    val candidate = File(videoDir, candidateName)
                    if (candidate.createNewFile()) {
                        createdLocalFile = candidate
                        recorder.setOutputFile(candidate.absolutePath)
                        break
                    }
                    if (candidate.exists()) {
                        suffix++
                    } else {
                        throw java.io.IOException("Could not create the recording file.")
                    }
                }
            }

            recorder.prepare()

            val display = mediaProjection.createVirtualDisplay(
                "FloatyRecorder",
                width,
                height,
                dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.surface,
                null,
                mainHandler
            ) ?: throw IllegalStateException("Could not create the recording display.")
            this.virtualDisplay = display

            recorder.start()
            this.isRecording = true
            this.isRecordingAudio = shouldRecordAudio
            // Retain the completed output document/file; only failed starts remove it.
            createdDocument = null
            createdLocalFile = null
            onStarted(true)
        } catch (e: Exception) {
            Log.e("RecorderEngine", "Failed to start recording", e)
            createdDocument?.delete()
            createdLocalFile?.delete()
            stopRecording()
            onStarted(false)
        }
    }

    fun stopRecording() {
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {}

        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (_: Exception) {}

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {}

        try {
            currentPfd?.close()
        } catch (_: Exception) {}

        mediaRecorder = null
        virtualDisplay = null
        currentPfd = null
        isRecordingAudio = false
        if (isRecording) {
            isRecording = false
            SettingsRepo(context).incrementVideos()
        }
    }

    fun pauseRecording() {
        if (!isRecording) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.pause()
            }
        } catch (e: Exception) {
            Log.e("RecorderEngine", "Failed to pause recording", e)
        }
    }

    fun resumeRecording() {
        if (!isRecording) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.resume()
            }
        } catch (e: Exception) {
            Log.e("RecorderEngine", "Failed to resume recording", e)
        }
    }
}
