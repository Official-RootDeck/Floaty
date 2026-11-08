package com.rootdeck.floaty.app.capture

import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.Serializable

open class SaveImageResult(
    val errorMessage: String = "",
    val success: Boolean = false
) : Serializable {
    override fun toString(): String = "SaveImageResult($errorMessage)"
}

data class SaveImageResultSuccess(
    val bitmap: Bitmap,
    val mimeType: String,
    val file: File?,
    val uri: Uri? = null,
    val fileTitle: String? = null,
    val dummyPath: String = ""
) : SaveImageResult("", true) {
    override fun toString(): String = "SaveImageResultSuccess($file, $uri)"
}
