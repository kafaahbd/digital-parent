package com.example

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SettingsLockManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings_lock_prefs", Context.MODE_PRIVATE)

    // Securely encrypted storage for tamper-proof safeguard keys
    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "encrypted_settings_guard_prefs",
                masterKeyAlias,
                context.applicationContext,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("SettingsLockManager", "Failed to create EncryptedSharedPreferences, falling back to standard sandbox prefs.", e)
            context.applicationContext.getSharedPreferences("settings_guard_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    companion object {
        private const val KEY_TARGET_UNLOCK_TIMESTAMP = "target_unlock_timestamp"
        private const val KEY_CHOSEN_DELAY_MINUTES = "chosen_delay_minutes"
        private const val KEY_IS_LOCKED_MANUALLY = "is_locked_manually"
        private const val KEY_THEME_OPTION = "theme_option"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_PERMANENT_DELAY_MINUTES = "permanent_delay_minutes"
        private const val KEY_CONTENT_SENSITIVITY_LEVEL = "content_sensitivity_level"
    }

    fun getContentSensitivityLevel(): Int {
        return prefs.getInt(KEY_CONTENT_SENSITIVITY_LEVEL, 2) // default to 2 (Medium)
    }

    fun setContentSensitivityLevel(level: Int) {
        prefs.edit().putInt(KEY_CONTENT_SENSITIVITY_LEVEL, level).apply()
    }

    fun getThemeOption(): Int {
        return prefs.getInt(KEY_THEME_OPTION, 0) // default to 0 (System Default)
    }

    fun setThemeOption(option: Int) {
        prefs.edit().putInt(KEY_THEME_OPTION, option).apply()
    }

    // --- Onboarding & Guard Delay Logic ---

    fun isOnboardingCompleted(): Boolean {
        return encryptedPrefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun completeOnboarding(delayMinutes: Int) {
        encryptedPrefs.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, true)
            .putInt(KEY_PERMANENT_DELAY_MINUTES, delayMinutes)
            .apply()
        
        // Synced to general prefs as well
        prefs.edit()
            .putInt(KEY_CHOSEN_DELAY_MINUTES, delayMinutes)
            .apply()
    }

    fun getPermanentDelayMinutes(): Int {
        return encryptedPrefs.getInt(KEY_PERMANENT_DELAY_MINUTES, 1)
    }

    fun getChosenDelayMinutes(): Int {
        return if (isOnboardingCompleted()) {
            getPermanentDelayMinutes()
        } else {
            prefs.getInt(KEY_CHOSEN_DELAY_MINUTES, 1)
        }
    }

    fun setChosenDelayMinutes(minutes: Int) {
        // Only allow setting if onboarding not completed
        if (!isOnboardingCompleted()) {
            prefs.edit().putInt(KEY_CHOSEN_DELAY_MINUTES, minutes).apply()
        }
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
