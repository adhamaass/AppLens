package com.applens.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.applens.service.AppLensAccessibilityService
import com.applens.util.ShizukuManager

@Composable
fun OnboardingScreen(
    onProceed: () -> Unit
) {
    var shizukuRunning by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }
    var accessibilityEnabled by remember { mutableStateOf(false) }
    var checkTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(checkTrigger) {
        shizukuRunning = ShizukuManager.isShizukuRunning()
        hasPermission = if (shizukuRunning) ShizukuManager.checkPermission() else false
        accessibilityEnabled = AppLensAccessibilityService.isRunning()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Text("AppLens", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "Extract any app full UI structure on-device",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        StatusCard("Shizuku Service", shizukuRunning, "Running", "Not running")
        Spacer(modifier = Modifier.height(12.dp))
        StatusCard("Shizuku Permission", hasPermission, "Granted", "Not granted")
        Spacer(modifier = Modifier.height(12.dp))
        StatusCard("Accessibility Service", accessibilityEnabled, "Enabled", "Not enabled")

        Spacer(modifier = Modifier.height(24.dp))

        if (!shizukuRunning) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Setup Instructions", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("1. Install Shizuku from Play Store or GitHub")
                    Text("2. Enable Developer Options on your phone")
                    Text("3. Enable Wireless Debugging")
                    Text("4. Start Shizuku via Wireless Debugging")
                    Text("5. Grant AppLens permission in Shizuku")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Get Shizuku: https://shizuku.rikka.app")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (!hasPermission && shizukuRunning) {
            Button(
                onClick = { ShizukuManager.requestPermission(); checkTrigger++ },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Grant Shizuku Permission") }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (!accessibilityEnabled) {
            val context = LocalContext.current
            Button(
                onClick = {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Enable Accessibility Service") }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = onProceed,
            enabled = shizukuRunning && hasPermission && accessibilityEnabled,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Proceed to App Picker") }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = { checkTrigger++ }) { Text("Re-check Status") }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun StatusCard(title: String, isOk: Boolean, okText: String, failText: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isOk) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                if (isOk) okText else failText,
                fontWeight = FontWeight.Bold,
                color = if (isOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}
