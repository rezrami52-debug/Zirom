package com.racha.ai.ui.screens.prayer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.racha.ai.domain.model.PrayerTimes
import com.racha.ai.services.location.LocationHelper
import com.racha.ai.services.prayer.PrayerAlarmScheduler
import com.racha.ai.services.prayer.PrayerTimesCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date

data class PrayerUiState(
    val isLoading: Boolean = false,
    val prayerTimes: PrayerTimes? = null,
    val cityName: String = "",
    val errorMessage: String? = null,
    val needsLocationPermission: Boolean = false,
    val nextPrayerName: String = "",
    val nextPrayerTime: Date? = null,
    val alarmsScheduled: Boolean = false
)

class PrayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PrayerUiState())
    val uiState: StateFlow<PrayerUiState> = _uiState.asStateFlow()

    init {
        loadPrayerTimes()
    }

    fun loadPrayerTimes() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                needsLocationPermission = false
            )

            val location = LocationHelper.getCurrentLocation(getApplication())
            if (location == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    needsLocationPermission = true,
                    errorMessage = "لا يمكن تحديد موقعك. تأكد من تفعيل GPS ومنح صلاحية الموقع."
                )
                return@launch
            }

            try {
                val times = PrayerTimesCalculator.calculate(
                    latitude = location.latitude,
                    longitude = location.longitude
                )

                // جدولة إشعارات الصلاة
                PrayerAlarmScheduler.scheduleAll(getApplication(), times)

                val now = Date()
                val next = findNextPrayer(times, now)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    prayerTimes = times,
                    cityName = location.cityName ?: "",
                    nextPrayerName = next?.first ?: "",
                    nextPrayerTime = next?.second,
                    alarmsScheduled = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "خطأ في حساب المواقيت: ${e.message}"
                )
            }
        }
    }

    private fun findNextPrayer(times: PrayerTimes, now: Date): Pair<String, Date>? {
        val prayers = listOf(
            "الفجر" to times.fajr,
            "الظهر" to times.dhuhr,
            "العصر" to times.asr,
            "المغرب" to times.maghrib,
            "العشاء" to times.isha
        )
        return prayers.firstOrNull { it.second.after(now) }
    }
}
