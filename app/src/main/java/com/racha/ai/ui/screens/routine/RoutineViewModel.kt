package com.racha.ai.ui.screens.routine

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.racha.ai.data.local.RoutineItem
import com.racha.ai.data.repository.RoutineRepository
import com.racha.ai.services.routine.RoutineAlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoutineUiState(
    val isLoading: Boolean = true,
    val showAddDialog: Boolean = false,
    val editingItem: RoutineItem? = null
)

class RoutineViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RoutineRepository(application)
    private val app = application

    private val _uiState = MutableStateFlow(RoutineUiState())
    val uiState: StateFlow<RoutineUiState> = _uiState.asStateFlow()

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
            // جدولة الإشعارات بعد التحميل
            scheduleAll()
        }
    }

    fun toggleEnabled(item: RoutineItem) {
        viewModelScope.launch {
            repository.update(item.copy(isEnabled = !item.isEnabled))
            scheduleAll()
        }
    }

    fun delete(item: RoutineItem) {
        viewModelScope.launch {
            repository.delete(item)
            scheduleAll()
        }
    }

    fun addItem(item: RoutineItem) {
        viewModelScope.launch {
            repository.insert(item)
            scheduleAll()
        }
    }

    fun updateItem(item: RoutineItem) {
        viewModelScope.launch {
            repository.update(item)
            scheduleAll()
        }
    }

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = true, editingItem = null)
    }

    fun showEditDialog(item: RoutineItem) {
        _uiState.value = _uiState.value.copy(showAddDialog = true, editingItem = item)
    }

    fun hideDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false, editingItem = null)
    }

    private suspend fun scheduleAll() {
        val current = items.value
        RoutineAlarmScheduler.scheduleAll(app, current)
    }

    fun testNotification(item: RoutineItem, isPreAlert: Boolean) {
        RoutineAlarmScheduler.triggerTest(app, item, isPreAlert)
    }
}
