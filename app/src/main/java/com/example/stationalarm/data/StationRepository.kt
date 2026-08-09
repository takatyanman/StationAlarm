package com.example.stationalarm.data

import android.content.Context
import android.content.SharedPreferences
import com.example.stationalarm.domain.model.FavoriteStationDefaults
import com.example.stationalarm.domain.model.MAX_FAVORITE_STATIONS
import com.example.stationalarm.domain.model.withFavoriteReplaced
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StationRepository private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("station_prefs", Context.MODE_PRIVATE)
    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()
    private val _favoriteStations = MutableStateFlow(FavoriteStationDefaults.names)
    val favoriteStations: StateFlow<List<String>> = _favoriteStations.asStateFlow()

    companion object {
        @Volatile
        private var INSTANCE: StationRepository? = null

        fun getInstance(context: Context): StationRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StationRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        loadHistory()
        loadFavoriteStations()
    }

    private fun loadHistory() {
        val historyString = prefs.getString("history", "") ?: ""
        if (historyString.isNotEmpty()) {
            _history.value = historyString.split(",").filter { it.isNotBlank() }
        }
    }

    fun addStation(stationName: String) {
        if (stationName.isBlank()) return
        
        val currentList = _history.value.toMutableList()
        // 既存項目を削除して先頭へ移動する
        currentList.remove(stationName)
        currentList.add(0, stationName)
        // 直近 5 件だけを保持する
        if (currentList.size > 5) {
            currentList.removeAt(currentList.lastIndex)
        }
        
        _history.value = currentList
        saveHistory()
    }

    private fun saveHistory() {
        val historyString = _history.value.joinToString(",")
        prefs.edit().putString("history", historyString).apply()
    }

    // Service と UI の間で共有する揮発状態
    private val _trackingState = MutableStateFlow<TrackingState>(TrackingState())
    val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()

    fun updateLocation(distance: Float?, accuracy: Float?) {
        _trackingState.value = _trackingState.value.copy(
            currentDistance = distance,
            locationAccuracy = accuracy
        )
    }

    private fun loadFavoriteStations() {
        val stored = prefs.getString(FavoritePreferences.KEY_FAVORITE_STATIONS, null)
            ?.split(FavoritePreferences.FAVORITE_SEPARATOR)
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.distinct()
            ?.take(MAX_FAVORITE_STATIONS)
            .orEmpty()

        _favoriteStations.value = if (stored.size == MAX_FAVORITE_STATIONS) {
            stored
        } else {
            (stored + FavoriteStationDefaults.names)
                .distinct()
                .take(MAX_FAVORITE_STATIONS)
        }
    }

    /** 指定スロットを置換する。重複・空文字・範囲外は変更しない。 */
    fun replaceFavoriteStation(index: Int, stationName: String): Boolean {
        val updated = _favoriteStations.value.withFavoriteReplaced(index, stationName)
            ?: return false
        _favoriteStations.value = updated
        prefs.edit()
            .putString(
                FavoritePreferences.KEY_FAVORITE_STATIONS,
                _favoriteStations.value.joinToString(FavoritePreferences.FAVORITE_SEPARATOR)
            )
            .apply()
        return true
    }

    fun updateMessage(message: String, isError: Boolean = false) {
        _trackingState.value = _trackingState.value.copy(message = message, isError = isError)
    }
    
    fun updateIsTracking(isTracking: Boolean) {
        _trackingState.value = _trackingState.value.copy(isTracking = isTracking)
    }
    
    fun updateStationName(stationName: String?) {
        _trackingState.value = _trackingState.value.copy(stationName = stationName)
    }

    fun updateHasArrived(hasArrived: Boolean) {
        _trackingState.value = _trackingState.value.copy(hasArrived = hasArrived)
    }

    /**
     * 追跡終了時の共有状態を 1 回の更新で初期化する。
     */
    fun finishTracking(preserveError: Boolean) {
        val current = _trackingState.value
        _trackingState.value = TrackingState(
            message = if (preserveError) current.message else "",
            isError = preserveError && current.isError
        )
    }

    data class TrackingState(
        val isTracking: Boolean = false,
        val currentDistance: Float? = null,
        val locationAccuracy: Float? = null,
        val message: String = "",
        val isError: Boolean = false,
        val stationName: String? = null,
        val hasArrived: Boolean = false
    )

    private object FavoritePreferences {
        const val KEY_FAVORITE_STATIONS = "favorite_stations"
        const val FAVORITE_SEPARATOR = "\u001F"
    }
}
