package com.example

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProtectionHealthState(
    val isAccessibilityEnabled: Boolean,
    val isOverlayEnabled: Boolean,
    val isDeviceAdminEnabled: Boolean,
    val isUsageAccessEnabled: Boolean,
    val isNotificationAccessEnabled: Boolean,
    val isAllHealthy: Boolean
)

class ProtectionHealthRepository private constructor(private val context: Context) {

    private val accessibilityManager = AccessibilityStateManager.getInstance(context)
    private val overlayManager = OverlayPermissionManager.getInstance(context)
    private val adminManager = DeviceAdminStateManager.getInstance(context)
    private val usageAccessManager = UsageAccessStateManager.getInstance(context)
    private val notificationAccessManager = NotificationAccessStateManager.getInstance(context)

    private val logRepository = ContentEventLogRepository(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    private var lastState: ProtectionHealthState? = null

    val healthState: StateFlow<ProtectionHealthState> = combine(
        accessibilityManager.isAccessibilityEnabled,
        overlayManager.isOverlayEnabled,
        adminManager.isDeviceAdminEnabled,
        usageAccessManager.isUsageAccessEnabled,
        notificationAccessManager.isNotificationAccessEnabled
    ) { accessibility, overlay, admin, usage, notification ->
        val state = ProtectionHealthState(
            isAccessibilityEnabled = accessibility,
            isOverlayEnabled = overlay,
            isDeviceAdminEnabled = admin,
            isUsageAccessEnabled = usage,
            isNotificationAccessEnabled = notification,
            isAllHealthy = accessibility && overlay && admin && usage && notification
        )
        logTransitionsIfNeeded(state)
        state
    }.stateIn(
        scope = CoroutineScope(Dispatchers.Default),
        started = SharingStarted.Eagerly,
        initialValue = ProtectionHealthState(
            isAccessibilityEnabled = accessibilityManager.isAccessibilityEnabled.value,
            isOverlayEnabled = overlayManager.isOverlayEnabled.value,
            isDeviceAdminEnabled = adminManager.isDeviceAdminEnabled.value,
            isUsageAccessEnabled = usageAccessManager.isUsageAccessEnabled.value,
            isNotificationAccessEnabled = notificationAccessManager.isNotificationAccessEnabled.value,
            isAllHealthy = accessibilityManager.isAccessibilityEnabled.value &&
                    overlayManager.isOverlayEnabled.value &&
                    adminManager.isDeviceAdminEnabled.value &&
                    usageAccessManager.isUsageAccessEnabled.value &&
                    notificationAccessManager.isNotificationAccessEnabled.value
        )
    )

    private fun logTransitionsIfNeeded(newState: ProtectionHealthState) {
        val old = lastState
        if (old == null) {
            lastState = newState
            return
        }
        lastState = newState

        if (old.isAccessibilityEnabled && !newState.isAccessibilityEnabled) {
            logPermissionFailure("Accessibility Service", "Disabled by User or system.")
        }
        if (old.isOverlayEnabled && !newState.isOverlayEnabled) {
            logPermissionFailure("Overlay Draw Permission", "Disabled by User.")
        }
        if (old.isDeviceAdminEnabled && !newState.isDeviceAdminEnabled) {
            logPermissionFailure("Device Administrator Status", "Deactivated by User.")
        }
        if (old.isUsageAccessEnabled && !newState.isUsageAccessEnabled) {
            logPermissionFailure("Usage Stats Access", "Revoked by User.")
        }
        if (old.isNotificationAccessEnabled && !newState.isNotificationAccessEnabled) {
            logPermissionFailure("Notification Access", "Revoked by User.")
        }
    }

    private fun logPermissionFailure(permissionName: String, reason: String) {
        scope.launch {
            try {
                logRepository.logEvent(
                    appName = "Hefazot Core Guardian",
                    packageName = context.packageName,
                    detectedText = "$permissionName was disabled!",
                    category = "Protection Failure",
                    severity = "CRITICAL",
                    actionTaken = "Displaying Shield Warning & Navigating to Settings"
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun refreshAll() {
        accessibilityManager.checkState()
        overlayManager.checkState()
        adminManager.checkState()
        usageAccessManager.checkState()
        notificationAccessManager.checkState()
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
