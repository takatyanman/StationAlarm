package com.example.stationalarm.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.app.RemoteInput
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.wear.input.RemoteInputIntentHelper
import com.example.stationalarm.R
import com.example.stationalarm.presentation.theme.StationAlarmTheme
import com.example.stationalarm.service.ArrivalAlarmContract
import com.example.stationalarm.tile.QuickStartContract
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var stationInputLauncher: ActivityResultLauncher<Intent>
    private var pendingTrackingStart = false
    private var isActivityResumed = false
    private var arrivalAutoCloseJob: Job? = null

    // FragmentActivity を使用していないため、Fragment 版数に関する誤検出を局所的に抑制する
    @SuppressLint("InvalidFragmentVersionForActivityResult")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            if (!pendingTrackingStart) return@registerForActivityResult

            when {
                hasPreciseLocationPermission() -> requestNotificationPermissionOrStart()
                hasApproximateLocationPermission() -> viewModel.showMessage(
                    getString(R.string.ui_permission_precise_required),
                    isError = true,
                    requiresAppSettings = !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                )
                else -> viewModel.showMessage(
                    getString(R.string.ui_permission_location_required),
                    isError = true,
                    requiresAppSettings = !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                )
            }
            if (!hasPreciseLocationPermission()) pendingTrackingStart = false
        }

        notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!pendingTrackingStart) return@registerForActivityResult

            if (granted) {
                startTrackingAfterPermissions()
            } else {
                pendingTrackingStart = false
                viewModel.showMessage(
                    getString(R.string.ui_permission_notifications_required),
                    isError = true,
                    requiresAppSettings = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                )
            }
        }

        stationInputLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != RESULT_OK || result.data == null) {
                return@registerForActivityResult
            }
            val stationName = RemoteInput.getResultsFromIntent(result.data)
                ?.getCharSequence(STATION_INPUT_RESULT_KEY)
                ?.toString()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return@registerForActivityResult
            viewModel.updateStationNameInput(stationName)
        }

        setContent {
            StationAlarmTheme {
                StationAlarmApp(
                    viewModel = viewModel,
                    onStartRequested = ::requestTrackingStart,
                    onStationInputRequested = ::requestStationNameInput,
                    onOpenAppSettings = ::openAppSettings
                )
            }
        }

        // タイルから受け取った駅名は、画面構築後に権限確認付きで処理する
        consumeQuickStartIntent(intent)
        scheduleArrivalAutoClose(intent, activityWasVisible = false)
    }

    override fun onNewIntent(intent: Intent) {
        val activityWasVisible = isActivityResumed
        super.onNewIntent(intent)
        setIntent(intent)
        consumeQuickStartIntent(intent)
        scheduleArrivalAutoClose(intent, activityWasVisible)
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
    }

    override fun onPause() {
        isActivityResumed = false
        super.onPause()
    }

    private fun requestTrackingStart() {
        if (viewModel.uiState.value.isTracking || viewModel.uiState.value.isSearching) return

        pendingTrackingStart = true
        if (hasPreciseLocationPermission()) {
            requestNotificationPermissionOrStart()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun requestNotificationPermissionOrStart() {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startTrackingAfterPermissions()
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startTrackingAfterPermissions() {
        pendingTrackingStart = false
        viewModel.startTracking()
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }

    private fun requestStationNameInput() {
        val remoteInput = RemoteInput.Builder(STATION_INPUT_RESULT_KEY)
            .setLabel(getString(R.string.ui_station_input_prompt))
            .setAllowFreeFormInput(true)
            .build()
        val inputIntent = RemoteInputIntentHelper.createActionRemoteInputIntent().also { intent ->
            RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
            RemoteInputIntentHelper.putTitleExtra(
                intent,
                getString(R.string.ui_station_input_title)
            )
            RemoteInputIntentHelper.putConfirmLabelExtra(
                intent,
                getString(R.string.ui_station_input_confirm)
            )
            RemoteInputIntentHelper.putCancelLabelExtra(intent, getString(R.string.ui_cancel))
        }
        stationInputLauncher.launch(inputIntent)
    }

    private fun scheduleArrivalAutoClose(intent: Intent?, activityWasVisible: Boolean) {
        arrivalAutoCloseJob?.cancel()

        val closeAt = intent?.getLongExtra(
            ArrivalAlarmContract.EXTRA_AUTO_CLOSE_AT_ELAPSED_REALTIME,
            0L
        ) ?: 0L

        val autoCloseDelay = ArrivalAlarmContract.remainingAutoCloseDelayMs(
            closeAtElapsedRealtime = closeAt,
            nowElapsedRealtime = SystemClock.elapsedRealtime(),
            activityWasVisible = activityWasVisible
        )
        if (autoCloseDelay == null) {
            intent?.removeExtra(ArrivalAlarmContract.EXTRA_AUTO_CLOSE_AT_ELAPSED_REALTIME)
            return
        }

        arrivalAutoCloseJob = lifecycleScope.launch {
            delay(autoCloseDelay)
            finishAndRemoveTask()
        }
    }

    /**
     * タイルから渡された駅名を反映し、通常画面と同じ権限確認を通して追跡開始する。
     */
    private fun consumeQuickStartIntent(intent: Intent?) {
        val station = intent?.getStringExtra(QuickStartContract.EXTRA_STATION_NAME)
            ?: return
        intent.removeExtra(QuickStartContract.EXTRA_STATION_NAME)

        if (viewModel.uiState.value.isTracking || viewModel.uiState.value.isSearching) {
            viewModel.showMessage(
                getString(R.string.ui_tracking_already_active),
                isError = false
            )
        } else {
            viewModel.prepareTrackingFor(station, QuickStartContract.DEFAULT_THRESHOLD_METERS)
            requestTrackingStart()
        }
    }

    private fun hasPreciseLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasApproximateLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun StationAlarmApp(
    viewModel: MainViewModel,
    onStartRequested: () -> Unit,
    onStationInputRequested: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    StationAlarmScreen(
        viewModel = viewModel,
        onStartRequested = onStartRequested,
        onStationInputRequested = onStationInputRequested,
        onOpenAppSettings = onOpenAppSettings
    )
}

@Preview(device = "id:wearos_small_round", showSystemUi = true)
@Composable
fun DefaultPreview() {
    StationAlarmTheme {
        SetupScreen(
            uiState = MainViewModel.UiState(),
            onHistoryClick = {},
            onDistanceChange = {},
            onReplaceFavorite = { _, _ -> },
            onStationInputRequested = {},
            onStartClick = {},
            onOpenAppSettings = {}
        )
    }
}

private const val STATION_INPUT_RESULT_KEY = "station_name_input"
