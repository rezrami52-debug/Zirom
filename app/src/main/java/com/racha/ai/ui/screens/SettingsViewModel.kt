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
                isKeyLoaded = true,
                isSaved = savedKey.isNotBlank()
            )
        }
    }

    fun onApiKeyChange(newValue: String) {
        val cleanKey = GeminiService.sanitizeApiKey(newValue)
        _uiState.value = _uiState.value.copy(
            apiKey = cleanKey,
            isSaved = false,
            saveMessage = null,
            testMessage = null,
            isTestSuccess = null
        )
    }

    fun clearApiKey() {
        _uiState.value = _uiState.value.copy(
            apiKey = "",
            isSaved = false,
            saveMessage = "تم مسح المفتاح",
            testMessage = null,
            isTestSuccess = null
        )
        viewModelScope.launch {
            try {
                securePrefs.saveApiKey("")
            } catch (_: Exception) {}
        }
    }

    fun getProviderName(): String {
        val key = _uiState.value.apiKey
        return when {
            key.startsWith("gsk_") -> "Groq (Llama 3.3 ⚡ فائق السرعة)"
            key.startsWith("AIza") -> "Google Gemini 2.0"
            key.isNotBlank() -> "مفتاح مخصص (${key.length} حرف)"
            else -> "لم يتم إدخال مفتاح بعد"
        }
    }

    fun testApiKeyConnection() {
        val rawKey = _uiState.value.apiKey
        val cleanKey = GeminiService.sanitizeApiKey(rawKey)

        if (cleanKey.isBlank()) {
            _uiState.value = _uiState.value.copy(
                testMessage = "⚠️ يرجى لصق المفتاح أولاً لفحصه.",
                isTestSuccess = false
            )
            return
        }

        if (cleanKey.startsWith("gsk_") && cleanKey.length > 70) {
            _uiState.value = _uiState.value.copy(
                testMessage = "⚠️ المفتاح أطول من المعتاد (${cleanKey.length} حرف). تأكد من نسخه فقط دون تكرار.",
                isTestSuccess = false
            )
            return
        }

        _uiState.value = _uiState.value.copy(isTestingKey = true, testMessage = null, isTestSuccess = null)

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val service = GeminiService(cleanKey)
                    service.generateResponse("ping")
                }
                securePrefs.saveApiKey(cleanKey)
                _uiState.value = _uiState.value.copy(
                    apiKey = cleanKey,
                    isTestingKey = false,
                    isTestSuccess = true,
                    isSaved = true,
                    testMessage = "✅ الاتصال ناجح ومستقر! تم تفعيل المفتاح بنجاح.",
                    saveMessage = "✅ تم حفظ المفتاح بنجاح يا رمزي!"
                )
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: e.message ?: "خطأ غير معروف"
                _uiState.value = _uiState.value.copy(
                    isTestingKey = false,
                    isTestSuccess = false,
                    testMessage = "❌ الرد من السيرفر: $errorMsg"
                )
            }
        }
    }

    fun saveApiKey() {
        val cleanKey = GeminiService.sanitizeApiKey(_uiState.value.apiKey)
        if (cleanKey.isBlank()) {
            _uiState.value = _uiState.value.copy(
                saveMessage = "⚠️ لا يمكن حفظ مفتاح فارغ",
                isSaved = false
            )
            return
        }

        viewModelScope.launch {
            try {
                securePrefs.saveApiKey(cleanKey)
                _uiState.value = _uiState.value.copy(
                    apiKey = cleanKey,
                    isSaved = true,
                    saveMessage = "✅ تم حفظ المفتاح بنجاح يا رمزي!"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaved = false,
                    saveMessage = "❌ فشل الحفظ: ${e.message}"
                )
            }
        }
    }
}
