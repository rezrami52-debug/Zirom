package com.racha.ai.services.prayer

import android.content.Context
import android.util.Log
import org.json.JSONObject
import kotlin.random.Random

/**
 * يقرأ محتوى إشعارات الصلاة من assets/prayer_messages.json.
 */
object PrayerMessagesProvider {

    private const val TAG = "RachaPrayerMessages"
    private const val ASSET_FILE = "prayer_messages.json"

    private var cachedData: JSONObject? = null

    /**
     * تحميل البيانات (مرة واحدة).
     */
    private fun loadData(context: Context): JSONObject? {
        cachedData?.let { return it }

        return try {
            val json = context.assets.open(ASSET_FILE)
                .bufferedReader()
                .use { it.readText() }
            val obj = JSONObject(json)
            cachedData = obj
            obj
        } catch (e: Exception) {
            Log.e(TAG, "فشل تحميل المحتوى", e)
            null
        }
    }

    /**
     * جلب رسالة عشوائية.
     *
     * @param prayerKey: "fajr", "dhuhr", "asr", "maghrib", "isha"
     * @param type: "before", "at_time", "delayed"
     */
    fun getMessage(context: Context, prayerKey: String, type: String): String? {
        val data = loadData(context) ?: return null

        return try {
            val prayerObj = data.optJSONObject(prayerKey) ?: return null
            val messages = prayerObj.optJSONArray(type) ?: return null
            if (messages.length() == 0) return null

            val index = Random.nextInt(messages.length())
            val messageObj = messages.getJSONObject(index)
            messageObj.getString("text")
        } catch (e: Exception) {
            Log.e(TAG, "فشل استخراج الرسالة", e)
            null
        }
    }

    /**
     * تحويل اسم الصلاة العربي إلى key.
     */
    fun getPrayerKey(prayerName: String): String = when (prayerName) {
        "الفجر", "fajr" -> "fajr"
        "الظهر", "dhuhr" -> "dhuhr"
        "العصر", "asr" -> "asr"
        "المغرب", "maghrib" -> "maghrib"
        "العشاء", "isha" -> "isha"
        else -> "fajr"
    }
}
