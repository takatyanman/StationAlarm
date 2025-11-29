package com.example.stationalarm.presentation

import android.app.Application
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.stationalarm.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val repository = com.example.stationalarm.data.StationRepository.getInstance(application)
    private val geocoder = Geocoder(application, Locale.JAPAN)
    
    // Tracking state from Repository
    init {
        viewModelScope.launch {
            launch {
                repository.history.collect { historyList ->
                    _uiState.value = _uiState.value.copy(history = historyList)
                }
            }
            launch {
                repository.trackingState.collect { state ->
                    _uiState.value = _uiState.value.copy(
                        isTracking = state.isTracking,
                        currentDistance = state.currentDistance,
                        message = state.message
                    )
                }
            }
        }
    }

    fun updateStationNameInput(input: String) {
        _uiState.value = _uiState.value.copy(stationNameInput = input)
    }

    fun updateDistanceThreshold(distance: Int) {
        val newDistance = distance.coerceIn(100, 2000)
        _uiState.value = _uiState.value.copy(distanceThreshold = newDistance)
    }

    fun startTracking() {
        if (_uiState.value.stationNameInput.isBlank()) return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(message = "駅を検索中...")
            
            try {
                val location = searchStation(_uiState.value.stationNameInput)
                if (location != null) {
                    repository.addStation(_uiState.value.stationNameInput)
                    startService(location, _uiState.value.stationNameInput)
                } else {
                    _uiState.value = _uiState.value.copy(message = "駅が見つかりませんでした")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(message = "エラー: ${e.localizedMessage}")
            }
        }
    }

    fun stopTracking() {
        val intent = android.content.Intent(getApplication(), com.example.stationalarm.service.StationAlarmService::class.java)
        intent.action = "STOP_SERVICE"
        getApplication<Application>().startService(intent)
    }

    private fun startService(target: Location, stationName: String) {
        val intent = android.content.Intent(getApplication(), com.example.stationalarm.service.StationAlarmService::class.java).apply {
            putExtra("STATION_NAME", stationName)
            putExtra("TARGET_LAT", target.latitude)
            putExtra("TARGET_LNG", target.longitude)
            putExtra("THRESHOLD", _uiState.value.distanceThreshold)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplication<Application>().startForegroundService(intent)
        } else {
            getApplication<Application>().startService(intent)
        }
    }

    private suspend fun searchStation(name: String): Location? {
        return withContext(Dispatchers.IO) {
            try {
                val query = if (name.endsWith("駅")) name else "$name 駅"
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(query, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    Location("").apply {
                        latitude = address.latitude
                        longitude = address.longitude
                    }
                } else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    data class UiState(
        val stationNameInput: String = "",
        val stationName: String = "",
        val distanceThreshold: Int = 500,
        val isTracking: Boolean = false,
        val currentDistance: Float? = null,
        val message: String = "",
        val history: List<String> = emptyList()
    )
}
