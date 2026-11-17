package ai.synheart.wear.backfill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class OvernightSummarizerTest {

    private val zone = ZoneId.of("UTC")
    private fun at(s: String) = Instant.parse(s)

    private val night = SleepWindow(at("2026-03-01T22:00:00Z"), at("2026-03-02T06:00:00Z"))
    private val wakeDay = LocalDate.of(2026, 3, 2)

    @Test
    fun restingHrComesFromTheRecordNotTheSleepSamples() {
        val out = summarizeOvernight(
            windows = listOf(night),
            heartRate = listOf(at("2026-03-02T01:00:00Z") to 50.0, at("2026-03-02T02:00:00Z") to 54.0),
            hrvRmssd = listOf(at("2026-03-02T01:30:00Z") to 40.0),
            restingHeartRate = listOf(at("2026-03-02T07:00:00Z") to 61.0),
            zone = zone,
        )
        val s = out.getValue(wakeDay)
        assertEquals(52.0, s.overnightHrBpm!!, 1e-9)
        assertEquals(61.0, s.restingHrBpm!!, 1e-9)
        assertEquals(40.0, s.hrvRmssdMs!!, 1e-9)
    }

    @Test
    fun noRecordMeansNoRestingHr() {
        val out = summarizeOvernight(
            windows = listOf(night),
            heartRate = listOf(at("2026-03-02T01:00:00Z") to 50.0),
            hrvRmssd = emptyList(),
            restingHeartRate = emptyList(),
            zone = zone,
        )
        val s = out.getValue(wakeDay)
        assertEquals(50.0, s.overnightHrBpm!!, 1e-9)
        assertNull(s.restingHrBpm)
        assertNull(s.hrvRmssdMs)
    }

    @Test
    fun samplesOutsideTheSleepWindowAreIgnored() {
        val out = summarizeOvernight(
            windows = listOf(night),
            heartRate = listOf(
                at("2026-03-01T21:59:59Z") to 90.0, // before sleep
                at("2026-03-02T03:00:00Z") to 50.0,
                at("2026-03-02T06:00:00Z") to 95.0, // the end is exclusive
            ),
            hrvRmssd = emptyList(),
            restingHeartRate = emptyList(),
            zone = zone,
        )
        assertEquals(50.0, out.getValue(wakeDay).overnightHrBpm!!, 1e-9)
    }

    @Test
    fun severalRecordsInADayGiveTheMedian() {
        val out = summarizeOvernight(
            windows = emptyList(),
            heartRate = emptyList(),
            hrvRmssd = emptyList(),
            restingHeartRate = listOf(
                at("2026-03-02T05:00:00Z") to 58.0,
                at("2026-03-02T09:00:00Z") to 90.0,
                at("2026-03-02T13:00:00Z") to 60.0,
                at("2026-03-02T17:00:00Z") to 0.0, // not a reading
            ),
            zone = zone,
        )
        val s = out.getValue(wakeDay)
        assertEquals(60.0, s.restingHrBpm!!, 1e-9)
        assertNull(s.overnightHrBpm)
    }

    @Test
    fun recordsAreFiledUnderTheLocalDay() {
        val out = summarizeOvernight(
            windows = emptyList(),
            heartRate = emptyList(),
            hrvRmssd = emptyList(),
            restingHeartRate = listOf(at("2026-03-02T22:30:00Z") to 57.0),
            zone = ZoneId.of("Africa/Addis_Ababa"), // UTC+3
        )
        assertEquals(setOf(LocalDate.of(2026, 3, 3)), out.keys)
    }

    @Test
    fun nothingToReadGivesAnEmptyMap() {
        assertTrue(summarizeOvernight(emptyList(), emptyList(), emptyList(), emptyList(), zone).isEmpty())
    }
}
