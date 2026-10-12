package com.pesaflow.app.parsers

import com.pesaflow.app.data.analytics.*
import com.pesaflow.app.data.models.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class GoalEngineTest {

    private val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 1, 12, 0) }.timeInMillis
    private fun tx(amount: Double, category: String, ts: Long, type: TransactionType = TransactionType.EXPENSE): Transaction {
        return Transaction(amount = amount, type = type, category = category, dateTimestamp = ts, merchant = "Test")
    }
    private fun day(offset: Int): Long = now - offset * 24L * 60 * 60 * 1000

    @Test
    fun `onTrackGoal`() {
        val goal = SavingsGoal(title = "Phone", targetAmount = 10000.0, currentAmount = 8000.0, targetTimestamp = now + 30 * 24L * 60 * 60 * 1000)
        // Saved 2000 in 10 days = 200/day; needs 2000/30 = 66.7/day
        val txs = (0..9).map { tx(200.0, "Saving", day(it), TransactionType.SAVING) }
        val p = buildGoalProjection(goal, txs, now)
        assertTrue(p.onTrack)
        assertEquals(GoalProjection.RiskLevel.ON_TRACK, p.riskLevel)
        assertTrue(p.suggestions.contains("On track — keep the pace"))
    }

    @Test
    fun `atRiskGoal`() {
        val goal = SavingsGoal(title = "Phone", targetAmount = 10000.0, currentAmount = 5000.0, targetTimestamp = now + 30 * 24L * 60 * 60 * 1000)
        // Saved 1000 in 10 days = 100/day; needs 5000/30 = 166.7/day
        val txs = (0..9).map { tx(100.0, "Saving", day(it), TransactionType.SAVING) }
        val p = buildGoalProjection(goal, txs, now)
        assertEquals(GoalProjection.RiskLevel.AT_RISK, p.riskLevel)
        assertTrue(p.suggestions.any { it.contains("Close the gap") })
    }

    @Test
    fun `dangerGoal`() {
        val goal = SavingsGoal(title = "Phone", targetAmount = 10000.0, currentAmount = 3000.0, targetTimestamp = now + 30 * 24L * 60 * 60 * 1000)
        // Saved 500 in 10 days = 50/day; needs 7000/30 = 233/day
        val txs = (0..9).map { tx(50.0, "Saving", day(it), TransactionType.SAVING) }
        val p = buildGoalProjection(goal, txs, now)
        assertEquals(GoalProjection.RiskLevel.DANGER, p.riskLevel)
        assertTrue(p.suggestions.any { it.contains("URGENT") })
    }

    @Test
    fun `progressPercentCalculated`() {
        val goal = SavingsGoal(title = "Phone", targetAmount = 10000.0, currentAmount = 7500.0)
        val txs = emptyList<Transaction>()
        val p = buildGoalProjection(goal, txs, now)
        assertEquals(75, p.progressPercent)
        assertEquals(2500.0, p.remaining, 0.001)
    }

    @Test
    fun `categoryCutsSuggested`() {
        val goal = SavingsGoal(title = "Phone", targetAmount = 10000.0, currentAmount = 5000.0, targetTimestamp = now + 30 * 24L * 60 * 60 * 1000)
        val txs = (0..9).map { tx(500.0, "Food", day(it)) } + (0..9).map { tx(200.0, "Transport", day(it)) }
        val p = buildGoalProjection(goal, txs, now)
        assertTrue(p.categoryCuts.isNotEmpty())
    }

    @Test
    fun `projectAllGoalsSortedByUrgency`() {
        val onTrack = SavingsGoal(title = "Safe", targetAmount = 10000.0, currentAmount = 9000.0, targetTimestamp = now + 30L * 86400000)
        val danger = SavingsGoal(title = "Risky", targetAmount = 10000.0, currentAmount = 2000.0, targetTimestamp = now + 30L * 86400000)
        val txs = (0..9).map { tx(100.0, "Saving", day(it), TransactionType.SAVING) }
        val results = projectAllGoals(listOf(onTrack, danger), txs, now)
        assertEquals(2, results.size)
        assertEquals(GoalProjection.RiskLevel.DANGER, results[0].riskLevel)
    }

    @Test
    fun `suggestNewGoalForHighSpender`() {
        val txs = (0..29).map { tx(200.0, "Food", day(it)) }
        val goals = emptyList<SavingsGoal>()
        val suggestion = suggestNewGoal(txs, goals, now)
        assertNotNull(suggestion)
        assertTrue(suggestion!!.contains("Emergency fund"))
    }

    @Test
    fun `noSuggestionForLowSpender`() {
        val txs = (0..5).map { tx(50.0, "Food", day(it)) }
        val goals = emptyList<SavingsGoal>()
        val suggestion = suggestNewGoal(txs, goals, now)
        assertNull(suggestion)
    }

    @Test
    fun `emptyHistoryReturnsSafeDefaults`() {
        val goal = SavingsGoal(title = "Phone", targetAmount = 10000.0, currentAmount = 5000.0, targetTimestamp = now + 30 * 86400000)
        val p = buildGoalProjection(goal, emptyList(), now)
        assertEquals(5000.0, p.remaining, 0.001)
        assertEquals(0.0, p.currentDailyPace, 0.001)
        assertFalse(p.suggestions.contains("On track — keep the pace"))
    }
}
