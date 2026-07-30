package com.applens.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.applens.data.LogLevel
import com.applens.data.ExtractionState
import com.applens.util.ShizukuManager

@Composable
fun OnboardingScreen(
    onProceed: () -> Unit
) {
    var shizukuRunning by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }
    var backendIp by remember { mutableStateOf("192.168.1.100") }
    var accessibilityEnabled by remember { mutableStateOf(false) }
    var checkTrigger by remember { mutableStateOf(0) }

    // Check status
    LaunchedEffect(checkTrigger) {
        shizukuRunning = ShizukuManager.isShizukuRunning()
        hasPermission = if (shizukuRunning) ShizukuManager.checkPermission() else false
        accessibilityEnabled = com.applens.service.AppLensAccessibilityService.isRunning()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "AppLens",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Extract any app's full UI structure",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Shizuku status card
        StatusCard(
            title = "Shizuku Service",
            isOk = shizukuRunning,
            okText = "Running",
            failText = "Not running"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Permission status
        StatusCard(
            title = "Shizuku Permission",
            isOk = hasPermission,
            okText = "Granted",
            failText = "Not granted"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Accessibility status
        StatusCard(
            title = "Accessibility Service",
            isOk = accessibilityEnabled,
            okText = "Enabled",
            failText = "Not enabled"
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (!shizukuRunning) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Setup Instructions", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("1. Enable Developer Options")
                    Text("2. Enable Wireless Debugging")
                    Text("3. Start Shizuku via ADB or Wireless Debugging")
                    Text("4. Grant AppLens permission in Shizuku")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Get Shizuku: https://shizuku.rikka.app")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (!hasPermission && shizukuRunning) {
            Button(
                onClick = {
                    ShizukuManager.requestPermission()
                    checkTrigger++
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Shizuku Permission")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (!accessibilityEnabled) {
            Button(
                onClick = {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    android.app.Activity::class.java
                    // Open accessibility settings
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    ctx.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enable Accessibility Service")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Backend IP input
        OutlinedTextField(
            value = backendIp,
            onValueChange = { backendIp = it },
            label = { Text("Backend IP Address") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Text(
            text = "Your PC running the Node.js backend (same WiFi)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                com.applens.engine.ExtractionEngine.getInstance(
                    androidx.compose.ui.platform.LocalContext.current
                ).setBackendIp(backendIp)
                onProceed()
            },
            enabled = shizukuRunning && hasPermission && accessibilityEnabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Proceed to App Picker")
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = { checkTrigger++ }) {
            Text("Re-check Status")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun StatusCard(
    title: String,
    isOk: Boolean,
    okText: String,
    failText: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isOk) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                text = if (isOk) okText else failText,
                fontWeight = FontWeight.Bold,
                color = if (isOk) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
        }
    }
}
