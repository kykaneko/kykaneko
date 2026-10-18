package jp.kykaneko.sleep.ui

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.kykaneko.sleep.data.SleepRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SleepUiState(
    val isLoading: Boolean = true,
    val healthConnectAvailable: Boolean = true,
    val hasPermission: Boolean = false,
    val balanceMinutes: Int? = null,
    val errorMessage: String? = null,
)

class SleepViewModel(application: Application) : AndroidViewModel(application) {
    private val repository by lazy { SleepRepository(getApplication()) }
    private val _uiState = MutableStateFlow(SleepUiState())
    val uiState: StateFlow<SleepUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun onPermissionsResult(grantedPermissions: Set<String>) {
        if (SleepRepository.SLEEP_PERMISSIONS.all(grantedPermissions::contains)) {
            refresh()
        } else {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                hasPermission = false,
                errorMessage = "睡眠データの読み取りが許可されていません。Health Connectの設定を確認してください。",
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val status = withContext<Int>(Dispatchers.IO) {
                    HealthConnectClient.getSdkStatus(
                        getApplication<Application>(),
                        SleepRepository.providerPackageName(getApplication<Application>()),
                    )
                }
                if (status != HealthConnectClient.SDK_AVAILABLE) {
                    _uiState.value = SleepUiState(
                        isLoading = false,
                        healthConnectAvailable = false,
                    )
                    return@launch
                }

                val hasPermission = withContext(Dispatchers.IO) {
                    repository.hasSleepPermission()
                }
                if (!hasPermission) {
                    _uiState.value = SleepUiState(
                        isLoading = false,
                        hasPermission = false,
                    )
                    return@launch
                }

                val balance = withContext(Dispatchers.IO) {
                    repository.readLast14DaysBalance()
                }
                _uiState.value = SleepUiState(
                    isLoading = false,
                    hasPermission = true,
                    balanceMinutes = balance.roundedMinutes,
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = exception.localizedMessage
                        ?: "睡眠データを取得できませんでした。",
                )
            }
        }
    }
}
