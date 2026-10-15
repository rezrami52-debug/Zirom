package com.racha.ai.core.apps

import android.util.Log

/**
 * يعرض قائمة التطبيقات المثبتة حسب الفئة.
 */
class AppListHandler(private val appResolver: AppResolver) {

    companion object {
        private const val TAG = "RachaAppList"
    }

    /**
     * هل السؤال عن قائمة التطبيقات؟
     */
    fun isAppListQuestion(input: String): Boolean {
        val normalized = input.lowercase().trim()
        return (normalized.contains("تطبيقات") || normalized.contains("تطبيق")) &&
               (normalized.contains("تفقد") || normalized.contains("اعرض") ||
                normalized.contains("قائمه") || normalized.contains("قائمة") ||
                normalized.contains("ما هي") || normalized.contains("الموجوده") ||
                normalized.contains("المثبته") || normalized.contains("المثبتة"))
    }

    /**
     * عرض قائمة الفئات.
     */
    fun listCategories(): String {
        val sb = StringBuilder()
        sb.append("📱 تطبيقاتك حسب الفئة:\n\n")

        val categories = listOf(
            AppResolver.AppCategory.MAPS,
            AppResolver.AppCategory.MESSAGING,
            AppResolver.AppCategory.EMAIL,
            AppResolver.AppCategory.BROWSER,
            AppResolver.AppCategory.CAMERA,
            AppResolver.AppCategory.MUSIC,
            AppResolver.AppCategory.VIDEO,
            AppResolver.AppCategory.NOTES,
            AppResolver.AppCategory.CALENDAR,
            AppResolver.AppCategory.PHONE
        )

        var totalApps = 0
        for (cat in categories) {
            val apps = appResolver.findAppsForCategory(cat)
            if (apps.isNotEmpty()) {
                sb.append("${getCategoryEmoji(cat)} ${cat.arabic}:\n")
                for (app in apps) {
                    sb.append("  • ${app.appName}\n")
                }
                sb.append("\n")
                totalApps += apps.size
            }
        }

        if (totalApps == 0) {
            sb.append("⚠️ لم أجد تطبيقات في الفئات المعروفة.\n")
            sb.append("جرّب إعطاء الصلاحية لـ Racha في الإعدادات.")
        } else {
            sb.append("📊 المجموع: $totalApps تطبيق")
        }

        return sb.toString()
    }

    private fun getCategoryEmoji(cat: AppResolver.AppCategory): String {
        return when (cat) {
            AppResolver.AppCategory.MAPS -> "🗺️"
            AppResolver.AppCategory.MESSAGING -> "💬"
            AppResolver.AppCategory.EMAIL -> "📧"
            AppResolver.AppCategory.PHONE -> "📞"
            AppResolver.AppCategory.CAMERA -> "📷"
            AppResolver.AppCategory.MUSIC -> "🎵"
            AppResolver.AppCategory.VIDEO -> "📺"
            AppResolver.AppCategory.BROWSER -> "🌐"
            AppResolver.AppCategory.NOTES -> "📝"
            AppResolver.AppCategory.CALENDAR -> "📅"
            else -> "📱"
        }
    }
}
