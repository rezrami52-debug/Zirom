package com.racha.ai.core.compiler

object CodeEngine {

    // استخراج الكود النظيف من نصوص Gemini AI
    fun extractCodeBlocks(aiResponse: String): List<CodeSnippet> {
        val regex = "```(\\w+)?\\n([\\s\\S]*?)```".toRegex()
        return regex.findAll(aiResponse).map { matchResult ->
            val language = matchResult.groupValues[1].ifEmpty { "text" }
            val code = matchResult.groupValues[2].trim()
            CodeSnippet(language = language, code = code)
        }.toList()
    }

    // قالب أساسي لإنشاء شاشة Android Compose سريعة
    fun generateComposeScreenTemplate(screenName: String): String {
        return """
            package com.racha.ai.ui.screens

            import androidx.compose.foundation.layout.*
            import androidx.compose.material3.*
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.unit.dp

            @Composable
            fun ${screenName}Screen() {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "$screenName Screen", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
        """.trimIndent()
    }
}

data class CodeSnippet(
    val language: String,
    val code: String
)
