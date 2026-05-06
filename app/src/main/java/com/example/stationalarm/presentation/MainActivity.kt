package com.example.stationalarm.presentation

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.example.stationalarm.presentation.theme.StationAlarmTheme
import com.example.stationalarm.tile.StationQuickStartTileService

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted = permissions.entries.all { it.value }
            if (granted) {
                // 権限取得直後にタイル経由の保留 Intent があれば処理する
                consumeQuickStartIntent(intent)
            }
        }

        permissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        // 起動時の Intent (タイルから渡された駅名) を処理
        consumeQuickStartIntent(intent)

        setContent {
            StationAlarmTheme {
                StationAlarmApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // singleTask 起動時のタイル再タップに対応
        consumeQuickStartIntent(intent)
    }

    /**
     * タイルから渡された駅名 extras を消費して即追跡開始する。
     * 権限未取得の場合は駅名を入力欄に反映するだけに留め、ユーザーの「開始」操作を待つ。
     */
    private fun consumeQuickStartIntent(intent: Intent?) {
        val station = intent?.getStringExtra(StationQuickStartTileService.EXTRA_QUICK_STATION)
            ?: return
        if (hasLocationPermission()) {
            viewModel.startTrackingFor(station, StationQuickStartTileService.QUICK_THRESHOLD)
        } else {
            // 権限未取得時は入力欄だけ埋め、権限ダイアログ後の再消費を待つ
            viewModel.updateStationNameInput(station)
        }
        // 二重消費防止のため extras をクリア
        intent.removeExtra(StationQuickStartTileService.EXTRA_QUICK_STATION)
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun StationAlarmApp() {
    StationAlarmScreen()
}

@Preview(device = Devices.WEAR_OS_SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    StationAlarmApp()
}
