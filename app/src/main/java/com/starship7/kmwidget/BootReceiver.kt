package com.starship7.kmwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.i("BootReceiver", "Boot completed — starting overlay and data service")

        // Start data collection service
        context.startForegroundService(Intent(context, RangeUpdateService::class.java))

        // Auto-start overlay if it was enabled before reboot
        if (OverlayService.isEnabled(context)) {
            context.startForegroundService(
                Intent(context, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_START
                }
            )
        }
    }
}
