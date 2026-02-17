package ai.synheart.wear.flux

import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FluxProcessorTest {

    @Before
    fun setup() {
        // Reset FFI state before each test
        FluxFfi.resetForTesting()
    }

    @After
    fun teardown() {
        // Clean up after tests
        FluxFfi.resetForTesting()
    }

    @Test
    fun `FluxProcessor gracefully handles unavailable native library`() {
        // In unit tests, native library won't be available
        val processor = FluxProcessor.create()

        // Should not crash, just return unavailable
        assertFalse(processor.isAvailable)

        // Methods should return null instead of throwing
        assertNull(processor.saveBaselines())
        assertNull(processor.processWhoop("{}", "UTC", "device-123"))
        assertNull(processor.processGarmin("{}", "UTC", "device-123"))
        assertNull(processor.currentBaselines)

        // loadBaselines should return false
        assertFalse(processor.loadBaselines("{}"))

        // close() should not throw
        processor.close()
    }

    @Test
    fun `FluxProcessor create with custom baseline window`() {
        val processor = FluxProcessor.create(baselineWindowDays = 7)

        // Should create successfully even without native library
        assertNotNull(processor)

        processor.close()
    }

    @Test
    fun `isFluxAvailable returns false when native library not loaded`() {
        // In unit tests, native library won't be available
        assertFalse(isFluxAvailable)
    }

    @Test
    fun `fluxLoadError contains meaningful message when library not available`() {
        // Force a load attempt
        val _ = isFluxAvailable

        // Should have an error message
        val error = fluxLoadError
        assertNotNull(error)
        assertTrue(error.contains("Failed to load") || error.contains("UnsatisfiedLinkError"))
    }

    @Test
    fun `whoopToHsiDaily returns null when native library not available`() {
        val result = whoopToHsiDaily("{}", "America/New_York", "device-123")
        assertNull(result)
    }

    @Test
    fun `garminToHsiDaily returns null when native library not available`() {
        val result = garminToHsiDaily("{}", "America/Los_Angeles", "device-456")
        assertNull(result)
    }

    @Test
    fun `FluxProcessor methods return null after close`() {
        val processor = FluxProcessor.create()

        // Close the processor
        processor.close()

        // Methods should return null
        assertFalse(processor.isAvailable)
        assertNull(processor.saveBaselines())
        assertNull(processor.processWhoop("{}", "UTC", "device-123"))
    }

    @Test
    fun `FluxProcessor can be closed multiple times safely`() {
        val processor = FluxProcessor.create()

        // Should not throw on multiple closes
        processor.close()
        processor.close()
        processor.close()
    }

    @Test
    fun `FluxFfi static methods are safe when library unavailable`() {
        // These should not crash
        val available = FluxFfi.isAvailable
        val error = FluxFfi.getLoadError()
        val lastError = FluxFfi.getLastError()

        assertFalse(available)
        // Error message should be set after load attempt
        assertNotNull(error)
    }
}
