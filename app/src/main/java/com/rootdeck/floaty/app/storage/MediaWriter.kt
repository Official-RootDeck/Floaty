package com.rootdeck.floaty.app.storage

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.MediaActionSound
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.rootdeck.floaty.app.capture.SaveImageResult
import com.rootdeck.floaty.app.capture.SaveImageResultSuccess
import com.rootdeck.floaty.app.prefs.SettingsRepo
import com.rootdeck.floaty.app.util.formatFileName
import com.rootdeck.floaty.app.util.mimeFromFileExtension
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import kotlin.math.max

object MediaWriter {

    private const val TAG = "MediaWriter"

    fun cutOutBitmap(fullBitmap: Bitmap, cutOutRect: Rect?): Bitmap {
        if (cutOutRect != null && cutOutRect.width() > 0 && cutOutRect.height() > 0) {
            val left = max(0, cutOutRect.left)
            val top = max(0, cutOutRect.top)
            val right = cutOutRect.right.coerceAtMost(fullBitmap.width)
            val bottom = cutOutRect.bottom.coerceAtMost(fullBitmap.height)
            val width = right - left
            val height = bottom - top
            if (width > 0 && height > 0) {
                return Bitmap.createBitmap(fullBitmap, left, top, width, height)
            }
        }
        return fullBitmap
    }

    fun saveScreenshotResult(
        context: Context,
        fullBitmap: Bitmap,
        settings: SettingsRepo
    ): SaveImageResult {
        val bitmap = if (settings.autoCropEnabled) {
            val cropRect = Rect(
                settings.autoCropLeft,
                settings.autoCropTop,
                fullBitmap.width - settings.autoCropRight,
                fullBitmap.height - settings.autoCropBottom
            )
            cutOutBitmap(fullBitmap, cropRect)
        } else {
            fullBitmap
        }

        val fileFormat = settings.fileFormat.lowercase()
        val (compressFormat, ext) = when (fileFormat) {
            "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG to "jpg"
            "webp" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (settings.formatQuality >= 100) {
                        Bitmap.CompressFormat.WEBP_LOSSLESS to "webp"
                    } else {
                        Bitmap.CompressFormat.WEBP_LOSSY to "webp"
                    }
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP to "webp"
                }
            }
            else -> Bitmap.CompressFormat.PNG to "png"
        }

        val mime = mimeFromFileExtension(ext)
        val baseName = formatFileName(context, settings.fileNamePattern, Date())
        val requestedName = if (baseName.endsWith(".$ext", ignoreCase = true)) baseName else "$baseName.$ext"

        val folder = FolderStore.folder(context, FolderStore.KEY_IMAGES)
        val defaultDirectory = FolderStore.getDefaultImagesDir(context)
        val filename = uniqueFileName(requestedName, folder, defaultDirectory)
        var outUri: Uri? = null
        var outFile: File? = null

        if (folder != null && folder.canWrite()) {
            try {
                val doc = folder.createFile(mime, filename)
                if (doc != null) {
                    val compressed = context.contentResolver.openOutputStream(doc.uri, "w")?.use { out ->
                        bitmap.compress(compressFormat, settings.formatQuality, out)
                    } == true
                    if (compressed) {
                        outUri = doc.uri
                    } else {
                        doc.delete()
                        throw IllegalStateException("The screenshot could not be encoded.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to SAF folder", e)
            }
        }

        // Fallback to default directory
        if (outUri == null) {
            try {
                val picturesDir = defaultDirectory
                val targetFile = createUniqueFile(picturesDir, requestedName)
                try {
                    FileOutputStream(targetFile).use { out ->
                        if (!bitmap.compress(compressFormat, settings.formatQuality, out)) {
                            throw IllegalStateException("The screenshot could not be encoded.")
                        }
                        out.fd.sync()
                    }
                } catch (error: Exception) {
                    targetFile.delete()
                    throw error
                }
                outFile = targetFile
                outUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    targetFile
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to fallback directory", e)
                return SaveImageResult("Failed to write screenshot: ${e.message}", false)
            }
        }

        if (outUri != null) {
            if (settings.captureSound) {
                try {
                    MediaActionSound().apply {
                        load(MediaActionSound.SHUTTER_CLICK)
                        play(MediaActionSound.SHUTTER_CLICK)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not play shutter sound", e)
                }
            }

            if (settings.autoCopyClipboard) {
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    if (clipboard != null) {
                        val clip = ClipData.newUri(context.contentResolver, filename, outUri)
                        clipboard.setPrimaryClip(clip)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not copy to clipboard", e)
                }
            }

            try {
                outFile?.absolutePath?.let { path ->
                    MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(mime), null)
                }
            } catch (_: Exception) {}

            settings.incrementScreenshots()
            return SaveImageResultSuccess(bitmap, mime, outFile, outUri, filename, outUri.toString())
        }

        return SaveImageResult("Unknown error saving screenshot", false)
    }

    private fun uniqueFileName(
        requestedName: String,
        folder: DocumentFile?,
        directory: File
    ): String {
        val dot = requestedName.lastIndexOf('.')
        val base = if (dot > 0) requestedName.substring(0, dot) else requestedName
        val extension = if (dot > 0) requestedName.substring(dot) else ""
        var candidate = requestedName
        var suffix = 1
        while ((folder?.findFile(candidate) != null) || File(directory, candidate).exists()) {
            candidate = "$base ($suffix)$extension"
            suffix++
        }
        return candidate
    }

    private fun createUniqueFile(directory: File, requestedName: String): File {
        val dot = requestedName.lastIndexOf('.')
        val base = if (dot > 0) requestedName.substring(0, dot) else requestedName
        val extension = if (dot > 0) requestedName.substring(dot) else ""
        var suffix = 0
        while (true) {
            val candidateName = if (suffix == 0) requestedName else "$base ($suffix)$extension"
            val candidate = File(directory, candidateName)
            if (candidate.exists()) {
                suffix++
                continue
            }
            if (candidate.createNewFile()) return candidate
            throw java.io.IOException("Could not create screenshot file in ${directory.absolutePath}.")
        }
    }

    fun saveScreenshot(context: Context, bmp: Bitmap, settings: SettingsRepo): Uri? {
        val result = saveScreenshotResult(context, bmp, settings)
        return if (result is SaveImageResultSuccess) result.uri else null
    }
}
