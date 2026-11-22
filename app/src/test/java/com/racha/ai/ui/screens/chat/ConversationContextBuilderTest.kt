package com.racha.ai.ui.screens.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationContextBuilderTest {
    @Test
    fun includesRecentTurnsButDoesNotRepeatCurrentQuestion() {
        val context = ConversationContextBuilder.build(
            messages = listOf(
                ChatMessage("أبحث عن فيلم خيال علمي", isFromUser = true),
                ChatMessage("هل تفضل شيئًا حديثًا؟", isFromUser = false),
                ChatMessage("نعم، مثل الذي ذكرته قبل قليل", isFromUser = true),
                ChatMessage("تقصد فيلم الخيال العلمي الذي تحدثنا عنه", isFromUser = false),
                ChatMessage("هل يوجد جزء ثانٍ؟", isFromUser = true)
            ),
            currentQuestion = "هل يوجد جزء ثانٍ؟",
            learnedContext = ""
        )

        assertTrue(context.contains("المستخدم: أبحث عن فيلم خيال علمي"))
        assertTrue(context.contains("رشا: تقصد فيلم الخيال العلمي الذي تحدثنا عنه"))
        assertTrue(context.endsWith("سؤال المستخدم الحالي:\nهل يوجد جزء ثانٍ؟"))
        assertTrue(context.indexOf("هل يوجد جزء ثانٍ؟") == context.lastIndexOf("هل يوجد جزء ثانٍ؟"))
    }

    @Test
    fun omitsImagesCredentialsAndLocalApkReports() {
        val context = ConversationContextBuilder.build(
            messages = listOf(
                ChatMessage("ما رأيك في هذه الصورة؟", isFromUser = true, imagePath = "/private/image.jpg"),
                ChatMessage("gsk_123456789012345678901234567890", isFromUser = true),
                ChatMessage("📱 تحليل APK محلي: SHA-256 abcdef", isFromUser = false),
                ChatMessage("أفضّل الإجابة بالعربية", isFromUser = true),
                ChatMessage("ما الخطة؟", isFromUser = true)
            ),
            currentQuestion = "ما الخطة؟",
            learnedContext = ""
        )

        assertFalse(context.contains("image.jpg"))
        assertFalse(context.contains("gsk_"))
        assertFalse(context.contains("SHA-256"))
        assertTrue(context.contains("المستخدم: أفضّل الإجابة بالعربية"))
        assertTrue(context.endsWith("سؤال المستخدم الحالي:\nما الخطة؟"))
    }

    @Test
    fun keepsTheQuestionWithinTheConfiguredLength() {
        val context = ConversationContextBuilder.build(
            messages = (1..20).map { ChatMessage("رسالة قديمة رقم $it", isFromUser = true) },
            currentQuestion = "السؤال الأحدث",
            learnedContext = "",
            maxChars = 500
        )

        assertTrue(context.length <= 500)
        assertTrue(context.endsWith("سؤال المستخدم الحالي:\nالسؤال الأحدث"))
        assertTrue(context.contains("رسالة قديمة رقم 20"))
    }
}
