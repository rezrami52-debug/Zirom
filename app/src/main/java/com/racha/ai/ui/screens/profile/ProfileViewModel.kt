package com.racha.ai.ui.screens.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.racha.ai.data.local.UserProfile
import com.racha.ai.data.repository.UserProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoading: Boolean = true,
    val profile: UserProfile = UserProfile(),
    val saveMessage: String? = null,
    val isSaved: Boolean = false
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UserProfileRepository(application)

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            val profile = repository.getProfile()
            _uiState.value = _uiState.value.copy(
                profile = profile,
                isLoading = false
            )
        }
    }

    /**
     * تحديث حقل واحد من الملف.
     */
    fun updateProfile(updated: UserProfile) {
        _uiState.value = _uiState.value.copy(
            profile = updated,
            saveMessage = null,
            isSaved = false
        )
    }

    fun save() {
        viewModelScope.launch {
            try {
                repository.save(_uiState.value.profile)
                _uiState.value = _uiState.value.copy(
                    isSaved = true,
                    saveMessage = "✅ تم حفظ الملف الشخصي"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaved = false,
                    saveMessage = "⚠️ فشل الحفظ: ${e.message}"
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(saveMessage = null)
    }
}
