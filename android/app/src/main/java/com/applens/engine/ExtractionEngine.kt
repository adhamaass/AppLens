package com.applens.engine

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import com.applens.data.*
import com.applens.processor.ZipBuilder
import com.applens.service.AppLensAccessibilityService
import com.applens.util.ShizukuManager
import kotlinx.coroutines.*
import java.io.File
import java.security.MessageDigest

class ExtractionEngine private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: ExtractionEngine? = null

        fun getInstance(context: Context): ExtractionEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ExtractionEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    var isExtractionActive = false
        private set

    var targetPackage: String = ""
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var extractionJob: Job? = null

    private val visitedScreens = mutableSetOf<String>()
    private val allScreens = mutableListOf<ScreenInfo>()
    private var screenCounter = 0

    fun startExtraction(packageName: String) {
        if (isExtractionActive) return
        targetPackage = packageName
        visitedScreens.clear()
        allScreens.clear()
        screenCounter = 0

        extractionJob = scope.launch {
            try {
                ExtractionState.update {
                    it.copy(
                        status = ExtractionStatus.Running,
                        currentPackage = packageName,
                        currentApp = getAppName(packageName)
                    )
                }
                ExtractionState.addLog("Starting extraction for $packageName")

                // Force stop and relaunch via Android Intent + Shizuku
                ShizukuManager.forceStopApp(packageName)
                delay(800)
                try {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        context.startActivity(launchIntent)
                    } else {
                        ShizukuManager.launchApp(packageName)
                    }
                } catch (e: Exception) {
                    ShizukuManager.launchApp(packageName)
                }

                // Wait until the target package is actually in the foreground
                var waitCount = 0
                while (!ShizukuManager.getCurrentActivity().contains(packageName) && waitCount < 10) {
                    delay(500)
                    waitCount++
                }
                ExtractionState.addLog("App launched into foreground: $packageName")

                // Collect metadata first
                ExtractionState.addLog("Collecting app metadata...")
                val metadata = collectMetadata(packageName)
                ExtractionState.update { it.copy(metadata = metadata) }
                ExtractionState.addLog("Metadata collected: ${metadata.activities.size} activities, ${metadata.permissions.size} permissions")

                // Start BFS traversal
                isExtractionActive = true
                bfsTraversal(packageName)

                // Process on-device and build ZIP
                isExtractionActive = false
                ExtractionState.update { it.copy(status = ExtractionStatus.Uploading) }
                ExtractionState.addLog("Processing ${allScreens.size} screens on-device...")

                // Save ZIP to Downloads
                val outputDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Use app-specific external storage on Android 10+
                    File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "AppLens")
                } else {
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AppLens")
                }
                outputDir.mkdirs()

                ExtractionState.addLog("Generating wireframes, components, navigation graph...")
                val zipFile = ZipBuilder.buildZip(
                    outputDir = outputDir,
                    appName = metadata.appName,
                    packageName = metadata.packageName,
                    version = metadata.version,
                    metadata = metadata,
                    screens = allScreens.toList()
                )

                ExtractionState.update {
                    it.copy(
                        status = ExtractionStatus.Done,
                        zipUrl = zipFile.absolutePath,
                        screens = allScreens.toList()
                    )
                }
                ExtractionState.addLog("Done! ZIP saved to: ${zipFile.absolutePath}")
                ExtractionState.addLog("File size: ${formatFileSize(zipFile.length())}")

            } catch (e: CancellationException) {
                ExtractionState.addLog("Extraction cancelled", LogLevel.WARN)
            } catch (e: Exception) {
                ExtractionState.addLog("Extraction error: ${e.message}", LogLevel.ERROR)
                ExtractionState.update {
                    it.copy(status = ExtractionStatus.Error, error = e.message)
                }
            } finally {
                isExtractionActive = false
            }
        }
    }

    fun stopExtraction() {
        isExtractionActive = false
        extractionJob?.cancel()
        ExtractionState.addLog("Extraction stopped by user", LogLevel.WARN)
    }

    /**
     * BFS traversal of all screens in the target app.
     */
    private suspend fun bfsTraversal(packageName: String) {
        val maxDepth = ExtractionState.state.value.maxDepth
        val maxScreens = ExtractionState.state.value.maxScreens

        // Process the first (current) screen
        processCurrentScreen(packageName, depth = 0)

        // After processing the initial screen, the recursive call in processCurrentScreen
        // handles exploring clickables at each depth level.
    }

    /**
     * Process the current screen: dump XML, find clickables, and recursively explore.
     */
    private suspend fun processCurrentScreen(packageName: String, depth: Int) {
        val maxDepth = ExtractionState.state.value.maxDepth
        val maxScreens = ExtractionState.state.value.maxScreens

        if (depth > maxDepth || allScreens.size >= maxScreens) return
        if (extractionJob?.isActive != true) return

        var service = AppLensAccessibilityService.instance
        var retryCount = 0
        var rootNode = service?.getRootNode()
        var currentAct = ShizukuManager.getCurrentActivity()

        // Wait until foreground activity belongs to the target package
        while (!currentAct.contains(packageName) && retryCount < 10 && extractionJob?.isActive == true) {
            delay(800)
            ShizukuManager.launchApp(packageName)
            service = AppLensAccessibilityService.instance
            rootNode = service?.getRootNode()
            currentAct = ShizukuManager.getCurrentActivity()
            retryCount++
        }

        // Wait up to 5 seconds for service and window to be ready
        while ((service == null || rootNode == null) && retryCount < 10 && extractionJob?.isActive == true) {
            delay(500)
            service = AppLensAccessibilityService.instance
            rootNode = service?.getRootNode()
            retryCount++
        }

        if (service == null) {
            ExtractionState.addLog("Accessibility service not connected", LogLevel.ERROR)
            return
        }

        val activityName = ShizukuManager.getCurrentActivity()
        val screenHash = if (rootNode != null) hashScreen(activityName, getRootResourceId(rootNode)) else "screen_hash_${System.currentTimeMillis()}"

        if (visitedScreens.contains(screenHash)) return
        visitedScreens.add(screenHash)

        // Try dumping XML via Shizuku
        var xml = ShizukuManager.uiautomatorDump()
        var dumpRetries = 0
        while (!xml.contains("<hierarchy") && dumpRetries < 3) {
            delay(600)
            xml = ShizukuManager.uiautomatorDump()
            dumpRetries++
        }

        if (!xml.contains("<hierarchy")) {
            ExtractionState.addLog("Failed to dump XML for $activityName, continuing...", LogLevel.WARN)
        }

        screenCounter++
        val clickables = if (rootNode != null) findClickableNodes(rootNode) else emptyList()
        val screen = ScreenInfo(
            id = "screen_${screenCounter.toString().padStart(2, '0')}",
            activityName = activityName,
            xml = xml,
            depth = depth,
            clickCount = clickables.size
        )
        allScreens.add(screen)
        saveXmlToStorage(screen.id, xml)

        ExtractionState.update {
            it.copy(
                screensFound = allScreens.size,
                currentActivity = activityName,
                currentDepth = depth
            )
        }
        ExtractionState.addLog("Screen ${screen.id}: $activityName (depth $depth, ${clickables.size} clicks)")

        // Recursively explore clickable children
        if (depth < maxDepth && clickables.isNotEmpty()) {
            for ((index, clickable) in clickables.withIndex()) {
                if (allScreens.size >= maxScreens) break
                if (extractionJob?.isActive != true) break

                ExtractionState.addLog("Clicking ${index + 1}/${clickables.size}")
                val bounds = android.graphics.Rect()
                clickable.getBoundsInScreen(bounds)
                val clicked = clickable.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                if (!clicked && bounds.width() > 0 && bounds.height() > 0) {
                    val centerX = bounds.centerX()
                    val centerY = bounds.centerY()
                    ShizukuManager.executeShell("input tap $centerX $centerY")
                }
                delay(1400)

                val newActivity = ShizukuManager.getCurrentActivity()
                val currentRoot = service?.getRootNode()
                val newHash = if (currentRoot != null) hashScreen(newActivity, getRootResourceId(currentRoot)) else ""

                if (!visitedScreens.contains(newHash) && !newActivity.contains("InputMethod") && newHash.isNotEmpty()) {
                    processCurrentScreen(packageName, depth + 1)
                    service?.performBack()
                    delay(1000)
                }
            }
        }
    }

    private fun findClickableNodes(root: android.view.accessibility.AccessibilityNodeInfo): List<android.view.accessibility.AccessibilityNodeInfo> {
        val result = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
        fun walk(n: android.view.accessibility.AccessibilityNodeInfo) {
            if (n.isClickable && n.isEnabled) result.add(n)
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { walk(it) }
            }
        }
        walk(root)
        return result
    }

    private fun findScrollableNodes(root: android.view.accessibility.AccessibilityNodeInfo): List<android.view.accessibility.AccessibilityNodeInfo> {
        val result = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
        fun walk(n: android.view.accessibility.AccessibilityNodeInfo) {
            if (n.isScrollable) result.add(n)
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { walk(it) }
            }
        }
        walk(root)
        return result
    }

    private fun getRootResourceId(root: android.view.accessibility.AccessibilityNodeInfo): String {
        return root.viewIdResourceName ?: root.className?.toString() ?: "unknown"
    }

    private fun hashScreen(activityName: String, resourceId: String): String {
        val input = "$activityName:$resourceId"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun saveXmlToStorage(screenId: String, xml: String) {
        try {
            val dir = File(context.filesDir, "screens")
            dir.mkdirs()
            File(dir, "$screenId.xml").writeText(xml)
        } catch (e: Exception) {
            ExtractionState.addLog("Failed to save XML: ${e.message}", LogLevel.WARN)
        }
    }

    /**
     * Collect comprehensive app metadata via PackageManager + Shizuku.
     */
    private fun collectMetadata(packageName: String): AppMetadata {
        val pm = context.packageManager
        val packageInfo = ShizukuManager.getPackageInfo(pm, packageName)
        val appInfo = packageInfo.applicationInfo

        // Permissions
        val permissions = mutableListOf<PermissionInfo>()
        packageInfo.requestedPermissions?.forEachIndexed { index, permName ->
            val granted = packageInfo.requestedPermissionsFlags?.getOrNull(index)?.let {
                it and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
            } ?: false
            val isDangerous = try {
                val permInfo = pm.getPermissionInfo(permName, 0)
                permInfo.protectionLevel == android.content.pm.PermissionInfo.PROTECTION_DANGEROUS
            } catch (e: Exception) { false }
            permissions.add(PermissionInfo(permName, granted, isDangerous))
        }

        return AppMetadata(
            appName = try { pm.getApplicationLabel(appInfo).toString() } catch (e: Exception) { packageName },
            packageName = packageName,
            version = @Suppress("DEPRECATION") packageInfo.versionName ?: "unknown",
            permissions = permissions,
            activities = packageInfo.activities?.map { it.name } ?: emptyList(),
            services = packageInfo.services?.map { it.name } ?: emptyList(),
            receivers = packageInfo.receivers?.map { it.name } ?: emptyList(),
            providers = packageInfo.providers?.map { it.name } ?: emptyList(),
            sdkInfo = SdkInfo(
                minSdk = appInfo.minSdkVersion,
                targetSdk = appInfo.targetSdkVersion,
                compileSdk = appInfo.compileSdkVersion
            ),
            apkPath = appInfo.sourceDir,
            installDate = try { packageInfo.firstInstallTime } catch (e: Exception) { 0L },
            lastUpdateDate = try { packageInfo.lastUpdateTime } catch (e: Exception) { 0L }
        )
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes > 1048576 -> "%.1f MB".format(bytes / 1048576.0)
            bytes > 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "$bytes bytes"
        }
    }
}
