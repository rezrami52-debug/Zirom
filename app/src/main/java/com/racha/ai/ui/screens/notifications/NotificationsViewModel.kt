package com.racha.ai.ui.screens.notifications

import androidx.lifecycle.ViewModel
import com.racha.ai.data.repository.NotificationRepository
import com.racha.ai.domain.model.AppNotification
import kotlinx.coroutines.flow.StateFlow

class NotificationsViewModel : ViewModel() {
    val notifications: StateFlow<List<AppNotification>> =
        NotificationRepository.notifications

    fun clearAll() {
        NotificationRepository.clear()
    }
}
