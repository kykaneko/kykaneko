package jp.kykaneko.sleep.ui

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.kykaneko.sleep.data.DailyDebtData
import jp.kykaneko.sleep.data.SettingsRepository
import jp.kykaneko.sleep.data.SleepRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

data class SleepUiState(
    val isLoading: Boolean = true,
    val healthConnectAvailable: Boolean = true,
    val hasPermission: Boolean = false,
    val sleepDebtMinutes: Int? = null,
    val dailyDebt: List<DailyDebtData> = emptyList(),
    val targetSleepMinutes: Int = SettingsRepository.DEFAULT_TARGET_SLEEP_MINUTES,
    val isSavingSettings: Boolean = false,
    val settingsSaved: Boolean = false,
    val errorMessage: String? = null,
)

class SleepViewModel(application: Application) : AndroidViewModel(application) {
    private val repository by lazy { SleepRepository(getApplication()) }
    private val settingsRepository = SettingsRepository(application)
    private val _uiState = MutableStateFlow(SleepUiState())
    val uiState: StateFlow<SleepUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.targetSleepMinutes
                .distinctUntilChanged()
                .catch { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = exception.localizedMessage
                            ?: "設定を読み込めませんでした。",
                    )
                }
                .collect { targetMinutes ->
                    _uiState.value = _uiState.value.copy(targetSleepMinutes = targetMinutes)
                    refresh(targetMinutes)
                }
        }
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

    fun setTargetSleepMinutes(minutes: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSavingSettings = true,
                settingsSaved = false,
                errorMessage = null,
            )
            try {
                settingsRepository.setTargetSleepMinutes(minutes)
                _uiState.value = _uiState.value.copy(
                    isSavingSettings = false,
                    settingsSaved = true,
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSavingSettings = false,
                    errorMessage = exception.localizedMessage ?: "設定を保存できませんでした。",
                )
            }
        }
    }

    fun refresh() = refresh(_uiState.value.targetSleepMinutes)

    private fun refresh(targetSleepMinutes: Int) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
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
                        targetSleepMinutes = targetSleepMinutes,
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
                        targetSleepMinutes = targetSleepMinutes,
                    )
                    return@launch
                }

                val balance = withContext(Dispatchers.IO) {
                    repository.readLast14DaysSummary(targetSleepMinutes)
                }
                _uiState.value = SleepUiState(
                    isLoading = false,
                    hasPermission = true,
                    sleepDebtMinutes = balance.balance.roundedMinutes.absoluteValue,
                    dailyDebt = balance.dailyDebt,
                    targetSleepMinutes = targetSleepMinutes,
                    settingsSaved = _uiState.value.settingsSaved,
                )
            } catch (exception: CancellationException) {
                throw exception
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
