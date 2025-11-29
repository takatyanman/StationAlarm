package com.example.stationalarm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.stationalarm.LocationManager
import com.example.stationalarm.R
import com.example.stationalarm.data.StationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StationAlarmService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repository: StationRepository
    private lateinit var locationManager: LocationManager
    private var locationJob: Job? = null
    
    private val vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = StationRepository.getInstance(applicationContext)
        locationManager = LocationManager(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_SERVICE") {
            stopSelf()
            return START_NOT_STICKY
        }

        val stationName = intent?.getStringExtra("STATION_NAME") ?: return START_NOT_STICKY
        val targetLat = intent.getDoubleExtra("TARGET_LAT", 0.0)
        val targetLng = intent.getDoubleExtra("TARGET_LNG", 0.0)
        val threshold = intent.getIntExtra("THRESHOLD", 500)

        val targetLocation = Location("").apply {
            latitude = targetLat
            longitude = targetLng
        }

        startForeground(1, createNotification("監視中: $stationName"))
        
        repository.updateIsTracking(true)
        repository.updateMessage("監視を開始します")

        startTracking(targetLocation, threshold, stationName)

        return START_STICKY
    }

    private fun startTracking(target: Location, threshold: Int, stationName: String) {
        locationJob?.cancel()
        locationJob = serviceScope.launch {
            locationManager.getLocationFlow().collect { location ->
                val distance = location.distanceTo(target)
                repository.updateDistance(distance)
                
                updateNotification("残り: ${distance.toInt()}m ($stationName)")

                if (distance <= threshold) {
                    vibrate()
                    repository.updateMessage("目的地に接近しました！")
                    updateNotification("目的地に接近しました！")
                }
            }
        }
    }

    private fun vibrate() {
        val vibrationEffect = VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0)
        vibrator.vibrate(vibrationEffect)
    }

    override fun onDestroy() {
        super.onDestroy()
        locationJob?.cancel()
        vibrator.cancel()
        repository.updateIsTracking(false)
        repository.updateDistance(null)
        repository.updateMessage("")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "station_alarm_channel",
                "Station Alarm Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, "station_alarm_channel")
            .setContentTitle("駅近振動通知")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, createNotification(content))
    }
}
