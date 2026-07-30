package com.applens.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ExtractionPhase { Onboarding, Picking, Extracting, Done }

enum class ExtractionStatus { Idle, Running, Uploading, Done, Error }

data class LogEntry(
    val timestamp: String,
    val message: String,
    val level: LogLevel = LogLevel.INFO
)

enum class LogLevel { INFO, WARN, ERROR }

data class ScreenInfo(
    val id: String,
    val activityName: String,
    val xml: String,
    val depth: Int,
    val clickCount: Int
)

data class AppMetadata(
    val appName: String,
    val packageName: String,
    val version: String,
    val permissions: List<PermissionInfo> = emptyList(),
    val activities: List<String> = emptyList(),
    val services: List<String> = emptyList(),
    val receivers: List<String> = emptyList(),
    val providers: List<String> = emptyList(),
    val sdkInfo: SdkInfo = SdkInfo(),
    val apkPath: String = "",
    val installDate: Long = 0L,
    val lastUpdateDate: Long = 0L
)

data class PermissionInfo(
    val name: String,
    val granted: Boolean,
    val dangerous: Boolean
)

data class SdkInfo(
    val minSdk: Int = 0,
    val targetSdk: Int = 0,
    val compileSdk: Int = 0
)

data class ExtractionStateData(
    val status: ExtractionStatus = ExtractionStatus.Idle,
    val currentApp: String = "",
    val currentPackage: String = "",
    val currentActivity: String = "",
    val screensFound: Int = 0,
    val currentDepth: Int = 0,
    val maxDepth: Int = 6,
    val maxScreens: Int = 60,
    val logs: List<LogEntry> = emptyList(),
    val screens: List<ScreenInfo> = emptyList(),
    val metadata: AppMetadata? = null,
    val zipUrl: String? = null,
    val error: String? = null
)

object ExtractionState {
    private val _state = MutableStateFlow(ExtractionStateData())
    val state: StateFlow<ExtractionStateData> = _state.asStateFlow()

    fun update(block: (ExtractionStateData) -> ExtractionStateData) {
        _state.value = block(_state.value)
    }

    fun addLog(message: String, level: LogLevel = LogLevel.INFO) {
        update { it.copy(logs = it.logs + LogEntry(System.currentTimeMillis().toString(), message, level)) }
    }

    fun startExtraction(packageName: String) {
        _state.value = ExtractionStateData(
            status = ExtractionStatus.Running,
            currentPackage = packageName,
            maxDepth = 6,
            maxScreens = 60
        )
    }

    fun reset() {
        _state.value = ExtractionStateData()
    }
}
