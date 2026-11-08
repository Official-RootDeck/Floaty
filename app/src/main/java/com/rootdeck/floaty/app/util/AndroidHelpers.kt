package com.rootdeck.floaty.app.util

import android.app.Dialog
import android.app.KeyguardManager
import android.content.Context
import android.content.DialogInterface
import android.widget.Toast
import android.os.Build

fun Context?.toastMessage(text: String, duration: Int = Toast.LENGTH_SHORT) {
    this ?: return
    Toast.makeText(this, text, duration).show()
}

fun Context?.toastMessage(resId: Int, duration: Int = Toast.LENGTH_SHORT) {
    this ?: return
    Toast.makeText(this, resId, duration).show()
}

fun DialogInterface?.safeDismiss() {
    this ?: return
    try {
        if (this is Dialog && !this.isShowing) return
        this.dismiss()
    } catch (_: Exception) {}
}

fun isDeviceLocked(context: Context): Boolean {
    val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        ?: return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
        km.isDeviceLocked
    } else {
        @Suppress("DEPRECATION")
        km.isKeyguardLocked
    }
}

fun toastDeviceIsLocked(context: Context) {
    context.toastMessage("Screenshot prevented: Device is locked", Toast.LENGTH_SHORT)
}
