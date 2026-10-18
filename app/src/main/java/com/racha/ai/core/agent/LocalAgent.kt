package com.racha.ai.core.agent

import android.content.Context
import com.racha.ai.core.tools.ToolExecutor

class LocalAgent(private val context: Context) {
    private val toolExecutor = ToolExecutor(context)

    fun processCommand(text: String): CommandResult {
        val lower = text.lowercase().trim()

        return when {
            lower.contains("صامت") || lower.contains("كتم") -> {
                val res = toolExecutor.execute("{\"tool\":\"set_silent\"}")
                CommandResult.Success(res.message)
            }
            lower.contains("عادي") || lower.contains("رنين") -> {
                val res = toolExecutor.execute("{\"tool\":\"set_normal\"}")
                CommandResult.Success(res.message)
            }
            lower.contains("هزاز") || lower.contains("اهتزاز") -> {
                val res = toolExecutor.execute("{\"tool\":\"set_vibrate\"}")
                CommandResult.Success(res.message)
            }
            lower.contains("موقع") || lower.contains("خريطه") -> {
                val res = toolExecutor.execute("{\"tool\":\"open_maps\"}")
                CommandResult.Success(res.message)
            }
            lower.contains("مقهى") || lower.contains("مقاهي") || lower.contains("فندق") || lower.contains("مسجد") -> {
                val res = toolExecutor.execute("{\"tool\":\"search_places\", \"query\":\"$text\"}")
                CommandResult.Success(res.message)
            }
            else -> CommandResult.NeedsLLM(text)
        }
    }
}
