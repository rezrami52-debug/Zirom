package com.racha.ai.data.search

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder

/**
 * بحث على الويب عبر DuckDuckGo HTML.
 * لا يحتاج API key.
 */
object WebSearchService {

    private const val TAG = "RachaWebSearch"
    private const val DDG_URL = "https://html.duckduckgo.com/html/"
    private const val USER_AGENT = "Mozilla/5.0 (Android 14; Mobile) AppleWebKit/537.36"

    /**
     * بحث على DuckDuckGo.
     */
    suspend fun search(query: String, maxResults: Int = 5): List<SearchResult> =
        withContext(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "$DDG_URL?q=$encodedQuery"

                Log.d(TAG, "بحث: $query")

                val doc: Document = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(15_000)
                    .get()

                val results = mutableListOf<SearchResult>()

                // DuckDuckGo HTML structure
                val items = doc.select("div.result, div.web-result")

                for (item in items.take(maxResults)) {
                    try {
                        val titleEl = item.selectFirst("a.result__a") ?: continue
                        val title = titleEl.text().trim()
                        val href = titleEl.attr("href").trim()
                        val snippetEl = item.selectFirst("a.result__snippet, div.result__snippet")
                        val snippet = snippetEl?.text()?.trim() ?: ""

                        if (title.isNotBlank() && href.isNotBlank()) {
                            results.add(
                                SearchResult(
                                    title = title,
                                    url = href,
                                    snippet = snippet
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "فشل استخراج نتيجة", e)
                    }
                }

                Log.d(TAG, "✅ ${results.size} نتيجة")
                results
            } catch (e: Exception) {
                Log.e(TAG, "فشل البحث", e)
                emptyList()
            }
        }

    /**
     * استخراج النص الكامل من صفحة.
     */
    suspend fun fetchPageText(url: String, maxChars: Int = 3000): String? =
        withContext(Dispatchers.IO) {
            try {
                val doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(15_000)
                    .get()

                // إزالة العناصر غير المرغوبة
                doc.select("script, style, nav, header, footer, aside, iframe, noscript").remove()

                // البحث عن المحتوى الرئيسي
                val mainContent = doc.selectFirst("article, main, div[role=main], div.content")
                    ?: doc.body()

                val text = mainContent?.text()?.trim() ?: ""
                text.take(maxChars)
            } catch (e: Exception) {
                Log.e(TAG, "فشل جلب الصفحة: $url", e)
                null
            }
        }
}
