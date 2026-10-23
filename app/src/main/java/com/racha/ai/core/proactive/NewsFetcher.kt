package com.racha.ai.core.proactive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object NewsFetcher {
    private const val RSS_URL = "https://news.google.com/rss?hl=ar&gl=SA&ceid=SA:ar"

    suspend fun fetchTopHeadlines(limit: Int = 5): List<String> = withContext(Dispatchers.IO) {
        val headlines = mutableListOf<String>()
        var connection: HttpURLConnection? = null
        try {
            val url = URL(RSS_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.requestMethod = "GET"

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                headlines.addAll(parseRss(connection.inputStream, limit))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection?.disconnect()
        }
        headlines
    }

    private fun parseRss(inputStream: InputStream, limit: Int): List<String> {
        val results = mutableListOf<String>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        var insideItem = false
        var currentTag = ""

        while (eventType != XmlPullParser.END_DOCUMENT && results.size < limit) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag.equals("item", ignoreCase = true)) {
                        insideItem = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideItem && currentTag.equals("title", ignoreCase = true)) {
                        val title = parser.text?.trim()
                        if (!title.isNullOrBlank()) {
                            results.add(title)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("item", ignoreCase = true)) {
                        insideItem = false
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }
        return results
    }
}
