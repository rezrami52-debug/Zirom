package com.racha.ai.core.command

import org.junit.Assert.assertEquals
import org.junit.Test

class RachaCommandRouterTest {
    @Test
    fun recognizesFlashlightOn() {
        assertEquals(
            RachaCommand.ToggleFlashlight(true),
            RachaCommandRouter.classify("شغل الفلاش")
        )
    }

    @Test
    fun recognizesFlashlightOffWithHamza() {
        assertEquals(
            RachaCommand.ToggleFlashlight(false),
            RachaCommandRouter.classify("أطفئ الفلاش")
        )
    }

    @Test
    fun recognizesTimeQuestion() {
        assertEquals(
            RachaCommand.GetTime,
            RachaCommandRouter.classify("كم الساعة الآن؟")
        )
    }

    @Test
    fun extractsAppName() {
        assertEquals(
            RachaCommand.OpenApp("فيسبوك"),
            RachaCommandRouter.classify("افتح فيسبوك")
        )
    }

    @Test
    fun sendsUnrecognizedQuestionToAi() {
        val text = "ما أفضل طريقة لتعلم البرمجة؟"
        assertEquals(
            RachaCommand.AskAI(text),
            RachaCommandRouter.classify(text)
        )
    }
}
