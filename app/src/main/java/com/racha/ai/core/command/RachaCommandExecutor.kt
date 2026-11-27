package com.racha.ai.core.command

import android.content.Context
import com.racha.ai.core.apps.IntentHandler
import com.racha.ai.core.apps.IntentClassifier
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

    suspend fun executeForVoice(
        text: String
    ): IntentHandler.HandlerResult {
        return when (val result = execute(text)) {
            is Result.KnownCommand -> result.result
            is Result.RequiresAI -> IntentHandler.HandlerResult.Unknown
        }
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
                val intentType = IntentClassifier.classify(cleanText)
                if (intentType == IntentClassifier.IntentType.GREETING ||
                    intentType == IntentClassifier.IntentType.SMALL_TALK ||
                    intentType == IntentClassifier.IntentType.CORRECTION
                ) {
                    Result.RequiresAI(cleanText)
                } else {
                    val localResult = intentHandler.handle(cleanText)
                    if (localResult is IntentHandler.HandlerResult.Unknown) {
                        Result.RequiresAI(cleanText)
                    } else {
                        Result.KnownCommand(localResult)
                    }
                }
            }

            else -> {
                Result.KnownCommand(
                    intentHandler.handle(cleanText)
                )
            }
        }
    }
}
