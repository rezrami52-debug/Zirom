package com.racha.ai.core.shizuku

import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuShellRunner {

    fun execCommand(command: String): String {
        if (!Shizuku.pingBinder()) return "خطأ: Shizuku غير متصل"
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString().trim()
        } catch (e: Exception) {
            e.printStackTrace()
            "فشل التنفيذ: ${e.message}"
        }
    }

    fun setWifiEnabled(enable: Boolean) {
        val state = if (enable) "enable" else "disable"
        execCommand("svc wifi $state")
    }

    fun setMobileDataEnabled(enable: Boolean) {
        val state = if (enable) "enable" else "disable"
        execCommand("svc data $state")
    }

    fun grantPermission(packageName: String, permission: String): Boolean {
        val result = execCommand("pm grant $packageName $permission")
        return !result.contains("Error") && !result.contains("Exception")
    }
}
