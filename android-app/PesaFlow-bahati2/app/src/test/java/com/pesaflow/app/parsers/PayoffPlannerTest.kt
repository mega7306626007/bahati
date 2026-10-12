package com.pesaflow.app.parsers

import com.pesaflow.app.data.analytics.billFrequencyFor
import com.pesaflow.app.data.finance.planPayoff
import com.pesaflow.app.data.models.Debt
import org.junit.Assert.*
import org.junit.Test


/** Debt payoff order, spillover, and bill-frequency mapping. */
class PayoffPlannerTest {

    private fun debt(person: String, amount: Double, due: Long, status: String = "OWING"): Debt =
        Debt(person = person, amount = amount, dateBorrowed = 0L, dueDate = due, status = status)

    @Test
    fun `earliest due clears first with spillover`() {
        val dayMs = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        val debts = listOf(
            debt("Babu", 500.0, now + 20 * dayMs),
            debt("Achi", 300.0, now + 5 * dayMs)
        )
        val plan = planPayoff(debts, 400.0)
        assertEquals(2, plan.steps.size)
        assertEquals("Achi", plan.steps[0].person)
        assertEquals(1, plan.steps[0].clearMonth)
        assertEquals("Babu", plan.steps[1].person)
        assertEquals(2, plan.steps[1].clearMonth)
        assertEquals(2, plan.totalMonths)
        assertEquals(800.0, plan.totalOwed, 0.001)
    }

    @Test
    fun `single debt spans exact months`() {
        val debts = listOf(debt("Babu", 1000.0, 0L))
        val plan = planPayoff(debts, 400.0)
        assertEquals(1, plan.steps.size)
        assertEquals(3, plan.steps[0].clearMonth)
        assertEquals(3, plan.totalMonths)
    }

    @Test
    fun `exact fit clears in one month`() {
        val plan = planPayoff(listOf(debt("Babu", 400.0, 0L)), 400.0)
        assertEquals(1, plan.totalMonths)
    }

    @Test
    fun `paid and zero debts are excluded`() {
        val debts = listOf(
            debt("Settled", 900.0, 0L, "PAID"),
            debt("Nothing", 0.0, 0L),
            debt("Live", 200.0, 0L)
        )
        val plan = planPayoff(debts, 500.0)
        assertEquals(1, plan.steps.size)
        assertEquals("Live", plan.steps[0].person)
        assertEquals(200.0, plan.totalOwed, 0.001)
    }

    @Test
    fun `zero payment yields an empty plan`() {
        val plan = planPayoff(listOf(debt("Babu", 400.0, 0L)), 0.0)
        assertTrue(plan.steps.isEmpty())
        assertEquals(0, plan.totalMonths)
    }

    @Test
    fun `empty debts yield an empty plan`() {
        val plan = planPayoff(emptyList(), 500.0)
        assertTrue(plan.steps.isEmpty())
        assertEquals(0, plan.totalMonths)
        assertEquals(0.0, plan.totalOwed, 0.001)
    }

    @Test
    fun `bill frequency maps monthly weekly and once`() {
        assertEquals("MONTHLY", billFrequencyFor(30))
        assertEquals("MONTHLY", billFrequencyFor(25))
        assertEquals("MONTHLY", billFrequencyFor(35))
        assertEquals("WEEKLY", billFrequencyFor(7))
        assertEquals("ONE_TIME", billFrequencyFor(14))
        assertEquals("ONE_TIME", billFrequencyFor(90))
    }
}
