package com.racha.ai.core.agent

import android.content.Context
import com.racha.ai.core.security.SecurePreferences
import com.racha.ai.core.shizuku.ShizukuShellRunner
import com.racha.ai.data.api.GeminiService
import com.racha.ai.data.llm.QwenService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class DynamicActionRunner(private val context: Context) {

    private val learnedStore = LearnedActionsStore(context)
    private val selfHealing = SelfHealingInspector(context)
    private val securePrefs = SecurePreferences(context)
    private val qwenService = QwenService(context)

    data class DynamicExecutionResult(
        val success: Boolean,
        val feedback: String,
        val wasAutoHealed: Boolean = false
    )

    suspend fun tryExecuteDynamicAction(userPrompt: String): DynamicExecutionResult? = withContext(Dispatchers.IO) {
        val promptClean = userPrompt.trim()

        // 1. هل سبق لرشا تعلم هذا الأمر وتخزينه؟
        val cachedCommand = learnedStore.getAction(promptClean)
        if (!cachedCommand.isNullOrBlank()) {
            val res = ShizukuShellRunner.execCommand(cachedCommand)
            if (!res.contains("فشل") && !res.contains("Error")) {
                return@withContext DynamicExecutionResult(
                    success = true,
                    feedback = "✅ نفذت لك طلبك مباشرة من الذاكرة يا رمزي."
                )
            }
        }

        // 2. إذا لم يكن محفوظاً، نسأل العقل الاصطناعي لتحويل النية إلى أمر نظام
        val aiPlan = askAiForActionPlan(promptClean) ?: return@withContext null

        if (!aiPlan.optBoolean("isActionable", false)) {
            return@withContext null
        }

        val shellCommand = aiPlan.optString("command", "").trim()
        val friendlyMessage = aiPlan.optString("feedback", "تم تنفيذ الأمر بنجاح يا رمزي.")
        val requiredHealing = aiPlan.optString("healingTarget", "NONE")

        if (shellCommand.isBlank() || !isCommandSafe(shellCommand)) {
            return@withContext null
        }

        var healed = false
        // 3. الفحص والإصلاح الذاتي إن تطلب الأمر
        when (requiredHealing.uppercase()) {
            "ACCESSIBILITY" -> {
                healed = selfHealing.autoHealAccessibility()
            }
            "NOTIFICATION" -> {
                healed = selfHealing.autoHealNotificationListener()
            }
            "RESTRICTED" -> {
                healed = selfHealing.autoUnlockRestrictedSettings()
            }
        }

        // 4. تنفيذ الأمر الديناميكي عبر Shizuku
        val output = ShizukuShellRunner.execCommand(shellCommand)
        val isSuccess = !output.contains("Error") && !output.contains("فشل") && !output.contains("Exception")

        if (isSuccess) {
            learnedStore.saveAction(promptClean, shellCommand)
            val prefix = if (healed) "يبدو أن الخدمة كانت بحاجة للتفعيل فقمت بتشغيلها، و" else ""
            return@withContext DynamicExecutionResult(
                success = true,
                feedback = "$prefix$friendlyMessage",
                wasAutoHealed = healed
            )
        }

        return@withContext null
    }

    private fun isCommandSafe(cmd: String): Boolean {
        val lower = cmd.lowercase()
        val dangerousTokens = listOf("rm -rf", "format", "mkfs", "dd if=", "reboot", "shutdown", "> /dev/")
        return dangerousTokens.none { lower.contains(it) }
    }

    private suspend fun askAiForActionPlan(prompt: String): JSONObject? {
        val systemPrompt = """
            You are Racha's internal Android System Architect.
            The user Ramzi wants an action executed on his Android phone.
            Analyze his request: '$prompt'.
            If this can be executed via Android ADB/Shell command (such as launching app in freeform floating window 'am start -n ... --windowingMode 5', clearing cache, interacting, opening deep links, toggling settings, taking screenshots, killing background app), generate the JSON.

            Output ONLY a JSON object:
            {
              "isActionable": true or false,
              "command": "precise adb shell command",
              "feedback": "Arabic witty reply to Ramzi (never use 'سيدي', use 'يا رمزي')",
              "healingTarget": "NONE" or "ACCESSIBILITY" or "NOTIFICATION" or "RESTRICTED"
            }
        """.trimIndent()

        val rawResponse = try {
            val key = securePrefs.getApiKey()
            if (!key.isNullOrBlank()) {
                GeminiService(key).generateResponse("$systemPrompt\n\nUser request: $prompt")
            } else if (qwenService.isModelPresent()) {
                qwenService.generateResponse("$systemPrompt\n\nUser request: $prompt")
            } else null
        } catch (_: Exception) { null } ?: return null

        return try {
            val jsonStart = rawResponse.indexOf("{")
            val jsonEnd = rawResponse.lastIndexOf("}")
            if (jsonStart != -1 && jsonEnd != -1) {
                JSONObject(rawResponse.substring(jsonStart, jsonEnd + 1))
            } else null
        } catch (_: Exception) { null }
    }
}
