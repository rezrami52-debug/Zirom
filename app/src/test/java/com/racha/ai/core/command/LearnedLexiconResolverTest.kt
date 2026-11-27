package com.racha.ai.core.command

import com.racha.ai.data.local.KnowledgeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnedLexiconResolverTest {
    private fun entry(term: String, meaning: String) = KnowledgeEntity(
        question = term,
        answer = meaning,
        topic = LearnedLexiconResolver.LEXICON_TOPIC,
        source = "user"
    )

    @Test fun expandsAnyLearnedVerbOrPhraseNotOnlyOpen() {
        val entries = listOf(entry("شغلي", "افتحي"), entry("نوري", "شغلي"))
        assertEquals("افتحي الموسيقى", LearnedLexiconResolver.expand("نوري الموسيقى", entries))
        assertEquals("اطفئ المصباح", LearnedLexiconResolver.expand("سكّري المصباح", listOf(entry("سكّري", "أطفئ"))))
    }

    @Test fun replacesWholeTermsOnly() {
        assertEquals("افتحي الصور", LearnedLexiconResolver.expand("شغلي الصور", listOf(entry("شغلي", "افتحي"))))
        assertEquals("مشغلي الصور", LearnedLexiconResolver.expand("مشغلي الصور", listOf(entry("شغلي", "افتحي"))))
    }

    @Test fun detectsLearnedUnsupportedActionsWithoutCallingThemSuccessful() {
        assertTrue(LearnedLexiconResolver.looksLikeAction("احذف الملفات"))
        assertFalse(LearnedLexiconResolver.looksLikeAction("ما معنى احذف"))
    }
}
