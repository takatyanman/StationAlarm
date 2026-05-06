package com.example.stationalarm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import com.example.stationalarm.presentation.MainActivity
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
    private var vibrationJob: Job? = null
    // しきい値到達後の二重発火を防ぐフラグ
    private var hasAlarmFired: Boolean = false

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
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
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

        // 監視中チャネルでフォアグラウンド通知を表示
        startForeground(
            NOTIFICATION_ID_MONITORING,
            createMonitoringNotification(getString(R.string.notification_monitoring, stationName))
        )

        // UIに通知して画面遷移させる
        repository.updateIsTracking(true)
        repository.updateStationName(stationName)
        repository.updateMessage(getString(R.string.notification_started))

        startTracking(targetLocation, threshold, stationName)

        return START_NOT_STICKY // 意図しない再起動を防ぐため sticky にしない（データ欠落の可能性があるため）
    }

    private fun startTracking(target: Location, threshold: Int, stationName: String) {
        locationJob?.cancel()
        hasAlarmFired = false
        locationJob = serviceScope.launch {
            try {
                locationManager.getLocationFlow().collect { location ->
                    val distance = location.distanceTo(target)
                    repository.updateDistance(distance)

                    if (!hasAlarmFired) {
                        updateMonitoringNotification(
                            getString(R.string.notification_remaining, distance.toInt(), stationName)
                        )
                    }

                    // しきい値到達時は1回だけ発火する
                    if (distance <= threshold && !hasAlarmFired) {
                        hasAlarmFired = true
                        repository.updateMessage(getString(R.string.notification_arrived))
                        // アプリを前面に出してアラーム画面表示と振動を開始する
                        fireArrivalAlarm(stationName)
                        // 8秒後にサービス自身を停止して追跡を終了する（ユーザーが画面を確認する猶予）
                        scheduleAutoStop()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                repository.updateMessage(getString(R.string.notification_error, e.localizedMessage))
            }
        }
    }

    /**
     * 到着アラーム発火: 振動・高優先度通知・アプリ前面化を行う
     */
    private fun fireArrivalAlarm(stationName: String) {
        vibrate()
        showAlarmNotification(stationName)
        launchAppToForeground()
    }

    private fun vibrate() {
        // 5秒間振動するパターン（0.5秒ON、0.5秒OFFを繰り返す）
        val vibrationEffect = VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0)
        vibrator.vibrate(vibrationEffect)

        // 5秒後に自動停止
        vibrationJob?.cancel()
        vibrationJob = serviceScope.launch {
            kotlinx.coroutines.delay(VIBRATION_DURATION_MS)
            vibrator.cancel()
        }
    }

    /**
     * アラーム発火後、サービスを自動停止する
     * 振動完了 + 短い猶予時間を待ってから stopSelf する
     */
    private fun scheduleAutoStop() {
        serviceScope.launch {
            kotlinx.coroutines.delay(AUTO_STOP_DELAY_MS)
            stopSelf()
        }
    }

    /**
     * MainActivity を直接起動してアプリを前面化する
     * 画面ロック中・スリープ中でも起動するように TURN_SCREEN_ON を併用
     */
    private fun launchAppToForeground() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // バックグラウンドからの startActivity 制限に該当した場合は通知のフルスクリーン Intent に頼る
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationJob?.cancel()
        vibrationJob?.cancel()
        vibrator.cancel()
        repository.updateIsTracking(false)
        repository.updateDistance(null)
        repository.updateStationName(null)
        repository.updateMessage("")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 通知チャネルを2種類作成する:
     *  - 監視中チャネル (LOW): フォアグラウンド常駐通知用、ヘッドアップ表示しない
     *  - アラームチャネル (HIGH): 到着時のフルスクリーン通知用、画面に強制表示
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val monitoringChannel = NotificationChannel(
                CHANNEL_ID_MONITORING,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(monitoringChannel)

            val alarmChannel = NotificationChannel(
                CHANNEL_ID_ALARM,
                getString(R.string.channel_name_alarm),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_description_alarm)
                // 音は鳴らさない: 振動のみで通知する
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(alarmChannel)
        }
    }

    private fun updateMonitoringNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_MONITORING, createMonitoringNotification(content))
    }

    private fun createMonitoringNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID_MONITORING)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(buildAppLaunchPendingIntent())
            .build()
    }

    /**
     * 到着アラーム通知を別 ID で表示する
     * 監視中通知とは別に表示することでフォアグラウンドサービス通知と切り離し、
     * フルスクリーン Intent をより確実に発火させる
     */
    private fun showAlarmNotification(stationName: String) {
        val pendingIntent = buildAppLaunchPendingIntent()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ALARM)
            .setContentTitle(stationName)
            .setContentText(getString(R.string.notification_arrived))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            // 音・通知側の振動を無効化（振動は Service 内 Vibrator で明示制御するため）
            .setSound(null)
            .setDefaults(0)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_ALARM, notification)
    }

    private fun buildAppLaunchPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        // 外部から Service を停止する際に使う Intent アクション
        const val ACTION_STOP_SERVICE = "com.example.stationalarm.action.STOP_SERVICE"

        private const val CHANNEL_ID_MONITORING = "station_alarm_channel"
        private const val CHANNEL_ID_ALARM = "station_alarm_channel_high"
        private const val NOTIFICATION_ID_MONITORING = 1
        private const val NOTIFICATION_ID_ALARM = 2

        // 振動継続時間
        private const val VIBRATION_DURATION_MS = 5000L
        // アラーム発火後にサービスを自動停止するまでの猶予 (ユーザーが画面を見られる時間を確保)
        private const val AUTO_STOP_DELAY_MS = 8000L
    }
}
