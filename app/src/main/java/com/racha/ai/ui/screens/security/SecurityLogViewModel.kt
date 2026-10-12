package com.racha.ai.ui.screens.security

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.racha.ai.data.local.LockAttemptEntity
import com.racha.ai.data.repository.LockAttemptRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SecurityLogViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = LockAttemptRepository(application)

    val attempts: StateFlow<List<LockAttemptEntity>> = repo.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun clearAll() {
        viewModelScope.launch {
            repo.deleteAll()
        }
    }
}
