package com.xuan.fitai.ai
import com.xuan.fitai.data.model.ModelLoadState
import org.junit.Assert.*
import org.junit.Test
class ScannerCapabilityTest {
    @Test fun visionOnlyModelIsAllowedButTextOnlyIsNot() {
        assertTrue(canScanFood(ModelLoadState.NotFound, ModelLoadState.Loaded, true))
        assertFalse(canScanFood(ModelLoadState.NotFound, ModelLoadState.Loaded, false))
        assertTrue(canScanFood(ModelLoadState.Loaded, ModelLoadState.NotFound, false))
        assertFalse(canScanFood(ModelLoadState.Loading, ModelLoadState.Loading, false))
    }
}
