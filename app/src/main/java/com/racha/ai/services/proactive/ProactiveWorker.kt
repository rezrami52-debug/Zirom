package com.racha.ai.services.proactive

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.racha.ai.core.eve.EveState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ProactiveWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // 1. جلب الأخبار (مثال: NewsAPI)
            val news = fetchNews()
            if (news.isNotEmpty()) {
                // 2. إرسال إشعار (عبر EveState)
                EveState.addNotification()
                // 3. حفظ الخبر في الذاكرة (يمكن استخدام Room)
                // TODO: حفظ الخبر
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private suspend fun fetchNews(): String {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = "YOUR_NEWS_API_KEY" // استبدلها بمفتاحك
                val url = URL("https://newsapi.org/v2/top-headlines?country=us&apiKey=$apiKey")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val articles = json.getJSONArray("articles")
                if (articles.length() > 0) {
                    val firstArticle = articles.getJSONObject(0)
                    firstArticle.getString("title")
                } else ""
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }
    }
}
