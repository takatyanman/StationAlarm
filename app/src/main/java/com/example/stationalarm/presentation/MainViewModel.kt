package com.example.stationalarm.presentation

import android.app.Application
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.tiles.TileService
import com.example.stationalarm.R
import com.example.stationalarm.data.StationRepository
import com.example.stationalarm.domain.model.FavoriteStationDefaults
import com.example.stationalarm.service.StationAlarmService
import com.example.stationalarm.tile.StationQuickStartTileService
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

    private val repository = StationRepository.getInstance(application)
    private val geocoder = Geocoder(application, Locale.JAPAN)

    init {
        viewModelScope.launch {
            launch {
                repository.history.collect { historyList ->
                    _uiState.value = _uiState.value.copy(history = historyList)
                }
            }
            launch {
                repository.favoriteStations.collect { favorites ->
                    _uiState.value = _uiState.value.copy(favoriteStations = favorites)
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
            stationCandidates = emptyList(),
            message = "",
            messageIsError = false,
            requiresAppSettings = false
        )
    }

    fun updateDistanceThreshold(distance: Int) {
        _uiState.value = _uiState.value.copy(distanceThreshold = distance.coerceIn(100, 2000))
    }

    fun replaceFavoriteStation(index: Int, stationName: String) {
        val normalizedName = stationName.trim()
        if (repository.replaceFavoriteStation(index, normalizedName)) {
            TileService.getUpdater(getApplication())
                .requestUpdate(StationQuickStartTileService::class.java)
            _uiState.value = _uiState.value.copy(
                message = getApplication<Application>().getString(
                    R.string.ui_favorite_updated,
                    normalizedName
                ),
                messageIsError = false
            )
        }
    }

    /** タイルなど外部導線から受け取った追跡条件を権限確認前に反映する。 */
    fun prepareTrackingFor(stationName: String, threshold: Int) {
        if (_uiState.value.isTracking || _uiState.value.isSearching) return
        _uiState.value = _uiState.value.copy(
            stationNameInput = stationName.trim(),
            stationCandidates = emptyList(),
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
                stationCandidates = emptyList(),
                isSearching = true,
                message = getApplication<Application>().getString(R.string.ui_searching),
                messageIsError = false,
                requiresAppSettings = false
            )

            try {
                val candidates = searchStations(stationName)
                when {
                    candidates.isEmpty() -> {
                        _uiState.value = _uiState.value.copy(
                            isSearching = false,
                            message = getApplication<Application>().getString(R.string.ui_not_found_detail),
                            messageIsError = true
                        )
                    }

                    candidates.size == 1 -> startTrackingAt(candidates.single(), stationName)

                    else -> {
                        _uiState.value = _uiState.value.copy(
                            isSearching = false,
                            stationCandidates = candidates,
                            message = "",
                            messageIsError = false
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showSearchError(e)
            }
        }
    }

    fun selectStationCandidate(candidate: StationCandidate) {
        val currentState = _uiState.value
        if (
            currentState.isTracking ||
            currentState.isSearching ||
            candidate !in currentState.stationCandidates
        ) return

        try {
            startTrackingAt(candidate, currentState.stationNameInput.trim())
        } catch (e: Exception) {
            showSearchError(e)
        }
    }

    fun cancelStationSelection() {
        _uiState.value = _uiState.value.copy(stationCandidates = emptyList())
    }

    fun stopTracking() {
        val intent = Intent(getApplication(), StationAlarmService::class.java).apply {
            action = StationAlarmService.ACTION_STOP_SERVICE
        }
        getApplication<Application>().startService(intent)

        _uiState.value = _uiState.value.copy(
            isTracking = false,
            stationCandidates = emptyList(),
            currentDistance = null,
            message = "",
            messageIsError = false,
            hasArrived = false,
            locationAccuracy = null
        )
    }

    private fun startTrackingAt(candidate: StationCandidate, stationName: String) {
        _uiState.value = _uiState.value.copy(
            isTracking = true,
            isSearching = false,
            stationCandidates = emptyList(),
            stationName = stationName,
            message = getApplication<Application>().getString(R.string.ui_service_starting),
            messageIsError = false
        )

        val location = Location("").apply {
            latitude = candidate.latitude
            longitude = candidate.longitude
        }
        startService(location, stationName)
        repository.addStation(stationName)
    }

    private fun showSearchError(error: Exception) {
        error.printStackTrace()
        _uiState.value = _uiState.value.copy(
            isSearching = false,
            isTracking = false,
            stationCandidates = emptyList(),
            currentDistance = null,
            message = getApplication<Application>().getString(
                R.string.ui_search_error,
                error.localizedMessage
                    ?: getApplication<Application>().getString(R.string.ui_unknown_error)
            ),
            messageIsError = true
        )
    }

    private fun startService(target: Location, stationName: String) {
        val intent = Intent(getApplication(), StationAlarmService::class.java).apply {
            putExtra("STATION_NAME", stationName)
            putExtra("TARGET_LAT", target.latitude)
            putExtra("TARGET_LNG", target.longitude)
            putExtra("THRESHOLD", _uiState.value.distanceThreshold)
        }
        getApplication<Application>().startForegroundService(intent)
    }

    private suspend fun searchStations(name: String): List<StationCandidate> {
        val query = if (name.endsWith("駅")) name else "$name 駅"
        val addresses = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine<List<Address>> { continuation ->
                    geocoder.getFromLocationName(
                        query,
                        MAX_SEARCH_RESULTS,
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
                    geocoder.getFromLocationName(query, MAX_SEARCH_RESULTS).orEmpty()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }

        return addresses
            .distinctBy { address -> address.latitude to address.longitude }
            .map { address -> address.toStationCandidate(name) }
    }

    private fun Address.toStationCandidate(fallbackName: String): StationCandidate {
        val title = featureName
            ?.takeIf { feature -> feature.isNotBlank() && feature != getAddressLine(0) }
            ?: fallbackName
        val area = listOfNotNull(adminArea, locality, subLocality, thoroughfare)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(separator = " ")
            .ifBlank { getAddressLine(0).orEmpty() }

        return StationCandidate(
            title = title,
            address = area,
            latitude = latitude,
            longitude = longitude
        )
    }

    data class StationCandidate(
        val title: String,
        val address: String,
        val latitude: Double,
        val longitude: Double
    )

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
        val history: List<String> = emptyList(),
        val favoriteStations: List<String> = FavoriteStationDefaults.names,
        val stationCandidates: List<StationCandidate> = emptyList()
    )

    private companion object {
        const val MAX_SEARCH_RESULTS = 5
    }
}
