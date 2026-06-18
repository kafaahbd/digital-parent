package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme

class BlockerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val blockedTarget = intent.getStringExtra("blocked_app_package") ?: "Unknown Target"
        val blockType = intent.getStringExtra("block_type") ?: "APP"

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize().testTag("blocker_scaffold")
                ) { innerPadding ->
                    // Set up back press behavior to force returning home instead of bypassing
                    BackHandler {
                        returnToHomeScreen()
                    }

                    BlockerScreen(
                        target = blockedTarget,
                        blockType = blockType,
                        paddingValues = innerPadding,
                        onGoHome = { returnToHomeScreen() }
                    )
                }
            }
        }
    }

    private fun returnToHomeScreen() {
        val startMain = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(startMain)
        finish()
    }
}

@Composable
fun BlockerScreen(
    target: String,
    blockType: String,
    paddingValues: PaddingValues,
    onGoHome: () -> Unit
) {
    val isWebsite = blockType == "WEBSITE"

    // Unified list of quotes
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

    // Remember a random quote so it's stable per recomposition
    val selectedQuote = remember(target) {
        if (isWebsite) islamicQuotes.random() else appQuotes.random()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(
                Brush.verticalGradient(
                    colors = if (isWebsite) {
                        listOf(
                            Color(0xFF14532D), // Deep Islamic/Emerald Green
                            Color(0xFF0F172A)  // Slate 900
                        )
                    } else {
                        listOf(
                            Color(0xFF450A0A), // Extremely Dark Cherry/Red Red 950
                            Color(0xFF0F172A)  // Slate 900
                        )
                    }
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Elegant pulsing shield lock / mosque logo
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        if (isWebsite) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFFEF4444).copy(alpha = 0.12f),
                        RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Shield Lock Icon",
                    tint = if (isWebsite) Color(0xFF34D399) else Color(0xFFF87171), // Soft Emerald or Soft Red
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = if (isWebsite) "WEBSITE ACCESS RESTRICTED" else "APPLICATION ACCESS RESTRICTED",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = if (isWebsite) Color(0xFF34D399) else Color(0xFFF87171)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isWebsite) "Divine Guard Active" else "Digital Guard Active",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White.copy(alpha = 0.04f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isWebsite) "Restricted Target Website:" else "Restricted Target App:",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8), // Slate 400
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = target,
                        fontSize = 14.sp,
                        color = Color(0xFFF1F5F9), // Slate 100
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Psychology / Spiritual & Mindfulness card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isWebsite) "GUIDE TO PURITY" else "BREATHE & DISCONNECT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = if (isWebsite) Color(0xFF10B981) else Color(0xFF38BDF8) // Emerald or Soft Blue
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = selectedQuote,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 22.sp
                        ),
                        color = Color(0xFFE2E8F0),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onGoHome,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isWebsite) Color(0xFF10B981) else Color(0xFFEF4444), // Emerald or Crimson
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("exit_blocker_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Home Icon"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isWebsite) "Return to Purity" else "Exit to Device Home Screen",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
