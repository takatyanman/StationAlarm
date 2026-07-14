package com.example.stationalarm.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.example.stationalarm.R
import com.example.stationalarm.presentation.theme.StationAlarmTheme
import com.example.stationalarm.tile.StationQuickStartTileService

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
    private var pendingTrackingStart = false

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

        setContent {
            StationAlarmTheme {
                StationAlarmApp(
                    viewModel = viewModel,
                    onStartRequested = ::requestTrackingStart,
                    onOpenAppSettings = ::openAppSettings
                )
            }
        }

        // タイルから受け取った駅名は、画面構築後に権限確認付きで処理する
        consumeQuickStartIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeQuickStartIntent(intent)
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

    /**
     * タイルから渡された駅名を反映し、通常画面と同じ権限確認を通して追跡開始する。
     */
    private fun consumeQuickStartIntent(intent: Intent?) {
        val station = intent?.getStringExtra(StationQuickStartTileService.EXTRA_QUICK_STATION)
            ?: return
        intent.removeExtra(StationQuickStartTileService.EXTRA_QUICK_STATION)

        if (viewModel.uiState.value.isTracking || viewModel.uiState.value.isSearching) {
            viewModel.showMessage(
                getString(R.string.ui_tracking_already_active),
                isError = false
            )
        } else {
            viewModel.prepareTrackingFor(station, StationQuickStartTileService.QUICK_THRESHOLD)
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
    onOpenAppSettings: () -> Unit
) {
    StationAlarmScreen(
        viewModel = viewModel,
        onStartRequested = onStartRequested,
        onOpenAppSettings = onOpenAppSettings
    )
}

@Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    StationAlarmTheme {
        SetupScreen(
            uiState = MainViewModel.UiState(),
            onStationNameChange = {},
            onHistoryClick = {},
            onDistanceChange = {},
            onStartClick = {},
            onOpenAppSettings = {}
        )
    }
}
