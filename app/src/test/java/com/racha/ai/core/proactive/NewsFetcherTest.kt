package com.racha.ai.core.proactive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsFetcherTest {
    @Test
    fun parsesRssPublicationDatesAndRejectsMalformedValues() {
        assertNotNull(NewsFetcher.parseRssDate("Sun, 22 Nov 2026 18:00:00 GMT"))
        assertNull(NewsFetcher.parseRssDate("date missing"))
    }

    @Test
    fun filtersOldAndImplausiblyFutureArticlesAndSortsNewestFirst() {
        val now = 1_795_000_000_000L
        val recent = NewsArticle("خبر حديث", "مصدر", now - 60_000L)
        val olderThan36Hours = NewsArticle(
            "فوز ريال مدريد بدوري الأبطال للمرة 14",
            "مصدر قديم",
            now - NewsFetcher.MAX_ARTICLE_AGE_MILLIS - 1
        )
        val future = NewsArticle("خبر مستقبلي", "مصدر", now + 20 * 60_000L)
        val newer = NewsArticle("الأحدث", "مصدر", now - 10_000L)

        val result = NewsFetcher.keepRecent(
            listOf(recent, olderThan36Hours, future, newer),
            nowMillis = now
        )

        assertEquals(listOf("الأحدث", "خبر حديث"), result.map { it.title })
        assertTrue(result.all { it.publishedAtMillis <= now })
    }

    @Test
    fun notificationBriefShowsSourceAndPublicationTime() {
        val now = 1_795_000_000_000L
        val brief = NewsFetcher.formatBrief(
            listOf(NewsArticle("عنوان موثوق", "وكالة الأخبار", now))
        )

        assertTrue(brief.contains("آخر 36 ساعة"))
        assertTrue(brief.contains("عنوان موثوق"))
        assertTrue(brief.contains("وكالة الأخبار"))
        assertTrue(brief.contains("نُشر"))
    }
}
