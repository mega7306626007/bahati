package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.RhythmKind
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.ui.budgets.Persona
import com.pesaflow.app.ui.dashboard.RhythmEngine
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar


// Rhythm back-port: same behavior as PesaFlow main, adapted to this tree's
// Persona input (RENT_COMMUTE/PARENTS_FAR ride the fare window).
class RhythmEnginePortTest {

    private fun tsMs(y: Int, m: Int, d: Int, h: Int = 12): Long =
        Calendar.getInstance().apply {
            set(y, m, d, h, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private val now = tsMs(2026, Calendar.OCTOBER, 15, 12)
    private val window = 45L * 24 * 60 * 60 * 1000

    private fun expense(amount: Double, category: String, y: Int, m: Int, d: Int, h: Int = 12, merchant: String = category): Transaction =
        Transaction(amount = amount, type = TransactionType.EXPENSE, category = category, dateTimestamp = tsMs(y, m, d, h), merchant = merchant)

    private fun baseRows(): MutableList<Transaction> {
        val rows = mutableListOf<Transaction>()
        listOf(
            Triple(2026, Calendar.OCTOBER, 1), Triple(2026, Calendar.OCTOBER, 2),
            Triple(2026, Calendar.OCTOBER, 5), Triple(2026, Calendar.OCTOBER, 6),
            Triple(2026, Calendar.OCTOBER, 7), Triple(2026, Calendar.OCTOBER, 8),
            Triple(2026, Calendar.OCTOBER, 9), Triple(2026, Calendar.OCTOBER, 12),
            Triple(2026, Calendar.OCTOBER, 13), Triple(2026, Calendar.OCTOBER, 14)
        ).forEach { (y, m, d) -> rows.add(expense(50.0, "Transport", y, m, d, 7, "Matatu")) }
        rows.add(expense(5000.0, "Rent", 2026, Calendar.SEPTEMBER, 5))
        rows.add(expense(5000.0, "Rent", 2026, Calendar.OCTOBER, 5))
        rows.add(Transaction(amount = 10000.0, type = TransactionType.INCOME, category = "Salary", dateTimestamp = tsMs(2026, Calendar.SEPTEMBER, 1, 9), merchant = "HELB"))
        rows.add(Transaction(amount = 10000.0, type = TransactionType.INCOME, category = "Salary", dateTimestamp = tsMs(2026, Calendar.OCTOBER, 1, 9), merchant = "HELB"))
        return rows
    }

    @Test
    fun `fare window reads daily fifty bob mornings`() {
        val out = RhythmEngine().propose(baseRows(), Persona.RENT_COMMUTE, window, now, false)
        val fare = out.firstOrNull { it.category == "Transport" }
        assertNotNull(fare)
        assertEquals(RhythmKind.FARE_WINDOW, fare!!.kind)
        assertEquals(50.0, fare.amount, 0.01)
        assertEquals(-1, fare.dayOfMonth)
        assertEquals(10, fare.evidence)
        assertTrue(fare.confidence in 0.5f..0.95f)
        assertTrue(fare.hint.contains("50"))
        assertFalse(fare.skewed)
    }

    @Test
    fun `rent lands on the real fifth`() {
        val out = RhythmEngine().propose(baseRows(), Persona.RENT_COMMUTE, window, now, false)
        val rent = out.firstOrNull { it.kind == RhythmKind.RENT_DAY }
        assertNotNull(rent)
        assertEquals(5, rent!!.dayOfMonth)
        assertEquals(5000.0, rent.amount, 0.01)
    }

    @Test
    fun `payday names HELB on the first`() {
        val out = RhythmEngine().propose(baseRows(), Persona.RENT_COMMUTE, window, now, false)
        val pay = out.firstOrNull { it.kind == RhythmKind.PAYDAY }
        assertNotNull(pay)
        assertEquals(1, pay!!.dayOfMonth)
        assertEquals(10000.0, pay.amount, 0.01)
        assertTrue(pay.hint.contains("HELB"))
    }

    @Test
    fun `thin data proposes nothing`() {
        val out = RhythmEngine().propose(
            listOf(expense(200.0, "Food", 2026, Calendar.OCTOBER, 14)),
            Persona.HOSTEL_COOK, window, now, false
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun `predictPaydays spots monthly HELB`() {
        val out = com.pesaflow.app.data.analytics.predictPaydays(baseRows(), now)
        assertTrue(out.isNotEmpty())
        assertEquals("HELB", out.first().first)
        assertEquals(10000.0, out.first().second, 0.01)
        assertTrue(out.first().third > now)
    }
}
