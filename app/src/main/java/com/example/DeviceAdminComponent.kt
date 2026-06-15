package com.example

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeviceAdminComponent : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("DeviceAdminComponent", "Device Administrator Role Enabled")
        Toast.makeText(context, "Device Admin Enabled", Toast.LENGTH_SHORT).show()
        _isAdminActive.value = true
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d("DeviceAdminComponent", "Device Administrator Role Disabled")
        Toast.makeText(context, "Device Admin Disabled", Toast.LENGTH_SHORT).show()
        _isAdminActive.value = false
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.d("DeviceAdminComponent", "Disable requested by user.")
        return "Warning: Disabling Device Admin will suspend all parent controls and safeguards!"
    }

    companion object {
        private val _isAdminActive = MutableStateFlow(false)
        val isAdminActive: StateFlow<Boolean> = _isAdminActive.asStateFlow()
    }
}
