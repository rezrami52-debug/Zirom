package com.racha.ai.core.proactive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class NewsArticle(
    val title: String,
    val source: String,
    val publishedAtMillis: Long
)

object NewsFetcher {
    private const val RSS_URL = "https://news.google.com/rss?hl=ar&gl=SA&ceid=SA:ar"
    private const val MAX_FEED_ITEMS = 60
    internal const val MAX_ARTICLE_AGE_MILLIS = 36L * 60L * 60L * 1000L
    private const val ALLOWED_FUTURE_SKEW_MILLIS = 10L * 60L * 1000L

    suspend fun fetchTopHeadlines(limit: Int = 5): List<NewsArticle> = withContext(Dispatchers.IO) {
        if (limit <= 0) return@withContext emptyList()
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(RSS_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "RachaAI/1.0 (Android; RSS reader)")
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext emptyList()
            }

            val fetchedAt = System.currentTimeMillis()
            val parsed = connection.inputStream.use { parseRss(it, MAX_FEED_ITEMS) }
            keepRecent(parsed, fetchedAt).take(limit)
        } catch (e: Exception) {
            android.util.Log.w("NewsFetcher", "تعذر جلب موجز RSS حديث", e)
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }

    internal fun parseRss(inputStream: InputStream, limit: Int): List<NewsArticle> {
        if (limit <= 0) return emptyList()
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(inputStream, "UTF-8")

        data class ItemBuilder(
            val title: StringBuilder = StringBuilder(),
            val source: StringBuilder = StringBuilder(),
            val pubDate: StringBuilder = StringBuilder()
        )

        val results = mutableListOf<NewsArticle>()
        var item: ItemBuilder? = null
        var activeField: String? = null
        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT && results.size < limit) {
            val tag = parser.name?.lowercase(Locale.ROOT).orEmpty()
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (tag == "item") {
                        item = ItemBuilder()
                        activeField = null
                    } else if (item != null && tag in setOf("title", "source", "pubdate")) {
                        activeField = tag
                    }
                }
                XmlPullParser.TEXT -> {
                    val currentItem = item
                    val field = activeField
                    if (currentItem != null && field != null) {
                        val destination = when (field) {
                            "title" -> currentItem.title
                            "source" -> currentItem.source
                            "pubdate" -> currentItem.pubDate
                            else -> null
                        }
                        destination?.append(parser.text.orEmpty())
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (item != null && tag == activeField) activeField = null
                    if (tag == "item") {
                        val completed = item
                        val title = completed?.title?.toString()?.trim().orEmpty()
                        val publishedAt = completed?.pubDate?.toString()?.trim()?.let(::parseRssDate)
                        if (title.isNotBlank() && publishedAt != null) {
                            results += NewsArticle(
                                title = title,
                                source = completed?.source?.toString()?.trim().orEmpty(),
                                publishedAtMillis = publishedAt
                            )
                        }
                        item = null
                        activeField = null
                    }
                }
            }
            eventType = parser.next()
        }
        return results
    }

    internal fun keepRecent(
        articles: List<NewsArticle>,
        nowMillis: Long,
        maxAgeMillis: Long = MAX_ARTICLE_AGE_MILLIS
    ): List<NewsArticle> {
        if (maxAgeMillis <= 0L) return emptyList()
        val oldestAllowed = nowMillis - maxAgeMillis
        val newestAllowed = nowMillis + ALLOWED_FUTURE_SKEW_MILLIS
        return articles
            .filter { it.publishedAtMillis in oldestAllowed..newestAllowed }
            .distinctBy { it.title.trim().lowercase(Locale.ROOT) }
            .sortedByDescending { it.publishedAtMillis }
    }

    fun formatBrief(articles: List<NewsArticle>): String {
        if (articles.isEmpty()) return "لا توجد عناوين حديثة موثقة لعرضها الآن."
        val timeFormatter = DateTimeFormatter.ofPattern("dd MMM، HH:mm", Locale("ar"))
        return buildString {
            append("أحدث العناوين المنشورة خلال آخر 36 ساعة:")
            articles.forEach { article ->
                val published = Instant.ofEpochMilli(article.publishedAtMillis)
                    .atZone(ZoneId.systemDefault())
                    .format(timeFormatter)
                append("\n• ").append(article.title.take(220))
                if (article.source.isNotBlank()) append(" — ").append(article.source.take(80))
                append(" (نُشر ").append(published).append(")")
            }
        }
    }

    internal fun parseRssDate(value: String): Long? = runCatching {
        Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(value.trim())).toEpochMilli()
    }.getOrNull()
}
