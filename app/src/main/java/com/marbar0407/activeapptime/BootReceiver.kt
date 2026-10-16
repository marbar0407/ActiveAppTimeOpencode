package com.marbar0407.activeapptime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs(context).running) {
            try {
                MonitorService.start(context)
            } catch (_: Exception) {
            }
        }
    }
}
