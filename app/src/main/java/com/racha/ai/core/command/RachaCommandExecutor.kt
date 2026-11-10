package com.racha.ai.core.command

import android.content.Context
import com.racha.ai.core.apps.IntentHandler
import com.racha.ai.core.context.LocationIntentHandler

class RachaCommandExecutor(
    private val context: Context,
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
            RachaCommand.GetLocation -> {
                Result.KnownCommand(
                    IntentHandler.HandlerResult.DirectReply(
                        LocationIntentHandler.answer(context)
                    )
                )
            }

            is RachaCommand.AskAI -> {
                Result.RequiresAI(cleanText)
            }

            else -> {
                Result.KnownCommand(
                    intentHandler.handle(cleanText)
                )
            }
        }
    }
}
