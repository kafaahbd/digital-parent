package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device reboot completed. Revalidating protection safeguards.")
            
            // Revalidate permission states automatically on boot
            val repository = ProtectionHealthRepository.getInstance(context)
            repository.refreshAll()
            
            val state = repository.healthState.value
            Log.d("BootReceiver", "Post-boot guardian health status - All Healthy: ${state.isAllHealthy}. Accessibility: ${state.isAccessibilityEnabled}, Overlay: ${state.isOverlayEnabled}, Admin: ${state.isDeviceAdminEnabled}")
        }
    }
}
