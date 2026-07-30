package com.applens.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.applens.data.ExtractionStateData
import com.applens.data.ExtractionStatus
import com.applens.data.LogEntry
import com.applens.data.LogLevel
import com.applens.engine.ExtractionEngine
import kotlinx.coroutines.launch

@Composable
fun ProgressScreen(
    state: ExtractionStateData,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll to bottom on new logs
    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) {
            listState.animateScrollToItem(state.logs.size - 1)
        }
    }

    // Check if done
    LaunchedEffect(state.status) {
        if (state.status == ExtractionStatus.Done || state.status == ExtractionStatus.Error) {
            delay(500)
            onComplete()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                ExtractionEngine.getInstance(androidx.compose.ui.platform.LocalContext.current).stopExtraction()
                onBack()
            }) { Text("Cancel") }

            Text(
                "Extracting...",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.width(56.dp))
        }

        // Stats card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(state.currentApp, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    state.currentPackage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatItem("Screens Found", state.screensFound.toString())
                    StatItem("Max Screens", state.maxScreens.toString())
                    StatItem("Depth", "${state.currentDepth}/${state.maxDepth}")
                }
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Current: ${state.currentActivity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Progress bar
                val progress = if (state.maxScreens > 0) {
                    state.screensFound.toFloat() / state.maxScreens.toFloat()
                } else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )

                // Status text
                Text(
                    when (state.status) {
                        ExtractionStatus.Running -> "Traversing UI..."
                        ExtractionStatus.Uploading -> "Uploading to backend..."
                        ExtractionStatus.Done -> "Complete!"
                        ExtractionStatus.Error -> "Error occurred"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Log viewer
        Text(
            "Live Log",
            modifier = Modifier.padding(horizontal = 16.dp),
            fontWeight = FontWeight.Bold
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(state.logs) { log ->
                LogRow(log)
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LogRow(log: LogEntry) {
    val color = when (log.level) {
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
        LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            "[${log.timestamp.takeLast(6)}] ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace
        )
        Text(
            log.message,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontFamily = FontFamily.Monospace
        )
    }
}
