package com.racha.ai.core.security

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SecurityFinding(
    val appName: String,
    val packageName: String,
    val category: String,
    val explanation: String,
    val priority: ReviewPriority,
    val installSource: String? = null,
    val grantedSensitivePermissions: List<String> = emptyList()
)

enum class ReviewPriority {
    HIGH,
    MEDIUM,
    LOW
}

data class SecurityAuditReport(
    val findings: List<SecurityFinding>,
    val microphonePermissionApps: List<String>,
    val enabledAccessibilityApps: List<String>,
    val scanWarnings: List<String>,
    val activeVpnDetected: Boolean?
)

class SpywareDetector(
    private val context: Context
) {

    suspend fun runFullSecurityAudit(): SecurityAuditReport =
        withContext(Dispatchers.IO) {
            val findings = mutableListOf<SecurityFinding>()
            val microphonePermissionApps = mutableListOf<String>()
            val enabledAccessibilityApps = mutableListOf<String>()
            val scanWarnings = mutableListOf<String>()
            val pm = context.packageManager
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager

            val sensitivePermissionNames = mapOf(
                "android.permission.RECORD_AUDIO" to "الميكروفون",
                "android.permission.CAMERA" to "الكاميرا",
                "android.permission.ACCESS_FINE_LOCATION" to "الموقع الدقيق",
                "android.permission.ACCESS_COARSE_LOCATION" to "الموقع التقريبي",
                "android.permission.READ_SMS" to "قراءة الرسائل النصية",
                "android.permission.RECEIVE_SMS" to "استقبال الرسائل النصية",
                "android.permission.READ_CALL_LOG" to "قراءة سجل المكالمات",
                "android.permission.READ_CONTACTS" to "قراءة جهات الاتصال"
            )

            fun enabledPackages(setting: String, humanName: String): Set<String> {
                return try {
                    Settings.Secure.getString(context.contentResolver, setting)
                        .orEmpty()
                        .split(':')
                        .mapNotNull { ComponentName.unflattenFromString(it)?.packageName }
                        .toSet()
                } catch (_: Exception) {
                    scanWarnings.add("تعذر قراءة إعداد $humanName.")
                    emptySet()
                }
            }

            val enabledAccessibility = enabledPackages(
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                "خدمات إمكانية الوصول"
            )
            val enabledNotificationListeners = enabledPackages(
                "enabled_notification_listeners",
                "مستمعي الإشعارات"
            )

            val installedApps = try {
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
            } catch (_: Exception) {
                scanWarnings.add("تعذر تعداد التطبيقات المثبتة؛ هذا الفحص غير مكتمل.")
                emptyList()
            }

            fun isSystemApp(app: ApplicationInfo): Boolean {
                return (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            }

            fun appLabel(app: ApplicationInfo): String {
                return try {
                    pm.getApplicationLabel(app).toString()
                } catch (_: Exception) {
                    app.packageName
                }
            }

            fun installSource(packageName: String): String? {
                return try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        pm.getInstallSourceInfo(packageName).installingPackageName
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getInstallerPackageName(packageName)
                    }
                } catch (_: Exception) {
                    null
                }
            }

            fun grantedSensitivePermissions(packageName: String): List<String> {
                return try {
                    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(
                            packageName,
                            PackageManager.PackageInfoFlags.of(
                                PackageManager.GET_PERMISSIONS.toLong()
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                    }
                    val requested = info.requestedPermissions ?: return emptyList()
                    val flags = info.requestedPermissionsFlags ?: return emptyList()
                    requested.mapIndexedNotNull { index, permission ->
                        val granted = index < flags.size &&
                            (flags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                        if (granted) sensitivePermissionNames[permission] else null
                    }.distinct()
                } catch (_: Exception) {
                    scanWarnings.add("تعذر التحقق من الأذونات الممنوحة لبعض التطبيقات.")
                    emptyList()
                }
            }

            @Suppress("DEPRECATION")
            fun appOpAllowed(operation: String, uid: Int, packageName: String): Boolean? {
                val manager = appOps
                if (manager == null) {
                    scanWarnings.add("مدير صلاحيات الوصول الخاصة غير متاح.")
                    return null
                }
                return try {
                    val mode = manager.checkOpNoThrow(operation, uid, packageName)
                    mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_FOREGROUND
                } catch (_: Exception) {
                    scanWarnings.add("تعذر التحقق من بعض صلاحيات الوصول الخاصة.")
                    null
                }
            }

            val specialAccessChecks = listOf(
                Triple(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    "الظهور فوق التطبيقات",
                    "لدى التطبيق سماح خاص بالظهور فوق تطبيقات أخرى؛ قد يُستخدم للفقاعات أو أدوات مساعدة، ولا يثبت إساءة الاستخدام."
                ),
                Triple(
                    "android:request_install_packages",
                    "طلب تثبيت تطبيقات أخرى",
                    "لدى التطبيق سماح بطلب تثبيت حزم؛ راجع ما إذا كنت تثق بمصدره، فهذا السماح وحده لا يثبت تثبيت برمجية ضارة."
                ),
                Triple(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    "الوصول إلى إحصاءات الاستخدام",
                    "لدى التطبيق سماح خاص بقراءة إحصاءات استخدام التطبيقات؛ راجع حاجته إليه."
                )
            )

            for (app in installedApps) {
                val packageName = app.packageName
                if (packageName == context.packageName || isSystemApp(app)) continue

                val name = appLabel(app)
                val source = installSource(packageName)
                val granted = grantedSensitivePermissions(packageName)

                if ("الميكروفون" in granted) microphonePermissionApps.add(name)
                if (packageName in enabledAccessibility) enabledAccessibilityApps.add(name)

                if (granted.isNotEmpty()) {
                    findings.add(
                        SecurityFinding(
                            appName = name,
                            packageName = packageName,
                            category = "أذونات حساسة ممنوحة",
                            explanation = "الأذونات الممنوحة: ${granted.joinToString("، ")}. منح الإذن لا يثبت أن التطبيق يستخدمه الآن أو يسيء استخدامه.",
                            priority = ReviewPriority.LOW,
                            installSource = source,
                            grantedSensitivePermissions = granted
                        )
                    )
                }

                if (packageName in enabledAccessibility) {
                    val priority = if (
                        granted.any { it == "الميكروفون" || it == "قراءة الرسائل النصية" }
                    ) ReviewPriority.MEDIUM else ReviewPriority.LOW
                    findings.add(
                        SecurityFinding(
                            appName = name,
                            packageName = packageName,
                            category = "خدمة إمكانية وصول مفعّلة",
                            explanation = "قد تتمكن الخدمة من قراءة عناصر الشاشة أو التفاعل معها بحسب وظيفتها. قد يكون ذلك مشروعًا، لكنه يستحق مراجعة سبب تفعيلها.",
                            priority = priority,
                            installSource = source,
                            grantedSensitivePermissions = granted
                        )
                    )
                }

                if (packageName in enabledNotificationListeners) {
                    findings.add(
                        SecurityFinding(
                            appName = name,
                            packageName = packageName,
                            category = "مستمع إشعارات مفعّل",
                            explanation = "يمكن لخدمة كهذه الاطلاع على الإشعارات التي يسمح Android بمشاركتها معها؛ هذا لا يثبت إساءة الاستخدام.",
                            priority = ReviewPriority.MEDIUM,
                            installSource = source,
                            grantedSensitivePermissions = granted
                        )
                    )
                }

                for ((operation, category, explanation) in specialAccessChecks) {
                    if (appOpAllowed(operation, app.uid, packageName) == true) {
                        findings.add(
                            SecurityFinding(
                                appName = name,
                                packageName = packageName,
                                category = category,
                                explanation = explanation,
                                priority = ReviewPriority.LOW,
                                installSource = source,
                                grantedSensitivePermissions = granted
                            )
                        )
                    }
                }
            }

            val devicePolicyManager = context.getSystemService(
                Context.DEVICE_POLICY_SERVICE
            ) as? DevicePolicyManager
            val activeAdmins = try {
                devicePolicyManager?.activeAdmins.orEmpty()
            } catch (_: Exception) {
                scanWarnings.add("تعذر فحص مسؤولي الجهاز؛ هذا الفحص غير مكتمل.")
                emptyList()
            }

            for (admin in activeAdmins) {
                val packageName = admin.packageName
                if (packageName == context.packageName) continue
                val name = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
                } catch (_: Exception) {
                    packageName
                }
                findings.add(
                    SecurityFinding(
                        appName = name,
                        packageName = packageName,
                        category = "مسؤول جهاز نشط",
                        explanation = "يمتلك التطبيق صلاحية إدارية قد تتيح إدارة بعض إعدادات الجهاز أو تقييد إزالة التطبيق. قد يكون ذلك مشروعًا؛ تحقق من سبب تفعيله.",
                        priority = ReviewPriority.MEDIUM,
                        installSource = installSource(packageName)
                    )
                )
            }

            @Suppress("DEPRECATION")
            val activeVpnDetected: Boolean? = try {
                val connectivity = context.getSystemService(
                    Context.CONNECTIVITY_SERVICE
                ) as ConnectivityManager
                connectivity.allNetworks.any { network ->
                    connectivity.getNetworkCapabilities(network)
                        ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                }
            } catch (_: Exception) {
                scanWarnings.add("تعذر التحقق من وجود اتصال VPN نشط.")
                null
            }

            SecurityAuditReport(
                findings = findings.distinctBy { "${it.packageName}:${it.category}" },
                microphonePermissionApps = microphonePermissionApps.distinct(),
                enabledAccessibilityApps = enabledAccessibilityApps.distinct(),
                scanWarnings = scanWarnings.distinct(),
                activeVpnDetected = activeVpnDetected
            )
        }
}
