// SPDX-License-Identifier: Apache-2.0

package ai.synheart.wear.backfill

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One sleep session's span. */
internal data class SleepWindow(val start: Instant, val end: Instant)

/**
 * Builds the per-day overnight summaries from raw samples.
 *
 * Heart rate and HRV are averaged over the samples inside each sleep window
 * and filed under the day the window ended in [zone]. Resting heart rate is
 * the median of that local day's resting-HR records, and is never derived
 * from the heart-rate samples: a day without a record has none. A day with a
 * resting-HR record but no sleep still gets an entry.
 */
internal fun summarizeOvernight(
    windows: List<SleepWindow>,
    heartRate: List<Pair<Instant, Double>>,
    hrvRmssd: List<Pair<Instant, Double>>,
    restingHeartRate: List<Pair<Instant, Double>>,
    zone: ZoneId,
): Map<LocalDate, OvernightPhysiologySummary> {
    class Acc(
        var hrSum: Double = 0.0, var hrN: Int = 0,
        var hrvSum: Double = 0.0, var hrvN: Int = 0,
    )
    val byDay = mutableMapOf<LocalDate, Acc>()

    for (window in windows) {
        val wakeDay = window.end.atZone(zone).toLocalDate()
        val acc = byDay.getOrPut(wakeDay) { Acc() }
        for ((t, bpm) in heartRate) {
            if (!t.isBefore(window.start) && t.isBefore(window.end)) {
                acc.hrSum += bpm; acc.hrN++
            }
        }
        for ((t, rmssd) in hrvRmssd) {
            if (!t.isBefore(window.start) && t.isBefore(window.end)) {
                acc.hrvSum += rmssd; acc.hrvN++
            }
        }
    }

    val restingByDay = restingHeartRate
        .filter { it.second > 0 }
        .groupBy({ it.first.atZone(zone).toLocalDate() }, { it.second })

    return (byDay.keys + restingByDay.keys).associateWith { day ->
        val a = byDay[day]
        OvernightPhysiologySummary(
            hrvRmssdMs = a?.takeIf { it.hrvN > 0 }?.let { it.hrvSum / it.hrvN },
            restingHrBpm = restingByDay[day]?.let(::median),
            overnightHrBpm = a?.takeIf { it.hrN > 0 }?.let { it.hrSum / it.hrN },
        )
    }
}

private fun median(values: List<Double>): Double {
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
}
