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
import com.applens.data.ExtractionStateData
import com.applens.data.ExtractionStatus
import java.io.File

@Composable
fun DoneScreen(
    state: ExtractionStateData,
    onRestart: () -> Unit
) {
    val scrollState = rememberScrollState()
    val isError = state.status == ExtractionStatus.Error
    var zipExists by remember { mutableStateOf(false) }
    var zipSize by remember { mutableStateOf("") }

    LaunchedEffect(state.zipUrl) {
        state.zipUrl?.let { path ->
            val file = File(path)
            zipExists = file.exists()
            if (zipExists) {
                val bytes = file.length()
                zipSize = when {
                    bytes > 1048576 -> "%.1f MB".format(bytes / 1048576.0)
                    bytes > 1024 -> "%.1f KB".format(bytes / 1024.0)
                    else -> "$bytes bytes"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        if (isError) {
            Text("Extraction Failed", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
            state.error?.let {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(it, modifier = Modifier.padding(16.dp), fontFamily = FontFamily.Monospace)
                }
            }
        } else {
            Text("Extraction Complete", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))

            // Summary card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Summary", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))

                    SummaryRow("App", state.currentApp)
                    SummaryRow("Package", state.currentPackage)
                    state.metadata?.let { SummaryRow("Version", it.version) }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    SummaryRow("Screens Extracted", state.screensFound.toString())
                    state.metadata?.let {
                        SummaryRow("Activities", it.activities.size.toString())
                        SummaryRow("Services", it.services.size.toString())
                        SummaryRow("Receivers", it.receivers.size.toString())
                        SummaryRow("Providers", it.providers.size.toString())
                        SummaryRow("Permissions", it.permissions.size.toString())
                        SummaryRow("Target SDK", it.sdkInfo.targetSdk.toString())
                        SummaryRow("Min SDK", it.sdkInfo.minSdk.toString())
                    }

                    if (zipExists) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SummaryRow("ZIP File Size", zipSize)
                        SummaryRow("ZIP Location", state.zipUrl ?: "N/A")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (zipExists) {
                Button(
                    onClick = {
                        // Share the ZIP file
                        state.zipUrl?.let { path ->
                            val file = File(path)
                            val ctx = androidx.compose.ui.platform.LocalContext.current
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
                            intent.type = "application/zip"
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                ctx,
                                "${ctx.packageName}.fileprovider",
                                file
                            )
                            intent.putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            ctx.startActivity(android.content.Intent.createChooser(intent, "Share ZIP"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Share ZIP File")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(if (isError) "Try Again" else "Extract Another App")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
    }
}
