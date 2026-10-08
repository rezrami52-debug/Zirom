package com.racha.ai.ui.screens.prayer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.racha.ai.domain.model.PrayerTimes
import com.racha.ai.services.location.LocationHelper
import com.racha.ai.services.prayer.PrayerTimesCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date

data class PrayerUiState(
    val isLoading: Boolean = true,
    val prayerTimes: PrayerTimes? = null,
    val errorMessage: String? = null,
    val cityName: String = "",
    val nextPrayerName: String = "",
    val nextPrayerTime: Date? = null
)

class PrayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PrayerUiState())
    val uiState: StateFlow<PrayerUiState> = _uiState.asStateFlow()

    init {
        loadPrayerTimes()
    }

    fun loadPrayerTimes() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val location = LocationHelper.getCurrentLocation(getApplication())
            if (location == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "لم نتمكن من تحديد موقعك. تأكد من تفعيل GPS ومنح الصلاحية."
                )
                return@launch
            }

            try {
                val times = PrayerTimesCalculator.calculate(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    locationName = location.cityName ?: "موقعك"
                )
                val next = times.nextPrayer()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    prayerTimes = times,
                    cityName = location.cityName ?: "موقعك",
                    nextPrayerName = next?.first ?: "",
                    nextPrayerTime = next?.second
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "خطأ في حساب المواقيت: ${e.message}"
                )
            }
        }
    }
}
