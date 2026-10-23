package com.racha.ai.core.shizuku

import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuPermissionGranter {

    fun grantPermission(packageName: String, permission: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("rish", "-c", "pm grant $packageName $permission"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
