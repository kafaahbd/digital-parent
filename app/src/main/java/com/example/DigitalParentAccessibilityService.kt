package com.example

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DigitalParentAccessibilityService : AccessibilityService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var repository: BlockedAppRepository
    
    // In-memory cache of blocked package names for fast thread-safe queries
    private val blockedPackageNames = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        repository = BlockedAppRepository(this)
        
        // Reactively collect the list of blocked app packages from Room
        serviceScope.launch {
            repository.allBlockedApps.collect { entities ->
                val pkgs = entities.filter { it.isBlocked }.map { it.packageName }.toSet()
                synchronized(blockedPackageNames) {
                    blockedPackageNames.clear()
                    blockedPackageNames.addAll(pkgs)
                }
                Log.d("DigitalParentAccess", "Updated blocked packages cache: $pkgs")
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // Match state changed to capture foreground application packaging
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageNameChar = event.packageName
            if (packageNameChar != null) {
                val packageName = packageNameChar.toString()
                Log.d("DigitalParentAccess", "Foreground App Detected: $packageName")
                _foregroundApp.value = packageName

                // Check if this package is a blocked package
                val isBlocked = synchronized(blockedPackageNames) {
                    blockedPackageNames.contains(packageName)
                }

                // Prevent blocking ourselves or general systems
                val isSelf = packageName == "com.example"
                val isSystemIgnorable = packageName == "com.android.systemui" || 
                        packageName == "com.android.settings" || 
                        packageName == "com.google.android.inputmethod.latin"

                if (isBlocked && !isSelf && !isSystemIgnorable) {
                    Log.d("DigitalParentAccess", "INTERCEPTING: Blocked App detected! Launching BlockerActivity for $packageName")
                    launchBlockerWindow(packageName)
                }
            }
        }
    }

    private fun launchBlockerWindow(blockedPackage: String) {
        val intent = Intent(this, BlockerActivity::class.java).apply {
            putExtra("blocked_app_package", blockedPackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
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
        serviceJob.cancel() // Cancel background collection
        _isServiceRunning.value = false
    }

    companion object {
        private val _foregroundApp = MutableStateFlow("None (Pending accessibility event)")
        val foregroundApp: StateFlow<String> = _foregroundApp.asStateFlow()

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()
    }
}
