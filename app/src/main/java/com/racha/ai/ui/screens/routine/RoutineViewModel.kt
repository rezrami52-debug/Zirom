package com.racha.ai.ui.screens.routine

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.racha.ai.data.local.RoutineItem
import com.racha.ai.data.repository.RoutineRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoutineUiState(
    val isLoading: Boolean = true,
    val currentTime: Long = System.currentTimeMillis()
)

class RoutineViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RoutineRepository(application)

    private val _uiState = MutableStateFlow(RoutineUiState())
    val uiState: StateFlow<RoutineUiState> = _uiState.asStateFlow()

    /**
     * كل عناصر الروتين، مرتّبة حسب الوقت.
     */
    val items: StateFlow<List<RoutineItem>> = repository.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            repository.initializeDefaultIfEmpty()
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    /**
     * تبديل حالة "مفعّل / معطّل".
     */
    fun toggleEnabled(item: RoutineItem) {
        viewModelScope.launch {
            repository.update(item.copy(isEnabled = !item.isEnabled))
        }
    }

    /**
     * حذف عنصر.
     */
    fun delete(item: RoutineItem) {
        viewModelScope.launch {
            repository.delete(item)
        }
    }
}
