#!/usr/bin/env python3
"""Correct Racha's security audit semantics and make its reply explanatory.

Validates all required anchors before writing, then creates sibling backups.
"""
from pathlib import Path
import shutil
import sys

ROOT = Path.cwd()
DETECTOR = ROOT / "app/src/main/java/com/racha/ai/core/security/SpywareDetector.kt"
HANDLER = ROOT / "app/src/main/java/com/racha/ai/core/apps/IntentHandler.kt"

for path in (DETECTOR, HANDLER):
    if not path.is_file():
        print(f"لم أعدّل أي ملف؛ الملف غير موجود: {path.relative_to(ROOT)}")
        sys.exit(1)

texts = {
    "detector": DETECTOR.read_text(encoding="utf-8"),
    "handler": HANDLER.read_text(encoding="utf-8"),
}


def replace_once(key: str, old: str, new: str, label: str) -> None:
    count = texts[key].count(old)
    if count != 1:
        raise RuntimeError(f"{label}: توقعت تطابقًا واحدًا ووجدت {count}؛ لم تُكتب أي تغييرات.")
    texts[key] = texts[key].replace(old, new, 1)


# Model: do not label allowed microphone permission as current microphone activity.
replace_once(
    "detector",
    "import com.racha.ai.core.shizuku.ShizukuShellRunner\n",
    "",
    "إزالة اعتماد فحص الميكروفون على Shizuku",
)
replace_once(
    "detector",
    """data class SecurityAuditReport(
    val isClean: Boolean,
    val threatsFound: List<SecurityThreat>,
    val activeMicApps: List<String>,
    val suspiciousAccessibilityApps: List<String>,
    val hiddenApps: List<String>
)
""",
    """data class SecurityAuditReport(
    val threatsFound: List<SecurityThreat>,
    val micPermissionApps: List<String>,
    val suspiciousAccessibilityApps: List<String>,
    val hiddenApps: List<String>,
    val scanWarnings: List<String> = emptyList()
)
""",
    "إعادة تسمية نتيجة الميكروفون وإضافة تحذيرات اكتمال الفحص",
)
replace_once(
    "detector",
    """            val threats = mutableListOf<SecurityThreat>()
            val accessibilityApps = mutableListOf<String>()
            val activeMic = mutableListOf<String>()
            val pm = context.packageManager
""",
    """            val threats = mutableListOf<SecurityThreat>()
            val accessibilityApps = mutableListOf<String>()
            val micPermissionApps = mutableListOf<String>()
            val scanWarnings = mutableListOf<String>()
            val pm = context.packageManager
""",
    "تجهيز قوائم الصلاحيات والتحذيرات",
)
replace_once(
    "detector",
    """            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
""",
    """            val enabledServices = try {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: ""
            } catch (_: Exception) {
                scanWarnings.add("تعذر قراءة إعداد خدمات إمكانية الوصول.")
                ""
            }
""",
    "إظهار تعذر قراءة إعداد إمكانية الوصول بدل إخفائه",
)
replace_once(
    "detector",
    """            val installedApps = try {
                pm.getInstalledApplications(
                    PackageManager.GET_META_DATA
                )
            } catch (_: Exception) {
                emptyList()
            }
""",
    """            val installedApps = try {
                pm.getInstalledApplications(
                    PackageManager.GET_META_DATA
                )
            } catch (_: Exception) {
                scanWarnings.add("تعذر تعداد التطبيقات المثبتة؛ نتيجة الفحص غير مكتملة.")
                emptyList()
            }
""",
    "عدم اعتبار فشل تعداد التطبيقات فحصًا نظيفًا",
)
replace_once(
    "detector",
    """                } catch (_: Exception) {
                    emptyList()
                }
            }

            fun isSystemApp(app: ApplicationInfo): Boolean {
""",
    """                } catch (_: Exception) {
                    scanWarnings.add("تعذر التحقق من بعض الأذونات المطلوبة للتطبيقات.")
                    emptyList()
                }
            }

            fun isSystemApp(app: ApplicationInfo): Boolean {
""",
    "تسجيل فشل قراءة الأذونات المطلوبة",
)
replace_once(
    "detector",
    """                val permissions = sensitivePermissions(packageName)
                val source = installSource(packageName)
""",
    """                val permissions = sensitivePermissions(packageName)
                val hasGrantedMicPermission = pm.checkPermission(
                    "android.permission.RECORD_AUDIO",
                    packageName
                ) == PackageManager.PERMISSION_GRANTED
                if (hasGrantedMicPermission) {
                    micPermissionApps.add(name)
                }
                val source = installSource(packageName)
""",
    "جمع التطبيقات التي مُنح لها إذن الميكروفون فقط",
)

# Remove the unsafe shell-output regex: it could parse Shizuku error text as a package.
start_marker = "            if (ShizukuShellRunner.isShizukuAvailable()) {\n"
end_marker = "\n            val hiddenApps = threats"
start = texts["detector"].find(start_marker)
end = texts["detector"].find(end_marker, start + len(start_marker)) if start >= 0 else -1
if start < 0 or end < 0:
    raise RuntimeError("كتلة AppOps/Shizuku لم تطابق المصدر؛ لم تُكتب أي تغييرات.")
texts["detector"] = texts["detector"][:start] + texts["detector"][end + 1:]

replace_once(
    "detector",
    """            val activeAdmins = try {
                dpm?.activeAdmins ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
""",
    """            val activeAdmins = try {
                dpm?.activeAdmins ?: emptyList()
            } catch (_: Exception) {
                scanWarnings.add("تعذر فحص مسؤولي الجهاز؛ نتيجة الفحص غير مكتملة.")
                emptyList()
            }
""",
    "إظهار فشل فحص مسؤولي الجهاز",
)
replace_once(
    "detector",
    """            SecurityAuditReport(
                isClean = threats.isEmpty(),
                threatsFound = threats.distinctBy {
                    "${it.packageName}:${it.threatType}"
                },
                activeMicApps = activeMic.distinct(),
                suspiciousAccessibilityApps =
                    accessibilityApps.distinct(),
                hiddenApps = hiddenApps.distinct()
            )
""",
    """            SecurityAuditReport(
                threatsFound = threats.distinctBy {
                    "${it.packageName}:${it.threatType}"
                },
                micPermissionApps = micPermissionApps.distinct(),
                suspiciousAccessibilityApps =
                    accessibilityApps.distinct(),
                hiddenApps = hiddenApps.distinct(),
                scanWarnings = scanWarnings.distinct()
            )
""",
    "تجميع تقرير يميز بين النتائج والتحذيرات",
)

# Unify the two security reply paths and explain what the evidence does/doesn't mean.
replace_once(
    "handler",
    "import com.racha.ai.core.security.SpywareDetector\n",
    "import com.racha.ai.core.security.SecurityAuditReport\nimport com.racha.ai.core.security.SpywareDetector\n",
    "استيراد نموذج التقرير الأمني",
)
security_start = "        // 1. فحص التنصت والمراقبة والأمان\n"
security_end = "\n        // 2. فحص استهلاك الإنترنت والبيانات\n"
start = texts["handler"].find(security_start)
end = texts["handler"].find(security_end, start + len(security_start)) if start >= 0 else -1
if start < 0 or end < 0:
    raise RuntimeError("مسار فحص الأمان الأول لم يطابق المصدر؛ لم تُكتب أي تغييرات.")
texts["handler"] = (
    texts["handler"][:start]
    + """        // فحص أمني محلي محدود؛ لا يُرسل السؤال إلى النموذج اللغوي.
        if (lower.contains("تنصت") || lower.contains("تجسس") ||
            lower.contains("مراقب") || lower.contains("مراقبة") ||
            lower.contains("فحص الامان") || lower.contains("فحص الأمان")) {
            val report = spywareDetector.runFullSecurityAudit()
            return@withContext buildSecurityAuditReply(report)
        }
"""
    + texts["handler"][end:]
)

when_start = "            IntentClassifier.IntentType.CHECK_SPYWARE -> {\n"
when_end = "\n            IntentClassifier.IntentType.CHECK_DATA_USAGE -> {\n"
start = texts["handler"].find(when_start)
end = texts["handler"].find(when_end, start + len(when_start)) if start >= 0 else -1
if start < 0 or end < 0:
    raise RuntimeError("مسار CHECK_SPYWARE في IntentClassifier لم يطابق المصدر؛ لم تُكتب أي تغييرات.")
texts["handler"] = (
    texts["handler"][:start]
    + """            IntentClassifier.IntentType.CHECK_SPYWARE -> {
                buildSecurityAuditReply(spywareDetector.runFullSecurityAudit())
            }"""
    + texts["handler"][end:]
)

helper_anchor = "    private fun handleAdvancedCommands(text: String): HandlerResult {\n"
helper = """    private fun buildSecurityAuditReply(report: SecurityAuditReport): HandlerResult {
        val suffix = getFriendlyName().let { if (it.isNotBlank()) " $it" else "" }
        val sb = StringBuilder("🛡️ خلاصة فحص أمني محدود$suffix:\\n")

        if (report.threatsFound.isEmpty()) {
            sb.append("لم أرصد مؤشرًا واضحًا ضمن الفحوص التي أمكن تنفيذها. هذا لا يثبت خلو الهاتف من كل تهديد.\\n")
        } else {
            sb.append("وجدت إعدادات أو خدمات تستحق المراجعة؛ وجودها لا يثبت التجسس: \\n")
            for (threat in report.threatsFound.take(8)) {
                val severityText = when (threat.severity) {
                    com.racha.ai.core.security.ThreatSeverity.HIGH -> "مرتفع"
                    com.racha.ai.core.security.ThreatSeverity.MEDIUM -> "متوسط"
                    com.racha.ai.core.security.ThreatSeverity.LOW -> "منخفض"
                }
                sb.append("• ${threat.appName}: ${threat.description} (أولوية المراجعة: $severityText).\\n")
            }
            if (report.threatsFound.size > 8) {
                sb.append("• توجد نتائج إضافية لم أعرضها هنا.\\n")
            }
        }

        if (report.micPermissionApps.isNotEmpty()) {
            sb.append(
                "• إذن الميكروفون ممنوح لهذه التطبيقات: " +
                    report.micPermissionApps.take(8).joinToString("، ") +
                    ". هذا لا يعني أنها تستخدمه الآن.\\n"
            )
        }

        if (report.scanWarnings.isNotEmpty()) {
            sb.append("• لم يكتمل الفحص: ${report.scanWarnings.joinToString("؛ ")}\\n")
            sb.append("لذلك لا أستطيع وصف الهاتف بأنه آمن بناءً على هذه النتيجة.")
        } else if (report.threatsFound.isEmpty()) {
            sb.append("الفحص اقتصر على بعض الإعدادات والصلاحيات، وليس تحليلًا شاملًا للبرمجيات الخبيثة.")
        }

        return HandlerResult.DirectReply(sb.toString().trim())
    }

"""
if texts["handler"].count(helper_anchor) != 1:
    raise RuntimeError("موضع إدراج ملخص التقرير الأمني لم يطابق المصدر؛ لم تُكتب أي تغييرات.")
texts["handler"] = texts["handler"].replace(helper_anchor, helper + helper_anchor, 1)

# Check all backups before writing either source file.
for path in (DETECTOR, HANDLER):
    backup = path.with_name(path.name + ".bak.security-report")
    if backup.exists():
        print(f"لم أكتب التغييرات؛ توجد نسخة احتياطية سابقة: {backup}")
        sys.exit(1)

shutil.copy2(DETECTOR, DETECTOR.with_name(DETECTOR.name + ".bak.security-report"))
shutil.copy2(HANDLER, HANDLER.with_name(HANDLER.name + ".bak.security-report"))
DETECTOR.write_text(texts["detector"], encoding="utf-8")
HANDLER.write_text(texts["handler"], encoding="utf-8")

print("تم تحديث فحص الأمان وصياغة رده في الملفين:")
print(f"- {DETECTOR.relative_to(ROOT)}")
print(f"- {HANDLER.relative_to(ROOT)}")
print("أزيل تحليل نص مخرجات Shizuku كأنه أسماء تطبيقات، وأصبحت القائمة تعني أذونات ميكروفون ممنوحة لا استخدامًا جاريًا.")
print("النسخ الاحتياطية محفوظة بامتداد .bak.security-report")
print("نفّذ git diff --check، ثم اختبارات GitHub Actions قبل تثبيت APK الجديد.")
