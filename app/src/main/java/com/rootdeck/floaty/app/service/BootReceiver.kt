package com.rootdeck.floaty.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.rootdeck.floaty.app.prefs.SettingsRepo

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = SettingsRepo(context)
        if (!prefs.startOnBoot) return

        // API 34+: cannot start mediaProjection from boot.
        // Restart overlay only.
        val i = Intent(context, FloatyService::class.java).apply {
            action = FloatyService.ACTION_START_OVERLAY_ONLY
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(i)
        } else {
            context.startService(i)
        }
    }
}
