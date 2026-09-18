package com.applens.util

import android.content.pm.PackageManager
import android.content.pm.PackageInfo
import android.os.Build
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuManager {

    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    fun checkPermission(): Boolean {
        return if (Shizuku.isPreV11() && Shizuku.shouldShowRequestPermissionRationale()) {
            false
        } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            true
        } else {
            false
        }
    }

    fun requestPermission(requestCode: Int = 0) {
        if (!Shizuku.shouldShowRequestPermissionRationale()) {
            Shizuku.requestPermission(requestCode)
        }
    }

    fun executeShell(command: String): String {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as java.lang.Process
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val errorReader = BufferedReader(InputStreamReader(process.errorStream))
        val output = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            output.appendLine(line)
        }
        val errorOutput = StringBuilder()
        while (errorReader.readLine().also { line = it } != null) {
            errorOutput.appendLine(line)
        }
        process.waitFor()
        return output.toString()
    }

    fun uiautomatorDump(): String {
        return try {
            val tmpDump = "/data/local/tmp/applens_dump.xml"
            executeShell("rm -f " + tmpDump)
            executeShell("uiautomator dump " + tmpDump)
            val content = executeShell("cat " + tmpDump)
            if (content.contains("<hierarchy")) {
                content.trim()
            } else {
                val direct = executeShell("uiautomator dump /dev/tty")
                if (direct.contains("<hierarchy")) direct.trim() else ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun getCurrentActivity(): String {
        return try {
            val output = executeShell("dumpsys window windows")
            val regex = Regex("mCurrentFocus=Window\{.*?\s([a-zA-Z0-9._]+/[a-zA-Z0-9._]+)")
            val match = regex.find(output)?.groupValues?.get(1)
            if (match != null) {
                match
            } else {
                val actOutput = executeShell("dumpsys activity top")
                val actRegex = Regex("ACTIVITY\s([a-zA-Z0-9._]+/[a-zA-Z0-9._]+)")
                actRegex.find(actOutput)?.groupValues?.get(1) ?: "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    fun forceStopApp(packageName: String) {
        executeShell("am force-stop " + packageName)
    }

    fun launchApp(packageName: String) {
        executeShell("monkey -p " + packageName + " -c android.intent.category.LAUNCHER 1")
    }

    fun getPackageInfo(pm: PackageManager, packageName: String): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                packageName,
                PackageManager.GET_PERMISSIONS or
                PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_PROVIDERS or
                PackageManager.GET_META_DATA
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(
                packageName,
                PackageManager.GET_PERMISSIONS or
                PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_PROVIDERS
            )
        }
    }
}
