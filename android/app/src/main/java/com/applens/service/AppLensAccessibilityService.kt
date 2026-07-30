package com.applens.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.applens.data.ExtractionState
import com.applens.engine.ExtractionEngine

class AppLensAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AppLensAccessibilityService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ExtractionState.addLog("Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val engine = ExtractionEngine.getInstance(this)
        if (!engine.isExtractionActive) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return
                val className = event.className?.toString() ?: ""
                if (pkg == engine.targetPackage) {
                    engine.onScreenChanged(className)
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Handled by the engine's polling loop
            }
        }
    }

    override fun onInterrupt() {
        ExtractionState.addLog("Accessibility service interrupted", com.applens.data.LogLevel.WARN)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /**
     * Get the root node of the current active window.
     */
    fun getRootNode(): AccessibilityNodeInfo? {
        return rootInActiveWindow
    }

    /**
     * Perform a global back action.
     */
    fun performBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }
}
