package com.pesaflow.app.data.finance

import com.pesaflow.app.data.models.Debt

// Debt payoff planner: earliest due date first (a student budget pays what
// is late before what is big), one monthly payment spilling across debts.
// Pure Kotlin, fully unit-tested.
data class PayoffStep(
    val person: String,
    val amount: Double,
    /** Months from now when this debt clears (1 = this month). */
    val clearMonth: Int
)

data class PayoffPlan(
    val steps: List<PayoffStep>,
    /** Months until every listed debt clears. */
    val totalMonths: Int,
    val totalOwed: Double
)

fun planPayoff(
    owed: List<Debt>,
    monthlyPayment: Double
): PayoffPlan {
    val open = owed.filter { it.status != "PAID" && it.amount > 0 }
        .sortedWith(compareBy({ it.dueDate }, { it.amount }))
    if (open.isEmpty() || monthlyPayment <= 0) return PayoffPlan(emptyList(), 0, 0.0)
    val steps = mutableListOf<PayoffStep>()
    var month = 1
    var purse = monthlyPayment
    var guard = 0
    for (debt in open) {
        var left = debt.amount
        while (left > 0 && guard < 1200) {
            guard++
            if (purse >= left) {
                purse -= left
                left = 0.0
            } else {
                left -= purse
                purse = 0.0
                month++
                purse = monthlyPayment
            }
        }
        steps.add(PayoffStep(debt.person, debt.amount, month))
        if (purse <= 0) {
            month++
            purse = monthlyPayment
        }
    }
    return PayoffPlan(steps, steps.maxOfOrNull { it.clearMonth } ?: 0, open.sumOf { it.amount })
}
