package com.example

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DigitalParentAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // Match state changed to capture foreground application packaging
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageNameChar = event.packageName
            if (packageNameChar != null) {
                val packageName = packageNameChar.toString()
                Log.d("DigitalParentAccess", "Foreground App Detected: $packageName")
                _foregroundApp.value = packageName
            }
        }
    }

    override fun onInterrupt() {
        Log.d("DigitalParentAccess", "Accessibility service interrupted.")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("DigitalParentAccess", "Accessibility service connected successfully.")
        _isServiceRunning.value = true
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceRunning.value = false
    }

    companion object {
        private val _foregroundApp = MutableStateFlow("None (Pending accessibility event)")
        val foregroundApp: StateFlow<String> = _foregroundApp.asStateFlow()

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()
    }
}
