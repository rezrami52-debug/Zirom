package com.racha.ai.core.shizuku

import java.io.BufferedReader
import java.io.InputStreamReader

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
            val exitCode = process.waitFor()
            if (exitCode == 0) output.toString().trim() else "فشل التنفيذ (كود $exitCode)"
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
        return !result.contains("Error") && !result.contains("Exception") && !result.contains("فشل")
    }
}
