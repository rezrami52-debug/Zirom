package com.racha.ai.core.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalApkCommandMatcherTest {
    @Test
    fun recognizesArabicDecompileCommands() {
        assertTrue(LocalApkCommandMatcher.matches("فكّكي هذا الملف"))
        assertTrue(LocalApkCommandMatcher.matches("قم بتفكيك هذا الملف واعرض الملفات"))
        assertTrue(LocalApkCommandMatcher.matches("استخرج الشيفرة محليًا"))
    }

    @Test
    fun recognizesEnglishDecompileCommands() {
        assertTrue(LocalApkCommandMatcher.matches("Please decompile this APK"))
    }

    @Test
    fun doesNotTriggerForNegatedOrUnrelatedText() {
        assertFalse(LocalApkCommandMatcher.matches("لا تفككي هذا الملف"))
        assertFalse(LocalApkCommandMatcher.matches("لا أريد تفكيك الملف"))
        assertFalse(LocalApkCommandMatcher.matches("ما أخبارك اليوم؟"))
        assertFalse(LocalApkCommandMatcher.matches(""))
    }
}
