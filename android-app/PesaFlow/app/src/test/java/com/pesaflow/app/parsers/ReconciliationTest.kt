package com.pesaflow.app.parsers

import com.pesaflow.app.data.money.BudgetPace
import com.pesaflow.app.data.money.ReconciliationStatus
import com.pesaflow.app.data.money.evaluateCategoryPace
import com.pesaflow.app.data.money.evaluateDrift
import org.junit.Assert.*
import org.junit.Test


class ReconciliationTest {

    @Test
    fun `drift tiers match tolerance zones`() {
        assertTrue(evaluateDrift(482.0, 480.0) is ReconciliationStatus.InSync)
        assertTrue(evaluateDrift(482.0, 432.0) is ReconciliationStatus.InSync)
        val minor = evaluateDrift(482.0, 300.0)
        assertTrue(minor is ReconciliationStatus.MinorDrift)
        assertEquals(182.0, (minor as ReconciliationStatus.MinorDrift).amount, 0.001)
        val major = evaluateDrift(482.0, 2438.0)
        assertTrue(major is ReconciliationStatus.MajorDrift)
        // Sign preserved: negative when the ledger overshoots the wallet.
        val over = evaluateDrift(482.0, 600.0)
        assertTrue(over is ReconciliationStatus.MinorDrift)
        assertEquals(-118.0, (over as ReconciliationStatus.MinorDrift).amount, 0.001)
    }

    @Test
    fun `category pace tiers follow elapsed time, not static caps`() {
        // Day 2 of a KSh 1200 envelope expects KSh 80: 100 is on pace,
        // 50 is safe, 300 (3.75x) is honestly ahead — the tier says so
        // instead of screaming "25% gone".
        assertEquals(BudgetPace.ON_TRACK, evaluateCategoryPace(100.0, 1200.0, 2, 30))
        assertEquals(BudgetPace.SAFE, evaluateCategoryPace(50.0, 1200.0, 2, 30))
        assertEquals(BudgetPace.AT_RISK, evaluateCategoryPace(300.0, 1200.0, 2, 30))
        assertEquals(BudgetPace.AT_RISK, evaluateCategoryPace(600.0, 1200.0, 5, 30))
        assertEquals(BudgetPace.EXCEEDED, evaluateCategoryPace(1300.0, 1200.0, 20, 30))
        // Degenerate budgets never alarm.
        assertEquals(BudgetPace.SAFE, evaluateCategoryPace(500.0, 0.0, 10, 30))
    }
}
