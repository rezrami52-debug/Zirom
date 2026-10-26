package com.racha.ai.core.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuShellRunner {

    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    fun requestPermission(requestCode: Int = 1001) {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun execCommand(command: String): String {
        return try {
            if (isAvailable()) {
                val clazz = Class.forName("rikka.shizuku.Shizuku")
                val method = clazz.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                method.isAccessible = true
                val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
                val exitCode = process.waitFor()
                if (exitCode == 0) output.toString().trim() else "فشل التنفيذ (كود $exitCode)"
            } else {
                runStandardExec(command)
            }
        } catch (e: Exception) {
            runStandardExec(command)
        }
    }

    private fun runStandardExec(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            val exitCode = process.waitFor()
            if (exitCode == 0) output.toString().trim() else "فشل التنفيذ (كود $exitCode)"
        } catch (e: Exception) {
            "فشل التنفيذ: ${e.localizedMessage}"
        }
    }

    fun setWifiEnabled(enable: Boolean): Boolean {
        val state = if (enable) "enable" else "disable"
        val res = execCommand("svc wifi $state")
        return !res.contains("فشل") && !res.contains("خطأ")
    }

    fun setMobileDataEnabled(enable: Boolean): Boolean {
        val state = if (enable) "enable" else "disable"
        val res = execCommand("svc data $state")
        return !res.contains("فشل") && !res.contains("خطأ")
    }

    fun setBluetoothEnabled(enable: Boolean): Boolean {
        val cmd = if (enable) "cmd bluetooth_manager enable" else "cmd bluetooth_manager disable"
        val res = execCommand(cmd)
        return !res.contains("فشل") && !res.contains("خطأ")
    }

    fun setBatterySaver(enable: Boolean): Boolean {
        val mode = if (enable) "1" else "0"
        val res = execCommand("cmd power set-mode $mode")
        return !res.contains("فشل") && !res.contains("خطأ")
    }

    fun lockScreen(): Boolean {
        val res = execCommand("input keyevent 26")
        return !res.contains("فشل") && !res.contains("خطأ")
    }

    fun takeScreenshot(path: String = "/sdcard/Pictures/racha_screen.png"): Boolean {
        val res = execCommand("screencap -p $path")
        return !res.contains("فشل") && !res.contains("خطأ")
    }

    fun forceStopApp(packageName: String): Boolean {
        val res = execCommand("am force-stop $packageName")
        return !res.contains("فشل") && !res.contains("خطأ")
    }

    fun trimAllCaches(): Boolean {
        val res = execCommand("pm trim-caches 999999999999")
        return !res.contains("فشل") && !res.contains("خطأ")
    }

    fun getBatteryReport(): String {
        return execCommand("dumpsys battery")
    }

    fun grantPermission(packageName: String, permission: String): Boolean {
        val result = execCommand("pm grant $packageName $permission")
        return !result.contains("Error") && !result.contains("Exception") && !result.contains("فشل")
    }
}
