package com.racha.ai.core.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object ShizukuShellRunner {

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun execCommand(command: String): String {
        return try {
            val process = if (isShizukuAvailable()) {
                val newProcessMethod = Shizuku::class.java.getMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                newProcessMethod.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            } else {
                Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            }

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
        } catch (e: Throwable) {
            "فشل: ${e.message}"
        }
    }

    fun enableFreeformSupport(): Boolean {
        val cmd1 = "settings put global enable_freeform_support 1"
        val cmd2 = "settings put secure force_resizable_activities 1"
        val r1 = execCommand(cmd1)
        val r2 = execCommand(cmd2)
        return !r1.contains("فشل") && !r2.contains("فشل")
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
