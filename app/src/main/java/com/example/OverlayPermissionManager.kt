package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OverlayPermissionManager private constructor(private val context: Context) {

    private val _isOverlayEnabled = MutableStateFlow(false)
    val isOverlayEnabled: StateFlow<Boolean> = _isOverlayEnabled.asStateFlow()

    init {
        checkState()
    }

    fun checkState(): Boolean {
        val isGranted = Settings.canDrawOverlays(context)
        _isOverlayEnabled.value = isGranted
        return isGranted
    }

    fun getNavigationIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
    }

    companion object {
        @Volatile
        private var instance: OverlayPermissionManager? = null

        fun getInstance(context: Context): OverlayPermissionManager {
            return instance ?: synchronized(this) {
                instance ?: OverlayPermissionManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
