package com.racha.ai.services.routine

import android.content.Context
import android.util.Log
import org.json.JSONObject
import kotlin.random.Random

/**
 * يقرأ رسائل الروتين الودّية من assets.
 */
object RoutineMessagesProvider {

    private const val TAG = "RachaRoutineMsgs"
    private const val ASSET_FILE = "routine_messages.json"

    private var cachedData: JSONObject? = null

    private fun loadData(context: Context): JSONObject? {
        cachedData?.let { return it }
        return try {
            val json = context.assets.open(ASSET_FILE)
                .bufferedReader()
                .use { it.readText() }
            JSONObject(json).also { cachedData = it }
        } catch (e: Exception) {
            Log.e(TAG, "فشل تحميل رسائل الروتين", e)
            null
        }
    }

    /**
     * جلب رسالة ودّية.
     *
     * @param category: "water", "meal", "sport", "sleep"
     * @param subType: للنوم ("bedtime", "wakeup")، للوجبات ("breakfast", "lunch", "dinner")
     *                للماء حسب الوقت ("morning", "midday", "afternoon", "evening")
     */
    fun getMessage(context: Context, category: String, subType: String): String? {
        val data = loadData(context) ?: return null
        return try {
            val categoryObj = data.optJSONObject(category) ?: return null
            val messages = categoryObj.optJSONArray(subType) ?: return null
            if (messages.length() == 0) return null
            val index = Random.nextInt(messages.length())
            messages.getString(index)
        } catch (e: Exception) {
            Log.e(TAG, "فشل استخراج الرسالة", e)
            null
        }
    }

    /**
     * هل هذا عنصر ماء؟
     */
    fun isWaterItem(title: String): Boolean {
        return title.contains("ماء", ignoreCase = true) ||
               title.contains("water", ignoreCase = true)
    }

    /**
     * هل هذا عنصر وجبة؟
     */
    fun isMealItem(title: String): Boolean {
        return title.contains("فطور", true) ||
               title.contains("غداء", true) ||
               title.contains("عشاء", true) ||
               title.contains("فطور", true) ||
               title.contains("breakfast", true) ||
               title.contains("lunch", true) ||
               title.contains("dinner", true)
    }

    /**
     * هل هذا عنصر رياضة؟
     */
    fun isSportItem(title: String): Boolean {
        return title.contains("تمرين", true) ||
               title.contains("رياضة", true) ||
               title.contains("جري", true) ||
               title.contains("sport", true) ||
               title.contains("exercise", true)
    }

    /**
     * هل هذا عنصر نوم؟
     */
    fun isSleepItem(title: String): Boolean {
        return title.contains("نوم", true) ||
               title.contains("نوم", true) ||
               title.contains("استعداد للنوم", true) ||
               title.contains("sleep", true)
    }

    /**
     * تحديد النوع الفرعي حسب الوقت.
     */
    fun getSubTypeByTime(itemTitle: String, hour: Int): String {
        return when {
            isWaterItem(itemTitle) -> when (hour) {
                in 5..11 -> "morning"
                in 12..16 -> "midday"
                in 17..20 -> "afternoon"
                else -> "evening"
            }
            isMealItem(itemTitle) -> when {
                itemTitle.contains("فطور", true) ||
                itemTitle.contains("breakfast", true) -> "breakfast"
                itemTitle.contains("غداء", true) ||
                itemTitle.contains("lunch", true) -> "lunch"
                else -> "dinner"
            }
            isSportItem(itemTitle) -> "before"
            isSleepItem(itemTitle) -> "bedtime"
            else -> ""
        }
    }

    /**
     * الفئة الرئيسية.
     */
    fun getCategory(itemTitle: String): String? {
        return when {
            isWaterItem(itemTitle) -> "water"
            isMealItem(itemTitle) -> "meal"
            isSportItem(itemTitle) -> "sport"
            isSleepItem(itemTitle) -> "sleep"
            else -> null
        }
    }
}
