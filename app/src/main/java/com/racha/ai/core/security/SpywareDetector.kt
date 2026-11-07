package com.racha.ai.core.security

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import com.racha.ai.core.shizuku.ShizukuShellRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SecurityThreat(
    val appName: String,
    val packageName: String,
    val threatType: String,
    val description: String,
    val severity: ThreatSeverity
)

enum class ThreatSeverity { HIGH, MEDIUM, LOW }

data class SecurityAuditReport(
    val isClean: Boolean,
    val threatsFound: List<SecurityThreat>,
    val activeMicApps: List<String>,
    val suspiciousAccessibilityApps: List<String>,
    val hiddenApps: List<String>
)

class SpywareDetector(private val context: Context) {

    suspend fun runFullSecurityAudit(): SecurityAuditReport = withContext(Dispatchers.IO) {
        val threats = mutableListOf<SecurityThreat>()
        val pm = context.packageManager

        // 1. فحص خدمات إمكانية الوصول المشبوهة (التي قد تسجل ضغطات المفاتيح)
        val accessibilityApps = mutableListOf<String>()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""

        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in installedApps) {
            // استثناء تطبيقات النظام وتطبيق Racha
            if (app.packageName == context.packageName || (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue

            val appName = pm.getApplicationLabel(app).toString()

            // فحص هل التطبيق مفعل كخدمة إمكانية وصول
            if (enabledServices.contains(app.packageName)) {
                accessibilityApps.add(appName)
                threats.add(
                    SecurityThreat(
                        appName = appName,
                        packageName = app.packageName,
                        threatType = "تسجيل الشاشة ولوحة المفاتيح",
                        description = "التطبيق يمتلك صلاحية الوصول الكاملة للشاشة (Accessibility)",
                        severity = ThreatSeverity.HIGH
                    )
                )
            }

            // 2. فحص التطبيقات المخفية (بدون أيقونة في الشاشة الرئيسية ولكنها تمتلك أذونات حساسة)
            val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
            if (launchIntent == null) {
                val hasAudio = pm.checkPermission(android.Manifest.permission.RECORD_AUDIO, app.packageName) == PackageManager.PERMISSION_GRANTED
                val hasCamera = pm.checkPermission(android.Manifest.permission.CAMERA, app.packageName) == PackageManager.PERMISSION_GRANTED
                val hasLoc = pm.checkPermission(android.Manifest.permission.ACCESS_FINE_LOCATION, app.packageName) == PackageManager.PERMISSION_GRANTED

                if (hasAudio || hasCamera || hasLoc) {
                    threats.add(
                        SecurityThreat(
                            appName = appName,
                            packageName = app.packageName,
                            threatType = "تطبيق شبحي مخفي",
                            description = "تطبيق يعمل بدون أيقونة ويمتلك صلاحيات تجسس حساسة",
                            severity = ThreatSeverity.HIGH
                        )
                    )
                }
            }
        }

        // 3. فحص مدراء الجهاز (Device Administrators التي قد تمنع الحذف)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val activeAdmins = dpm?.activeAdmins ?: emptyList()
        for (admin in activeAdmins) {
            val pkg = admin.packageName
            if (pkg != context.packageName && !pkg.startsWith("com.google") && !pkg.startsWith("com.android")) {
                val name = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
                threats.add(
                    SecurityThreat(
                        appName = name,
                        packageName = pkg,
                        threatType = "مسؤول جهاز (Device Admin)",
                        description = "يمتلك صلاحية إدارة الهاتف والتحكم بالقفل ومسح البيانات",
                        severity = ThreatSeverity.MEDIUM
                    )
                )
            }
        }

        // 4. فحص التطبيقات التي تستخدم المايكروفون حالياً عبر Shizuku
        val activeMic = mutableListOf<String>()
        if (ShizukuShellRunner.isShizukuAvailable()) {
            val cmd = "cmd appops query-op --user 0 RECORD_AUDIO allow"
            val output = ShizukuShellRunner.execCommand(cmd)
            for (line in output.lines()) {
                val match = Regex("\\[([a-zA-Z0-9._]+)\\]").find(line)
                if (match != null) {
                    val pkg = match.groupValues[1]
                    if (pkg != context.packageName && !pkg.contains("systemui") && !pkg.contains("google")) {
                        val name = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
                        activeMic.add(name)
                    }
                }
            }
        }

        SecurityAuditReport(
            isClean = threats.isEmpty(),
            threatsFound = threats,
            activeMicApps = activeMic,
            suspiciousAccessibilityApps = accessibilityApps,
            hiddenApps = threats.filter { it.threatType.contains("مخفي") }.map { it.appName }
        )
    }
}
