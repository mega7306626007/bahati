package com.pesaflow.app.ui.buddy

// Canonical capability registry: the only advertised surface. Every entry
// states its risk class and whether it needs confirmation, so "what can you
// do?" can never promise something unsupported. Grows per phase.
enum class BuddyRiskClass { READ, APP_CONTROL, FINANCIAL_MUTATION, DESTRUCTIVE }

data class BuddyCapability(
    val id: String,
    val title: String,
    val description: String,
    val intent: BuddyIntent,
    val risk: BuddyRiskClass,
    val needsConfirmation: Boolean,
    val domain: String
)

object BuddyCapabilities {

    val all: List<BuddyCapability> = listOf(
        BuddyCapability(
            "query.balance", "Check balance",
            "Recorded balance from the canonical ledger.",
            BuddyIntent.QueryBalance, BuddyRiskClass.READ,
            needsConfirmation = false, domain = "ledger"
        ),
        BuddyCapability(
            "query.flexible", "Check flexible money",
            "Calculated flexible money from the financial engine.",
            BuddyIntent.QueryFlexible, BuddyRiskClass.READ,
            needsConfirmation = false, domain = "financial-engine"
        ),
        BuddyCapability(
            "query.safe_spend", "Check safe to spend",
            "Safe-today and safe-week figures from the financial engine.",
            BuddyIntent.QuerySafeSpend, BuddyRiskClass.READ,
            needsConfirmation = false, domain = "financial-engine"
        ),
        BuddyCapability(
            "query.bills", "Check bills",
            "Open bills and the total you still owe.",
            BuddyIntent.QueryBills, BuddyRiskClass.READ,
            needsConfirmation = false, domain = "bills"
        ),
        BuddyCapability(
            "query.debts", "Check debts",
            "Open debts and the total outstanding.",
            BuddyIntent.QueryDebts, BuddyRiskClass.READ,
            needsConfirmation = false, domain = "debt"
        ),
        BuddyCapability(
            "query.goals", "Check savings goals",
            "How many savings goals you have.",
            BuddyIntent.QueryGoals, BuddyRiskClass.READ,
            needsConfirmation = false, domain = "goals"
        ),
        BuddyCapability(
            "app.navigate", "Open a screen",
            "Go to Budgets, Bills, Semester, Settings and the other screens.",
            BuddyIntent.OpenScreen(route = ""), BuddyRiskClass.APP_CONTROL,
            needsConfirmation = false, domain = "navigation"
        ),
        BuddyCapability(
            "app.help", "What Buddy can do",
            "Lists exactly this registry — nothing invented.",
            BuddyIntent.Help, BuddyRiskClass.READ,
            needsConfirmation = false, domain = "buddy"
        ),
        BuddyCapability(
            "app.preference", "Change display preferences",
            "Dark mode, hide/show balances, language. Applies immediately.",
            BuddyIntent.UpdatePreference(PrefKey.THEME, "DARK"), BuddyRiskClass.APP_CONTROL,
            needsConfirmation = false, domain = "settings"
        ),
        BuddyCapability(
            "txn.add", "Add a transaction",
            "Drafts a manual transaction behind a confirm card first.",
            BuddyIntent.AddTransaction(null, null, null, false), BuddyRiskClass.FINANCIAL_MUTATION,
            needsConfirmation = true, domain = "ledger"
        ),
        BuddyCapability(
            "txn.find", "Find transactions",
            "Searches recorded transactions by person, category or amount.",
            BuddyIntent.FindTransactions(TxnFilter()), BuddyRiskClass.READ,
            needsConfirmation = false, domain = "ledger"
        ),
        BuddyCapability(
            "txn.reclassify", "Reclassify transactions",
            "Files ledger rows under a new category behind a confirm card.",
            BuddyIntent.Reclassify("Other", null, null), BuddyRiskClass.FINANCIAL_MUTATION,
            needsConfirmation = true, domain = "ledger"
        ),
        BuddyCapability(
            "txn.delete", "Delete transactions",
            "Undoable ledger delete behind a confirm card. Never bulk.",
            BuddyIntent.Delete(null, null), BuddyRiskClass.DESTRUCTIVE,
            needsConfirmation = true, domain = "ledger"
        ),
        BuddyCapability(
            "sim.purchase", "Simulate a purchase",
            "Shows flexible money and runway impact without touching the ledger.",
            BuddyIntent.SimulatePurchase(null), BuddyRiskClass.READ,
            needsConfirmation = false, domain = "simulation"
        ),
        BuddyCapability(
            "sim.income_late", "Simulate late income",
            "Shows held cash vs burn when income lands late. Simulated.",
            BuddyIntent.SimulateIncomeLate(null), BuddyRiskClass.READ,
            needsConfirmation = false, domain = "simulation"
        ),
        BuddyCapability(
            "sim.rent", "Simulate a rent change",
            "Monthly effect of a different rent. Simulated.",
            BuddyIntent.SimulateRentChange(null, false), BuddyRiskClass.READ,
            needsConfirmation = false, domain = "simulation"
        ),
        BuddyCapability(
            "plan.survive", "Build a survival plan",
            "Read-only N-day projection: protected bills, daily spendable, pace verdict. Apply comes later, never silently.",
            BuddyIntent.BuildPlan(14), BuddyRiskClass.READ,
            needsConfirmation = false, domain = "planning"
        ),
        BuddyCapability(
            "budget.create", "Create a budget",
            "Monthly envelope behind a confirm card.",
            BuddyIntent.CreateBudget(null, null), BuddyRiskClass.FINANCIAL_MUTATION,
            needsConfirmation = true, domain = "budgets"
        ),
        BuddyCapability(
            "bill.add", "Add a bill",
            "Bill with due date behind a confirm card.",
            BuddyIntent.AddBill(null, null, 7, "ONE_TIME"), BuddyRiskClass.FINANCIAL_MUTATION,
            needsConfirmation = true, domain = "bills"
        ),
        BuddyCapability(
            "debt.add", "Record a debt",
            "Debt record behind a confirm card.",
            BuddyIntent.AddDebt(null, null, true), BuddyRiskClass.FINANCIAL_MUTATION,
            needsConfirmation = true, domain = "debt"
        ),
        BuddyCapability(
            "goal.create", "Create a savings goal",
            "90-day savings goal behind a confirm card.",
            BuddyIntent.CreateGoal(null, null), BuddyRiskClass.FINANCIAL_MUTATION,
            needsConfirmation = true, domain = "goals"
        ),
        BuddyCapability(
            "reminder.create", "Create a reminder",
            "Time-based reminders (once, daily, weekly, monthly) behind a confirm card. No conditional triggers.",
            BuddyIntent.CreateReminder(null, null, null), BuddyRiskClass.APP_CONTROL,
            needsConfirmation = true, domain = "notifications"
        ),
        BuddyCapability(
            "reminder.list", "List reminders",
            "Shows your Buddy-created reminders.",
            BuddyIntent.ListReminders, BuddyRiskClass.READ,
            needsConfirmation = false, domain = "notifications"
        ),
        BuddyCapability(
            "reminder.cancel", "Cancel all reminders",
            "Cancels every Buddy-created reminder behind a confirm card.",
            BuddyIntent.CancelReminders, BuddyRiskClass.APP_CONTROL,
            needsConfirmation = true, domain = "notifications"
        ),
        BuddyCapability(
            "briefing.morning", "Morning briefing",
            "State, changes, upcoming pressure and one action. Read-only.",
            BuddyIntent.MorningBriefing, BuddyRiskClass.READ,
            needsConfirmation = false, domain = "briefing"
        ),
        BuddyCapability(
            "briefing.changed", "What changed",
            "This month vs last month, queue depth, open bills. Read-only.",
            BuddyIntent.WhatChanged, BuddyRiskClass.READ,
            needsConfirmation = false, domain = "briefing"
        ),
        BuddyCapability(
            "briefing.compare", "Compare periods",
            "This month vs last month with top categories. Read-only.",
            BuddyIntent.ComparePeriods, BuddyRiskClass.READ,
            needsConfirmation = false, domain = "briefing"
        ),
        BuddyCapability(
            "plan.apply", "Apply the survival plan",
            "Sets the monthly envelope and a daily check reminder behind one confirm card.",
            BuddyIntent.ApplyPlan, BuddyRiskClass.FINANCIAL_MUTATION,
            needsConfirmation = true, domain = "planning"
        ),
        BuddyCapability(
            "bill.pay", "Mark a bill paid",
            "Canonical bill payment behind a confirm card.",
            BuddyIntent.MarkBillPaid(null, null), BuddyRiskClass.FINANCIAL_MUTATION,
            needsConfirmation = true, domain = "bills"
        ),
        BuddyCapability(
            "bill.move_date", "Move a bill due date",
            "Canonical due-date move behind a confirm card.",
            BuddyIntent.EditBillDate(null, null), BuddyRiskClass.FINANCIAL_MUTATION,
            needsConfirmation = true, domain = "bills"
        ),
        BuddyCapability(
            "bill.edit", "Edit a bill amount",
            "Canonical bill edit behind a confirm card.",
            BuddyIntent.EditBill(null, null), BuddyRiskClass.FINANCIAL_MUTATION,
            needsConfirmation = true, domain = "bills"
        ),
        BuddyCapability(
            "debt.pay", "Record a debt payment",
            "Canonical partial/full settlement behind a confirm card.",
            BuddyIntent.RecordDebtPayment(null, null), BuddyRiskClass.FINANCIAL_MUTATION,
            needsConfirmation = true, domain = "debt"
        ),
        BuddyCapability(
            "sim.compare_plans", "Compare spending plans",
            "Conservative/normal/aggressive scenarios with end-flexible and runway. Simulated.",
            BuddyIntent.CompareSpendingPlans(30), BuddyRiskClass.READ,
            needsConfirmation = false, domain = "simulation"
        ),
        BuddyCapability(
            "semester.outlook", "Semester outlook",
            "Term position, weekly allowance and projected end from the runway engine.",
            BuddyIntent.SemesterOutlook, BuddyRiskClass.READ,
            needsConfirmation = false, domain = "semester"
        ),
        BuddyCapability(
            "income.query", "Check income",
            "Earned this month plus expected paydays. Read-only.",
            BuddyIntent.QueryIncome, BuddyRiskClass.READ,
            needsConfirmation = false, domain = "income"
        ),
        BuddyCapability(
            "income.add_source", "Track expected income",
            "Payday source with landing date behind a confirm card.",
            BuddyIntent.AddIncomeSource(null, "OTHER", null, "MONTHLY", 0), BuddyRiskClass.FINANCIAL_MUTATION,
            needsConfirmation = true, domain = "income"
        )
    )

    fun forIntent(intent: BuddyIntent): BuddyCapability? = when (intent) {
        is BuddyIntent.OpenScreen -> all.first { it.id == "app.navigate" }
        BuddyIntent.QueryBalance -> all.first { it.id == "query.balance" }
        BuddyIntent.QueryFlexible -> all.first { it.id == "query.flexible" }
        BuddyIntent.QuerySafeSpend -> all.first { it.id == "query.safe_spend" }
        BuddyIntent.QueryBills -> all.first { it.id == "query.bills" }
        BuddyIntent.QueryDebts -> all.first { it.id == "query.debts" }
        BuddyIntent.QueryGoals -> all.first { it.id == "query.goals" }
        BuddyIntent.Help -> all.first { it.id == "app.help" }
        is BuddyIntent.UpdatePreference -> all.first { it.id == "app.preference" }
        is BuddyIntent.AddTransaction -> all.first { it.id == "txn.add" }
        is BuddyIntent.FindTransactions -> all.first { it.id == "txn.find" }
        is BuddyIntent.Reclassify -> all.first { it.id == "txn.reclassify" }
        is BuddyIntent.Delete -> all.first { it.id == "txn.delete" }
        is BuddyIntent.ResolvedReclassify -> all.first { it.id == "txn.reclassify" }
        is BuddyIntent.ResolvedDelete -> all.first { it.id == "txn.delete" }
        is BuddyIntent.SimulatePurchase -> all.first { it.id == "sim.purchase" }
        is BuddyIntent.SimulateIncomeLate -> all.first { it.id == "sim.income_late" }
        is BuddyIntent.SimulateRentChange -> all.first { it.id == "sim.rent" }
        is BuddyIntent.BuildPlan -> all.first { it.id == "plan.survive" }
        is BuddyIntent.CreateBudget -> all.first { it.id == "budget.create" }
        is BuddyIntent.AddBill -> all.first { it.id == "bill.add" }
        is BuddyIntent.AddDebt -> all.first { it.id == "debt.add" }
        is BuddyIntent.CreateGoal -> all.first { it.id == "goal.create" }
        BuddyIntent.QueryIncome -> all.first { it.id == "income.query" }
        is BuddyIntent.AddIncomeSource -> all.first { it.id == "income.add_source" }
        is BuddyIntent.CompareSpendingPlans -> all.first { it.id == "sim.compare_plans" }
        is BuddyIntent.CreateReminder -> all.first { it.id == "reminder.create" }
        BuddyIntent.ApplyPlan -> all.first { it.id == "plan.apply" }
        is BuddyIntent.MarkBillPaid -> all.first { it.id == "bill.pay" }
        is BuddyIntent.EditBillDate -> all.first { it.id == "bill.move_date" }
        is BuddyIntent.EditBill -> all.first { it.id == "bill.edit" }
        is BuddyIntent.RecordDebtPayment -> all.first { it.id == "debt.pay" }
        is BuddyIntent.ResolvedDebtPay -> all.first { it.id == "debt.pay" }
        is BuddyIntent.CompareSpendingPlans -> all.first { it.id == "sim.compare_plans" }
        BuddyIntent.SemesterOutlook -> all.first { it.id == "semester.outlook" }
        BuddyIntent.MorningBriefing -> all.first { it.id == "briefing.morning" }
        BuddyIntent.WhatChanged -> all.first { it.id == "briefing.changed" }
        BuddyIntent.ComparePeriods -> all.first { it.id == "briefing.compare" }
        BuddyIntent.ListReminders -> all.first { it.id == "reminder.list" }
        BuddyIntent.CancelReminders -> all.first { it.id == "reminder.cancel" }
        BuddyIntent.Unknown -> null
    }

    fun describe(lang: com.pesaflow.app.data.models.AppLanguage = com.pesaflow.app.data.models.AppLanguage.ENGLISH): String =
        BuddyStrings.describe(all.map { it.title.lowercase() }, lang)
}
