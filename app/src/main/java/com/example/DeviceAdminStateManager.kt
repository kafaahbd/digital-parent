package com.example

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeviceAdminStateManager private constructor(private val context: Context) {

    private val _isDeviceAdminEnabled = MutableStateFlow(false)
    val isDeviceAdminEnabled: StateFlow<Boolean> = _isDeviceAdminEnabled.asStateFlow()

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, DeviceAdminComponent::class.java)

    init {
        checkState()
    }

    fun checkState(): Boolean {
        val isActive = dpm.isAdminActive(adminComponent)
        _isDeviceAdminEnabled.value = isActive
        return isActive
    }

    fun getNavigationIntent(): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Enables parental/system-level device controls to prevent unauthorized modification or app uninstallation."
            )
        }
    }

    companion object {
        @Volatile
        private var instance: DeviceAdminStateManager? = null

        fun getInstance(context: Context): DeviceAdminStateManager {
            return instance ?: synchronized(this) {
                instance ?: DeviceAdminStateManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
