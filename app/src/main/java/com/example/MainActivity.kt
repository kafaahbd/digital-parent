package com.example

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

class PermissionsViewModel : androidx.lifecycle.ViewModel() {
    private val _permissionsState = MutableStateFlow(PermissionsState())
    val permissionsState: StateFlow<PermissionsState> = _permissionsState.asStateFlow()

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
}

data class PermissionsState(
    val isAccessibilityGranted: Boolean = false,
    val isDeviceAdminGranted: Boolean = false,
    val isOverlayGranted: Boolean = false
)

@Composable
fun PermissionDashboardScreen(
    onToggleOverlay: () -> Unit,
    viewModel: PermissionsViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.permissionsState.collectAsStateWithLifecycle()
    
    val currentApp by DigitalParentAccessibilityService.foregroundApp.collectAsStateWithLifecycle()
    val isAccessibilityServiceConnected by DigitalParentAccessibilityService.isServiceRunning.collectAsStateWithLifecycle()

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
        viewModel.checkPermissions(context)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp),
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
                color = Color(0xFF64748B), // Slate 500
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
                text = "Phase 1: Foundations & Permissions",
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
        border = CardStroke(Color(0xFF334155)) // Slate 700
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
        border = CardStroke(borderColor)
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
        border = CardStroke(Color(0xFF312E81)) // Indigo 800
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

// Inline helper constructor to support custom CardStroke borders inside compose Material 3
private fun CardStroke(color: Color): androidx.compose.foundation.BorderStroke? {
    return androidx.compose.foundation.BorderStroke(1.dp, color)
}
