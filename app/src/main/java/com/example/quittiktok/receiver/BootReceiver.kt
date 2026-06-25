package com.example.quittiktok.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.quittiktok.service.MonitorService

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        const val ACTION_RESTART_MONITOR = "com.example.quittiktok.ACTION_RESTART_MONITOR"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "BootReceiver received: ${intent.action}")

        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == ACTION_RESTART_MONITOR
        ) {
            MonitorService.requestStart(context)
            Log.d(TAG, "Auto-starting monitor service")
        }
    }
}