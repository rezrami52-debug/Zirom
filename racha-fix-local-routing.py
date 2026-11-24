#!/usr/bin/env python3
"""Apply targeted local-routing and screen-read fixes to the Racha Android project.

The script validates every expected source anchor before writing any file, creates
one .bak.local-routing backup per changed file, and aborts on source drift.
"""
from pathlib import Path
import shutil
import sys

ROOT = Path.cwd()
FILES = {
    "command": ROOT / "app/src/main/java/com/racha/ai/core/command/RachaCommand.kt",
    "router": ROOT / "app/src/main/java/com/racha/ai/core/command/RachaCommandRouter.kt",
    "network": ROOT / "app/src/main/java/com/racha/ai/core/security/NetworkUsageManager.kt",
    "accessibility": ROOT / "app/src/main/java/com/racha/ai/core/eve/EveAccessibilityService.kt",
    "intent": ROOT / "app/src/main/java/com/racha/ai/core/apps/IntentHandler.kt",
    "manifest": ROOT / "app/src/main/AndroidManifest.xml",
    "tests": ROOT / "app/src/test/java/com/racha/ai/core/command/RachaCommandRouterTest.kt",
}

missing = [str(path.relative_to(ROOT)) for path in FILES.values() if not path.is_file()]
if missing:
    print("لم أعدّل أي ملف؛ الملفات التالية غير موجودة:")
    print("\n".join(missing))
    sys.exit(1)

texts = {key: path.read_text(encoding="utf-8") for key, path in FILES.items()}


def replace_once(key: str, old: str, new: str, label: str) -> None:
    count = texts[key].count(old)
    if count != 1:
        raise RuntimeError(f"{label}: توقعت مقطعًا واحدًا مطابقًا ووجدت {count}؛ لم تُكتب أي تغييرات.")
    texts[key] = texts[key].replace(old, new, 1)


# Explicit command types ensure typed security/data requests bypass the AI branch.
replace_once(
    "command",
    "    data object ReadScreen : RachaCommand\n",
    "    data object ReadScreen : RachaCommand\n"
    "    data object CheckSecurity : RachaCommand\n"
    "    data object CheckDataUsage : RachaCommand\n",
    "إضافة أنواع الأوامر المحلية",
)

replace_once(
    "router",
    """        if (text.isBlank()) {
            return RachaCommand.Unknown
        }

        if (isNearestPavedRoadRequest(lower)) {
""",
    """        if (text.isBlank()) {
            return RachaCommand.Unknown
        }

        if (isSecurityAuditRequest(lower)) return RachaCommand.CheckSecurity
        if (isDataUsageRequest(lower)) return RachaCommand.CheckDataUsage

        if (isNearestPavedRoadRequest(lower)) {
""",
    "توجيه أوامر الفحص قبل AskAI",
)

replace_once(
    "router",
    "    private fun isNearestPavedRoadRequest(text: String): Boolean {\n",
    """    private fun isSecurityAuditRequest(text: String): Boolean {
        val normalized = normalizeArabic(text)
        return listOf(
            "تجسس", "تنصت", "مراقب", "مراقبة", "فحص الامان", "فحص الامن",
            "حماية الهاتف", "هل الهاتف امن", "هل هاتفي امن"
        ).any { normalized.contains(it) }
    }

    private fun isDataUsageRequest(text: String): Boolean {
        val normalized = normalizeArabic(text)
        val asksAboutUsage = listOf(
            "استهلاك", "يستهلك", "تستهلك", "يستهلكون", "قداش كليت",
            "كونسوماسيون", "كمية البيانات"
        ).any { normalized.contains(it) }
        val mentionsNetworkData = listOf(
            "انترنت", "انترنات", "النت", "بيانات", "data", "كونيكسيون"
        ).any { normalized.contains(it) }
        return asksAboutUsage && mentionsNetworkData
    }

    private fun normalizeArabic(text: String): String {
        return text.lowercase()
            .replace(Regex("[\\u064B-\\u065F\\u0670\\u0640]"), "")
            .replace(Regex("[أإآٱ]"), "ا")
            .replace(Regex("\\\\s+"), " ")
            .trim()
    }

    private fun isNearestPavedRoadRequest(text: String): Boolean {
""",
    "إضافة تصنيف محلي للأمان والبيانات",
)

# Make explicit screen reading fetch the current active window, including Racha,
# and return null instead of a stale snapshot when there is no readable content.
replace_once(
    "accessibility",
    """    fun getLatestScreenSnapshot(): ScreenSnapshot? {
        updateCurrentScreenSnapshot()
        return latestSnapshot
    }
""",
    """    fun readCurrentWindowSnapshot(): ScreenSnapshot? {
        val rootNode = rootInActiveWindow ?: return null
        val pkgName = rootNode.packageName?.toString() ?: return null
        if (pkgName == "com.android.systemui") return null

        val appName = try {
            val appInfo = packageManager.getApplicationInfo(pkgName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            pkgName
        }

        val extractedTexts = mutableListOf<String>()
        extractTextFromNode(rootNode, extractedTexts)
        val fullText = extractedTexts
            .filter { it.isNotBlank() && it.length > 2 }
            .distinct()
            .joinToString("\\n")
            .take(2000)

        if (fullText.isBlank()) return null
        return ScreenSnapshot(
            packageName = pkgName,
            appName = appName,
            visibleText = fullText
        )
    }

    fun getLatestScreenSnapshot(): ScreenSnapshot? {
        updateCurrentScreenSnapshot()
        return latestSnapshot
    }
""",
    "إضافة التقاط آني لمحتوى النافذة",
)

replace_once(
    "intent",
    """            lower.contains("ماذا ترى") || lower.contains("اقرأ الشاشة") -> {
                val snapshot = EveAccessibilityService.instance?.getLatestScreenSnapshot()
""",
    """            lower.contains("ماذا ترى") || lower.contains("اقرأ الشاشة") ||
                lower.contains("اقرئي الشاشة") || lower.contains("شوف الشاشة") ||
                lower.contains("شوفي الشاشة") || lower.contains("حللي الشاشة") -> {
                val snapshot = EveAccessibilityService.instance?.readCurrentWindowSnapshot()
""",
    "قراءة الشاشة عند الطلب بدل اللقطة القديمة",
)

# Explicitly declare and verify usage access; never silently report an empty list
# as a successful low-data result when Android denied the query.
replace_once(
    "network",
    "import android.app.usage.NetworkStats\n",
    "import android.app.AppOpsManager\nimport android.app.usage.NetworkStats\n",
    "استيراد AppOpsManager",
)
replace_once(
    "network",
    "import android.os.Build\n",
    "import android.os.Build\nimport android.os.Process\n",
    "استيراد Process",
)
replace_once(
    "network",
    """    private fun ensureUsageStatsPermission() {
        if (ShizukuShellRunner.isShizukuAvailable()) {
            ShizukuShellRunner.execCommand("appops set ${context.packageName} GET_USAGE_STATS allow")
        }
    }
""",
    """    private fun ensureUsageStatsPermission() {
        if (ShizukuShellRunner.isShizukuAvailable()) {
            ShizukuShellRunner.execCommand("appops set ${context.packageName} GET_USAGE_STATS allow")
        }

        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        val mode = try {
            appOps?.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } catch (_: Exception) {
            null
        }
        if (mode != AppOpsManager.MODE_ALLOWED) {
            throw SecurityException(
                "إذن إحصاءات الاستخدام غير مفعّل. فعّل وصول الاستخدام لـ Racha من إعدادات التطبيقات الخاصة."
            )
        }
    }
""",
    "التحقق الفعلي من صلاحية إحصاءات الاستخدام",
)
replace_once(
    "network",
    "        if (networkStatsManager == null) return@withContext emptyList()\n",
    "        if (networkStatsManager == null) throw IllegalStateException(\"NetworkStatsManager غير متاح\")\n",
    "رفض نتيجة فارغة عند غياب مدير الشبكة",
)
replace_once(
    "network",
    """        // 1. استهلاك الواي فاي (Wi-Fi)
        accumulateTraffic(NetworkStats.Bucket.DEFAULT_NETWORK_ALL, ConnectivityManager.TYPE_WIFI, startTime, endTime, usageMap)

        // 2. استهلاك بيانات الهاتف (Mobile Data)
        accumulateTraffic(NetworkStats.Bucket.DEFAULT_NETWORK_ALL, ConnectivityManager.TYPE_MOBILE, startTime, endTime, usageMap)
""",
    """        // استهلاك الواي فاي وبيانات الهاتف منذ منتصف الليل.
        accumulateTraffic(ConnectivityManager.TYPE_WIFI, startTime, endTime, usageMap)
        accumulateTraffic(ConnectivityManager.TYPE_MOBILE, startTime, endTime, usageMap)
""",
    "تبسيط استدعاءات ملخص الشبكة",
)
replace_once(
    "network",
    """    private fun accumulateTraffic(
        networkType: Int,
        transportType: Int,
        startTime: Long,
        endTime: Long,
        outMap: MutableMap<Int, Long>
    ) {
        try {
            val bucket = NetworkStats.Bucket()
            val stats = networkStatsManager?.querySummary(transportType, null, startTime, endTime)
            while (stats?.hasNextBucket() == true) {
                stats.getNextBucket(bucket)
                val uid = bucket.uid
                val bytes = bucket.rxBytes + bucket.txBytes
                outMap[uid] = (outMap[uid] ?: 0L) + bytes
            }
            stats?.close()
        } catch (_: Exception) {}
    }
""",
    """    private fun accumulateTraffic(
        networkType: Int,
        startTime: Long,
        endTime: Long,
        outMap: MutableMap<Int, Long>
    ) {
        val stats = networkStatsManager?.querySummary(networkType, null, startTime, endTime)
            ?: throw IllegalStateException("تعذر فتح إحصاءات الشبكة")
        try {
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val uid = bucket.uid
                val bytes = bucket.rxBytes + bucket.txBytes
                outMap[uid] = (outMap[uid] ?: 0L) + bytes
            }
        } finally {
            stats.close()
        }
    }
""",
    "عدم ابتلاع أخطاء استعلام الشبكة",
)

# Caller-facing messages now distinguish permission/query errors from low usage.
usage_error_block = """val topApps = try {
                networkUsageManager.getTodayTopDataConsumers(5)
            } catch (e: SecurityException) {
                return@withContext HandlerResult.Error("لا أستطيع قراءة إحصاءات البيانات: فعّل وصول الاستخدام لـ Racha من إعدادات Android.")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "تعذر قراءة إحصاءات الشبكة", e)
                return@withContext HandlerResult.Error("تعذر قراءة إحصاءات استهلاك البيانات من Android.")
            }"""
# Preserve indentation of both occurrences by replacing only the statement text.
old_usage_call = "val topApps = networkUsageManager.getTodayTopDataConsumers(5)"
if texts["intent"].count(old_usage_call) != 2:
    raise RuntimeError("استدعاء NetworkUsageManager: توقعت موضعين؛ لم تُكتب أي تغييرات.")
texts["intent"] = texts["intent"].replace(old_usage_call, usage_error_block)
replace_once(
    "intent",
    "HandlerResult.DirectReply(\"📊 استهلاك الإنترنت اليوم خفيف جداً، ولم يتجاوز أي تطبيق 1 ميغابايت$suffix.\")",
    "HandlerResult.DirectReply(\"📊 لم أعثر على تطبيق تجاوز 1 ميغابايت منذ منتصف الليل حتى الآن$suffix.\")",
    "تصحيح رسالة عدم وجود تطبيقات فوق الحد",
)
replace_once(
    "intent",
    "HandlerResult.DirectReply(\"📊 استهلاك الإنترنت منخفض جداً اليوم$suffix.\")",
    "HandlerResult.DirectReply(\"📊 لم أعثر على تطبيق تجاوز 1 ميغابايت منذ منتصف الليل حتى الآن$suffix.\")",
    "تصحيح رسالة نتيجة الاستخدام الفارغة",
)

replace_once(
    "manifest",
    "    <uses-permission android:name=\"android.permission.ACCESS_NETWORK_STATE\" />\n",
    "    <uses-permission android:name=\"android.permission.ACCESS_NETWORK_STATE\" />\n"
    "    <uses-permission android:name=\"android.permission.PACKAGE_USAGE_STATS\" />\n",
    "إضافة تصريح PACKAGE_USAGE_STATS",
)

# Add regression tests without replacing the existing router tests.
test_additions = """
    @Test
    fun routesTypedDataUsageRequestLocally() {
        assertEquals(
            RachaCommand.CheckDataUsage,
            RachaCommandRouter.classify("أكثر التطبيقات استهلاكًا للإنترنت اليوم")
        )
    }

    @Test
    fun routesSecurityAuditRequestLocally() {
        assertEquals(
            RachaCommand.CheckSecurity,
            RachaCommandRouter.classify("فحص الأمان")
        )
    }
"""
if "routesTypedDataUsageRequestLocally" in texts["tests"] or "routesSecurityAuditRequestLocally" in texts["tests"]:
    raise RuntimeError("اختبارات التوجيه الجديدة موجودة مسبقًا؛ لم تُكتب أي تغييرات.")
closing = texts["tests"].rfind("\n}")
if closing < 0:
    raise RuntimeError("لم أجد نهاية class في ملف اختبارات الموجّه؛ لم تُكتب أي تغييرات.")
texts["tests"] = texts["tests"][:closing] + test_additions + texts["tests"][closing:]

# Write only after all source anchors have validated successfully.
changed = [key for key, path in FILES.items() if texts[key] != path.read_text(encoding="utf-8")]
for key in changed:
    path = FILES[key]
    backup = path.with_name(path.name + ".bak.local-routing")
    if backup.exists():
        raise RuntimeError(f"النسخة الاحتياطية موجودة بالفعل: {backup}; لم تُكتب أي تغييرات.")

for key in changed:
    path = FILES[key]
    shutil.copy2(path, path.with_name(path.name + ".bak.local-routing"))
for key in changed:
    FILES[key].write_text(texts[key], encoding="utf-8")

print("تم تطبيق إصلاحات التوجيه المحلي وقراءة الشاشة وصلاحية بيانات الاستخدام على:")
for key in changed:
    print(f"- {FILES[key].relative_to(ROOT)}")
print("\nالنسخ الاحتياطية محفوظة بجانب الملفات بامتداد .bak.local-routing")
print("نفّذ الآن: git diff --check، ثم ارفع التعديلات لتشغيل GitHub Actions.")
