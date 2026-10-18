package jp.kykaneko.sleep.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SleepDebtCalculationTest {
    @Test
    fun sleepAboveTargetRepaysDebtAndSleepBelowTargetAddsDebt() {
        val progress = calculateDebtProgress(
            actualSleepMinutes = listOf(400L, 300L),
            targetSleepMinutes = 450,
        )

        assertEquals(listOf(50, 200), progress)
    }

    @Test
    fun extraSleepRepaysDebtButNeverCreatesSavings() {
        val progress = calculateDebtProgress(
            actualSleepMinutes = listOf(400L, 600L, 400L),
            targetSleepMinutes = 450,
        )

        assertEquals(listOf(50, 0, 50), progress)
    }

    @Test
    fun missingSleepRecordsDoNotInventSleepOrDebt() {
        val progress = calculateDebtProgress(
            actualSleepMinutes = listOf(400L, null, 500L),
            targetSleepMinutes = 450,
        )

        assertEquals(listOf(50, 50, 0), progress)
    }

    @Test
    fun configuredDailySleepTargetIsUsedForDebtAndRepayment() {
        val progress = calculateDebtProgress(
            actualSleepMinutes = listOf(450L, 510L),
            targetSleepMinutes = 480,
        )

        assertEquals(listOf(30, 0), progress)
    }

    @Test
    fun configuredTargetIsNotAddedAsDebtAndExtraSleepRepaysIt() {
        val progress = calculateDebtProgress(
            actualSleepMinutes = listOf(380L, 500L, 400L),
            targetSleepMinutes = 450,
        )

        assertEquals(listOf(70, 20, 70), progress)
    }

    @Test
    fun chartDataContainsTheLastSevenDatesAndTheirCumulativeDebt() {
        val firstDay = LocalDate.of(2026, 10, 5)
        val today = firstDay.plusDays(13)
        val progress = calculateDebtProgress(
            actualSleepMinutes = listOf(
                450L, 450L, 450L, 450L, 450L, 450L, 450L,
                400L, 380L, 500L, 400L, 450L, 420L, 450L,
            ),
            targetSleepMinutes = 450,
        )

        val actualSleepMinutes = listOf(
            450L, 450L, 450L, 450L, 450L, 450L, 450L,
            400L, 380L, 500L, 400L, 450L, 420L, 450L,
        )
        val chartData = buildDailyDebtData(firstDay, actualSleepMinutes, progress, today)

        assertEquals(7, chartData.size)
        assertEquals(
            listOf("10/12", "10/13", "10/14", "10/15", "10/16", "10/17", "今日"),
            chartData.map { it.date },
        )
        assertEquals(listOf(50, 120, 70, 120, 120, 150, 150), chartData.map { it.debtMinutes })
        assertEquals(
            listOf(400L, 380L, 500L, 400L, 450L, 420L, 450L),
            chartData.map { it.actualSleepMinutes },
        )
    }

    @Test
    fun chartIncludesDebtAccumulatedDuringTheFirstWeekOfTheFourteenDayHistory() {
        val firstDay = LocalDate.of(2026, 10, 5)
        val actualSleepMinutes = List(SleepRepository.HISTORY_DAYS) { index ->
            if (index < SleepRepository.CHART_START_INDEX) 400L else 450L
        }
        val debtProgress = calculateDebtProgress(
            actualSleepMinutes = actualSleepMinutes,
            targetSleepMinutes = 450,
        )
        val chartData = buildDailyDebtData(
            firstDay = firstDay,
            actualSleepMinutes = actualSleepMinutes,
            debtProgress = debtProgress,
            today = firstDay.plusDays((SleepRepository.HISTORY_DAYS - 1).toLong()),
        )

        assertEquals(7, chartData.size)
        assertEquals(List(7) { 350 }, chartData.map { it.debtMinutes })
    }

    @Test
    fun overnightSleepSessionIsCountedOnItsWakeDateWithoutSplittingAtMidnight() {
        val zone = ZoneId.of("Asia/Tokyo")
        val now = Instant.parse("2026-10-18T04:00:00Z")
        val session = sessionDurationOnWakeDate(
            startTime = Instant.parse("2026-10-11T13:00:00Z"),
            endTime = Instant.parse("2026-10-11T23:07:00Z"),
            zone = zone,
            now = now,
        )

        assertEquals(LocalDate.of(2026, 10, 12), session?.first)
        assertEquals(607L, session?.second)
    }
}
