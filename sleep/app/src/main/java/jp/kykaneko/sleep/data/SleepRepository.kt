package jp.kykaneko.sleep.data

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.round

data class SleepBalance(
    val rawMinutes: Int,
    val roundedMinutes: Int,
)

data class DailyDebtData(
    val date: String,
    val debtMinutes: Int,
    val actualSleepMinutes: Long?,
)

data class SleepSummary(
    val balance: SleepBalance,
    val dailyDebt: List<DailyDebtData>,
)

internal fun calculateDebtProgress(
    actualSleepMinutes: List<Long?>,
    targetSleepMinutes: Int,
): List<Int> {
    require(targetSleepMinutes in SettingsRepository.MIN_TARGET_SLEEP_MINUTES..SettingsRepository.MAX_TARGET_SLEEP_MINUTES)
    var debtMinutes = 0L
    return actualSleepMinutes.map { actualMinutes ->
        if (actualMinutes != null) {
            debtMinutes = (debtMinutes + targetSleepMinutes - actualMinutes)
                .coerceAtLeast(0L)
        }
        debtMinutes.toInt()
    }
}

internal fun buildDailyDebtData(
    firstDay: LocalDate,
    actualSleepMinutes: List<Long?>,
    debtProgress: List<Int>,
    today: LocalDate,
): List<DailyDebtData> {
    require(
        debtProgress.size == SleepRepository.HISTORY_DAYS &&
            actualSleepMinutes.size == SleepRepository.HISTORY_DAYS
    ) {
        "14日分の睡眠データが必要です。"
    }
    return (SleepRepository.CHART_START_INDEX until SleepRepository.HISTORY_DAYS).map { index ->
        val date = firstDay.plusDays(index.toLong())
        DailyDebtData(
            date = if (date == today) "今日" else date.format(SleepRepository.DATE_LABEL_FORMAT),
            debtMinutes = debtProgress[index],
            actualSleepMinutes = actualSleepMinutes[index],
        )
    }
}

internal fun sessionDurationOnWakeDate(
    startTime: Instant,
    endTime: Instant,
    zone: ZoneId,
    now: Instant,
): Pair<LocalDate, Long>? {
    val boundedEnd = minOf(endTime, now)
    if (startTime >= boundedEnd) return null
    return boundedEnd.atZone(zone).toLocalDate() to
        Duration.between(startTime, boundedEnd).toMinutes()
}

class SleepRepository(context: Context) {
    private val client = HealthConnectClient.getOrCreate(
        context,
        providerPackageName(context),
    )

    suspend fun hasSleepPermission(): Boolean =
        SLEEP_PERMISSIONS.all(client.permissionController.getGrantedPermissions()::contains)

    suspend fun readLast14DaysBalance(now: Instant = Instant.now()): SleepBalance =
        readLast14DaysSummary(SettingsRepository.DEFAULT_TARGET_SLEEP_MINUTES, now).balance

    suspend fun readLast14DaysBalance(
        targetSleepMinutes: Int,
        now: Instant = Instant.now(),
    ): SleepBalance = readLast14DaysSummary(targetSleepMinutes, now).balance

    suspend fun readLast14DaysSummary(
        targetSleepMinutes: Int = SettingsRepository.DEFAULT_TARGET_SLEEP_MINUTES,
        now: Instant = Instant.now(),
    ): SleepSummary {
        require(targetSleepMinutes in SettingsRepository.MIN_TARGET_SLEEP_MINUTES..SettingsRepository.MAX_TARGET_SLEEP_MINUTES)
        val zone = ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        val firstDay = today.minusDays((HISTORY_DAYS - 1).toLong())
        val rangeStart = firstDay.minusDays(1).atStartOfDay(zone).toInstant()
        val rangeEnd = now

        val sessions = buildList {
            var pageToken: String? = null
            do {
                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = SleepSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(rangeStart, rangeEnd),
                        pageSize = RECORDS_PAGE_SIZE,
                        pageToken = pageToken,
                    ),
                )
                addAll(response.records)
                pageToken = response.pageToken
            } while (pageToken != null)
        }

        val minutesByWakeDate = mutableMapOf<LocalDate, Long>()
        val hasSleepRecordByDay = mutableSetOf<LocalDate>()
        sessions.forEach { session ->
            sessionDurationOnWakeDate(
                startTime = session.startTime,
                endTime = session.endTime,
                zone = zone,
                now = rangeEnd,
            )?.let { (wakeDate, minutes) ->
                if (wakeDate >= firstDay && wakeDate <= today) {
                    minutesByWakeDate[wakeDate] =
                        (minutesByWakeDate[wakeDate] ?: 0L) + minutes
                    hasSleepRecordByDay.add(wakeDate)
                }
            }
        }

        val actualSleepMinutes = (0 until HISTORY_DAYS).map { offset ->
            val date = firstDay.plusDays(offset.toLong())
            minutesByWakeDate[date]?.takeIf { date in hasSleepRecordByDay }
        }
        val debtProgress = calculateDebtProgress(actualSleepMinutes, targetSleepMinutes)
        val dailyDebt = buildDailyDebtData(firstDay, actualSleepMinutes, debtProgress, today)

        val debtMinutes = debtProgress.lastOrNull()?.toLong() ?: 0L
        val rawMinutes = -debtMinutes.toInt()
        val roundedMinutes = -round(debtMinutes / ROUNDING_MINUTES.toFloat())
            .toInt() * ROUNDING_MINUTES

        return SleepSummary(
            balance = SleepBalance(rawMinutes = rawMinutes, roundedMinutes = roundedMinutes),
            dailyDebt = dailyDebt,
        )
    }

    companion object {
        private const val LEGACY_PROVIDER_PACKAGE_NAME = "com.google.android.apps.healthdata"
        private const val SYSTEM_PROVIDER_PACKAGE_NAME = "com.google.android.healthconnect"
        internal const val HISTORY_DAYS = 14
        internal const val CHART_DAYS = 7
        internal const val CHART_START_INDEX = HISTORY_DAYS - CHART_DAYS
        private const val ROUNDING_MINUTES = 10
        private const val RECORDS_PAGE_SIZE = 500
        internal val DATE_LABEL_FORMAT = java.time.format.DateTimeFormatter.ofPattern("M/d")

        fun providerPackageName(context: Context): String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                SYSTEM_PROVIDER_PACKAGE_NAME
            } else {
                LEGACY_PROVIDER_PACKAGE_NAME
            }

        val SLEEP_PERMISSIONS = setOf(
            androidx.health.connect.client.permission.HealthPermission.getReadPermission(
                SleepSessionRecord::class,
            ),
        )
    }
}
