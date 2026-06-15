package com.example

import android.content.Context
import android.content.SharedPreferences

class SettingsLockManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings_lock_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TARGET_UNLOCK_TIMESTAMP = "target_unlock_timestamp"
        private const val KEY_CHOSEN_DELAY_MINUTES = "chosen_delay_minutes"
        private const val KEY_IS_LOCKED_MANUALLY = "is_locked_manually"
    }

    fun getChosenDelayMinutes(): Int {
        return prefs.getInt(KEY_CHOSEN_DELAY_MINUTES, 1) // default to 1 min for development/testing ease
    }

    fun setChosenDelayMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_CHOSEN_DELAY_MINUTES, minutes).apply()
    }

    fun getTargetUnlockTimestamp(): Long {
        return prefs.getLong(KEY_TARGET_UNLOCK_TIMESTAMP, 0L)
    }

    fun startUnlockCountdown() {
        val delayMs = getChosenDelayMinutes() * 60 * 1000L
        val targetTimestamp = System.currentTimeMillis() + delayMs
        prefs.edit()
            .putLong(KEY_TARGET_UNLOCK_TIMESTAMP, targetTimestamp)
            .putBoolean(KEY_IS_LOCKED_MANUALLY, false)
            .apply()
    }

    fun isLocked(): Boolean {
        // If manually locked, it's definitely locked.
        if (prefs.getBoolean(KEY_IS_LOCKED_MANUALLY, true)) {
            return true
        }
        // If countdown is active, we are still locked.
        val target = getTargetUnlockTimestamp()
        return System.currentTimeMillis() < target
    }

    fun lockSettings() {
        prefs.edit()
            .putBoolean(KEY_IS_LOCKED_MANUALLY, true)
            .putLong(KEY_TARGET_UNLOCK_TIMESTAMP, 0L)
            .apply()
    }

    fun unlockSettingsFully() {
        prefs.edit()
            .putBoolean(KEY_IS_LOCKED_MANUALLY, false)
            .putLong(KEY_TARGET_UNLOCK_TIMESTAMP, 0L)
            .apply()
    }

    fun getRemainingTimeMs(): Long {
        val target = getTargetUnlockTimestamp()
        val remaining = target - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0
    }
}
