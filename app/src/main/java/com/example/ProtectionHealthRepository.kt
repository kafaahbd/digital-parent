package com.example

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ProtectionHealthState(
    val isAccessibilityEnabled: Boolean,
    val isOverlayEnabled: Boolean,
    val isDeviceAdminEnabled: Boolean,
    val isAllHealthy: Boolean
)

class ProtectionHealthRepository private constructor(context: Context) {

    private val accessibilityManager = AccessibilityStateManager.getInstance(context)
    private val overlayManager = OverlayPermissionManager.getInstance(context)
    private val adminManager = DeviceAdminStateManager.getInstance(context)

    val healthState: StateFlow<ProtectionHealthState> = combine(
        accessibilityManager.isAccessibilityEnabled,
        overlayManager.isOverlayEnabled,
        adminManager.isDeviceAdminEnabled
    ) { accessibility, overlay, admin ->
        ProtectionHealthState(
            isAccessibilityEnabled = accessibility,
            isOverlayEnabled = overlay,
            isDeviceAdminEnabled = admin,
            isAllHealthy = accessibility && overlay && admin
        )
    }.stateIn(
        scope = CoroutineScope(Dispatchers.Default),
        started = SharingStarted.Eagerly,
        initialValue = ProtectionHealthState(
            isAccessibilityEnabled = accessibilityManager.isAccessibilityEnabled.value,
            isOverlayEnabled = overlayManager.isOverlayEnabled.value,
            isDeviceAdminEnabled = adminManager.isDeviceAdminEnabled.value,
            isAllHealthy = accessibilityManager.isAccessibilityEnabled.value &&
                    overlayManager.isOverlayEnabled.value &&
                    adminManager.isDeviceAdminEnabled.value
        )
    )

    fun refreshAll() {
        accessibilityManager.checkState()
        overlayManager.checkState()
        adminManager.checkState()
    }

    companion object {
        @Volatile
        private var instance: ProtectionHealthRepository? = null

        fun getInstance(context: Context): ProtectionHealthRepository {
            return instance ?: synchronized(this) {
                instance ?: ProtectionHealthRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
