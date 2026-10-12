package com.pesaflow.app.ui.buddy

// The only state the Phase-1 composer may read: canonical figures handed in
// by the UI layer (same ViewModel flows the screens read). Buddy owns no
// financial truth — no balances, no budgets, no second database here.
// lang selects the response voice; figures and entity names never change.
data class BuddyReadState(
    val liquid: Double,
    val flexible: Double,
    val safeToday: Double,
    val safeWeek: Double,
    val openBillCount: Int,
    val openBillTotal: Double,
    val openDebtCount: Int,
    val openDebtTotal: Double,
    val goalCount: Int,
    val balanceExplanation: String? = null,
    val dataQuality: String = "FULL",
    // Simulation inputs (derived, never stored as truth): current daily burn
    // and current monthly rent when known.
    val dailyBurn: Double = 0.0,
    val currentRent: Double? = null,
    // Buddy-created reminders, "title (schedule)" lines for listing.
    val reminderLines: List<String> = emptyList(),
    // Briefing inputs: pending queue depth, this/last month spend with top
    // categories, bills due within 7 days.
    val pendingCount: Int = 0,
    val thisMonthExpense: Double = 0.0,
    val thisMonthTop: String = "",
    val lastMonthExpense: Double = 0.0,
    val lastMonthTop: String = "",
    val billsDueSoonCount: Int = 0,
    val billsDueSoonTotal: Double = 0.0,
    // Canonical semester runway when term dates are set; null otherwise.
    val semester: com.pesaflow.app.data.finance.SemesterRunway? = null,
    // Earned income this month plus expected payday lines for 30 days.
    val monthIncome: Double = 0.0,
    val expectedIncomeLines: List<String> = emptyList(),
    // Response voice: figures and entity names never change with it.
    val lang: com.pesaflow.app.data.models.AppLanguage = com.pesaflow.app.data.models.AppLanguage.ENGLISH
)
