package com.example

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.annotation.Keep
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var demoOverlayView: FrameLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            
            val isDark = when (themeMode) {
                1 -> false // Light Mode
                2 -> true  // Dark Mode
                else -> androidx.compose.foundation.isSystemInDarkTheme() // System Default
            }

            MyApplicationTheme(darkTheme = isDark) {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold"),
                    contentWindowInsets = WindowInsets.safeDrawing,
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        PermissionDashboardScreen(
                            onToggleOverlay = { toggleDemoOverlay() },
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }

    private fun toggleDemoOverlay() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Enable Draw Overlay permission first!", Toast.LENGTH_LONG).show()
            return
        }

        if (demoOverlayView != null) {
            try {
                windowManager.removeView(demoOverlayView)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error removing overlay", e)
            }
            demoOverlayView = null
            Toast.makeText(this, "Parental Lock Overlay Removed", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                380, // overlay block height
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
                y = 120
            }

            val overlay = FrameLayout(this).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#E11D48")) // Rose 600 alert
                setPadding(32, 24, 32, 24)
            }

            val textView = android.widget.TextView(this).apply {
                text = "🛡️ DIGITAL PARENT LOCKOUT TRIAL ACTIVE\nThis overlay simulates parental blocks over restricted apps."
                setTextColor(android.graphics.Color.WHITE)
                textSize = 15f
                setLineSpacing(4f, 1f)
                textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            }
            overlay.addView(textView)

            overlay.setOnClickListener {
                toggleDemoOverlay()
            }

            windowManager.addView(overlay, params)
            demoOverlayView = overlay
            Toast.makeText(this, "Parental Lock Overlay Active - Tap to dismiss", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to add window overlay", e)
            Toast.makeText(this, "Could not launch overlay: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (demoOverlayView != null) {
            try {
                val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager.removeView(demoOverlayView)
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}

// Data model representing visible installed apps
data class AppInfoItem(
    val appName: String,
    val packageName: String,
    val isBlocked: Boolean
)

data class PermissionsState(
    val isAccessibilityGranted: Boolean = false,
    val isDeviceAdminGranted: Boolean = false,
    val isOverlayGranted: Boolean = false
)

class MainViewModel : ViewModel() {
    private val _permissionsState = MutableStateFlow(PermissionsState())
    val permissionsState: StateFlow<PermissionsState> = _permissionsState.asStateFlow()

    // Installed apps
    private val _appItems = MutableStateFlow<List<AppInfoItem>>(emptyList())
    val appItems: StateFlow<List<AppInfoItem>> = _appItems.asStateFlow()

    private val _isAppsLoading = MutableStateFlow(false)
    val isAppsLoading: StateFlow<Boolean> = _isAppsLoading.asStateFlow()

    // Settings delay lock remaining countdown time (milisecond state)
    private val _remainingTimeMs = MutableStateFlow(0L)
    val remainingTimeMs: StateFlow<Long> = _remainingTimeMs.asStateFlow()

    private val _isSettingsLocked = MutableStateFlow(true)
    val isSettingsLocked: StateFlow<Boolean> = _isSettingsLocked.asStateFlow()

    private val _chosenDelay = MutableStateFlow(1) // in minutes
    val chosenDelay: StateFlow<Int> = _chosenDelay.asStateFlow()

    private val _themeMode = MutableStateFlow(0)
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    private val _isOnboardingCompleted = MutableStateFlow(false)
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    private var lockManager: SettingsLockManager? = null
    private var repository: BlockedAppRepository? = null

    fun initialize(context: Context) {
        val appCtx = context.applicationContext
        if (lockManager == null) {
            lockManager = SettingsLockManager(appCtx)
            _chosenDelay.value = lockManager!!.getChosenDelayMinutes()
            _isSettingsLocked.value = lockManager!!.isLocked()
            _remainingTimeMs.value = lockManager!!.getRemainingTimeMs()
            _themeMode.value = lockManager!!.getThemeOption()
            _isOnboardingCompleted.value = lockManager!!.isOnboardingCompleted()
        }
        if (repository == null) {
            repository = BlockedAppRepository(appCtx)
            // Reactively fetch DB changes
            viewModelScope.launch {
                repository!!.allBlockedApps.collect { dbList ->
                    loadInstalledApps(appCtx, dbList)
                }
            }
        }
        checkPermissions(appCtx)
        startTimerLoop()
    }

    private fun startTimerLoop() {
        viewModelScope.launch {
            while (true) {
                lockManager?.let { lm ->
                    val remaining = lm.getRemainingTimeMs()
                    _remainingTimeMs.value = remaining
                    val isL = lm.isLocked()
                    _isSettingsLocked.value = isL
                }
                delay(1000)
            }
        }
    }

    fun setLockDelayMinutes(minutes: Int) {
        _chosenDelay.value = minutes
        lockManager?.setChosenDelayMinutes(minutes)
    }

    fun initiateSettingsLock() {
        lockManager?.lockSettings()
        _isSettingsLocked.value = true
        _remainingTimeMs.value = 0L
    }

    fun initiateSettingsUnlockCountdown() {
        lockManager?.startUnlockCountdown()
        lockManager?.let { lm ->
            _isSettingsLocked.value = lm.isLocked()
            _remainingTimeMs.value = lm.getRemainingTimeMs()
        }
    }

    fun bypassUnlockForTesting() {
        // Handy cheat button to unlock instantly during preview/testing
        lockManager?.unlockSettingsFully()
        _isSettingsLocked.value = false
        _remainingTimeMs.value = 0L
    }

    fun checkPermissions(context: Context) {
        val accessibilityComp = ComponentName(context, DigitalParentAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        val isAccessibilityGranted = enabledServices.split(':').any {
            val component = ComponentName.unflattenFromString(it)
            component != null && component == accessibilityComp
        }

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, DeviceAdminComponent::class.java)
        val isDeviceAdminGranted = dpm.isAdminActive(adminComponent)

        val isOverlayGranted = Settings.canDrawOverlays(context)

        _permissionsState.value = PermissionsState(
            isAccessibilityGranted = isAccessibilityGranted,
            isDeviceAdminGranted = isDeviceAdminGranted,
            isOverlayGranted = isOverlayGranted
        )
    }

    private fun loadInstalledApps(context: Context, blockedApps: List<BlockedAppEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            _isAppsLoading.value = true
            try {
                val pm = context.packageManager
                val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val launcherPackages = pm.queryIntentActivities(launcherIntent, 0)
                    .map { it.activityInfo.packageName }
                    .toSet()

                val listOfApps = apps.mapNotNull { appInfo ->
                    val pName = appInfo.packageName
                    if (pName == context.packageName) return@mapNotNull null // ignore ourselves

                    val isLauncherApp = launcherPackages.contains(pName)
                    val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    val isUpdatedSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

                    if (isSystem && !isUpdatedSystem && !isLauncherApp) {
                        return@mapNotNull null
                    }

                    val label = appInfo.loadLabel(pm).toString()
                    val isCurrentlyBlocked = blockedApps.any { it.packageName == pName && it.isBlocked }
                    AppInfoItem(appName = label, packageName = pName, isBlocked = isCurrentlyBlocked)
                }.distinctBy { it.packageName }.sortedBy { it.appName }

                _appItems.value = listOfApps
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading installed apps", e)
            } finally {
                _isAppsLoading.value = false
            }
        }
    }

    fun toggleAppBlocked(packageName: String, isBlocked: Boolean) {
        viewModelScope.launch {
            repository?.setAppBlocked(packageName, isBlocked)
        }
    }

    fun completeOnboarding(delayMinutes: Int) {
        lockManager?.completeOnboarding(delayMinutes)
        _isOnboardingCompleted.value = true
        _chosenDelay.value = delayMinutes
        _isSettingsLocked.value = true // safeguard is active instantly after onboarding
    }

    fun setThemeOption(option: Int) {
        _themeMode.value = option
        lockManager?.setThemeOption(option)
    }

    fun onBackground(context: Context) {
        if (_isSettingsLocked.value && _remainingTimeMs.value > 0) {
            lockManager?.lockSettings()
            _isSettingsLocked.value = true
            _remainingTimeMs.value = 0L
            Toast.makeText(context.applicationContext, "Lock Countdown immediately reset due to app backgrounding!", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun ThemeSelectorSection(
    currentThemeOption: Int,
    onThemeSelect: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("theme_selector_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "VISUAL INTUITIVE STYLE",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Adapt the interface dynamically to match your eye comfort preferences and save resources.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val modes = listOf(
                    Triple(0, "System", "📺"),
                    Triple(1, "Light", "☀️"),
                    Triple(2, "Dark", "🌙")
                )

                modes.forEach { (option, label, icon) ->
                    val isSelected = currentThemeOption == option
                    Button(
                        onClick = { onThemeSelect(option) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(text = "$icon ", fontSize = 11.sp)
                            Text(text = label, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionDashboardScreen(
    onToggleOverlay: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val state by viewModel.permissionsState.collectAsStateWithLifecycle()
    val appItems by viewModel.appItems.collectAsStateWithLifecycle()
    val isAppsLoading by viewModel.isAppsLoading.collectAsStateWithLifecycle()
    val isSettingsLocked by viewModel.isSettingsLocked.collectAsStateWithLifecycle()
    val remainingTimeMs by viewModel.remainingTimeMs.collectAsStateWithLifecycle()
    val chosenDelay by viewModel.chosenDelay.collectAsStateWithLifecycle()
    val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsStateWithLifecycle()

    val currentApp by DigitalParentAccessibilityService.foregroundApp.collectAsStateWithLifecycle()
    val isAccessibilityServiceConnected by DigitalParentAccessibilityService.isServiceRunning.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Safeguard Center, 1 = Live Settings Config
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showUnlockPrompt by remember { mutableStateOf(false) }
    var blockToUnblock by remember { mutableStateOf<AppInfoItem?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermissions(context)
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.onBackground(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    // --- ONBOARDING FLOW ---
    if (!isOnboardingCompleted) {
        OnboardingSetupScreen(
            onComplete = { minutes ->
                viewModel.completeOnboarding(minutes)
                Toast.makeText(context, "Permanent Self-Control Delay locked to $minutes min(s)", Toast.LENGTH_LONG).show()
            }
        )
        return
    }

    // --- COOLDOWN BLOCKOVERLAY (UN-BYPASSABLE FOREGROUND LOCK) ---
    if (remainingTimeMs > 0) {
        CountdownDelayScreen(
            remainingMs = remainingTimeMs,
            chosenDelay = chosenDelay,
            onBypass = { viewModel.bypassUnlockForTesting() }
        )
        return
    }

    // --- CUSTOM SETTINGS OVERLAY ---
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Close", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Security Settings", style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val themeOptionState by viewModel.themeMode.collectAsStateWithLifecycle()
                    ThemeSelectorSection(
                        currentThemeOption = themeOptionState,
                        onThemeSelect = { viewModel.setThemeOption(it) }
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "SECURITY SHIELD PROFILE",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Text(
                            text = "Permanent Self-Control Delay: $chosenDelay Min(s)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Text(
                            text = "This delay is permanently locked via sandbox EncryptedSharedPreferences and cannot be modified. Any actions to unrestrict applications or settings will initiate this delay countdown.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.widthIn(max = 440.dp)
        )
    }

    // --- UNLOCK DELAY CONFIRMATION PROMPT ---
    if (showUnlockPrompt) {
        AlertDialog(
            onDismissRequest = { showUnlockPrompt = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Alert",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Deactivation Safeguard Active")
                }
            },
            text = {
                Text(
                    text = "Turning OFF restriction rules for this application belongs to protected settings. To prevent impulsive bypasses, you must start and await through the un-bypassable self-control countdown of $chosenDelay Minute(s). Start countdown now?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnlockPrompt = false
                        viewModel.initiateSettingsUnlockCountdown()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Initiate Countdown", color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlockPrompt = false }) {
                    Text("Keep Blocked", color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab system for layout hygiene
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Permissions & Monitor", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Config Icon") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("App Block Settings", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Warning, contentDescription = "Block Icon") }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (selectedTab == 0) {
                    // Safeguard Screen
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            HeaderSection(onOpenSettings = { showSettingsDialog = true })
                        }

                        item {
                            val isProtected = state.isAccessibilityGranted && state.isDeviceAdminGranted && state.isOverlayGranted
                            val blockedAppsCount = appItems.count { it.isBlocked }
                            val permissionsCount = (if (state.isAccessibilityGranted) 1 else 0) + 
                                                   (if (state.isDeviceAdminGranted) 1 else 0) + 
                                                   (if (state.isOverlayGranted) 1 else 0)
                            StatusCard(
                                isProtected = isProtected,
                                blockedCount = blockedAppsCount,
                                requiredPermissionsCount = permissionsCount,
                                onActivatePermissions = {
                                    if (!state.isAccessibilityGranted) {
                                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        context.startActivity(intent)
                                    } else if (!state.isDeviceAdminGranted) {
                                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                            putExtra(
                                                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                                ComponentName(context, DeviceAdminComponent::class.java)
                                            )
                                        }
                                        context.startActivity(intent)
                                    } else if (!state.isOverlayGranted) {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    }
                                }
                            )
                        }

                        item {
                            SafeSystemLogConsole(
                                isAccessibilityActive = state.isAccessibilityGranted,
                                isServiceRunning = isAccessibilityServiceConnected,
                                currentPackageName = currentApp
                            )
                        }

                        item {
                            Text(
                                text = "SYSTEM ACCESS POLICIES",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        item {
                            PermissionCard(
                                title = "1. Accessibility Service",
                                description = "Monitors app switches and window events to determine when restricted foreground applications are opened by children.",
                                isGranted = state.isAccessibilityGranted,
                                testTag = "accessibility_card",
                                onConfigure = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                }
                            )
                        }

                        item {
                            PermissionCard(
                                title = "2. Device Administrator",
                                description = "Secures parental profiles on device, requiring parent authentication and preventing children from unauthorized uninstallation.",
                                isGranted = state.isDeviceAdminGranted,
                                testTag = "device_admin_card",
                                onConfigure = {
                                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                        putExtra(
                                            DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                            ComponentName(context, DeviceAdminComponent::class.java)
                                        )
                                        putExtra(
                                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                            "Provides parental/system-level device controls to prevent unauthorized app uninstallation."
                                        )
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        }

                        item {
                            PermissionCard(
                                title = "3. System Display Overlay",
                                description = "Draws full-screen warning overlays over unauthorized apps and games, instantly restricting usage when time limits are reached.",
                                isGranted = state.isOverlayGranted,
                                testTag = "overlay_card",
                                onConfigure = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }
                            )
                        }

                        item {
                            OverlayTrialSection(
                                isOverlayGranted = state.isOverlayGranted,
                                onToggleOverlay = onToggleOverlay
                            )
                        }
                    }
                } else {
                    // App Selection Screen (ALWAYS accessible to enable blocking instantly!)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Settings lock notification banner
                        if (!isSettingsLocked) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Lock Open Icon",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Settings Safeguard Unlocked",
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            )
                                        }

                                        Button(
                                            onClick = { viewModel.initiateSettingsLock() },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.height(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "Lock settings",
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onError
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Lock Now", fontSize = 12.sp, color = MaterialTheme.colorScheme.onError)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "You are currently unlocked. Modify block rules freely. Click 'Lock Now' to re-engage safeguard armor.",
                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                    )
                                }
                            }
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "Lock Closed Icon",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Safeguard Shield Engaged",
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Instant Shielding Active. You can block any social media or application IMMEDIATELY with 0 delay. Turning off restrictions is protected by your self-control delay of $chosenDelay Min(s).",
                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                }
                            }
                        }

                        // Search box for installed apps
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search installed applications...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("app_search_field"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Text(
                            text = "SELECT APPS TO RESTRICT",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (isAppsLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            val filteredApps = appItems.filter {
                                it.appName.contains(searchQuery, ignoreCase = true) ||
                                        it.packageName.contains(searchQuery, ignoreCase = true)
                            }

                            if (filteredApps.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "No applications resolved",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Try typing another search keyword...",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .testTag("app_list"),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(filteredApps, key = { it.packageName }) { appInfo ->
                                        AppBlockingRow(
                                            appInfo = appInfo,
                                            onToggleBlock = { isBlocked ->
                                                if (isBlocked) {
                                                    // Turning ON a block is INSTANT with absolutely 0 delay
                                                    viewModel.toggleAppBlocked(appInfo.packageName, true)
                                                    Toast.makeText(context, "${appInfo.appName} blocked instantly!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    // Turning OFF a block is protected by countdown if locked!
                                                    if (!isSettingsLocked) {
                                                        viewModel.toggleAppBlocked(appInfo.packageName, false)
                                                        Toast.makeText(context, "${appInfo.appName} protection disabled.", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        blockToUnblock = appInfo
                                                        showUnlockPrompt = true
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppBlockingRow(
    appInfo: AppInfoItem,
    onToggleBlock: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val appIcon = remember(appInfo.packageName) {
        try {
            context.packageManager.getApplicationIcon(appInfo.packageName)
        } catch (e: Exception) {
            null
        }
    }

    val containerBg = if (appInfo.isBlocked) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    }
    val strokeColor = if (appInfo.isBlocked) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerBg, RoundedCornerShape(12.dp))
            .border(1.dp, strokeColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (appInfo.isBlocked) MaterialTheme.colorScheme.error.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (appIcon != null) {
                    coil.compose.AsyncImage(
                        model = appIcon,
                        contentDescription = "App Icon",
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Text(
                        text = appInfo.appName.take(2).uppercase(),
                        color = if (appInfo.isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = appInfo.appName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = appInfo.packageName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = appInfo.isBlocked,
            onCheckedChange = onToggleBlock,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onError,
                checkedTrackColor = MaterialTheme.colorScheme.error,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.testTag("switch_${appInfo.packageName}")
        )
    }
}

@Composable
fun SettingsLockedScreen(
    chosenDelay: Int,
    onSelectDelay: (Int) -> Unit,
    onInitiateUnlock: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        EmeraldCard(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked settings Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "CONFIGURATION SECURED",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Shield Lock Activated",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Your digital parents settings are fortified. To access restrictions customizations or unblock apps, you must initiate a patience training delay first.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onInitiateUnlock,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("START UNLOCK DELAY ($chosenDelay MINUTES)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CountdownDelayScreen(
    remainingMs: Long,
    chosenDelay: Int,
    onBypass: () -> Unit
) {
    val totalSeconds = remainingMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)

    val totalDurationMs = chosenDelay * 60 * 1000L
    val rawProgress = if (totalDurationMs > 0) remainingMs.toFloat() / totalDurationMs else 1f
    val progressFraction = rawProgress.coerceIn(0f, 1f)

    // Breathing Animation
    val infiniteTransition = rememberInfiniteTransition(label = "serene_breath")
    val scalePulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val quotes = remember {
        listOf(
            "Indeed, God is with those who patiently endure (Sabr)." to "Quran 2:153",
            "Patience is the beautiful key to tranquility." to "Islamic Wisdom",
            "The greatest struggle is the struggle against one's own desires." to "Hadith",
            "Patience is a solid pillar of faith." to "Ali ibn Abi Talib",
            "Between stimulus and response there is a space, where we choose our response." to "Viktor Frankl",
            "The strong controls himself in moments of urge." to "Hadith",
            "Do not ruin a tranquil moment with urgency; patience is a virtue of focus." to "Sabr Wisdom",
            "Calmness is the cradle of digital power." to "Josiah Gilbert Holland"
        )
    }

    var quoteIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(8000) // Quote changes every 8 seconds
            quoteIndex = (quoteIndex + 1) % quotes.size
        }
    }
    val currentQuoteItem = quotes[quoteIndex % quotes.size]

    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.widthIn(max = 480.dp)
        ) {
            Box(
                modifier = Modifier.size(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(190.dp)) {
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.08f),
                        radius = size.minDimension / 2f
                    )
                    
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.03f),
                        radius = (size.minDimension / 2f) * scalePulse
                    )
                }

                CircularProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier.size(170.dp),
                    color = primaryColor,
                    strokeWidth = 6.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Patience Dial Logo",
                        tint = primaryColor,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "SABR GRATIFICATION DELAY ENABLED",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
                color = primaryColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Cultivating Digital Patience",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            ReflectionCard(
                quote = currentQuoteItem.first,
                author = currentQuoteItem.second
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Observe this quiet window or step away. Your protection shield configuration unlocks automatically when the countdown completes.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = onBypass,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("dev_bypass_timer_btn")
            ) {
                Text("Developer Bypass (Skip Timer)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun HeaderSection(
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("header_section"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Shield Protection Icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Digital Parent Core",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Text(
                    text = "Emerald Safeguard Shield",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.testTag("onboarding_settings_gear_btn")
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Theme and Security Settings",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun SafeSystemLogConsole(
    isAccessibilityActive: Boolean,
    isServiceRunning: Boolean,
    currentPackageName: String
) {
    EmeraldCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Activity Feed",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Boundary Guardian Monitor",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Smooth monitor active green dot
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val pulse = rememberInfiniteTransition(label = "pulse_green")
                    val alphaScale by pulse.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pGreen"
                    )

                    val activeColor = Color(0xFF10B981)

                    Box(
                        modifier = Modifier
                            .graphicsLayer { alpha = alphaScale }
                            .size(8.dp)
                            .background(
                                color = if (isAccessibilityActive && isServiceRunning) activeColor else Color(0xFFEF4444),
                                shape = RoundedCornerShape(50)
                            )
                    )
                    Text(
                        text = if (isAccessibilityActive && isServiceRunning) "SHIELD ACTIVE" else "MONITOR OFFLINE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = if (isAccessibilityActive && isServiceRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(12.dp)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "EMERALD SAFEST WATCHER",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isAccessibilityActive && isServiceRunning) {
                            "The Emerald Guard engine is actively running real-time boundaries in the background to monitor digital wellness. Operating status is pristine."
                        } else {
                            "Please authorize 'Shield Guard (Accessibility)' permission to engage real-time self-control watch."
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    testTag: String,
    onConfigure: () -> Unit
) {
    val borderColor = if (isGranted) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
    val cardBackground = if (isGranted) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                PermissionStatusBadge(isGranted = isGranted)
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onConfigure,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGranted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                    contentColor = if (isGranted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("${testTag}_btn"),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                        contentDescription = "Action Icon",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isGranted) "Reconfigure Service Settings" else "Grant Access Permission",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Forward Icon",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionStatusBadge(isGranted: Boolean) {
    val badgeColor = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val label = if (isGranted) "Granted" else "Missing"
    val icon = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(badgeColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Status symbol",
            tint = badgeColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = badgeColor
        )
    }
}

@Composable
fun OverlayTrialSection(
    isOverlayGranted: Boolean,
    onToggleOverlay: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("overlay_test_section"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Lock overlay",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Lockout Overlay Trial",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Experience a live simulation of a digital self-control lock window drawing directly on top of your current application view.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onToggleOverlay,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("toggle_demo_overlay_btn"),
                enabled = isOverlayGranted
            ) {
                Text(
                    text = if (isOverlayGranted) "Launch / Dismiss Lock Trial Frame" else "Enable Overlay Permission to Try",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

// Custom reusable Design System components
@Composable
fun EmeraldCard(
    modifier: Modifier = Modifier,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        border = border,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = elevation,
        shape = shape,
        content = content
    )
}

@Composable
fun StatusCard(
    isProtected: Boolean,
    blockedCount: Int,
    requiredPermissionsCount: Int,
    onActivatePermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_infinity")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isProtected) {
                colorScheme.primaryContainer.copy(alpha = 0.15f)
            } else {
                colorScheme.errorContainer.copy(alpha = 0.12f)
            }
        ),
        border = BorderStroke(
            width = 1.5.dp,
            color = if (isProtected) colorScheme.primary else colorScheme.error.copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(90.dp)
            ) {
                Canvas(modifier = Modifier.size(90.dp)) {
                    drawCircle(
                        color = (if (isProtected) Color(0xFF0F5132) else Color(0xFFB02A37)).copy(alpha = pulseAlpha),
                        radius = (size.minDimension / 2f) * pulseScale
                    )
                }

                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            color = if (isProtected) colorScheme.primary else colorScheme.error,
                            shape = RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isProtected) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "Shield Status Indicator",
                        tint = if (isProtected) colorScheme.onPrimary else colorScheme.onError,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isProtected) "EMERALD SHIELD ENGAGED" else "ARMOR RESTING (ACTION REQUIRED)",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                ),
                color = if (isProtected) colorScheme.primary else colorScheme.error
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isProtected) {
                    "Your mindful boundary is active. Turn off app rules with delayed reward cooling."
                } else {
                    "Permissions are not fully authorized yet! Secure your device boundaries now."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$blockedCount",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = colorScheme.primary
                    )
                    Text(
                        text = "App Barriers",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$requiredPermissionsCount/3",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = if (requiredPermissionsCount == 3) colorScheme.primary else colorScheme.error
                    )
                    Text(
                        text = "Core Anchors",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            if (requiredPermissionsCount < 3) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onActivatePermissions,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.error,
                        contentColor = colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("AUTHORIZE ACTIVE PROTECT", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ProtectionToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = modifier
    )
}

@Composable
fun ReflectionCard(
    quote: String,
    author: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Mindful wisdom",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "“$quote”",
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 24.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    textAlign = TextAlign.Center
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "— $author",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Keep
@Composable
fun OnboardingSetupScreen(
    onComplete: (Int) -> Unit
) {
    var step by remember { mutableIntStateOf(1) }
    var selectedPurpose by remember { mutableStateOf<String?>(null) }
    var selectedDelay by remember { mutableIntStateOf(10) } // Default choice 10 Min

    val purposes = listOf(
        "Reduce Mindless Doomscrolling" to "🧘",
        "Maximize Deep Focus" to "⚡",
        "Cherish More Family Bond" to "🕌",
        "Cultivate Patient Sobr" to "🛡️"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (step) {
                1 -> { // Welcome Screen
                    EmeraldCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                            val pulseScale by infiniteTransition.animateFloat(
                                initialValue = 0.95f,
                                targetValue = 1.05f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200, easing = LinearOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "gPulse"
                            )
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .graphicsLayer {
                                        scaleX = pulseScale
                                        scaleY = pulseScale
                                    }
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        RoundedCornerShape(24.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Shield Guard Logo",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            Text(
                                text = "DIGITAL SERENITY SHIELD",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.5.sp
                                )
                            )

                            Text(
                                text = "Welcome to Emerald Safeguard",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "An Islamic-inspired mindful delay armor that nurtures patience (Sabr) by introducing a thoughtful waiting loop when modifying restrictive apps or boundaries. No more impulsive app usage.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { step = 2 },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text("COMMENCE EMERALD JOURNEY", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(Icons.Default.ArrowForward, contentDescription = "Next")
                                }
                            }
                        }
                    }
                }
                2 -> { // Purpose Selection Screen
                    EmeraldCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "STEP 2 OF 4: OUR INTENT",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.5.sp
                                )
                            )

                            Text(
                                text = "Why do you seek protection?",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Choose the core visual purpose which best guides your path toward digital focus and spiritual serenity.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            purposes.forEach { (label, emoji) ->
                                val isSelected = selectedPurpose == label
                                val cardBg = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                val borderStr = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(cardBg, RoundedCornerShape(14.dp))
                                        .border(borderStr, RoundedCornerShape(14.dp))
                                        .clickable { selectedPurpose = label }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "$emoji  ", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedPurpose = label },
                                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { step = 1 },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Back", fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { step = 3 },
                                    enabled = selectedPurpose != null,
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Continue", fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.Default.ArrowForward, contentDescription = "Next")
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> { // Delay Selection
                    EmeraldCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "STEP 3 OF 4: PATIENCE BUFFER",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.5.sp
                                )
                            )

                            Text(
                                text = "Choose Your Mindful Delay",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "To undo app barriers, you must wait through this cooling period. This rewires dopamine loops and promotes spiritual Sabr. Note: Choose wisely; this is a permanent setting.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            val options = listOf(
                                Triple(1, "1 Minute Delay", "⏱️"),
                                Triple(5, "5 Minutes Delay", "⏳"),
                                Triple(10, "10 Minutes Delay", "🛡️"),
                                Triple(60, "60 Minutes Delay", "🔋")
                            )

                            options.forEach { (mins, label, emoji) ->
                                val isSelected = selectedDelay == mins
                                val cardBg = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                val borderStr = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(cardBg, RoundedCornerShape(14.dp))
                                        .border(borderStr, RoundedCornerShape(14.dp))
                                        .clickable { selectedDelay = mins }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "$emoji  ", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedDelay = mins },
                                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { step = 2 },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Back", fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { step = 4 },
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Continue", fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.Default.ArrowForward, contentDescription = "Next")
                                    }
                                }
                            }
                        }
                    }
                }
                4 -> { // Protection Setup Summary
                    EmeraldCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Approved Summary Icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )

                            Text(
                                text = "COMPLETE SETUP SUMMARY",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.5.sp
                                )
                            )

                            Text(
                                text = "Establish Core Safeguards",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                textAlign = TextAlign.Center
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Delay Duration:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("$selectedDelay Minutes", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Visual Purpose:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(selectedPurpose ?: "None Selection", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Armor Shielding:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Armed Immediately", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Text(
                                text = "By clicking below, you acknowledge that delayed gratification is an act of spiritual discipline (Sabr). Turning off restrictions inside the settings will enforce this wait timer. There are no bypasses.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { step = 3 },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text("Back", fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { onComplete(selectedDelay) },
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .height(50.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("ACTIVATE EMERALD GUARD", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
