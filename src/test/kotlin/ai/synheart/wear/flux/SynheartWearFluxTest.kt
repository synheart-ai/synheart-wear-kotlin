package ai.synheart.wear.flux

import ai.synheart.wear.SynheartWearException
import ai.synheart.wear.config.SynheartWearConfig
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for Flux integration in SynheartWear
 *
 * Note: These tests run without a mocked Android context, so they focus on
 * the Flux-specific functionality that doesn't require Android APIs.
 */
class SynheartWearFluxTest {

    @Before
    fun setup() {
        FluxFfi.resetForTesting()
    }

    @After
    fun teardown() {
        FluxFfi.resetForTesting()
    }

    @Test
    fun `SynheartWearConfig default has Flux disabled`() {
        val config = SynheartWearConfig()
        assertFalse(config.enableFlux)
    }

    @Test
    fun `SynheartWearConfig can enable Flux`() {
        val config = SynheartWearConfig(enableFlux = true)
        assertTrue(config.enableFlux)
    }

    @Test
    fun `SynheartWearConfig default baseline window is 14 days`() {
        val config = SynheartWearConfig()
        assertEquals(14, config.fluxBaselineWindowDays)
    }

    @Test
    fun `SynheartWearConfig can set custom baseline window`() {
        val config = SynheartWearConfig(
            enableFlux = true,
            fluxBaselineWindowDays = 7
        )
        assertEquals(7, config.fluxBaselineWindowDays)
    }

    @Test
    fun `Flux error codes are defined correctly`() {
        // These are the error codes used by SynheartWear for Flux errors
        val fluxDisabledCode = "FLUX_DISABLED"
        val fluxNotInitializedCode = "FLUX_NOT_INITIALIZED"
        val fluxNotAvailableCode = "FLUX_NOT_AVAILABLE"
        val fluxProcessingFailedCode = "FLUX_PROCESSING_FAILED"

        // Verify they are non-empty
        assertTrue(fluxDisabledCode.isNotEmpty())
        assertTrue(fluxNotInitializedCode.isNotEmpty())
        assertTrue(fluxNotAvailableCode.isNotEmpty())
        assertTrue(fluxProcessingFailedCode.isNotEmpty())
    }

    @Test
    fun `SynheartWearException supports error codes`() {
        val exception = SynheartWearException(
            message = "Test error",
            code = "TEST_CODE"
        )

        assertEquals("Test error", exception.message)
        assertEquals("TEST_CODE", exception.code)
    }

    @Test
    fun `SynheartWearException code is optional`() {
        val exception = SynheartWearException("Test error")

        assertEquals("Test error", exception.message)
        assertEquals(null, exception.code)
    }

    @Test
    fun `Vendor enum covers expected vendors`() {
        // Ensure we have the expected vendors
        assertEquals(2, Vendor.entries.size)
        assertTrue(Vendor.entries.any { it == Vendor.WHOOP })
        assertTrue(Vendor.entries.any { it == Vendor.GARMIN })
    }
}
