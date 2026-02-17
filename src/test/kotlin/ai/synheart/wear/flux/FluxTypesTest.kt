package ai.synheart.wear.flux

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FluxTypesTest {

    @Test
    fun `Vendor enum has expected wire values`() {
        assertEquals("whoop", Vendor.WHOOP.value)
        assertEquals("garmin", Vendor.GARMIN.value)
    }

    @Test
    fun `Baselines JSON roundtrip`() {
        val baselines = Baselines(
            hrvBaselineMs = 42.5,
            rhrBaselineBpm = 55,
            sleepBaselineMinutes = 420,
            sleepEfficiencyBaseline = 0.9,
            baselineDays = 14
        )

        val json = baselines.toJson()
        val decoded = Baselines.fromJson(json)

        assertEquals(42.5, decoded.hrvBaselineMs)
        assertEquals(55, decoded.rhrBaselineBpm)
        assertEquals(420, decoded.sleepBaselineMinutes)
        assertEquals(0.9, decoded.sleepEfficiencyBaseline)
        assertEquals(14, decoded.baselineDays)
    }

    @Test
    fun `HsiPayload toJson includes required HSI 1_0 keys`() {
        val payload = HsiPayload(
            hsiVersion = "1.0",
            observedAtUtc = "2026-01-01T00:00:00+00:00",
            computedAtUtc = "2026-01-01T00:00:01+00:00",
            producer = HsiProducer(
                name = "synheart_flux",
                version = "0.1.0",
                instanceId = "test-instance"
            ),
            windowIds = listOf("w_test"),
            windows = mapOf(
                "w_test" to HsiWindow(
                    start = "2026-01-01T00:00:00+00:00",
                    end = "2026-01-01T23:59:59+00:00",
                    label = "test-window"
                )
            ),
            sourceIds = listOf("s_test"),
            sources = mapOf(
                "s_test" to HsiSource(
                    type = HsiSourceType.APP,
                    quality = 0.95,
                    degraded = false
                )
            ),
            axes = HsiAxes(
                behavior = HsiAxesDomain(
                    readings = listOf(
                        HsiAxisReading(
                            axis = "test_metric",
                            score = 0.5,
                            confidence = 0.95,
                            windowId = "w_test",
                            direction = HsiDirection.HIGHER_IS_MORE,
                            evidenceSourceIds = listOf("s_test")
                        )
                    )
                )
            ),
            privacy = HsiPrivacy(
                containsPii = false,
                rawBiosignalsAllowed = false,
                derivedMetricsAllowed = true
            )
        )

        val json = payload.toJson()
        val parsed = Json.parseToJsonElement(json).jsonObject

        // HSI 1.0 required fields
        assertTrue(parsed.containsKey("hsi_version"))
        assertEquals("\"1.0\"", parsed["hsi_version"].toString())
        assertTrue(parsed.containsKey("observed_at_utc"))
        assertTrue(parsed.containsKey("computed_at_utc"))
        assertTrue(parsed.containsKey("producer"))
        assertTrue(parsed.containsKey("window_ids"))
        assertTrue(parsed.containsKey("windows"))
        assertTrue(parsed.containsKey("source_ids"))
        assertTrue(parsed.containsKey("sources"))
        assertTrue(parsed.containsKey("axes"))
        assertTrue(parsed.containsKey("privacy"))
    }

    @Test
    fun `HsiPayload fromJson roundtrips correctly`() {
        val original = HsiPayload(
            hsiVersion = "1.0",
            observedAtUtc = "2026-01-01T00:00:00+00:00",
            computedAtUtc = "2026-01-01T00:00:01+00:00",
            producer = HsiProducer(
                name = "test",
                version = "1.0.0",
                instanceId = "test-id"
            ),
            windowIds = listOf("w_1"),
            windows = mapOf(
                "w_1" to HsiWindow(
                    start = "2026-01-01T00:00:00+00:00",
                    end = "2026-01-01T12:00:00+00:00"
                )
            ),
            sourceIds = listOf("s_1"),
            sources = mapOf(
                "s_1" to HsiSource(
                    type = HsiSourceType.SENSOR,
                    quality = 0.8,
                    degraded = false
                )
            ),
            axes = HsiAxes(),
            privacy = HsiPrivacy()
        )

        val json = original.toJson()
        val decoded = HsiPayload.fromJson(json)

        assertEquals("1.0", decoded.hsiVersion)
        assertEquals(listOf("w_1"), decoded.windowIds)
        assertEquals(listOf("s_1"), decoded.sourceIds)
        assertEquals("test", decoded.producer.name)
    }

    @Test
    fun `HsiDirection serializes to snake_case`() {
        val reading = HsiAxisReading(
            axis = "test",
            score = 0.5,
            confidence = 0.9,
            windowId = "w_1",
            direction = HsiDirection.HIGHER_IS_MORE
        )

        val axes = HsiAxesDomain(readings = listOf(reading))
        val payload = HsiPayload(
            hsiVersion = "1.0",
            observedAtUtc = "2026-01-01T00:00:00+00:00",
            computedAtUtc = "2026-01-01T00:00:01+00:00",
            producer = HsiProducer("test", "1.0", "id"),
            windowIds = listOf("w_1"),
            windows = mapOf("w_1" to HsiWindow("2026-01-01T00:00:00+00:00", "2026-01-01T12:00:00+00:00")),
            sourceIds = emptyList(),
            sources = emptyMap(),
            axes = HsiAxes(behavior = axes),
            privacy = HsiPrivacy()
        )

        val json = payload.toJson()
        assertTrue(json.contains("higher_is_more"))
    }

    @Test
    fun `HsiSourceType serializes to snake_case`() {
        val source = HsiSource(
            type = HsiSourceType.SELF_REPORT,
            quality = 0.7,
            degraded = false
        )

        val payload = HsiPayload(
            hsiVersion = "1.0",
            observedAtUtc = "2026-01-01T00:00:00+00:00",
            computedAtUtc = "2026-01-01T00:00:01+00:00",
            producer = HsiProducer("test", "1.0", "id"),
            windowIds = emptyList(),
            windows = emptyMap(),
            sourceIds = listOf("s_1"),
            sources = mapOf("s_1" to source),
            axes = HsiAxes(),
            privacy = HsiPrivacy()
        )

        val json = payload.toJson()
        assertTrue(json.contains("self_report"))
    }
}
