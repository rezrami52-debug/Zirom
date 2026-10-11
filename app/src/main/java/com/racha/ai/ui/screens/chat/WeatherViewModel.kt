package com.racha.ai.ui.screens.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.racha.ai.domain.model.WeatherInfo
import com.racha.ai.services.location.LocationHelper
import com.racha.ai.services.weather.WeatherService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WeatherUiState(
    val isLoading: Boolean = false,
    val weather: WeatherInfo? = null,
    val error: String? = null
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val location = LocationHelper.getCurrentLocation(getApplication())
            if (location == null) {
                _uiState.value = WeatherUiState(
                    isLoading = false,
                    error = "لا يمكن تحديد الموقع"
                )
                return@launch
            }

            val weather = WeatherService.fetchWeather(
                latitude = location.latitude,
                longitude = location.longitude,
                cityName = location.cityName ?: ""
            )

            _uiState.value = if (weather == null) {
                WeatherUiState(isLoading = false, error = "فشل جلب الطقس")
            } else {
                WeatherUiState(isLoading = false, weather = weather)
            }
        }
    }
}
