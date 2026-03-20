package com.aihub.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            if (Settings.canDrawOverlays(context)) {
                val serviceIntent = Intent(context, OverlayService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
