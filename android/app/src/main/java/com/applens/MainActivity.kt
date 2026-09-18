package com.applens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.applens.data.ExtractionStateData
import com.applens.ui.AppPickerScreen
import com.applens.ui.DoneScreen
import com.applens.ui.OnboardingScreen
import com.applens.ui.ProgressScreen

enum class Screen {
    ONBOARDING,
    PICKER,
    PROGRESS,
    DONE
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf(Screen.ONBOARDING) }
                    var extractionState by remember { mutableStateOf(ExtractionStateData()) }

                    when (currentScreen) {
                        Screen.ONBOARDING -> {
                            OnboardingScreen(
                                onProceed = { currentScreen = Screen.PICKER }
                            )
                        }
                        Screen.PICKER -> {
                            AppPickerScreen(
                                onAppSelected = { pkg ->
                                    extractionState = extractionState.copy(currentPackage = pkg)
                                    currentScreen = Screen.PROGRESS
                                },
                                onBack = { currentScreen = Screen.ONBOARDING }
                            )
                        }
                        Screen.PROGRESS -> {
                            ProgressScreen(
                                state = extractionState,
                                onComplete = { currentScreen = Screen.DONE },
                                onBack = { currentScreen = Screen.PICKER }
                            )
                        }
                        Screen.DONE -> {
                            DoneScreen(
                                state = extractionState,
                                onRestart = { currentScreen = Screen.PICKER }
                            )
                        }
                    }
                }
            }
        }
    }
}
