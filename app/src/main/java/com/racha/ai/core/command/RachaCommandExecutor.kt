package com.racha.ai.core.command

import com.racha.ai.core.apps.IntentHandler

class RachaCommandExecutor(
    private val intentHandler: IntentHandler
) {
    sealed interface Result {
        data class KnownCommand(
            val result: IntentHandler.HandlerResult
        ) : Result

        data class RequiresAI(
            val text: String
        ) : Result
    }

    suspend fun execute(text: String): Result {
        val cleanText = text.trim()

        if (cleanText.isBlank()) {
            return Result.RequiresAI(cleanText)
        }

        return when (RachaCommandRouter.classify(cleanText)) {
            is RachaCommand.AskAI -> Result.RequiresAI(cleanText)
            else -> Result.KnownCommand(
                intentHandler.handle(cleanText)
            )
        }
    }
}
