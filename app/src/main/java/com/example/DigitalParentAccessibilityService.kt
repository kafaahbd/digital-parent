package com.example

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
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

    private var premiumOverlayView: FrameLayout? = null
    private var currentOverlayPackage: String? = null

    private val launcherPackages = mutableSetOf<String>()

    private fun loadLauncherPackages() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val pm = packageManager
            val list = pm.queryIntentActivities(intent, 0)
            synchronized(launcherPackages) {
                launcherPackages.clear()
                for (info in list) {
                    launcherPackages.add(info.activityInfo.packageName)
                }
            }
            Log.d("DigitalParentAccess", "Loaded launcher packages: $launcherPackages")
        } catch (e: Exception) {
            Log.e("DigitalParentAccess", "Error loading launcher packages", e)
        }
    }

    private fun isIgnoredPackage(packageName: String): Boolean {
        if (packageName == this.packageName) return true
        
        // Android system packages and permission controller packages
        if (packageName == "android" ||
            packageName.startsWith("com.android.systemui") ||
            packageName.startsWith("com.android.settings") ||
            packageName.startsWith("com.android.permissioncontroller") ||
            packageName.startsWith("com.google.android.permissioncontroller") ||
            packageName.startsWith("com.android.packageinstaller") ||
            packageName.startsWith("com.google.android.packageinstaller") ||
            packageName.startsWith("com.google.android.inputmethod")
        ) {
            return true
        }
        
        // Check Launcher packages
        synchronized(launcherPackages) {
            if (launcherPackages.contains(packageName)) {
                return true
            }
        }
        
        // Additional generic System packages check (FLAG_SYSTEM)
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem) {
                return true
            }
        } catch (e: Exception) {
            // ignore
        }
        
        return false
    }

    override fun onCreate() {
        super.onCreate()
        repository = BlockedAppRepository(this)
        loadLauncherPackages()
        
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

                // Resolve real application name
                _currentPackageName.value = packageName
                var appLabel = packageName
                try {
                    val pm = packageManager
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    appLabel = pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    // fall back
                }
                _currentAppName.value = appLabel

                val ignored = isIgnoredPackage(packageName)
                _isCurrentAppIgnored.value = ignored

                // Check if this package is a blocked package
                val isBlocked = synchronized(blockedPackageNames) {
                    blockedPackageNames.contains(packageName)
                }

                if (isBlocked && !ignored) {
                    Log.d("DigitalParentAccess", "INTERCEPTING: Blocked App detected! Launching Blocker Overlay for $packageName")
                    showPremiumOverlay(packageName)
                } else {
                    // Switch to a non-blocked app or system screen -> remove overlay
                    removePremiumOverlay()
                }
            }
        }
    }

    private fun showPremiumOverlay(blockedPackage: String) {
        if (!Settings.canDrawOverlays(this)) {
            Log.e("DigitalParentAccess", "Cannot draw overlay, permission missing. Falling back to Activity.")
            launchBlockerWindow(blockedPackage)
            return
        }

        if (premiumOverlayView != null) {
            if (currentOverlayPackage == blockedPackage) {
                return
            }
            // Update existing layout instead of recreation to avoid flickering
            val pkgTextView = premiumOverlayView?.findViewWithTag<TextView>("package_text")
            pkgTextView?.text = "Blocked App: $blockedPackage"
            currentOverlayPackage = blockedPackage
            return
        }

        currentOverlayPackage = blockedPackage
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        // 1. Root container (fills screen, locks background taps)
        val root = FrameLayout(this).apply {
            val backgroundDrawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.parseColor("#450A0A"), // Deep Crimson (Red 950)
                    Color.parseColor("#0F172A")  // Deep Slate-Blue (Slate 900)
                )
            )
            background = backgroundDrawable
            isClickable = true
            isFocusable = true
        }

        // 2. Vertical layout content container
        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(32))
        }

        // 3. Shield Lock Icon View
        val lockIcon = TextView(this).apply {
            text = "🛡️"
            textSize = 64f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }
        contentLayout.addView(lockIcon)

        // 4. Little restricted banner
        val restrictedLabel = TextView(this).apply {
            text = "ACCESS RESTRICTED"
            setTextColor(Color.parseColor("#EF4444")) // Red 500
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
            letterSpacing = 0.15f
        }
        contentLayout.addView(restrictedLabel)

        // 5. Giant Bold Title
        val titleLabel = TextView(this).apply {
            text = "Digital Parent Guard"
            setTextColor(Color.WHITE)
            textSize = 28f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        }
        contentLayout.addView(titleLabel)

        // 6. Subtitle / Warning Note
        val subtitleLabel = TextView(this).apply {
            text = "Balanced digital lifestyle requires moderation."
            setTextColor(Color.parseColor("#94A3B8")) // Slate 400
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))
        }
        contentLayout.addView(subtitleLabel)

        // 7. Target Package Container Card
        val targetCard = FrameLayout(this).apply {
            val gd = GradientDrawable().apply {
                setColor(Color.parseColor("#34EF4444")) // Semi-transparent Red accent
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#66EF4444"))
            }
            background = gd
            setPadding(dp(16), dp(12), dp(16), dp(12))
            
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dp(24))
            }
            layoutParams = lp
        }

        val targetText = TextView(this).apply {
            text = "Blocked App: $blockedPackage"
            tag = "package_text"
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        targetCard.addView(targetText)
        contentLayout.addView(targetCard)

        // 8. Mindfulness/Parent Quote Box
        val quoteCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val gd = GradientDrawable().apply {
                setColor(Color.parseColor("#121E293B")) // Transparent slate details
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor("#22FFFFFF"))
            }
            background = gd
            setPadding(dp(20), dp(20), dp(20), dp(20))
            
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dp(32))
            }
            layoutParams = lp
        }

        val quoteHeader = TextView(this).apply {
            text = "BREATHE & DISCONNECT"
            setTextColor(Color.parseColor("#38BDF8")) // Cool Soft Blue
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(10))
            letterSpacing = 0.1f
        }
        quoteCard.addView(quoteHeader)

        val quotes = listOf(
            "\"Self-control is strength. Right thought is mastery. Calmness is power.\" — James Allen",
            "\"The first and best victory is to conquer self.\" — Plato",
            "\"Rule your mind or it will rule you.\" — Horace",
            "\"It is not that we have a short time to live, but that we waste a lot of it.\" — Seneca",
            "\"Do not let your mind control you, master the present moment instead.\" — Marcus Aurelius"
        )
        val selectedQuote = quotes.random()

        val quoteBody = TextView(this).apply {
            text = selectedQuote
            setTextColor(Color.parseColor("#E2E8F0")) // Slate 200
            textSize = 15f
            typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.ITALIC)
            gravity = Gravity.CENTER
            setLineSpacing(dp(4).toFloat(), 1.0f)
        }
        quoteCard.addView(quoteBody)
        contentLayout.addView(quoteCard)

        // 9. Prominent authoritative "Exit to Home Screen" Button
        val exitButton = Button(this).apply {
            text = "Exit to Home Screen"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            
            val activeBg = GradientDrawable().apply {
                setColor(Color.parseColor("#E11D48")) // Rose 600
                cornerRadius = dp(12).toFloat()
            }
            background = activeBg
            isAllCaps = false
            
            setOnClickListener {
                val startMain = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(startMain)
                removePremiumOverlay()
            }
            
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
            )
            layoutParams = lp
        }
        contentLayout.addView(exitButton)

        // Add visual content in centering FrameLayout parent
        val flp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        root.addView(contentLayout, flp)

        val params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or 
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or 
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
            format = android.graphics.PixelFormat.TRANSLUCENT
            screenOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        try {
            windowManager.addView(root, params)
            premiumOverlayView = root
            Log.d("DigitalParentAccess", "Premium fullscreen WindowManager overlay drawn.")
        } catch (e: Exception) {
            Log.e("DigitalParentAccess", "Failed to draw fullscreen overlay via WindowManager", e)
            // fallback so they never escape
            launchBlockerWindow(blockedPackage)
        }
    }

    private fun removePremiumOverlay() {
        if (premiumOverlayView != null) {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            try {
                windowManager.removeView(premiumOverlayView)
                Log.d("DigitalParentAccess", "Premium system overlay dismissed cleanly.")
            } catch (e: Exception) {
                Log.e("DigitalParentAccess", "Failed to remove system overlay view", e)
            }
            premiumOverlayView = null
            currentOverlayPackage = null
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
        removePremiumOverlay()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("DigitalParentAccess", "Accessibility service connected successfully.")
        _isServiceRunning.value = true
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel() // Cancel background collection
        removePremiumOverlay()
        _isServiceRunning.value = false
    }

    companion object {
        private val _foregroundApp = MutableStateFlow("None (Pending accessibility event)")
        val foregroundApp: StateFlow<String> = _foregroundApp.asStateFlow()

        private val _currentPackageName = MutableStateFlow("None")
        val currentPackageName: StateFlow<String> = _currentPackageName.asStateFlow()

        private val _currentAppName = MutableStateFlow("None")
        val currentAppName: StateFlow<String> = _currentAppName.asStateFlow()

        private val _isCurrentAppIgnored = MutableStateFlow(false)
        val isCurrentAppIgnored: StateFlow<Boolean> = _isCurrentAppIgnored.asStateFlow()

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()
    }
}
