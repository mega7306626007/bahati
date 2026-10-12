package com.pesaflow.app.parsers

import com.pesaflow.app.data.finance.SweepVerdict
import com.pesaflow.app.data.finance.dailyTargetFrom
import com.pesaflow.app.data.finance.sweepVerdict
import org.junit.Assert.*
import org.junit.Test

class SavingsSweepTest {

    @Test
    fun `unspent sixty of one-twenty offers a sweep`() {
        val v = sweepVerdict(spentToday = 60.0, dailyTarget = 120.0)
        assertTrue(v is SweepVerdict.Sweep)
        assertEquals(60.0, (v as SweepVerdict.Sweep).surplus, 0.01)
    }

    @Test
    fun `overspend flags the overage`() {
        val v = sweepVerdict(spentToday = 150.0, dailyTarget = 120.0)
        assertTrue(v is SweepVerdict.Over)
        assertEquals(30.0, (v as SweepVerdict.Over).overage, 0.01)
    }

    @Test
    fun `dust either way stays silent`() {
        assertTrue(sweepVerdict(115.0, 120.0) is SweepVerdict.Silent)
        assertTrue(sweepVerdict(124.0, 120.0) is SweepVerdict.Silent)
    }

    @Test
    fun `no target means silence never fabrication`() {
        assertTrue(sweepVerdict(60.0, null) is SweepVerdict.Silent)
        assertTrue(sweepVerdict(60.0, 0.0) is SweepVerdict.Silent)
        assertTrue(sweepVerdict(60.0, -50.0) is SweepVerdict.Silent)
    }

    @Test
    fun `weekly allowance wins over monthly envelope`() {
        assertEquals(20.0, dailyTargetFrom(weeklyAllowance = 140.0, monthlyAll = 900.0)!!, 0.01)
        assertEquals(30.0, dailyTargetFrom(weeklyAllowance = null, monthlyAll = 900.0)!!, 0.01)
        assertNull(dailyTargetFrom(null, null))
        assertEquals(30.0, dailyTargetFrom(weeklyAllowance = -5.0, monthlyAll = 900.0)!!, 0.01)
    }
}
