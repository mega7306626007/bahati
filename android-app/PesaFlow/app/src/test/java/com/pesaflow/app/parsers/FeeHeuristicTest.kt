package com.pesaflow.app.parsers

import com.pesaflow.app.data.parsers.extractSmallFeeAmount
import com.pesaflow.app.data.parsers.isLikelyFeeAmount
import com.pesaflow.app.data.parsers.parseFeeWithFallback
import org.junit.Assert.*
import org.junit.Test

class FeeHeuristicTest {
    @Test
    fun `single digit amounts are likely fees`() {
        assertTrue(isLikelyFeeAmount(5.0))
        assertTrue(isLikelyFeeAmount(7.5))
        assertTrue(isLikelyFeeAmount(0.5))
        assertTrue(isLikelyFeeAmount(9.99))
        assertTrue(isLikelyFeeAmount(0.01))
    }

    @Test
    fun `real movements are not fees`() {
        assertFalse(isLikelyFeeAmount(10.0))
        assertFalse(isLikelyFeeAmount(500.0))
        assertFalse(isLikelyFeeAmount(0.0))
        assertFalse(isLikelyFeeAmount(-5.0))
    }

    @Test
    fun `strict tail still wins`() {
        assertEquals(22.0, parseFeeWithFallback("Transaction cost, KSh22.00 charged.") ?: -1.0, 0.001)
    }

    @Test
    fun `odd tail with small amount falls back`() {
        assertEquals(5.0, extractSmallFeeAmount("Service cost KSh5.00 deducted today.") ?: -1.0, 0.001)
        assertEquals(0.5, extractSmallFeeAmount("Charge of KSh0.50 applied.") ?: -1.0, 0.001)
    }

    @Test
    fun `big amount with cost word is not a fee`() {
        assertNull(extractSmallFeeAmount("Cost of goods KSh500.00 paid."))
    }

    @Test
    fun `no cost word means no fallback`() {
        assertNull(extractSmallFeeAmount("KSh5.00 sent to Nancy."))
    }
}
