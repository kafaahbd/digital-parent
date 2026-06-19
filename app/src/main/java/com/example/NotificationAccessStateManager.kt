package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NotificationAccessStateManager private constructor(private val context: Context) {

    private val _isNotificationAccessEnabled = MutableStateFlow(false)
    val isNotificationAccessEnabled: StateFlow<Boolean> = _isNotificationAccessEnabled.asStateFlow()

    init {
        checkState()
    }

    fun checkState(): Boolean {
        val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: ""
        val isGranted = enabledListeners.split(':').any {
            val component = ComponentName.unflattenFromString(it)
            component != null && component.packageName == context.packageName
        }
        _isNotificationAccessEnabled.value = isGranted
        return isGranted
    }

    fun getNavigationIntent(): Intent {
        return Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
    }

    companion object {
        @Volatile
        private var instance: NotificationAccessStateManager? = null

        fun getInstance(context: Context): NotificationAccessStateManager {
            return instance ?: synchronized(this) {
                instance ?: NotificationAccessStateManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
