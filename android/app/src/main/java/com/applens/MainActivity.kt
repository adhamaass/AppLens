package com.applens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.applens.ui.*
import com.applens.data.ExtractionState
import com.applens.data.ExtractionPhase

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppLensApp()
                }
            }
        }
    }
}

@Composable
fun AppLensApp() {
    var phase by remember { mutableStateOf(ExtractionPhase.Onboarding) }
    val state by ExtractionState.state.collectAsState()

    when (phase) {
        ExtractionPhase.Onboarding -> {
            OnboardingScreen(onProceed = { phase = ExtractionPhase.Picking })
        }
        ExtractionPhase.Picking -> {
            AppPickerScreen(
                onAppSelected = { packageName ->
                    ExtractionState.startExtraction(packageName)
                    phase = ExtractionPhase.Extracting
                },
                onBack = { phase = ExtractionPhase.Onboarding }
            )
        }
        ExtractionPhase.Extracting -> {
            ProgressScreen(
                state = state,
                onComplete = { phase = ExtractionPhase.Done },
                onBack = { phase = ExtractionPhase.Picking }
            )
        }
        ExtractionPhase.Done -> {
            DoneScreen(
                state = state,
                onRestart = {
                    ExtractionState.reset()
                    phase = ExtractionPhase.Picking
                }
            )
        }
    }
}
