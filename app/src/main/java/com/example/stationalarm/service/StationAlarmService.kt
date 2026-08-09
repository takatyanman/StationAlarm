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
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import com.example.stationalarm.R
import com.example.stationalarm.data.StationRepository
import com.example.stationalarm.location.LocationManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class StationAlarmService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Default)
    private lateinit var repository: StationRepository
    private lateinit var locationManager: LocationManager
    private var locationJob: Job? = null
    private var vibrationJob: Job? = null
    private var autoStopJob: Job? = null
    // しきい値到達後の二重発火を防ぐフラグ
    private var hasAlarmFired: Boolean = false
    // GPS の一時的な飛び値で発火しないよう、連続到達回数を保持する
    private var consecutiveArrivalSamples: Int = 0

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
            getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID_ALARM)
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
        repository.updateHasArrived(false)

        startTracking(targetLocation, threshold, stationName)

        return START_NOT_STICKY // 意図しない再起動を防ぐため sticky にしない（データ欠落の可能性があるため）
    }

    private fun startTracking(target: Location, threshold: Int, stationName: String) {
        locationJob?.cancel()
        autoStopJob?.cancel()
        hasAlarmFired = false
        consecutiveArrivalSamples = 0
        locationJob = serviceScope.launch {
            try {
                locationManager.getLocationFlow().collect { location ->
                    val distance = location.distanceTo(target)
                    val accuracy = if (location.hasAccuracy()) location.accuracy else null
                    repository.updateLocation(distance, accuracy)

                    if (!hasAlarmFired) {
                        updateMonitoringNotification(
                            getString(R.string.notification_remaining, distance.toInt(), stationName)
                        )
                    }

                    val accuracyIsAcceptable = accuracy == null ||
                            accuracy <= threshold.coerceAtMost(MAX_ACCEPTABLE_ACCURACY_METERS).toFloat()
                    consecutiveArrivalSamples = if (
                        distance <= threshold && accuracyIsAcceptable && !hasAlarmFired
                    ) {
                        consecutiveArrivalSamples + 1
                    } else {
                        0
                    }

                    // 許容精度の位置が連続してしきい値内に入った場合だけ、1回発火する
                    if (
                        consecutiveArrivalSamples >= REQUIRED_ARRIVAL_SAMPLES &&
                        !hasAlarmFired
                    ) {
                        hasAlarmFired = true
                        repository.updateMessage(getString(R.string.notification_arrived))
                        repository.updateHasArrived(true)
                        // フルスクリーン通知でアラーム画面表示と振動を開始する
                        fireArrivalAlarm(stationName)
                        // 到着確認の猶予後にサービス自身を停止して追跡を終了する
                        scheduleAutoStop()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                repository.updateMessage(
                    getString(R.string.notification_error, e.localizedMessage),
                    isError = true
                )
                stopSelf()
            }
        }
    }

    /**
     * 到着アラーム発火: 振動・高優先度通知・アプリ前面化を行う
     */
    private fun fireArrivalAlarm(stationName: String) {
        vibrate()
        showAlarmNotification(stationName)
    }

    private fun vibrate() {
        // 5秒間振動するパターン（0.5秒ON、0.5秒OFFを繰り返す）
        val vibrationEffect = VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0)
        vibrator.vibrate(vibrationEffect)

        // 5秒後に自動停止
        vibrationJob?.cancel()
        vibrationJob = serviceScope.launch {
            kotlinx.coroutines.delay(ArrivalAlarmContract.COMPLETION_DELAY_MS)
            vibrator.cancel()
        }
    }

    /**
     * アラーム発火後、サービスを自動停止する
     * 振動完了 + 短い猶予時間を待ってから stopSelf する
     */
    private fun scheduleAutoStop() {
        autoStopJob?.cancel()
        autoStopJob = serviceScope.launch {
            kotlinx.coroutines.delay(ArrivalAlarmContract.COMPLETION_DELAY_MS)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val keepErrorMessage = repository.trackingState.value.isError
        locationJob?.cancel()
        vibrationJob?.cancel()
        autoStopJob?.cancel()
        vibrator.cancel()
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID_ALARM)
        repository.finishTracking(preserveError = keepErrorMessage)
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 通知チャネルを2種類作成する:
     *  - 監視中チャネル (LOW): フォアグラウンド常駐通知用、ヘッドアップ表示しない
     *  - アラームチャネル (HIGH): 到着時のフルスクリーン通知用、画面に強制表示
     */
    private fun createNotificationChannels() {
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

    private fun updateMonitoringNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_MONITORING, createMonitoringNotification(content))
    }

    private fun createMonitoringNotification(content: String): Notification {
        val touchIntent = buildAppLaunchPendingIntent()
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID_MONITORING)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_station_alarm_notification)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(touchIntent)

        OngoingActivity.Builder(this, NOTIFICATION_ID_MONITORING, notificationBuilder)
            .setStaticIcon(R.drawable.ic_station_alarm_notification)
            .setTouchIntent(touchIntent)
            .setContentDescription(getString(R.string.ongoing_activity_description))
            .build()
            .apply(this)

        return notificationBuilder.build()
    }

    /**
     * 到着アラーム通知を別 ID で表示する
     * 監視中通知とは別に表示することでフォアグラウンドサービス通知と切り離し、
     * フルスクリーン Intent をより確実に発火させる
     */
    private fun showAlarmNotification(stationName: String) {
        val fullScreenPendingIntent = buildAppLaunchPendingIntent(autoClose = true)
        val contentPendingIntent = buildAppLaunchPendingIntent(autoClose = false)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ALARM)
            .setContentTitle(stationName)
            .setContentText(getString(R.string.notification_arrived))
            .setSmallIcon(R.drawable.ic_station_alarm_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            // 音・通知側の振動を無効化（振動は Service 内 Vibrator で明示制御するため）
            .setSound(null)
            .setDefaults(0)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_ALARM, notification)
    }

    private fun buildAppLaunchPendingIntent(autoClose: Boolean = false): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            if (autoClose) {
                putExtra(
                    ArrivalAlarmContract.EXTRA_AUTO_CLOSE_AT_ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + ArrivalAlarmContract.COMPLETION_DELAY_MS
                )
            }
        }
        return PendingIntent.getActivity(
            this,
            if (autoClose) REQUEST_CODE_ARRIVAL_FULL_SCREEN else REQUEST_CODE_APP_LAUNCH,
            intent,
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
        private const val REQUEST_CODE_APP_LAUNCH = 0
        private const val REQUEST_CODE_ARRIVAL_FULL_SCREEN = 1

        private const val REQUIRED_ARRIVAL_SAMPLES = 2
        private const val MAX_ACCEPTABLE_ACCURACY_METERS = 200
    }
}
