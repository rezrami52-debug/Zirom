package com.racha.ai.core.agent

import android.content.Context
import android.content.SharedPreferences

class LearnedActionsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("racha_learned_actions", Context.MODE_PRIVATE)

    fun getAction(prompt: String): String? {
        val cleanKey = normalizePrompt(prompt)
        return prefs.getString(cleanKey, null)
    }

    fun saveAction(prompt: String, shellCommand: String) {
        val cleanKey = normalizePrompt(prompt)
        prefs.edit().putString(cleanKey, shellCommand).apply()
    }

    private fun normalizePrompt(prompt: String): String {
        return prompt.lowercase().trim()
            .replace(Regex("(?i)يا |رمزي|من فضلك|لو سمحت|عفوا"), "")
            .trim()
    }
}
