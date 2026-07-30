package com.applens.ui

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.applens.util.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val name: String,
    val packageName: String,
    val icon: Drawable?
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppPickerScreen(
    onAppSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchText by remember { mutableStateOf("") }
    var backendIp by remember { mutableStateOf("192.168.1.100") }
    var showIpDialog by remember { mutableStateOf(true) }

    // Load installed apps
    LaunchedEffect(Unit) {
        loading = true
        apps = withContext(Dispatchers.IO) {
            loadInstalledApps(context)
        }
        loading = false
    }

    // IP entry dialog first
    if (showIpDialog) {
        AlertDialog(
            onDismissRequest = { showIpDialog = false },
            title = { Text("Backend IP Address") },
            text = {
                Column {
                    Text("Enter your PC's IP address (running the Node.js backend)")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = backendIp,
                        onValueChange = { backendIp = it },
                        label = { Text("IP:Port") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    com.applens.engine.ExtractionEngine.getInstance(context).setBackendIp(backendIp)
                    showIpDialog = false
                }) { Text("OK") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Select an App", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        // Search bar
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            label = { Text("Search apps...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Loading installed apps...")
                }
            }
        } else {
            val filtered = apps.filter {
                it.name.contains(searchText, ignoreCase = true) ||
                it.packageName.contains(searchText, ignoreCase = true)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filtered) { app ->
                    AppRow(app = app, onClick = { onAppSelected(app.packageName) })
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App icon
        if (app.icon != null) {
            val bitmap = android.graphics.Bitmap.createBitmap(
                app.icon.intrinsicWidth.coerceAtLeast(1),
                app.icon.intrinsicHeight.coerceAtLeast(1),
                android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            app.icon.setBounds(0, 0, canvas.width, canvas.height)
            app.icon.draw(canvas)
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = app.name,
                modifier = Modifier.size(40.dp)
            )
        } else {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Text("?")
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(app.name, fontWeight = FontWeight.Medium)
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HorizontalDivider()
}

private fun loadInstalledApps(context: android.content.Context): List<InstalledApp> {
    val pm = context.packageManager
    val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
    return packages
        .filter { it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 } // User-installed only
        .sortedBy {
            try { pm.getApplicationLabel(it).toString() } catch (e: Exception) { it.packageName }
        }
        .map {
            InstalledApp(
                name = try { pm.getApplicationLabel(it).toString() } catch (e: Exception) { it.packageName },
                packageName = it.packageName,
                icon = try { pm.getApplicationIcon(it) } catch (e: Exception) { null }
            )
        }
}
