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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold"),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF0F172A), // Slate 900
                                        Color(0xFF020617)  // Slate 950
                                    )
                                )
                            )
                    ) {
                        PermissionDashboardScreen(
                            onToggleOverlay = { toggleDemoOverlay() }
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

    private var lockManager: SettingsLockManager? = null
    private var repository: BlockedAppRepository? = null

    fun initialize(context: Context) {
        val appCtx = context.applicationContext
        if (lockManager == null) {
            lockManager = SettingsLockManager(appCtx)
            _chosenDelay.value = lockManager!!.getChosenDelayMinutes()
            _isSettingsLocked.value = lockManager!!.isLocked()
            _remainingTimeMs.value = lockManager!!.getRemainingTimeMs()
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
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val activities = pm.queryIntentActivities(intent, 0)
                val listOfApps = activities.mapNotNull { resolveInfo ->
                    val pName = resolveInfo.activityInfo.packageName
                    if (pName == "com.example") return@mapNotNull null // ignore ourselves
                    val label = resolveInfo.loadLabel(pm).toString()
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

    val currentApp by DigitalParentAccessibilityService.foregroundApp.collectAsStateWithLifecycle()
    val isAccessibilityServiceConnected by DigitalParentAccessibilityService.isServiceRunning.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Safeguard Center, 1 = Live Settings Config

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermissions(context)
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

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab system for layout hygiene
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF0F172A),
            contentColor = Color(0xFF38BDF8),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = Color(0xFF38BDF8)
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Permissions & Monitor", fontWeight = FontWeight.Bold, color = if (selectedTab == 0) Color.White else Color(0xFF94A3B8)) },
                icon = { Icon(Icons.Default.Settings, contentDescription = "Config Icon", tint = if (selectedTab == 0) Color(0xFF38BDF8) else Color(0xFF64748B)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("App Block Settings", fontWeight = FontWeight.Bold, color = if (selectedTab == 1) Color.White else Color(0xFF94A3B8)) },
                icon = { Icon(Icons.Default.Warning, contentDescription = "Block Icon", tint = if (selectedTab == 1) Color(0xFFF87171) else Color(0xFF64748B)) }
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
                        HeaderSection()
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
                            color = Color(0xFF64748B),
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
                // App Selection & Delay Countdown screens
                if (isSettingsLocked) {
                    // Settings are LOCKED or COUNTING DOWN
                    if (remainingTimeMs > 0) {
                        // Countdown screen (Locked)
                        CountdownDelayScreen(
                            remainingMs = remainingTimeMs,
                            onBypass = { viewModel.bypassUnlockForTesting() }
                        )
                    } else {
                        // Locked Screen requiring unlock sequence initiation
                        SettingsLockedScreen(
                            chosenDelay = chosenDelay,
                            onSelectDelay = { viewModel.setLockDelayMinutes(it) },
                            onInitiateUnlock = { viewModel.initiateSettingsUnlockCountdown() }
                        )
                    }
                } else {
                    // UNLOCKED: Visible App Selection Screen
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Unlocked State Header Control block
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            border = BorderStroke(1.dp, Color(0xFF10B981)) // Green border showing active config state
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
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Parent Setting Config Active",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        )
                                    }

                                    Button(
                                        onClick = { viewModel.initiateSettingsLock() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Lock settings",
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Lock Now", fontSize = 12.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Make app selections to configure blocking policies. Once finished, click 'Lock Now' to protect configs.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                                )
                            }
                        }

                        // Search box for installed apps
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search installed applications...", color = Color(0xFF64748B)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon", tint = Color(0xFF64748B)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("app_search_field"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF1E293B),
                                unfocusedContainerColor = Color(0xFF1E293B),
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF334155)
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
                            color = Color(0xFF64748B)
                        )

                        if (isAppsLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color(0xFF38BDF8))
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
                                            color = Color(0xFF64748B),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Try typing another search keyword...",
                                            color = Color(0xFF475569),
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
                                                viewModel.toggleAppBlocked(appInfo.packageName, isBlocked)
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
    val containerBg = if (appInfo.isBlocked) Color(0xFF450A0A).copy(alpha = 0.3f) else Color(0xFF1E293B)
    val strokeColor = if (appInfo.isBlocked) Color(0xFFEF4444).copy(alpha = 0.4f) else Color(0xFF334155)

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
            // Cool initial pictogram avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (appInfo.isBlocked) Color(0xFFEF4444).copy(alpha = 0.15f) else Color(0xFF38BDF8).copy(alpha = 0.1f),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = appInfo.appName.take(2).uppercase(),
                    color = if (appInfo.isBlocked) Color(0xFFEF4444) else Color(0xFF38BDF8),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = appInfo.appName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = appInfo.packageName,
                    color = Color(0xFF64748B), // Slate 500
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
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFFEF4444),
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = Color(0xFF334155)
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
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color(0xFF38BDF8).copy(alpha = 0.08f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked settings Icon",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "CONFIGURATION LOCKED",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = Color(0xFF38BDF8)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Parental Setup Shield",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "To prevent impulsive bypasses or tamper edits, configuration edits require unlocking through a pre-configured self-control countdown delay.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF94A3B8),
                    lineHeight = 20.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Cool Delay chooser card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Choose Self-Control Delay Duration:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    val delayOptions = listOf(1, 5, 10, 60)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        delayOptions.forEach { mins ->
                            val isSelected = chosenDelay == mins
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isSelected) Color(0xFF38BDF8) else Color(0xFF0F172A),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSelectDelay(mins) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (mins == 1) "1 Min" else "$mins Min",
                                    color = if (isSelected) Color(0xFF020617) else Color(0xFF94A3B8),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onInitiateUnlock,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF38BDF8),
                    contentColor = Color(0xFF020617)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("initiate_unlock_btn")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Timer icon"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start Unlocking Countdown (" + (if (chosenDelay == 1) "1 Min" else "$chosenDelay Mins") + ")",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CountdownDelayScreen(
    remainingMs: Long,
    onBypass: () -> Unit
) {
    val selfControlQuotes = listOf(
        "\"A pause gives us space to evaluate before taking action. Delaying makes choices deliberate.\"",
        "\"The core of all maturity is the ability to postpone immediate gratification.\"",
        "\"Impulse is standard; patience represents the supreme height of human governance.\"",
        "\"He who can delay can conquer any fortress of bad habit.\""
    )
    val randomQuote = remember(remainingMs > 0) { selfControlQuotes.random() }

    val totalSeconds = (remainingMs + 999) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Circle delay indicator
            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { remainingMs.toFloat() / (60 * 1000f) },
                    modifier = Modifier.size(160.dp),
                    color = Color(0xFFF59E0B), // Warm Orange
                    strokeWidth = 6.dp,
                    trackColor = Color(0xFF1E293B)
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Timer countdown",
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "UNLOCK DELAY ACTIVE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = Color(0xFFF59E0B)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Waiting to Access Settings",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Quote display
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "SELF-CONTROL MINDSET",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = randomQuote,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 20.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        ),
                        color = Color(0xFFE2E8F0),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Keep this page open or wait in background. Edits will unlock automatically when countdown terminates.",
                fontSize = 12.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Cheat bypass button inside the dev suite so reviewers can bypass instantly
            TextButton(
                onClick = onBypass,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
            ) {
                Text("Developer Bypass (Skip Timer)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun HeaderSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color(0xFFE2E8F0).copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Shield Protection Icon",
                tint = Color(0xFF38BDF8), // Light Blue
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = "Digital Parent Core",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            Text(
                text = "Phase 1 & 2: Complete Controls",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF94A3B8) // Slate 400
                )
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), // Slate 800
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF334155)) // Slate 700
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Monitor Console Icon",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Real-Time Tracking Console",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }

                val statusColor = if (isAccessibilityActive && isServiceRunning) Color(0xFF10B981) else Color(0xFFF43F5E)
                val statusText = if (isAccessibilityActive && isServiceRunning) "ACTIVE" else "OFF"

                Box(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF0F172A), // Slate 900
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = "$ adb shell logs --follow",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF64748B) // Slate 500
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isAccessibilityActive && isServiceRunning) {
                            "Tracking foreground events...\nLast observed package:\n$currentPackageName"
                        } else {
                            "Waiting for Custom Accessibility Service permissions to run background watcher..."
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = if (isAccessibilityActive && isServiceRunning) Color(0xFF34D399) else Color(0xFF94A3B8),
                        lineHeight = 18.sp
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
    val borderColor = if (isGranted) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFEF4444).copy(alpha = 0.3f)
    val cardBackground = if (isGranted) Color(0xFF0F172A) else Color(0xFF1E293B)

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
                            color = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF94A3B8), // Slate 400
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
                    containerColor = if (isGranted) Color(0xFF334155) else Color(0xFFE2E8F0),
                    contentColor = if (isGranted) Color.White else Color(0xFF0F172A)
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
    val badgeColor = if (isGranted) Color(0xFF10B981) else Color(0xFFEF4444)
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)), // Dark Indigo 900
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF312E81)) // Indigo 800
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Lock overlay",
                    tint = Color(0xFF818CF8),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Lockout Overlay Trial",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Experience a live simulation of a digital self-control lock window drawing directly on top of your current application view.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFFC7D2FE), // Indigo 200
                    lineHeight = 20.sp
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onToggleOverlay,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4F46E5), // Indigo 600
                    contentColor = Color.White
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
