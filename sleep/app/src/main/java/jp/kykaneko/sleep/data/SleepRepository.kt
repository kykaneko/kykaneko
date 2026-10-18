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

class SleepRepository(context: Context) {
    private val client = HealthConnectClient.getOrCreate(
        context,
        providerPackageName(context),
    )

    suspend fun hasSleepPermission(): Boolean =
        SLEEP_PERMISSIONS.all(client.permissionController.getGrantedPermissions()::contains)

    suspend fun readLast14DaysBalance(now: Instant = Instant.now()): SleepBalance {
        val zone = ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        val firstDay = today.minusDays(13)
        val rangeStart = firstDay.atStartOfDay(zone).toInstant()
        val rangeEnd = now

        val sessions = client.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(rangeStart, rangeEnd),
            ),
        ).records

        val minutesByDay = mutableMapOf<LocalDate, Long>()
        sessions.forEach { session ->
            var segmentStart = maxOf(session.startTime, rangeStart)
            val segmentEnd = minOf(session.endTime, rangeEnd)
            while (segmentStart < segmentEnd) {
                val date = segmentStart.atZone(zone).toLocalDate()
                val nextDay = date.plusDays(1).atStartOfDay(zone).toInstant()
                val segmentEndForDay = minOf(segmentEnd, nextDay)
                val minutes = Duration.between(segmentStart, segmentEndForDay).toMinutes()
                minutesByDay[date] = (minutesByDay[date] ?: 0L) + minutes
                segmentStart = segmentEndForDay
            }
        }

        var debtMinutes = 0L
        (0L..13L).forEach { offset ->
            val actualMinutes = minutesByDay[firstDay.plusDays(offset)] ?: 0L
            val dailyShortfall = TARGET_MINUTES_PER_DAY - actualMinutes
            if (dailyShortfall > SHORTFALL_THRESHOLD_MINUTES) {
                debtMinutes += dailyShortfall - SHORTFALL_THRESHOLD_MINUTES
            } else if (actualMinutes > TARGET_MINUTES_PER_DAY) {
                debtMinutes = (debtMinutes - (actualMinutes - TARGET_MINUTES_PER_DAY))
                    .coerceAtLeast(0L)
            }
        }

        val rawMinutes = -debtMinutes.toInt()
        val roundedMinutes = -round(debtMinutes / ROUNDING_MINUTES.toFloat())
            .toInt() * ROUNDING_MINUTES

        return SleepBalance(rawMinutes = rawMinutes, roundedMinutes = roundedMinutes)
    }

    companion object {
        private const val LEGACY_PROVIDER_PACKAGE_NAME = "com.google.android.apps.healthdata"
        private const val SYSTEM_PROVIDER_PACKAGE_NAME = "com.google.android.healthconnect"
        const val TARGET_MINUTES_PER_DAY = 450L
        const val SHORTFALL_THRESHOLD_MINUTES = 0L
        private const val ROUNDING_MINUTES = 10

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
