package com.racha.ai.core.agent

import android.content.Context
import com.racha.ai.core.eve.EveAccessibilityService
import com.racha.ai.core.shizuku.ShizukuShellRunner

class SelfHealingInspector(private val context: Context) {

    fun autoHealAccessibility(): Boolean {
        if (EveAccessibilityService.isRunning()) return true
        val serviceName = "${context.packageName}/${context.packageName}.core.eve.EveAccessibilityService"
        val cmd = "settings put secure enabled_accessibility_services $serviceName && settings put secure accessibility_enabled 1"
        val result = ShizukuShellRunner.execCommand(cmd)
        return !result.contains("فشل") && !result.contains("error")
    }

    fun autoHealNotificationListener(): Boolean {
        val serviceName = "${context.packageName}/${context.packageName}.services.notification.RachaNotificationListener"
        val cmd = "cmd notification allow_listener $serviceName"
        val result = ShizukuShellRunner.execCommand(cmd)
        return !result.contains("فشل") && !result.contains("error")
    }

    fun autoGrantPermission(permission: String): Boolean {
        val cmd = "pm grant ${context.packageName} $permission"
        val result = ShizukuShellRunner.execCommand(cmd)
        return !result.contains("فشل") && !result.contains("error")
    }

    fun autoUnlockRestrictedSettings(): Boolean {
        val cmd = "appops set ${context.packageName} ACCESS_RESTRICTED_SETTINGS allow"
        val result = ShizukuShellRunner.execCommand(cmd)
        return !result.contains("فشل")
    }
}
