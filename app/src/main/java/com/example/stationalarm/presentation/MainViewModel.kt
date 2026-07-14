package com.example.stationalarm.presentation

import android.app.Application
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.stationalarm.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val repository = com.example.stationalarm.data.StationRepository.getInstance(application)
    private val geocoder = Geocoder(application, Locale.JAPAN)
    
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
                        message = state.message,
                        messageIsError = state.isError,
                        stationName = state.stationName.orEmpty(),
                        hasArrived = state.hasArrived,
                        locationAccuracy = state.locationAccuracy
                    )
                }
            }
        }
    }

    fun updateStationNameInput(input: String) {
        _uiState.value = _uiState.value.copy(
            stationNameInput = input,
            message = "",
            messageIsError = false,
            requiresAppSettings = false
        )
    }

    fun updateDistanceThreshold(distance: Int) {
        val newDistance = distance.coerceIn(100, 2000)
        _uiState.value = _uiState.value.copy(distanceThreshold = newDistance)
    }

    /**
     * タイルなど外部導線から受け取った追跡条件を、権限確認前に UI へ反映する。
     */
    fun prepareTrackingFor(stationName: String, threshold: Int) {
        if (_uiState.value.isTracking || _uiState.value.isSearching) return
        _uiState.value = _uiState.value.copy(
            stationNameInput = stationName.trim(),
            distanceThreshold = threshold.coerceIn(100, 2000),
            message = "",
            messageIsError = false,
            requiresAppSettings = false
        )
    }

    fun showMessage(message: String, isError: Boolean, requiresAppSettings: Boolean = false) {
        _uiState.value = _uiState.value.copy(
            message = message,
            messageIsError = isError,
            requiresAppSettings = requiresAppSettings,
            isSearching = false
        )
    }

    fun startTracking() {
        val currentState = _uiState.value
        if (
            currentState.stationNameInput.isBlank() ||
            currentState.isSearching ||
            currentState.isTracking
        ) return

        val stationName = currentState.stationNameInput.trim()
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                stationNameInput = stationName,
                isSearching = true,
                message = getApplication<Application>().getString(R.string.ui_searching),
                messageIsError = false,
                requiresAppSettings = false
            )
            
            try {
                val location = searchStation(stationName)
                if (location != null) {
                    // 検索成功後、サービス開始失敗を捕捉できる状態で起動する
                    _uiState.value = _uiState.value.copy(
                        isTracking = true,
                        isSearching = false,
                        stationName = stationName,
                        message = getApplication<Application>().getString(R.string.ui_service_starting),
                        messageIsError = false
                    )

                    startService(location, stationName)
                    repository.addStation(stationName)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        message = getApplication<Application>().getString(R.string.ui_not_found_detail),
                        messageIsError = true
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    isTracking = false,
                    currentDistance = null,
                    message = getApplication<Application>().getString(
                        R.string.ui_search_error,
                        e.localizedMessage ?: getApplication<Application>().getString(R.string.ui_unknown_error)
                    ),
                    messageIsError = true
                )
            }
        }
    }

    fun stopTracking() {
        val intent = android.content.Intent(getApplication(), com.example.stationalarm.service.StationAlarmService::class.java)
        intent.action = com.example.stationalarm.service.StationAlarmService.ACTION_STOP_SERVICE
        getApplication<Application>().startService(intent)
        
        // 即座にUIを戻す
        _uiState.value = _uiState.value.copy(
            isTracking = false,
            currentDistance = null,
            message = "",
            messageIsError = false,
            hasArrived = false,
            locationAccuracy = null
        )
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
        val query = if (name.endsWith("駅")) name else "$name 駅"
        val addresses = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine<List<Address>> { continuation ->
                    geocoder.getFromLocationName(
                        query,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (continuation.isActive) continuation.resume(addresses.toList())
                            }

                            override fun onError(errorMessage: String?) {
                                if (continuation.isActive) continuation.resume(emptyList())
                            }
                        }
                    )
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(query, 1).orEmpty()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }

        return addresses.firstOrNull()?.let { address ->
            Location("").apply {
                latitude = address.latitude
                longitude = address.longitude
            }
        }
    }
    
    data class UiState(
        val stationNameInput: String = "",
        val stationName: String = "",
        val distanceThreshold: Int = 500,
        val isTracking: Boolean = false,
        val isSearching: Boolean = false,
        val hasArrived: Boolean = false,
        val currentDistance: Float? = null,
        val locationAccuracy: Float? = null,
        val message: String = "",
        val messageIsError: Boolean = false,
        val requiresAppSettings: Boolean = false,
        val history: List<String> = emptyList()
    )
}
