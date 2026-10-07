package com.racha.ai.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.racha.ai.core.security.SecurePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val apiKey: String = "",
    val isKeyLoaded: Boolean = false,
    val saveMessage: String? = null,
    val isSaved: Boolean = false
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
            saveMessage = null
        )
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
