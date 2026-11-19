package com.racha.ai.core.security

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SecurityThreat(
    val appName: String,
    val packageName: String,
    val threatType: String,
    val description: String,
    val severity: ThreatSeverity,
    val installSource: String? = null,
    val sensitivePermissions: List<String> = emptyList()
)

enum class ThreatSeverity {
    HIGH,
    MEDIUM,
    LOW
}

data class SecurityAuditReport(
    val threatsFound: List<SecurityThreat>,
    val micPermissionApps: List<String>,
    val suspiciousAccessibilityApps: List<String>,
    val hiddenApps: List<String>,
    val scanWarnings: List<String> = emptyList()
)

class SpywareDetector(
    private val context: Context
) {

    suspend fun runFullSecurityAudit(): SecurityAuditReport =
        withContext(Dispatchers.IO) {
            val threats = mutableListOf<SecurityThreat>()
            val accessibilityApps = mutableListOf<String>()
            val micPermissionApps = mutableListOf<String>()
            val scanWarnings = mutableListOf<String>()
            val pm = context.packageManager

            val enabledServices = try {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: ""
            } catch (_: Exception) {
                scanWarnings.add("تعذر قراءة إعداد خدمات إمكانية الوصول.")
                ""
            }

            val installedApps = try {
                pm.getInstalledApplications(
                    PackageManager.GET_META_DATA
                )
            } catch (_: Exception) {
                scanWarnings.add("تعذر تعداد التطبيقات المثبتة؛ نتيجة الفحص غير مكتملة.")
                emptyList()
            }

            val sensitivePermissionNames = mapOf(
                "android.permission.RECORD_AUDIO" to "الميكروفون",
                "android.permission.CAMERA" to "الكاميرا",
                "android.permission.ACCESS_FINE_LOCATION" to "الموقع الدقيق",
                "android.permission.ACCESS_COARSE_LOCATION" to "الموقع التقريبي",
                "android.permission.READ_SMS" to "قراءة الرسائل",
                "android.permission.READ_CALL_LOG" to "قراءة سجل المكالمات",
                "android.permission.READ_CONTACTS" to "قراءة جهات الاتصال",
                "android.permission.REQUEST_INSTALL_PACKAGES" to "تثبيت تطبيقات أخرى",
                "android.permission.SYSTEM_ALERT_WINDOW" to "الظهور فوق التطبيقات"
            )

            fun appName(packageName: String): String {
                return try {
                    pm.getApplicationLabel(
                        pm.getApplicationInfo(packageName, 0)
                    ).toString()
                } catch (_: Exception) {
                    packageName
                }
            }

            fun installSource(packageName: String): String? {
                return try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        pm.getInstallSourceInfo(packageName)
                            .installingPackageName
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getInstallerPackageName(packageName)
                    }
                } catch (_: Exception) {
                    null
                }
            }

            fun sensitivePermissions(
                packageName: String
            ): List<String> {
                return try {
                    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(
                            packageName,
                            PackageManager.PackageInfoFlags.of(
                                PackageManager.GET_PERMISSIONS.toLong()
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(
                            packageName,
                            PackageManager.GET_PERMISSIONS
                        )
                    }

                    packageInfo.requestedPermissions
                        ?.mapNotNull { sensitivePermissionNames[it] }
                        ?.distinct()
                        ?: emptyList()
                } catch (_: Exception) {
                    scanWarnings.add("تعذر التحقق من بعض الأذونات المطلوبة للتطبيقات.")
                    emptyList()
                }
            }

            fun isSystemApp(app: ApplicationInfo): Boolean {
                return (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            }

            for (app in installedApps) {
                val packageName = app.packageName

                if (
                    packageName == context.packageName ||
                    isSystemApp(app)
                ) {
                    continue
                }

                val name = try {
                    pm.getApplicationLabel(app).toString()
                } catch (_: Exception) {
                    packageName
                }

                val permissions = sensitivePermissions(packageName)
                val hasGrantedMicPermission = pm.checkPermission(
                    "android.permission.RECORD_AUDIO",
                    packageName
                ) == PackageManager.PERMISSION_GRANTED
                if (hasGrantedMicPermission) {
                    micPermissionApps.add(name)
                }
                val source = installSource(packageName)
                val accessibilityEnabled =
                    enabledServices.contains(packageName)

                if (accessibilityEnabled) {
                    accessibilityApps.add(name)

                    val severity = if (
                        permissions.contains("الميكروفون") ||
                        permissions.contains("قراءة الرسائل") ||
                        permissions.contains("الظهور فوق التطبيقات")
                    ) {
                        ThreatSeverity.MEDIUM
                    } else {
                        ThreatSeverity.LOW
                    }

                    threats.add(
                        SecurityThreat(
                            appName = name,
                            packageName = packageName,
                            threatType = "خدمة إمكانية وصول مفعّلة",
                            description =
                                "هذه صلاحية حساسة وقد تكون مشروعة، " +
                                    "لكنها تستحق المراجعة لأنها تسمح بقراءة " +
                                    "محتوى الشاشة أو التفاعل مع التطبيقات.",
                            severity = severity,
                            installSource = source,
                            sensitivePermissions = permissions
                        )
                    )
                }
            }

            val dpm = context.getSystemService(
                Context.DEVICE_POLICY_SERVICE
            ) as? DevicePolicyManager

            val activeAdmins = try {
                dpm?.activeAdmins ?: emptyList()
            } catch (_: Exception) {
                scanWarnings.add("تعذر فحص مسؤولي الجهاز؛ نتيجة الفحص غير مكتملة.")
                emptyList()
            }

            for (admin in activeAdmins) {
                val packageName = admin.packageName

                if (
                    packageName == context.packageName ||
                    packageName.startsWith("com.google") ||
                    packageName.startsWith("com.android")
                ) {
                    continue
                }

                val name = appName(packageName)

                threats.add(
                    SecurityThreat(
                        appName = name,
                        packageName = packageName,
                        threatType = "مسؤول جهاز مفعّل",
                        description =
                            "يمتلك التطبيق صلاحية إدارية حساسة " +
                                "وقد يمنع إزالة التطبيق أو يغيّر بعض إعدادات الجهاز. " +
                                "وجودها لا يثبت أن التطبيق ضار.",
                        severity = ThreatSeverity.MEDIUM,
                        installSource = installSource(packageName),
                        sensitivePermissions =
                            sensitivePermissions(packageName)
                    )
                )
            }

            val hiddenApps = threats
                .filter {
                    it.threatType.contains("مخفي")
                }
                .map { it.appName }

            SecurityAuditReport(
                threatsFound = threats.distinctBy {
                    "${it.packageName}:${it.threatType}"
                },
                micPermissionApps = micPermissionApps.distinct(),
                suspiciousAccessibilityApps =
                    accessibilityApps.distinct(),
                hiddenApps = hiddenApps.distinct(),
                scanWarnings = scanWarnings.distinct()
            )
        }
}
