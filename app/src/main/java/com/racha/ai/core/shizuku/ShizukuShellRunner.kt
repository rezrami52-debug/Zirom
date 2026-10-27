package com.racha.ai.core.shizuku

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object ShizukuShellRunner {

    fun execCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("rish", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            val finished = process.waitFor(3, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return "انتهت المهلة"
            }
            val exitCode = process.exitValue()
            if (exitCode == 0) output.toString().trim() else "فشل ($exitCode)"
        } catch (e: Exception) {
            "فشل: ${e.message}"
        }
    }

    fun setWifiEnabled(enable: Boolean): Boolean {
        val state = if (enable) "enable" else "disable"
        val res = execCommand("svc wifi $state")
        return !res.contains("فشل")
    }

    fun setMobileDataEnabled(enable: Boolean): Boolean {
        val state = if (enable) "enable" else "disable"
        val res = execCommand("svc data $state")
        return !res.contains("فشل")
    }

    fun setBluetoothEnabled(enable: Boolean): Boolean {
        val cmd = if (enable) "cmd bluetooth_manager enable" else "cmd bluetooth_manager disable"
        val res = execCommand(cmd)
        return !res.contains("فشل")
    }

    fun lockScreen(): Boolean {
        val res = execCommand("input keyevent 26")
        return !res.contains("فشل")
    }

    fun takeScreenshot(path: String = "/sdcard/Pictures/racha_screen.png"): Boolean {
        val res = execCommand("screencap -p $path")
        return !res.contains("فشل")
    }

    fun grantPermission(packageName: String, permission: String): Boolean {
        val result = execCommand("pm grant $packageName $permission")
        return !result.contains("Error") && !result.contains("Exception") && !result.contains("فشل")
    }
}
