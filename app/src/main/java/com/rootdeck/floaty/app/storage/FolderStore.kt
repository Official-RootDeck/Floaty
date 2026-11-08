package com.rootdeck.floaty.app.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import java.io.File

object FolderStore {
    const val KEY_IMAGES = "images_tree_uri"
    const val KEY_VIDEOS = "videos_tree_uri"

    fun save(context: Context, key: String, treeUri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Permission may already be held or granted by picker
        }
        context.getSharedPreferences("folders", Context.MODE_PRIVATE)
            .edit().putString(key, treeUri.toString()).apply()
    }

    fun uri(context: Context, key: String): Uri? {
        val s = context.getSharedPreferences("folders", Context.MODE_PRIVATE)
            .getString(key, null) ?: return null
        return try {
            Uri.parse(s)
        } catch (_: Exception) {
            null
        }
    }

    fun folder(context: Context, key: String): DocumentFile? {
        val u = uri(context, key) ?: return null
        return try {
            DocumentFile.fromTreeUri(context, u)
        } catch (_: Exception) {
            null
        }
    }

    fun getDefaultImagesDir(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: File(context.filesDir, "Pictures")
        return File(base, "Floaty_Images").apply { mkdirs() }
    }

    fun getDefaultVideosDir(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "Movies")
        return File(base, "Floaty_Videos").apply { mkdirs() }
    }

    fun ensureDefaultFoldersCreated(context: Context) {
        getDefaultImagesDir(context)
        getDefaultVideosDir(context)
    }

    fun getDisplayPath(context: Context, key: String): String {
        val customUri = uri(context, key)
        if (customUri != null) {
            return customUri.path ?: customUri.toString()
        }
        return if (key == KEY_IMAGES) {
            getDefaultImagesDir(context).absolutePath
        } else {
            getDefaultVideosDir(context).absolutePath
        }
    }
}
