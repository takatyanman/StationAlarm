package com.example.stationalarm.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StationRepository private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("station_prefs", Context.MODE_PRIVATE)
    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

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
        // Remove if exists to move to top
        currentList.remove(stationName)
        // Add to top
        currentList.add(0, stationName)
        // Keep only last 5
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

    // Transient state for Service <-> UI communication
    private val _trackingState = MutableStateFlow<TrackingState>(TrackingState())
    val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()

    fun updateDistance(distance: Float?) {
        _trackingState.value = _trackingState.value.copy(currentDistance = distance)
    }

    fun updateMessage(message: String) {
        _trackingState.value = _trackingState.value.copy(message = message)
    }
    
    fun updateIsTracking(isTracking: Boolean) {
        _trackingState.value = _trackingState.value.copy(isTracking = isTracking)
    }

    data class TrackingState(
        val isTracking: Boolean = false,
        val currentDistance: Float? = null,
        val message: String = ""
    )
}
