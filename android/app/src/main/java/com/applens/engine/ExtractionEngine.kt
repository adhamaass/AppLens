package com.applens.engine

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.applens.data.*
import com.applens.network.ApiClient
import com.applens.service.AppLensAccessibilityService
import com.applens.util.ShizukuManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private var apiClient: ApiClient? = null

    fun setBackendIp(ip: String) {
        apiClient = ApiClient("http://$ip:3000")
    }

    fun startExtraction(packageName: String, backendIp: String) {
        if (isExtractionActive) return
        setBackendIp(backendIp)
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

                // Force stop and relaunch
                ShizukuManager.forceStopApp(packageName)
                delay(1000)
                ShizukuManager.launchApp(packageName)
                delay(2000)
                ExtractionState.addLog("App launched: $packageName")

                // Collect metadata first
                ExtractionState.addLog("Collecting app metadata...")
                val metadata = collectMetadata(packageName)
                ExtractionState.update { it.copy(metadata = metadata) }
                ExtractionState.addLog("Metadata collected: ${metadata.activities.size} activities, ${metadata.permissions.size} permissions")

                // Start BFS traversal
                isExtractionActive = true
                bfsTraversal(packageName)

                // Upload to backend
                isExtractionActive = false
                ExtractionState.update { it.copy(status = ExtractionStatus.Uploading) }
                ExtractionState.addLog("Uploading ${allScreens.size} screens to backend...")

                val zipBytes = apiClient?.analyze(
                    appName = metadata.appName,
                    packageName = metadata.packageName,
                    version = metadata.version,
                    metadata = metadata,
                    screens = allScreens.toList()
                )

                // Save ZIP to internal storage
                val zipFile = File(context.filesDir, "${packageName}_output.zip")
                zipBytes?.let { zipFile.writeBytes(it) }

                ExtractionState.update {
                    it.copy(
                        status = ExtractionStatus.Done,
                        zipUrl = zipFile.absolutePath,
                        screens = allScreens.toList()
                    )
                }
                ExtractionState.addLog("Extraction complete! ZIP saved: ${zipFile.absolutePath}")

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
        val queue = ArrayDeque<Pair<String, Int>>() // (screenHash, depth)

        // Process the first (current) screen
        processCurrentScreen(packageName, depth = 0)

        while (allScreens.size < maxScreens && extractionJob?.isActive == true) {
            val service = AppLensAccessibilityService.instance
            if (service == null) {
                ExtractionState.addLog("Accessibility service not available", LogLevel.ERROR)
                break
            }

            // Get root node and find clickable elements
            val rootNode = service.getRootNode()
            if (rootNode == null) {
                ExtractionState.addLog("No root node available, waiting...", LogLevel.WARN)
                delay(1000)
                continue
            }

            val clickables = findClickableNodes(rootNode)
            val activityName = ShizukuManager.getCurrentActivity()
            val screenHash = hashScreen(activityName, getRootResourceId(rootNode))

            if (visitedScreens.contains(screenHash)) {
                // Already visited, go back
                service.performBack()
                delay(800)
                continue
            }

            visitedScreens.add(screenHash)
            ExtractionState.addLog("Exploring screen: $activityName (${clickables.size} clickables)")

            // Dump XML for this screen
            val xml = ShizukuManager.uiautomatorDump()
            screenCounter++
            val screen = ScreenInfo(
                id = "screen_${screenCounter.toString().padStart(2, '0')}",
                activityName = activityName,
                xml = xml,
                depth = 0,
                clickCount = clickables.size
            )
            allScreens.add(screen)
            saveXmlToStorage(screen.id, xml)

            ExtractionState.update {
                it.copy(
                    screensFound = allScreens.size,
                    currentActivity = activityName,
                    currentDepth = 0
                )
            }

            // Click each clickable element and explore
            for ((index, clickable) in clickables.withIndex()) {
                if (allScreens.size >= maxScreens) break
                if (extractionJob?.isActive != true) break

                ExtractionState.addLog("Clicking ${index + 1}/${clickables.size}: ${clickable.className}")
                clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                delay(800)

                // Check if we navigated to a new screen
                val newActivity = ShizukuManager.getCurrentActivity()
                if (newActivity != activityName && !newActivity.contains("InputMethod")) {
                    // New screen! Process it at depth 1
                    if (allScreens.size < maxScreens) {
                        processCurrentScreen(packageName, depth = 1)
                    }
                    // Go back
                    service.performBack()
                    delay(800)
                }
            }

            // Try scrolling if no new screens found
            val scrollable = findScrollableNodes(rootNode)
            for (scrollNode in scrollable) {
                if (allScreens.size >= maxScreens) break
                scrollNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                delay(800)
                val newActivity = ShizukuManager.getCurrentActivity()
                if (newActivity != activityName) {
                    processCurrentScreen(packageName, depth = 1)
                    service.performBack()
                    delay(800)
                }
            }

            // We've explored this screen fully, break if we're back at the top
            break
        }
    }

    /**
     * Process the current screen: dump XML, find clickables, and recursively explore.
     */
    private suspend fun processCurrentScreen(packageName: String, depth: Int) {
        val maxDepth = ExtractionState.state.value.maxDepth
        val maxScreens = ExtractionState.state.value.maxScreens

        if (depth > maxDepth || allScreens.size >= maxScreens) return
        if (extractionJob?.isActive != true) return

        delay(800) // Wait for screen to settle

        val service = AppLensAccessibilityService.instance ?: return
        val activityName = ShizukuManager.getCurrentActivity()

        // Ensure we're in the target app
        val rootNode = service.getRootNode() ?: return
        val screenHash = hashScreen(activityName, getRootResourceId(rootNode))

        if (visitedScreens.contains(screenHash)) return
        visitedScreens.add(screenHash)

        // Dump XML
        val xml = ShizukuManager.uiautomatorDump()
        if (!xml.contains("<hierarchy")) return

        screenCounter++
        val clickables = findClickableNodes(rootNode)
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
        if (depth < maxDepth) {
            for ((index, clickable) in clickables.withIndex()) {
                if (allScreens.size >= maxScreens) break
                if (extractionJob?.isActive != true) break

                clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                delay(800)

                val newActivity = ShizukuManager.getCurrentActivity()
                if (newActivity != activityName && !newActivity.contains("InputMethod")) {
                    processCurrentScreen(packageName, depth + 1)
                    service.performBack()
                    delay(800)
                }
            }
        }
    }

    /**
     * Recursively find all clickable AccessibilityNodeInfo nodes.
     */
    private fun findClickableNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        findNodesByProperty(root) { node -> node.isClickable && node.isEnabled }
        return result
    }

    private fun findScrollableNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        findNodesByProperty(root) { node -> node.isScrollable }
        return result
    }

    private fun findNodesByProperty(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun walk(n: AccessibilityNodeInfo) {
            if (predicate(n)) result.add(n)
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { walk(it) }
            }
        }
        walk(node)
        return result
    }

    private fun getRootResourceId(root: AccessibilityNodeInfo): String {
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
        packageInfo.requestedPermissions?.forEach { permName ->
            val granted = packageInfo.requestedPermissionsFlags?.any { it and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 } ?: false
            val isDangerous = try {
                val permInfo = pm.getPermissionInfo(permName, 0)
                permInfo.protectionLevel == android.content.pm.PermissionInfo.PROTECTION_DANGEROUS
            } catch (e: Exception) { false }
            permissions.add(PermissionInfo(permName, granted, isDangerous))
        }

        // Activities
        val activities = packageInfo.activities?.map { it.name } ?: emptyList()

        // Services
        val services = packageInfo.services?.map { it.name } ?: emptyList()

        // Receivers
        val receivers = packageInfo.receivers?.map { it.name } ?: emptyList()

        // Providers
        val providers = packageInfo.providers?.map { it.name } ?: emptyList()

        // SDK info
        val sdkInfo = SdkInfo(
            minSdk = appInfo.minSdkVersion,
            targetSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.DONUT) appInfo.targetSdkVersion else 0,
            compileSdk = appInfo.compileSdkVersion
        )

        // Version
        val version = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionName.toString()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionName ?: "unknown"
        }

        // Install/update dates
        val installDate = try { packageInfo.firstInstallTime } catch (e: Exception) { 0L }
        val lastUpdateDate = try { packageInfo.lastUpdateTime } catch (e: Exception) { 0L }

        // App name
        val appName = try { pm.getApplicationLabel(appInfo).toString() } catch (e: Exception) { packageName }

        return AppMetadata(
            appName = appName,
            packageName = packageName,
            version = version,
            permissions = permissions,
            activities = activities,
            services = services,
            receivers = receivers,
            providers = providers,
            sdkInfo = sdkInfo,
            apkPath = appInfo.sourceDir,
            installDate = installDate,
            lastUpdateDate = lastUpdateDate
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
}
