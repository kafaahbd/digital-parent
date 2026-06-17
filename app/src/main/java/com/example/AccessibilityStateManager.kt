package com.example

import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AccessibilityStateManager private constructor(private val context: Context) {

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    private val serviceComponent = ComponentName(context, DigitalParentAccessibilityService::class.java)

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            checkState()
        }
    }

    init {
        checkState()
        try {
            context.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                true,
                observer
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun checkState(): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        val isEnabled = enabledServices.split(':').any {
            val component = ComponentName.unflattenFromString(it)
            component != null && component == serviceComponent
        }
        _isAccessibilityEnabled.value = isEnabled
        return isEnabled
    }

    fun release() {
        try {
            context.contentResolver.unregisterContentObserver(observer)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        @Volatile
        private var instance: AccessibilityStateManager? = null

        fun getInstance(context: Context): AccessibilityStateManager {
            return instance ?: synchronized(this) {
                instance ?: AccessibilityStateManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
