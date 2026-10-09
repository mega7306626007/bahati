package com.pesaflow.app.data.finance

import com.pesaflow.app.data.income.IncomeSource
import com.pesaflow.app.data.models.Bill
import com.pesaflow.app.data.models.Budget
import com.pesaflow.app.data.models.Debt
import com.pesaflow.app.data.models.FinancialProfile
import com.pesaflow.app.data.models.SavingsGoal
import com.pesaflow.app.data.models.Transaction

// Canonical financial vocabulary (§4). A transaction is an event, an account
// is where money lives, an obligation is owed, a reservation is assigned,
// a transfer nets to zero, a forecast is never current cash.
enum class Account { M_PESA, CASH, BANK, SAVINGS, ZIIDI, OTHER }

enum class Reliability { CONFIRMED, LIKELY, VARIABLE, POSSIBLE, UNKNOWN }

enum class Horizon { TODAY, WEEK, UNTIL_NEXT_INCOME, MONTH, SEMESTER }

enum class DataQuality { FULL, PARTIAL, SPARSE }

// Multidimensional profile dimensions (§12). Personas survive only as
// fallback presets mapped into these via ProfileSignals.fromPersona().
enum class Housing { PARENTS, RENTAL, SHARED_RENT, HOSTEL }
enum class Commute { WALK, SHORT, LONG, MIXED, IRREGULAR }
enum class FoodStyle { HOME_FED, COOK, BUY, MIXED }
enum class IncomeStability { FIXED, VARIABLE, MIXED, NONE }
enum class DebtLevel { NONE, LOW, MEDIUM, HIGH }
enum class RiskPreference { CONSERVATIVE, STANDARD, FLEXIBLE }

data class ProfileSignals(
    val housing: Housing = Housing.HOSTEL,
    val commute: Commute = Commute.SHORT,
    val food: FoodStyle = FoodStyle.MIXED,
    val incomeStability: IncomeStability = IncomeStability.MIXED,
    val incomeKinds: Set<String> = emptySet(), // HELB, PARENT, HUSTLE, JOB, ...
    val debtLevel: DebtLevel = DebtLevel.NONE,
    val risk: RiskPreference = RiskPreference.STANDARD,
    val academicInSession: Boolean = true,
    val examPeriod: Boolean = false,
    val roommates: Int = 0
)

data class IncomeReliability(
    val label: String,
    val reliability: Reliability,
    val expectedMonthly: Money,
    val landedThisMonth: Money
)

data class ObligationView(
    val name: String,
    val amount: Money,
    val remaining: Money,
    val dueInDays: Int,
    val essential: Boolean,
    val overdue: Boolean,
    val urgency: Double,
    val dailyReserve: Money
)

data class ReservationView(
    val label: String,
    val amount: Money,
    val projectedNote: String
)

// Forecast scenarios (§19): projected month-end FLEXIBLE money, so
// favourable >= typical >= cautious by construction.
data class ForecastRange(
    val cautious: Money,
    val typical: Money,
    val favourable: Money
)

data class MetricExplanation(
    val label: String,
    val headline: Money,
    val horizon: String,
    val why: String,
    val contributors: List<String> = emptyList(),
    val basis: String = "",
    val quality: DataQuality = DataQuality.SPARSE
)

// The canonical snapshot (§3): every hero/supporting figure derives here.
data class FinancialSnapshot(
    val liquid: Money,
    val accounts: Map<Account, Money>,
    val committed: Money,
    val reserved: Money,
    val flexible: Money,
    val safeToday: Money,
    val safeWeek: Money,
    val safeUntilIncome: Money,
    val safeMonth: Money,
    val safeSemester: Money,
    val primaryHorizon: Horizon,
    val reliableIncomeAhead: Money,
    val expectedIncomeAhead: Money,
    val essentialAhead: Money,
    val obligations: List<ObligationView>,
    val upcomingBillsTotal: Money,
    val upcomingDebtTotal: Money,
    val upcomingFees: Money,
    val goalReservations: List<ReservationView>,
    val riskBuffer: Money,
    val totalAssets: Money,
    val totalLiabilities: Money,
    val netWorth: Money,
    val monthlyEarnedIncome: Money,
    val forecast: ForecastRange,
    val projectedEndFlexible: Money,
    val incomeReliabilities: List<IncomeReliability>,
    val helbExpected: Money,
    val helbFeesCovered: Money,
    val helbUpkeep: Money,
    val quality: DataQuality,
    val explanations: Map<String, MetricExplanation>
)

data class SnapshotInput(
    val txs: List<Transaction>,
    val budgets: List<Budget> = emptyList(),
    val bills: List<Bill> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val goals: List<SavingsGoal> = emptyList(),
    val incomeSources: List<IncomeSource> = emptyList(),
    val profile: ProfileSignals = ProfileSignals(),
    val helbExpected: Double = 0.0,
    val feesAmount: Double = 0.0,
    val nowMs: Long = System.currentTimeMillis()
)

// Stored declaration → working signals. Corrupt values fall back, never crash.
fun FinancialProfile.toSignals(): ProfileSignals {
    fun housing(): Housing = Housing.values().firstOrNull { it.name == housing } ?: Housing.HOSTEL
    fun commute(): Commute = Commute.values().firstOrNull { it.name == commute } ?: Commute.SHORT
    fun food(): FoodStyle = FoodStyle.values().firstOrNull { it.name == food } ?: FoodStyle.MIXED
    fun stability(): IncomeStability =
        IncomeStability.values().firstOrNull { it.name == incomeStability } ?: IncomeStability.MIXED
    fun debt(): DebtLevel = DebtLevel.values().firstOrNull { it.name == debtLevel } ?: DebtLevel.NONE
    fun riskPref(): RiskPreference =
        RiskPreference.values().firstOrNull { it.name == risk } ?: RiskPreference.STANDARD
    return ProfileSignals(
        housing = housing(),
        commute = commute(),
        food = food(),
        incomeStability = stability(),
        incomeKinds = com.pesaflow.app.data.ledger.splitUserList(incomeKindsCsv).toSet(),
        debtLevel = debt(),
        risk = riskPref(),
        roommates = roommates
    )
}
