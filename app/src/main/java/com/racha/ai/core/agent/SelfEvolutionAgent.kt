package com.racha.ai.core.agent

import android.content.Context
import com.racha.ai.core.security.SecurePreferences
import com.racha.ai.data.api.GeminiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SelfEvolutionAgent(private val context: Context) {

    private val securePrefs = SecurePreferences(context)

    suspend fun auditAndProposeFix(targetFilePath: String, userInstructions: String): String = withContext(Dispatchers.IO) {
        val githubToken = securePrefs.getGitHubToken()
        if (githubToken.isNullOrBlank()) {
            return@withContext "⚠️ يا رمزي، يرجى إدخال مفتاح GitHub Token (يبدأ بـ ghp_) أولاً لأتمكن من فحص المستودع."
        }

        val client = GitHubApiClient(githubToken)
        val fileResult = client.getFileContent(targetFilePath)

        fileResult.fold(
            onSuccess = { fetched ->
                val prompt = """
                    أنت المهندسة البرمجية ومطورة النظام EVE. صديقك رمزي يطلب منك مراجعة وتعديل هذا الملف البرمجي من تطبيقك:
                    الملف: '$targetFilePath'
                    طلب رمزي: '$userInstructions'
                    هذا هو الكود المصدري الحالي:
                    '''
                    ${fetched.content}
                    '''
                    لخص في جملتين أنيقتين لرمزي ما هي التعديلات واختم بسؤاله: 'هل ترغب في أن أرفع هذا التعديل إلى GitHub يا رمزي؟'
                """.trimIndent()

                val apiKey = securePrefs.getApiKey()
                if (!apiKey.isNullOrBlank()) {
                    try {
                        GeminiService(apiKey).generateResponse(prompt)
                    } catch (e: Exception) {
                        "يا رمزي، تمكنت من قراءة الملف من GitHub، لكن تعذر صياغة التحليل: ${e.localizedMessage}"
                    }
                } else {
                    "✅ يا رمزي، قمت بقراءة ملف $targetFilePath بنجاح من GitHub، والملف جاهز للتطوير!"
                }
            },
            onFailure = { error ->
                "⚠️ تعذر العثور على الملف أو قراءته من GitHub يا رمزي: ${error.localizedMessage}"
            }
        )
    }
}
