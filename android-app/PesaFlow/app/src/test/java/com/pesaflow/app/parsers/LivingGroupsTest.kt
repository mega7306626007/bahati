package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.AppLanguage
import com.pesaflow.app.data.models.Budget
import com.pesaflow.app.data.models.BudgetType
import com.pesaflow.app.ui.budgets.BudgetRule
import com.pesaflow.app.ui.budgets.LivingSituation
import com.pesaflow.app.ui.budgets.dailyFareReserve
import com.pesaflow.app.ui.budgets.deriveLivingGroup
import com.pesaflow.app.ui.budgets.parseLiving
import com.pesaflow.app.ui.budgets.scaledFareReserve
import com.pesaflow.app.ui.budgets.smartBudget
import com.pesaflow.app.ui.dashboard.buildInsights
import org.junit.Assert.*
import org.junit.Test


class LivingGroupsTest {

    private fun plan(group: LivingSituation, base: Double = 15000.0) =
        smartBudget(base, BudgetRule.CAMPUS, BudgetType.MONTHLY, group, true)

    @Test
    fun `parents groups never price rent`() {
        assertTrue(plan(LivingSituation.PARENT_FAR).suggestions.none { it.category == "Rent" })
        assertTrue(plan(LivingSituation.PARENT_NEAR).suggestions.none { it.category == "Rent" })
        assertTrue(plan(LivingSituation.RENT_WALK).suggestions.any { it.category == "Rent" })
    }

    @Test
    fun `long routes protect more fares than walkers`() {
        val far = plan(LivingSituation.PARENT_FAR).suggestions.firstOrNull { it.category == "Transport" }?.amount ?: 0
        val walk = plan(LivingSituation.RENT_WALK).suggestions.firstOrNull { it.category == "Transport" }?.amount ?: 0
        assertTrue("far=$far walk=$walk", far > walk)
    }

    @Test
    fun `no kitchen funds food higher than cooking`() {
        val buy = plan(LivingSituation.HOSTEL_BUY).suggestions.firstOrNull { it.category == "Food" }?.amount ?: 0
        val cook = plan(LivingSituation.HOSTEL_COOK).suggestions.firstOrNull { it.category == "Food" }?.amount ?: 0
        assertTrue("buy=$buy cook=$cook", buy > cook)
    }

    @Test
    fun `legacy answers map to closest group`() {
        assertEquals(LivingSituation.RENT_COMMUTE, parseLiving("daily=1|living=COMMUTER"))
        assertEquals(LivingSituation.HOSTEL_COOK, parseLiving("daily=1|living=HOSTEL"))
        assertEquals(LivingSituation.PARENT_FAR, parseLiving("x|living=PARENT_FAR"))
    }

    @Test
    fun `derivation covers all six groups`() {
        assertEquals(LivingSituation.PARENT_FAR, deriveLivingGroup("Parents", "Long", true))
        assertEquals(LivingSituation.PARENT_NEAR, deriveLivingGroup("Parents", "Short", true))
        assertEquals(LivingSituation.RENT_WALK, deriveLivingGroup("Rent", "Walk", true))
        assertEquals(LivingSituation.RENT_COMMUTE, deriveLivingGroup("Rent", "Long", false))
        assertEquals(LivingSituation.HOSTEL_COOK, deriveLivingGroup("Hostel", "Walk", true))
        assertEquals(LivingSituation.HOSTEL_BUY, deriveLivingGroup("Hostel", "Walk", false))
    }
    @Test
    fun `living alone rents like a renter`() {
        assertEquals(LivingSituation.RENT_WALK, deriveLivingGroup("Alone", "Walk", true))
        assertEquals(LivingSituation.RENT_COMMUTE, deriveLivingGroup("Alone", "Long", false))
    }

    @Test
    fun `money I have is wallet plus ziidi`() {
        assertEquals(890.0, com.pesaflow.app.data.parsers.moneyIHave(390.0, 500.0), 0.001)
        assertEquals(500.0, com.pesaflow.app.data.parsers.moneyIHave(null, 500.0), 0.001)
        assertEquals(0.0, com.pesaflow.app.data.parsers.moneyIHave(null, 0.0), 0.001)
    }

    @Test
    fun `fare reserve only guards real routes`() {
        assertEquals(65, dailyFareReserve(LivingSituation.PARENT_FAR))
        assertEquals(40, dailyFareReserve(LivingSituation.RENT_COMMUTE))
        assertEquals(15, dailyFareReserve(LivingSituation.PARENT_NEAR))
        assertEquals(0, dailyFareReserve(LivingSituation.RENT_WALK))
        assertEquals(0, dailyFareReserve(LivingSituation.HOSTEL_COOK))
    }

    @Test
    fun `no-rent group gets savings nudge without savings budget`() {
        val txs = listOf(
            com.pesaflow.app.data.models.Transaction(
                amount = 500.0,
                type = com.pesaflow.app.data.models.TransactionType.EXPENSE,
                category = "Food",
                dateTimestamp = System.currentTimeMillis(),
                merchant = "Kibanda"
            )
        )
        val out = buildInsights(
            txs, emptyList(), AppLanguage.ENGLISH, "", emptyList(),
            emptyList(), emptyList(), emptyMap(), LivingSituation.PARENT_NEAR
        )
        assertTrue(out.any { it.contains("No rent") })
    }

    @Test
    fun `home-fed food stays below hostel cooking food`() {
        val home = plan(LivingSituation.PARENT_FAR).suggestions.firstOrNull { it.category == "Food" }?.amount ?: 0
        val cook = plan(LivingSituation.HOSTEL_COOK).suggestions.firstOrNull { it.category == "Food" }?.amount ?: 0
        assertTrue("home=$home cook=$cook", home < cook)
    }

    @Test
    fun `own daily fare protects commuter transport`() {
        val base = smartBudget(15000.0, BudgetRule.CAMPUS, BudgetType.MONTHLY, LivingSituation.PARENT_FAR, true)
            .suggestions.firstOrNull { it.category == "Transport" }?.amount ?: 0
        val withFare = smartBudget(15000.0, BudgetRule.CAMPUS, BudgetType.MONTHLY, LivingSituation.PARENT_FAR, true, fareDaily = 150.0)
            .suggestions.firstOrNull { it.category == "Transport" }?.amount ?: 0
        assertTrue("withFare=$withFare base=$base", withFare >= 4500 && withFare >= base)
    }

    @Test
    fun `fare number never leaks into walker envelopes`() {
        val plain = smartBudget(15000.0, BudgetRule.CAMPUS, BudgetType.MONTHLY, LivingSituation.RENT_WALK, true)
            .suggestions.firstOrNull { it.category == "Transport" }?.amount ?: -1
        val withFare = smartBudget(15000.0, BudgetRule.CAMPUS, BudgetType.MONTHLY, LivingSituation.RENT_WALK, true, fareDaily = 150.0)
            .suggestions.firstOrNull { it.category == "Transport" }?.amount ?: -2
        assertEquals(plain, withFare)
    }

    @Test
    fun `tight mode follows floors not fixed shillings`() {
        // 6000 covers PARENT_FAR floors (2200) 2.7× — not tight, even though
        // the old 8000 rule called it tight. 3000 is genuinely tight.
        assertFalse(plan(LivingSituation.PARENT_FAR, 6000.0).tightMode)
        assertTrue(plan(LivingSituation.PARENT_FAR, 3000.0).tightMode)
    }

    @Test
    fun `fare guard follows your number on heavy routes`() {
        assertEquals(150, scaledFareReserve(LivingSituation.PARENT_FAR, 150.0))
        assertEquals(65, scaledFareReserve(LivingSituation.PARENT_FAR, 0.0))
        assertEquals(0, scaledFareReserve(LivingSituation.RENT_WALK, 150.0))
    }

    @Test
    fun `heavy commuter gets fare envelope nudge`() {
        val txs = listOf(
            com.pesaflow.app.data.models.Transaction(
                amount = 500.0,
                type = com.pesaflow.app.data.models.TransactionType.EXPENSE,
                category = "Food",
                dateTimestamp = System.currentTimeMillis(),
                merchant = "Kibanda"
            )
        )
        val out = buildInsights(
            txs, emptyList(), AppLanguage.ENGLISH, "", emptyList(),
            emptyList(), emptyList(), emptyMap(), LivingSituation.PARENT_FAR
        )
        assertTrue(out.any { it.contains("Transport envelope") })
    }
}
