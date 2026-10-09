package com.pesaflow.app.parsers

import com.pesaflow.app.data.finance.DataQuality
import com.pesaflow.app.data.finance.Money
import com.pesaflow.app.data.finance.explainFlexible
import com.pesaflow.app.data.finance.explainForecast
import com.pesaflow.app.data.finance.explainNetWorth
import com.pesaflow.app.data.finance.explainSafeToday
import org.junit.Assert.*
import org.junit.Test


// Explanation engine: every builder carries headline, why, contributors,
// basis and quality — the three disclosure levels (§38).
class ExplanationEngineTest {

    @Test
    fun `safe today explains its components`() {
        val e = explainSafeToday(
            headline = Money.of(1850.0), liquid = Money.of(12450.0),
            committed = Money.of(8200.0), essentialDaily = 2500.0,
            buffer = Money.of(850.0), basis = "40 rows", quality = DataQuality.FULL
        )
        assertEquals("Safe to spend today", e.label)
        assertEquals(Money.of(1850.0), e.headline)
        assertEquals("today", e.horizon)
        assertTrue(e.why.contains("buffer"))
        assertEquals(4, e.contributors.size)
        assertEquals(DataQuality.FULL, e.quality)
    }

    @Test
    fun `flexible names every claim on cash`() {
        val e = explainFlexible(
            headline = Money.of(4250.0), liquid = Money.of(12450.0),
            bills = Money.of(5000.0), debts = Money.of(2000.0),
            reserved = Money.of(1200.0), obligationCount = 3,
            reservationCount = 1, quality = DataQuality.PARTIAL
        )
        assertTrue(e.contributors.any { it.contains("5,000") || it.contains("5k") })
        assertTrue(e.basis.contains("3 open obligations"))
    }

    @Test
    fun `forecast shows the range`() {
        val e = explainForecast(
            headline = Money.of(8200.0), paceTypical = 600.0,
            cautious = Money.of(3000.0), favourable = Money.of(12000.0),
            quality = DataQuality.FULL
        )
        assertEquals("month end", e.horizon)
        assertTrue(e.contributors.any { it.contains("600") })
    }

    @Test
    fun `net worth splits assets and owed`() {
        val e = explainNetWorth(
            headline = Money.of(80000.0), assets = Money.of(100000.0),
            liabilities = Money.of(20000.0), quality = DataQuality.FULL
        )
        assertTrue(e.contributors.any { it.contains("100k") })
        assertTrue(e.contributors.any { it.contains("20k") })
    }
}
