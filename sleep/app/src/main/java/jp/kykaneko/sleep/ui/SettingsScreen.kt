package jp.kykaneko.sleep.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import jp.kykaneko.sleep.data.SettingsRepository

@Composable
fun SettingsScreen(
    targetSleepMinutes: Int,
    isSaving: Boolean,
    settingsSaved: Boolean,
    errorMessage: String?,
    onSaveTargetSleep: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var hoursText by rememberSaveable { mutableStateOf((targetSleepMinutes / 60).toString()) }
    var minutesText by rememberSaveable { mutableStateOf((targetSleepMinutes % 60).toString()) }

    LaunchedEffect(targetSleepMinutes) {
        hoursText = (targetSleepMinutes / 60).toString()
        minutesText = (targetSleepMinutes % 60).toString()
    }

    val hours = hoursText.toIntOrNull()
    val minutes = minutesText.toIntOrNull()
    val totalMinutes = if (hours != null && minutes != null) {
        hours * 60 + minutes
    } else {
        null
    }
    val validInput = hours != null &&
        minutes != null &&
        hours in 0..23 &&
        minutes in 0..59 &&
        totalMinutes in SettingsRepository.MIN_TARGET_SLEEP_MINUTES..SettingsRepository.MAX_TARGET_SLEEP_MINUTES

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("目標睡眠時間", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text("毎日の実睡眠時間と比較する目標です。不足分は負債に加算し、超過分は負債の返済に使います。")
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = hoursText,
                onValueChange = { value ->
                    if (value.all(Char::isDigit) && value.length <= 2) hoursText = value
                },
                modifier = Modifier.weight(1f),
                label = { Text("時間") },
                suffix = { Text("時間") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            OutlinedTextField(
                value = minutesText,
                onValueChange = { value ->
                    if (value.all(Char::isDigit) && value.length <= 2) minutesText = value
                },
                modifier = Modifier.weight(1f),
                label = { Text("分") },
                suffix = { Text("分") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        }
        Text(
            text = "現在の目標: ${targetSleepMinutes / 60}時間${targetSleepMinutes % 60}分",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (settingsSaved) {
            Text(
                text = "設定を保存しました。",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onBack) {
                Text("戻る")
            }
            Button(
                enabled = validInput && !isSaving,
                onClick = {
                    totalMinutes
                        ?.takeIf {
                            it in SettingsRepository.MIN_TARGET_SLEEP_MINUTES..SettingsRepository.MAX_TARGET_SLEEP_MINUTES
                        }
                        ?.let(onSaveTargetSleep)
                },
            ) {
                Text(if (isSaving) "保存中..." else "保存")
            }
        }
    }
}
