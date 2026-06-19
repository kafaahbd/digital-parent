package com.example

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UsageAccessStateManager private constructor(private val context: Context) {

    private val _isUsageAccessEnabled = MutableStateFlow(false)
    val isUsageAccessEnabled: StateFlow<Boolean> = _isUsageAccessEnabled.asStateFlow()

    init {
        checkState()
    }

    fun checkState(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.noteOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        val isGranted = mode == AppOpsManager.MODE_ALLOWED
        _isUsageAccessEnabled.value = isGranted
        return isGranted
    }

    fun getNavigationIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    companion object {
        @Volatile
        private var instance: UsageAccessStateManager? = null

        fun getInstance(context: Context): UsageAccessStateManager {
            return instance ?: synchronized(this) {
                instance ?: UsageAccessStateManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
