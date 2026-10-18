package jp.kykaneko.sleep

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import jp.kykaneko.sleep.data.SleepRepository
import jp.kykaneko.sleep.ui.SleepUiState
import jp.kykaneko.sleep.ui.SleepViewModel
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private val viewModel: SleepViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.collectAsState()
            val permissionLauncher = rememberLauncherForActivityResult(
                PermissionController.createRequestPermissionResultContract(),
            ) { permissions ->
                viewModel.onPermissionsResult(permissions)
            }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF7F8FC),
                ) {
                    SleepHome(
                        state = state,
                        onConnectClick = {
                            when {
                                state.hasPermission -> viewModel.refresh()
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                                    permissionLauncher.launch(SleepRepository.SLEEP_PERMISSIONS)
                                !state.healthConnectAvailable -> openHealthConnectInstallPage()
                                else -> permissionLauncher.launch(SleepRepository.SLEEP_PERMISSIONS)
                            }
                        },
                        onOpenHealthConnectSettings = ::openHealthConnectSettings,
                    )
                }
            }
        }
    }

    private fun openHealthConnectInstallPage() {
        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "market://details?id=${SleepRepository.providerPackageName(this)}",
                ),
            ),
        )
    }

    private fun openHealthConnectSettings() {
        startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
    }
}

@androidx.compose.runtime.Composable
private fun SleepHome(
    state: SleepUiState,
    onConnectClick: () -> Unit,
    onOpenHealthConnectSettings: () -> Unit,
) {
    val balance = state.balanceMinutes
    val debtColor = Color(0xFFC62828)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            state.isLoading -> CircularProgressIndicator()
            balance != null -> {
                val hours = abs(balance) / 60
                val minutes = abs(balance) % 60
                Text(
                    text = if (balance == 0) {
                        "負債ゼロ"
                    } else {
                        "現在の睡眠負債：${hours}時間${minutes.toString().padStart(2, '0')}分"
                    },
                    color = debtColor,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            !state.healthConnectAvailable ->
                Text(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        "Health Connectの権限を許可してください。"
                    } else {
                        "Health Connectをインストールしてから連携してください。"
                    },
                )
            !state.hasPermission ->
                Text("睡眠データを読み取るにはアクセス許可が必要です。")
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onConnectClick,
            enabled = !state.isLoading || Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
        ) {
            Text(
                if (state.hasPermission) "再同期"
                else "睡眠データの読み取りを許可",
            )
        }
        if (!state.hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onOpenHealthConnectSettings) {
                Text("Health Connect設定を開く")
            }
        }

        state.errorMessage?.let { message ->
            Spacer(Modifier.height(16.dp))
            Text(message, color = MaterialTheme.colorScheme.error)
        }
    }
}
