package com.racha.ai.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.racha.ai.core.security.SecurePreferences
import com.racha.ai.data.api.GeminiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val apiKey: String = "",
    val isKeyLoaded: Boolean = false,
    val saveMessage: String? = null,
    val isSaved: Boolean = false,
    val isTestingKey: Boolean = false,
    val testMessage: String? = null,
    val isTestSuccess: Boolean? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val securePrefs = SecurePreferences(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadApiKey()
    }

    private fun loadApiKey() {
        viewModelScope.launch {
            val savedKey = securePrefs.getApiKey() ?: ""
            _uiState.value = _uiState.value.copy(
                apiKey = savedKey,
                isKeyLoaded = true
            )
        }
    }

    fun onApiKeyChange(newValue: String) {
        _uiState.value = _uiState.value.copy(
            apiKey = newValue,
            isSaved = false,
            saveMessage = null,
            testMessage = null,
            isTestSuccess = null
        )
    }

    fun getProviderName(): String {
        val key = _uiState.value.apiKey.trim()
        return when {
            key.startsWith("gsk_") -> "Groq (Llama 3.3 ⚡ فائق السرعة)"
            key.startsWith("AIza") -> "Google Gemini 2.0"
            key.isNotBlank() -> "مفتاح ذكاء اصطناعي مخصص"
            else -> "لم يتم إدخال مفتاح بعد"
        }
    }

    fun testApiKeyConnection() {
        val key = _uiState.value.apiKey.trim()
        if (key.isBlank()) {
            _uiState.value = _uiState.value.copy(
                testMessage = "⚠️ يرجى إدخال المفتاح أولاً لفحصه.",
                isTestSuccess = false
            )
            return
        }

        _uiState.value = _uiState.value.copy(isTestingKey = true, testMessage = null, isTestSuccess = null)

        viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    val service = GeminiService(key)
                    service.generateResponse("قل: الاتصال سليم.")
                }
                _uiState.value = _uiState.value.copy(
                    isTestingKey = false,
                    isTestSuccess = true,
                    testMessage = "✅ الاتصال ناجح ومستقر!"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isTestingKey = false,
                    isTestSuccess = false,
                    testMessage = "❌ فشل الاتصال: تأكد من صحة المفتاح والإنترنت."
                )
            }
        }
    }

    fun saveApiKey() {
        val key = _uiState.value.apiKey.trim()
        if (key.isBlank()) {
            _uiState.value = _uiState.value.copy(
                saveMessage = "المفتاح فارغ",
                isSaved = false
            )
            return
        }

        viewModelScope.launch {
            try {
                securePrefs.saveApiKey(key)
                _uiState.value = _uiState.value.copy(
                    isSaved = true,
                    saveMessage = "تم حفظ المفتاح بنجاح"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaved = false,
                    saveMessage = "فشل الحفظ: ${e.message}"
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(saveMessage = null)
    }
}
