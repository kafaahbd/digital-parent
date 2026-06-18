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
import android.view.accessibility.AccessibilityNodeInfo
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

enum class BlockType { APP, WEBSITE, CONTENT_WARN }

class DigitalParentAccessibilityService : AccessibilityService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var repository: BlockedAppRepository
    private lateinit var websiteRepository: BlockedWebsiteRepository
    
    // Content Safety variables
    private lateinit var contentSafetyManager: ContentSafetyManager
    private lateinit var contentEventLogRepository: ContentEventLogRepository
    private lateinit var settingsLockManager: SettingsLockManager
    
    private val lastScanTimeMap = mutableMapOf<String, Long>()
    private val lastTextMap = mutableMapOf<String, String>()
    
    // In-memory cache of blocked package names for fast thread-safe queries
    private val blockedPackageNames = mutableSetOf<String>()
    private val blockedWebsites = mutableListOf<BlockedWebsiteEntity>()

    private var premiumOverlayView: FrameLayout? = null
    private var currentOverlayTarget: String? = null
    private var currentOverlayType: BlockType? = null

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
        websiteRepository = BlockedWebsiteRepository(this)
        contentSafetyManager = ContentSafetyManager(this)
        contentEventLogRepository = ContentEventLogRepository(this)
        settingsLockManager = SettingsLockManager(this)
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

        // Reactively collect blocked websites
        serviceScope.launch {
            websiteRepository.allBlockedWebsites.collect { entities ->
                synchronized(blockedWebsites) {
                    blockedWebsites.clear()
                    blockedWebsites.addAll(entities)
                }
                Log.d("DigitalParentAccess", "Updated blocked websites cache: ${entities.map { it.urlOrKeyword }}")
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        val packageNameChar = event.packageName
        if (packageNameChar != null) {
            val packageName = packageNameChar.toString()
            
            // Flag to check if we handled website blocking
            var websiteInterceded = false
            
            val browserPackages = setOf(
                "com.android.chrome",
                "org.mozilla.firefox",
                "org.mozilla.focus",
                "com.brave.browser",
                "com.microsoft.emmx",
                "com.sec.android.app.sbrowser"
            )
            
            // Content Safety local analysis layer (privacy-first screening)
            run {
                val levelValue = settingsLockManager.getContentSensitivityLevel()
                val sensitivity = ContentSensitivityLevel.fromValue(levelValue)
                if (sensitivity != ContentSensitivityLevel.OFF && contentSafetyManager.isSupportedApp(packageName)) {
                    val rootNode = rootInActiveWindow
                    if (rootNode != null) {
                        val builder = java.lang.StringBuilder()
                        extractTextFromNode(rootNode, builder)
                        val screenText = builder.toString().trim()
                        rootNode.recycle()

                        if (screenText.isNotEmpty()) {
                            val now = System.currentTimeMillis()
                            val lastScan = lastScanTimeMap[packageName] ?: 0L
                            val lastText = lastTextMap[packageName] ?: ""
                            
                            // Battery & Performance optimization: scan screen throttled OR if text updates
                            if ((now - lastScan > 1000) && screenText != lastText) {
                                lastScanTimeMap[packageName] = now
                                lastTextMap[packageName] = screenText

                                val detection = contentSafetyManager.analyzeText(screenText, sensitivity)
                                if (detection.isRisky) {
                                    var appLabel = packageName
                                    try {
                                        val pm = packageManager
                                        val appInfo = pm.getApplicationInfo(packageName, 0)
                                        appLabel = pm.getApplicationLabel(appInfo).toString()
                                    } catch (e: Exception) {
                                        // ignore
                                    }

                                    Log.w("DigitalParentAccess", "Content Safety Alert: Risky keyword '${detection.matchedKeyword}' in app $appLabel")
                                    
                                    serviceScope.launch {
                                        contentEventLogRepository.logEvent(
                                            appName = appLabel,
                                            packageName = packageName,
                                            detectedText = detection.matchedKeyword,
                                            category = detection.category,
                                            severity = detection.severity,
                                            actionTaken = "Shield Overlay Warning"
                                        )
                                    }

                                    showOverlay(BlockType.CONTENT_WARN, detection.category + " Content (Word: " + detection.matchedKeyword + ")")
                                    return
                                }
                            }
                        }
                    }
                }
            }

            if (browserPackages.contains(packageName)) {
                val rootNode = rootInActiveWindow
                val url = findBrowserUrl(rootNode)
                if (url != null) {
                    Log.d("DigitalParentAccess", "Detected browser URL: $url in package $packageName")
                    val isBlockedWeb = synchronized(blockedWebsites) {
                        isUrlBlocked(url, blockedWebsites)
                    }
                    if (isBlockedWeb) {
                        Log.d("DigitalParentAccess", "INTERCEPTING: Blocked website detected! Drawing Overlay for $url")
                        showOverlay(BlockType.WEBSITE, url)
                        websiteInterceded = true
                    }
                }
            }
            
            if (!websiteInterceded) {
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    Log.d("DigitalParentAccess", "Foreground App Detected: $packageName")
                    _foregroundApp.value = packageName

                    _currentPackageName.value = packageName
                    var appLabel = packageName
                    try {
                        val pm = packageManager
                        val appInfo = pm.getApplicationInfo(packageName, 0)
                        appLabel = pm.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                        // ignore
                    }
                    _currentAppName.value = appLabel

                    val ignored = isIgnoredPackage(packageName)
                    _isCurrentAppIgnored.value = ignored

                    val isBlocked = synchronized(blockedPackageNames) {
                        blockedPackageNames.contains(packageName)
                    }

                    if (isBlocked && !ignored) {
                        Log.d("DigitalParentAccess", "INTERCEPTING: Blocked App detected! Launching Blocker Overlay for $packageName")
                        showOverlay(BlockType.APP, packageName)
                    } else {
                        // Switch to a non-blocked app -> remove overlay
                        val type = currentOverlayType
                        if (type == BlockType.APP || 
                            (type == BlockType.WEBSITE && !browserPackages.contains(packageName)) ||
                            (type == BlockType.CONTENT_WARN && isIgnoredPackage(packageName))) {
                            removePremiumOverlay()
                        }
                    }
                }
            }
        }
    }

    private fun extractTextFromNode(node: AccessibilityNodeInfo?, builder: java.lang.StringBuilder) {
        if (node == null) return
        val text = node.text
        if (text != null && text.isNotBlank()) {
            builder.append(text).append(" ")
        }
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            extractTextFromNode(child, builder)
            child.recycle()
        }
    }

    private fun isUrlBlocked(url: String, blockedWebsites: List<BlockedWebsiteEntity>): Boolean {
        val cleanUrl = url.trim().lowercase()
        
        // Strip protocol
        var hostAndPath = cleanUrl
        if (hostAndPath.startsWith("http://")) {
            hostAndPath = hostAndPath.substring(7)
        } else if (hostAndPath.startsWith("https://")) {
            hostAndPath = hostAndPath.substring(8)
        }
        
        // Strip "www." prefix
        if (hostAndPath.startsWith("www.")) {
            hostAndPath = hostAndPath.substring(4)
        }
        
        for (website in blockedWebsites) {
            if (!website.isBlocked) continue
            val pattern = website.urlOrKeyword.trim().lowercase()
            
            if (website.isKeyword) {
                if (cleanUrl.contains(pattern)) {
                    return true
                }
            } else {
                var cleanPattern = pattern
                if (cleanPattern.startsWith("http://")) {
                    cleanPattern = cleanPattern.substring(7)
                } else if (cleanPattern.startsWith("https://")) {
                    cleanPattern = cleanPattern.substring(8)
                }
                if (cleanPattern.startsWith("www.")) {
                    cleanPattern = cleanPattern.substring(4)
                }
                
                if (hostAndPath == cleanPattern || 
                    hostAndPath.startsWith("$cleanPattern/") || 
                    hostAndPath.endsWith(".$cleanPattern") || 
                    hostAndPath.contains(".$cleanPattern/")
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun findBrowserUrl(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null
        
        val id = node.viewIdResourceName
        if (id != null) {
            val trimmedId = id.lowercase()
            if (trimmedId.contains("url_bar") || 
                trimmedId.contains("url_text") || 
                trimmedId.contains("location_bar_edit_text") ||
                trimmedId.contains("search_src_text") ||
                trimmedId.contains("url_bar_title") ||
                trimmedId.contains("address_bar")
            ) {
                val text = node.text?.toString()?.trim()
                if (!text.isNullOrBlank()) {
                    return text
                }
            }
        }
        
        val className = node.className?.toString()
        if (className != null && (className.contains("EditText") || className.contains("TextView"))) {
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrBlank() && (text.startsWith("http://") || text.startsWith("https://") || isUrlPattern(text))) {
                return text
            }
        }
        
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            val url = findBrowserUrl(child)
            child.recycle()
            if (url != null) {
                return url
            }
        }
        
        return null
    }

    private fun isUrlPattern(text: String): Boolean {
        if (text.contains(" ")) return false
        val dotIndex = text.indexOf('.')
        if (dotIndex > 0 && dotIndex < text.length - 1) {
            val domainParts = text.split('.')
            val lastPart = domainParts.lastOrNull()?.lowercase() ?: ""
            if (lastPart.length in 2..6) {
                return true
            }
        }
        return false
    }

    private fun showOverlay(blockType: BlockType, target: String) {
        if (!Settings.canDrawOverlays(this)) {
            Log.e("DigitalParentAccess", "Cannot draw overlay, permission missing. Falling back to Activity.")
            launchBlockerWindow(target, blockType)
            return
        }

        if (premiumOverlayView != null) {
            if (currentOverlayTarget == target && currentOverlayType == blockType) {
                return
            }
            // Update existing view text to avoid flickering
            val pkgTextView = premiumOverlayView?.findViewWithTag<TextView>("package_text")
            if (pkgTextView != null) {
                pkgTextView.text = if (blockType == BlockType.APP) {
                    "Blocked App: $target"
                } else if (blockType == BlockType.CONTENT_WARN) {
                    "Detected: $target"
                } else {
                    "Blocked Website: $target"
                }
            }
            currentOverlayTarget = target
            currentOverlayType = blockType
            return
        }

        currentOverlayTarget = target
        currentOverlayType = blockType
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        // 1. Root container (fills screen, locks background taps)
        val root = FrameLayout(this).apply {
            val backgroundDrawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                if (blockType == BlockType.WEBSITE) {
                    intArrayOf(
                        Color.parseColor("#14532D"), // Deep Islamic Green (Green 900)
                        Color.parseColor("#0F172A")  // Deep Slate-Blue
                    )
                } else if (blockType == BlockType.CONTENT_WARN) {
                    intArrayOf(
                        Color.parseColor("#78350F"), // Deep Warning Gold/Amber (Amber 900)
                        Color.parseColor("#0F172A")  // Deep Slate-Blue
                    )
                } else {
                    intArrayOf(
                        Color.parseColor("#450A0A"), // Deep Crimson (Red 950)
                        Color.parseColor("#0F172A")  // Deep Slate-Blue
                    )
                }
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
            text = if (blockType == BlockType.WEBSITE) {
                "🕌"
            } else if (blockType == BlockType.CONTENT_WARN) {
                "⚠️"
            } else {
                "🛡️"
            }
            textSize = 64f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }
        contentLayout.addView(lockIcon)

        // 4. Little restricted banner
        val restrictedLabel = TextView(this).apply {
            text = if (blockType == BlockType.WEBSITE) {
                "WEBSITE ACCESS RESTRICTED"
            } else if (blockType == BlockType.CONTENT_WARN) {
                "INAPPROPRIATE CONTENT SHIELDED"
            } else {
                "APPLICATION ACCESS RESTRICTED"
            }
            
            setTextColor(
                if (blockType == BlockType.WEBSITE) {
                    Color.parseColor("#10B981")
                } else if (blockType == BlockType.CONTENT_WARN) {
                    Color.parseColor("#F59E0B")
                } else {
                    Color.parseColor("#EF4444")
                }
            )
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
            letterSpacing = 0.15f
        }
        contentLayout.addView(restrictedLabel)

        // 5. Giant Bold Title
        val titleLabel = TextView(this).apply {
            text = if (blockType == BlockType.WEBSITE) {
                "Divine Guard Filter"
            } else if (blockType == BlockType.CONTENT_WARN) {
                "Hefazot Core Guardian"
            } else {
                "Digital Parent Guard"
            }
            setTextColor(Color.WHITE)
            textSize = 28f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        }
        contentLayout.addView(titleLabel)

        // 6. Subtitle / Warning Note
        val subtitleLabel = TextView(this).apply {
            text = if (blockType == BlockType.WEBSITE) {
                "Guarding our hearts, minds, and sight from distractions."
            } else if (blockType == BlockType.CONTENT_WARN) {
                "Lower your gaze. Content safety filters has shielded inappropriate visual/textual content."
            } else {
                "Balanced digital lifestyle requires moderation."
            }
            setTextColor(Color.parseColor("#94A3B8")) // Slate 400
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))
        }
        contentLayout.addView(subtitleLabel)

        // 7. Target Package Container Card
        val targetCard = FrameLayout(this).apply {
            val gd = GradientDrawable().apply {
                if (blockType == BlockType.WEBSITE) {
                    setColor(Color.parseColor("#3410B981")) // Semi-transparent Green
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), Color.parseColor("#6610B981"))
                } else if (blockType == BlockType.CONTENT_WARN) {
                    setColor(Color.parseColor("#34F59E0B")) // Semi-transparent Amber
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), Color.parseColor("#66F59E0B"))
                } else {
                    setColor(Color.parseColor("#34EF4444")) // Semi-transparent Red accent
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), Color.parseColor("#66EF4444"))
                }
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
            text = if (blockType == BlockType.APP) {
                "Blocked App: $target"
            } else if (blockType == BlockType.CONTENT_WARN) {
                "Detected: $target"
            } else {
                "Blocked Website: $target"
            }
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
            text = if (blockType == BlockType.WEBSITE) {
                "GUIDE TO MINDFULNESS"
            } else if (blockType == BlockType.CONTENT_WARN) {
                "HEFAZOT MORAL SHIELD"
            } else {
                "BREATHE & DISCONNECT"
            }
            
            setTextColor(
                if (blockType == BlockType.WEBSITE) {
                    Color.parseColor("#10B981")
                } else if (blockType == BlockType.CONTENT_WARN) {
                    Color.parseColor("#F59E0B")
                } else {
                    Color.parseColor("#38BDF8")
                }
            )
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(10))
            letterSpacing = 0.1f
        }
        quoteCard.addView(quoteHeader)

        val appQuotes = listOf(
            "\"Self-control is strength. Right thought is mastery. Calmness is power.\" — James Allen",
            "\"The first and best victory is to conquer self.\" — Plato",
            "\"Rule your mind or it will rule you.\" — Horace",
            "\"It is not that we have a short time to live, but that we waste a lot of it.\" — Seneca",
            "\"Do not let your mind control you, master the present moment instead.\" — Marcus Aurelius"
        )
        
        val islamicQuotes = listOf(
            "\"Indeed, the hearing, the sight and the heart - about all those [one] will be questioned.\" — Surah Al-Isra' [17:36]",
            "\"Tell the believing men to reduce [some] of their vision and guard their private parts. That is purer for them.\" — Surah An-Nur [24:30]",
            "\"There are two blessings which many people lose: health and free time.\" — Prophet Muhammad (PBUH)",
            "\"Speak good or remain silent.\" — Prophet Muhammad (PBUH)",
            "\"A wise man is one who calls himself to account and acts for the life after death.\" — Prophet Muhammad (PBUH)"
        )

        val safetyQuotes = listOf(
            "\"Lower your gaze and guard your heart. Shielding your mind builds strength and moral purity.\"",
            "\"Do not let sight or words tempt your soul. True digital self-control is our highest shield.\"",
            "\"Keep your intentions pure and environment clean. A pure mind leads to a pure life.\"",
            "\"When harmful desires whisper, replace them with remembrance, focus, and noble work.\"",
            "\"Purity is a shield that safeguards the light within your soul from getting dimmed.\""
        )
        
        val selectedQuote = if (blockType == BlockType.WEBSITE) {
            islamicQuotes.random()
        } else if (blockType == BlockType.CONTENT_WARN) {
            safetyQuotes.random()
        } else {
            appQuotes.random()
        }

        val quoteBody = TextView(this).apply {
            text = selectedQuote
            setTextColor(Color.parseColor("#E2E8F0")) // Slate 200
            textSize = 15f
            if (blockType == BlockType.WEBSITE || blockType == BlockType.CONTENT_WARN) {
                typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
            } else {
                typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.ITALIC)
            }
            gravity = Gravity.CENTER
            setLineSpacing(dp(4).toFloat(), 1.0f)
        }
        quoteCard.addView(quoteBody)
        contentLayout.addView(quoteCard)

        // 9. Prominent authoritative "Exit to Home Screen" Button
        val exitButton = Button(this).apply {
            text = if (blockType == BlockType.WEBSITE) {
                "Return to Purity (Exit)"
            } else if (blockType == BlockType.CONTENT_WARN) {
                "Back to Safety (Exit)"
            } else {
                "Exit to Home Screen"
            }
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            
            val activeBg = GradientDrawable().apply {
                val colorHex = if (blockType == BlockType.WEBSITE) {
                    "#10B981"
                } else if (blockType == BlockType.CONTENT_WARN) {
                    "#F59E0B"
                } else {
                    "#E11D48"
                }
                setColor(Color.parseColor(colorHex))
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
            launchBlockerWindow(target, blockType)
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
            currentOverlayTarget = null
            currentOverlayType = null
        }
    }

    private fun launchBlockerWindow(target: String, type: BlockType = BlockType.APP) {
        val intent = Intent(this, BlockerActivity::class.java).apply {
            putExtra("blocked_app_package", target)
            putExtra("block_type", type.name)
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
