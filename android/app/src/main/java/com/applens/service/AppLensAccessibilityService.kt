package com.applens.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.applens.data.ExtractionState

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
        // Events are handled by the ExtractionEngine's polling loop
        // via ShizukuManager.getCurrentActivity() and uiautomatorDump()
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
