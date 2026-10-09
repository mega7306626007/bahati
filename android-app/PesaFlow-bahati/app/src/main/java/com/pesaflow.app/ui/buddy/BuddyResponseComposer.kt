package com.pesaflow.app.ui.buddy

import com.pesaflow.app.data.finance.Money
import com.pesaflow.app.data.finance.MoneyFormatter

// Natural-language answers composed SOLELY from the handed-in canonical
// state, formatted with the canonical MoneyFormatter (Dashboard parity by
// construction). Every figure carries its provenance: recorded, calculated,
// or estimate. The voice comes from state.lang via BuddyStrings — figures
// and entity names never change with it. Pure, unit-tested.
sealed interface BuddyOutcome {
    data class Answer(val text: String) : BuddyOutcome
    data class Navigate(val route: String, val label: String) : BuddyOutcome
    data class Ask(val text: String) : BuddyOutcome
    // No-confirmation command (low-risk control): the bridge runs it at once.
    data class ExecuteNow(val command: BuddyCommand) : BuddyOutcome
    // Financial mutation: the bridge renders a confirm card; nothing runs
    // until the user taps it.
    data class ProposeCommand(val command: BuddyCommand, val summary: String) : BuddyOutcome
    // Several requests in one message: each part resolved independently.
    // partial = some clause fell outside Buddy (try it on its own).
    data class Compound(val parts: List<BuddyOutcome>, val partial: Boolean = false) : BuddyOutcome
    data object Unhandled : BuddyOutcome
}

object BuddyResponseComposer {

    private fun ksh(amount: Double): String = MoneyFormatter.compact(Money.of(amount))

    fun compose(intent: BuddyIntent, state: BuddyReadState): BuddyOutcome = when (intent) {
        BuddyIntent.QueryBalance -> {
            val why = state.balanceExplanation?.let { " $it" }.orEmpty()
            BuddyOutcome.Answer(BuddyStrings.recordedBalance(ksh(state.liquid), why, state.lang))
        }
        BuddyIntent.QueryFlexible ->
            BuddyOutcome.Answer(
                BuddyStrings.flexibleMoney(
                    ksh(state.flexible),
                    BuddyStrings.qualityNote(state.dataQuality, state.lang),
                    state.lang
                )
            )
        BuddyIntent.QuerySafeSpend ->
            BuddyOutcome.Answer(
                BuddyStrings.safeSpend(
                    ksh(state.safeToday), ksh(state.safeWeek),
                    BuddyStrings.qualityNote(state.dataQuality, state.lang),
                    state.lang
                )
            )
        BuddyIntent.QueryBills -> if (state.openBillCount == 0) {
            BuddyOutcome.Answer(BuddyStrings.noOpenBills(state.lang))
        } else {
            BuddyOutcome.Answer(
                BuddyStrings.openBills(state.openBillCount, ksh(state.openBillTotal), state.lang)
            )
        }
        BuddyIntent.QueryDebts -> if (state.openDebtCount == 0) {
            BuddyOutcome.Answer(BuddyStrings.noOpenDebts(state.lang))
        } else {
            BuddyOutcome.Answer(
                BuddyStrings.openDebts(state.openDebtCount, ksh(state.openDebtTotal), state.lang)
            )
        }
        BuddyIntent.QueryGoals -> if (state.goalCount == 0) {
            BuddyOutcome.Answer(BuddyStrings.noGoals(state.lang))
        } else {
            BuddyOutcome.Answer(BuddyStrings.goals(state.goalCount, state.lang))
        }
        is BuddyIntent.OpenScreen ->
            BuddyOutcome.Navigate(intent.route, BuddyStrings.opening(intent.route, state.lang))
        is BuddyIntent.FindTransactions,
        is BuddyIntent.Reclassify,
        is BuddyIntent.Delete,
        is BuddyIntent.ResolvedReclassify,
        is BuddyIntent.ResolvedDelete ->
            BuddyOutcome.Unhandled // resolved by BuddyPhaseOne with ledger access
        is BuddyIntent.BuildPlan -> answerPlan(intent.horizonDays, state)
        is BuddyIntent.MarkBillPaid,
        is BuddyIntent.EditBill,
        is BuddyIntent.EditBillDate,
        is BuddyIntent.RecordDebtPayment,
        is BuddyIntent.ResolvedDebtPay ->
            BuddyOutcome.Unhandled // resolved by BuddyPhaseOne with bills/debts access
        BuddyIntent.ApplyPlan -> {
            val plan = com.pesaflow.app.ui.dashboard.BuddyMemory.lastPlan
            if (plan == null) {
                BuddyOutcome.Ask(BuddyStrings.askPlanFirst(state.lang))
            } else {
                // Envelope = current bills plus the plan's spendable remainder.
                val command = BuddyCommand.ApplyPlan(
                    plan.horizonDays, plan.daily, state.openBillTotal + plan.spendable
                )
                BuddyOutcome.ProposeCommand(command, summarize(command, state.lang))
            }
        }
        is BuddyIntent.AddIncomeSource -> {
            if (intent.amount == null || intent.amount <= 0) {
                BuddyOutcome.Ask(BuddyStrings.askIncomeAmount(state.lang))
            } else if (intent.label.isNullOrBlank()) {
                BuddyOutcome.Ask(BuddyStrings.askIncomeLabel(ksh(intent.amount), state.lang))
            } else {
                val command = BuddyCommand.AddIncomeSource(
                    intent.label, intent.kind, intent.amount, intent.frequency, intent.dayOfMonth
                )
                BuddyOutcome.ProposeCommand(command, summarize(command, state.lang))
            }
        }
        BuddyIntent.QueryIncome -> {
            val head = BuddyStrings.incomeEarned(ksh(state.monthIncome), state.lang)
            if (state.expectedIncomeLines.isEmpty()) {
                BuddyOutcome.Answer("$head\n${BuddyStrings.incomeNoExpected(state.lang)}")
            } else {
                BuddyOutcome.Answer(
                    "$head\n${BuddyStrings.incomeExpectedHead(state.lang)}\n" +
                        state.expectedIncomeLines.joinToString("\n")
                )
            }
        }
        is BuddyIntent.CompareSpendingPlans -> answerComparePlans(intent.horizonDays, state)
        BuddyIntent.SemesterOutlook -> answerSemester(state)
        BuddyIntent.MorningBriefing -> answerBriefing(state)
        BuddyIntent.WhatChanged -> answerChanged(state)
        BuddyIntent.ComparePeriods -> answerCompare(state)
        is BuddyIntent.CreateBudget -> {
            val amount = intent.amount
            val category = intent.category
            if (amount == null || amount <= 0) {
                BuddyOutcome.Ask(BuddyStrings.askBudgetAmount(state.lang))
            } else if (category == null) {
                BuddyOutcome.Ask(BuddyStrings.askBudgetCategory(ksh(amount), state.lang))
            } else {
                val command = BuddyCommand.CreateBudget(category, amount)
                BuddyOutcome.ProposeCommand(command, summarize(command, state.lang))
            }
        }
        is BuddyIntent.AddBill -> {
            val amount = intent.amount
            if (amount == null || amount <= 0) {
                BuddyOutcome.Ask(BuddyStrings.askBillAmount(state.lang))
            } else if (intent.name.isNullOrBlank()) {
                BuddyOutcome.Ask(BuddyStrings.askBillName(ksh(amount), state.lang))
            } else {
                val command = BuddyCommand.AddBill(intent.name, amount, intent.dueDays, intent.frequency)
                BuddyOutcome.ProposeCommand(command, summarize(command, state.lang))
            }
        }
        is BuddyIntent.AddDebt -> {
            val amount = intent.amount
            if (amount == null || amount <= 0) {
                BuddyOutcome.Ask(BuddyStrings.askDebtAmount(state.lang))
            } else if (intent.person.isNullOrBlank()) {
                BuddyOutcome.Ask(BuddyStrings.askDebtPerson(ksh(amount), state.lang))
            } else {
                val command = BuddyCommand.AddDebt(intent.person, amount, intent.iOwe)
                BuddyOutcome.ProposeCommand(command, summarize(command, state.lang))
            }
        }
        is BuddyIntent.CreateGoal -> {
            val amount = intent.amount
            if (amount == null || amount <= 0) {
                BuddyOutcome.Ask(BuddyStrings.askGoalAmount(state.lang))
            } else if (intent.title.isNullOrBlank()) {
                BuddyOutcome.Ask(BuddyStrings.askGoalTitle(ksh(amount), state.lang))
            } else {
                val command = BuddyCommand.CreateGoal(intent.title, amount)
                BuddyOutcome.ProposeCommand(command, summarize(command, state.lang))
            }
        }
        is BuddyIntent.CreateReminder -> {
            if (intent.conditional) {
                BuddyOutcome.Ask(BuddyStrings.conditionalDecline(state.lang))
            } else if (intent.title.isNullOrBlank()) {
                BuddyOutcome.Ask(BuddyStrings.askReminderTitle(state.lang))
            } else if (intent.kind == null) {
                BuddyOutcome.Ask(BuddyStrings.askReminderWhen(state.lang))
            } else {
                val command = BuddyCommand.CreateReminder(
                    intent.title, "Reminder: ${intent.title}",
                    intent.kind, intent.scheduleLabel.orEmpty()
                )
                BuddyOutcome.ProposeCommand(command, summarize(command, state.lang))
            }
        }
        BuddyIntent.ListReminders ->
            if (state.reminderLines.isEmpty()) {
                BuddyOutcome.Answer(BuddyStrings.noReminders(state.lang))
            } else {
                BuddyOutcome.Answer(
                    "${BuddyStrings.remindersHead(state.lang)}\n" + state.reminderLines.joinToString("\n")
                )
            }
        BuddyIntent.CancelReminders ->
            if (state.reminderLines.isEmpty()) {
                BuddyOutcome.Answer(BuddyStrings.nothingToCancel(state.lang))
            } else {
                val command = BuddyCommand.CancelAllReminders
                BuddyOutcome.ProposeCommand(command, summarize(command, state.lang))
            }
        is BuddyIntent.SimulatePurchase -> simulatePurchase(intent.amount, state)
        is BuddyIntent.SimulateIncomeLate -> simulateIncomeLate(intent.days, state)
        is BuddyIntent.SimulateRentChange -> simulateRentChange(intent.newRent, intent.delta, state)
        BuddyIntent.Help ->
            BuddyOutcome.Answer(BuddyCapabilities.describe(state.lang))
        is BuddyIntent.UpdatePreference ->
            BuddyOutcome.ExecuteNow(BuddyCommand.UpdatePreference(intent.key, intent.value))
        is BuddyIntent.AddTransaction ->
            if (intent.amount == null || intent.amount <= 0) {
                BuddyOutcome.Ask(BuddyStrings.askTxnAmount(state.lang))
            } else {
                val command = BuddyCommand.AddTransaction(
                    intent.amount, intent.category ?: "Other", intent.merchant, intent.income
                )
                BuddyOutcome.ProposeCommand(command, summarize(command, state.lang))
            }
        BuddyIntent.Unknown ->
            BuddyOutcome.Unhandled
    }

    fun answerTxnQuery(filter: TxnFilter, result: TxnQueryResult, lang: com.pesaflow.app.data.models.AppLanguage): BuddyOutcome {
        if (result.count == 0) return BuddyOutcome.Answer(BuddyStrings.noTxnMatch(lang))
        val label = BuddyStrings.txnQueryLabel(
            filter.merchant,
            filter.category,
            filter.amount?.let { ksh(it) },
            lang
        )
        val head = BuddyStrings.txnQueryHead(result.count, label, ksh(result.total), lang)
        return BuddyOutcome.Answer(BuddyStrings.txnQuery(head, result.lines, lang))
    }

    fun askWhich(
        candidates: List<com.pesaflow.app.ui.dashboard.PendingCandidate>,
        verb: String,
        scope: String = "matching",
        lang: com.pesaflow.app.data.models.AppLanguage = com.pesaflow.app.data.models.AppLanguage.ENGLISH
    ): BuddyOutcome {
        val numbered = candidates.mapIndexed { i, c -> "${i + 1}. ${c.label}" }.joinToString("\n")
        return BuddyOutcome.Ask(
            BuddyStrings.askWhich(candidates.size, numbered, verb, scope, lang)
        )
    }

    fun bulkDeleteUnsupported(lang: com.pesaflow.app.data.models.AppLanguage = com.pesaflow.app.data.models.AppLanguage.ENGLISH): BuddyOutcome =
        BuddyOutcome.Ask(BuddyStrings.bulkDeleteUnsupported(lang))

    private fun answerComparePlans(horizon: Int, state: BuddyReadState): BuddyOutcome {
        val lang = state.lang
        val n = horizon.coerceIn(1, 365)
        val burn = state.dailyBurn
        if (burn <= 0) {
            return BuddyOutcome.Ask(BuddyStrings.simBurnUnknown(lang))
        }
        val labels = BuddyStrings.comparePlansLabels(lang)
        fun scenario(factor: Double): Triple<String, String, String> {
            val b = burn * factor
            val end = state.flexible - b * n
            val lasts = if (b > 0) state.flexible / b else Double.POSITIVE_INFINITY
            val lastsText = if (lasts.isInfinite()) BuddyStrings.lastsIndefinitely(lang)
            else BuddyStrings.lastsDays("%.0f".format(lasts), lang)
            return Triple(ksh(b), ksh(end), lastsText)
        }
        val rows = listOf(0.7, 1.0, 1.3).mapIndexed { i, f ->
            val (b, e, l) = scenario(f)
            BuddyStrings.comparePlansRow(labels[i], b, e, l, lang)
        }
        return BuddyOutcome.Answer(
            BuddyStrings.comparePlansHead(n, ksh(state.flexible), lang) + "\n" +
                rows.joinToString("\n") + "\n${BuddyStrings.simTag(lang)}"
        )
    }

    private fun answerSemester(state: BuddyReadState): BuddyOutcome {
        val lang = state.lang
        val r = state.semester
            ?: return BuddyOutcome.Ask(BuddyStrings.semesterNoDates(lang))
        if (r.isUpcoming) {
            return BuddyOutcome.Answer(
                BuddyStrings.semesterUpcoming(
                    r.daysUntilStart, r.daysRemaining,
                    ksh(r.availableAfterCommitments), lang
                )
            )
        }
        if (r.isEnded) {
            return BuddyOutcome.Answer(
                BuddyStrings.semesterEnded(ksh(r.outflows), ksh(r.openingFunds + r.income), lang)
            )
        }
        val verdict = if (r.projectedEndAfterCommitments >= 0) {
            BuddyStrings.semesterOnTrack(ksh(r.projectedEndAfterCommitments), lang)
        } else {
            val tightDay = if (r.dailyPace > 0) {
                r.daysElapsed + (r.availableAfterCommitments / r.dailyPace).toInt().coerceAtLeast(0)
            } else r.daysElapsed
            BuddyStrings.semesterTight(tightDay, lang)
        }
        return BuddyOutcome.Answer(
            BuddyStrings.semesterHead(
                r.daysElapsed, r.daysElapsed + r.daysRemaining,
                ksh(r.weeklyAllowance), ksh(r.outflows), verdict, lang
            )
        )
    }

    private fun answerBriefing(state: BuddyReadState): BuddyOutcome {
        val lang = state.lang
        val lines = mutableListOf(
            BuddyStrings.briefingHead(lang),
            BuddyStrings.briefingState(ksh(state.flexible), ksh(state.safeToday), lang)
        )
        lines.add(BuddyStrings.briefingQueue(state.pendingCount, lang))
        lines.add(BuddyStrings.briefingPressure(state.billsDueSoonCount, ksh(state.billsDueSoonTotal), lang))
        val action = when {
            state.billsDueSoonCount > 0 ->
                BuddyStrings.briefingActionProtect(ksh(state.billsDueSoonTotal), lang)
            state.pendingCount > 0 ->
                BuddyStrings.briefingActionReview(lang)
            state.flexible < 0 ->
                BuddyStrings.briefingActionTrim(lang)
            else -> BuddyStrings.briefingActionHold(lang)
        }
        lines.add(action)
        return BuddyOutcome.Answer(lines.joinToString("\n"))
    }

    private fun answerChanged(state: BuddyReadState): BuddyOutcome {
        val lang = state.lang
        return BuddyOutcome.Answer(
            BuddyStrings.changedHead(
                ksh(state.thisMonthExpense), ksh(state.lastMonthExpense),
                BuddyStrings.pctLabel(state.thisMonthExpense, state.lastMonthExpense, lang), lang
            ) + "\n" + BuddyStrings.changedTail(state.pendingCount, state.openBillCount, lang)
        )
    }

    private fun answerCompare(state: BuddyReadState): BuddyOutcome {
        val lang = state.lang
        return BuddyOutcome.Answer(
            BuddyStrings.compareHead(
                ksh(state.thisMonthExpense), ksh(state.lastMonthExpense),
                BuddyStrings.pctLabel(state.thisMonthExpense, state.lastMonthExpense, lang), lang
            ) + "\n" + BuddyStrings.compareTops(state.thisMonthTop, state.lastMonthTop, lang)
        )
    }

    private fun answerPlan(horizon: Int, state: BuddyReadState): BuddyOutcome {
        val lang = state.lang
        val n = horizon.coerceIn(1, 365)
        val spendable = state.flexible - state.openBillTotal
        val head = BuddyStrings.planHead(n, ksh(state.flexible), ksh(state.openBillTotal), ksh(spendable), lang)
        if (spendable <= 0) {
            com.pesaflow.app.ui.dashboard.BuddyMemory.lastPlan = null
            return BuddyOutcome.Answer(
                "$head\n${BuddyStrings.planBillsFirst(ksh(-spendable), lang)}"
            )
        }
        val daily = spendable / n
        // Stored for "apply plan" — one card, envelope + reminder, your tap.
        com.pesaflow.app.ui.dashboard.BuddyMemory.lastPlan =
            com.pesaflow.app.ui.buddy.BuiltPlan(n, daily, spendable)
        val pace = if (state.dailyBurn > 0) {
            if (state.dailyBurn <= daily) {
                BuddyStrings.planPaceFits(ksh(state.dailyBurn), lang)
            } else {
                BuddyStrings.planPaceOver(ksh(state.dailyBurn), ksh(state.dailyBurn - daily), lang)
            }
        } else {
            BuddyStrings.planBurnUnknown(lang)
        }
        return BuddyOutcome.Answer(
            "$head\n${BuddyStrings.planDaily(ksh(daily), pace, lang)}"
        )
    }

    private fun simulatePurchase(amount: Double?, state: BuddyReadState): BuddyOutcome {
        val lang = state.lang
        if (amount == null || amount <= 0) {
            return BuddyOutcome.Ask(BuddyStrings.askAmountForSpend(lang))
        }
        val after = state.flexible - amount
        val runway = if (state.dailyBurn > 0) {
            val days = amount / state.dailyBurn
            BuddyStrings.simRunway("%.1f".format(days), ksh(state.dailyBurn), lang)
        } else {
            BuddyStrings.simRunwayUnknown(lang)
        }
        val verdict = if (after < 0) {
            BuddyStrings.simPurchaseOver(ksh(-after), lang)
        } else {
            BuddyStrings.simPurchaseFlex(ksh(state.flexible), ksh(after), lang)
        }
        return BuddyOutcome.Answer(
            BuddyStrings.simPurchase(ksh(amount), verdict, runway, lang)
        )
    }

    private fun simulateIncomeLate(days: Int?, state: BuddyReadState): BuddyOutcome {
        val lang = state.lang
        if (days == null || days <= 0) {
            return BuddyOutcome.Ask(BuddyStrings.askDaysLate(lang))
        }
        val r = com.pesaflow.app.data.finance.scenarioIncomeDelayed(
            heldCash = state.flexible, dailyBurn = state.dailyBurn, daysLate = days
        )
        val missing = BuddyStrings.missingInputs(r.missingInputs, lang)
        return BuddyOutcome.Answer(
            BuddyStrings.simLate(days, ksh(state.flexible), ksh(state.dailyBurn), ksh(r.newMonthlyTotal), missing, lang)
        )
    }

    private fun simulateRentChange(newRent: Double?, delta: Boolean, state: BuddyReadState): BuddyOutcome {
        val lang = state.lang
        if (newRent == null || newRent <= 0) {
            return BuddyOutcome.Ask(BuddyStrings.askNewRent(lang))
        }
        val old = state.currentRent
            ?: return BuddyOutcome.Ask(BuddyStrings.askCurrentRent(lang))
        val target = if (delta) old + newRent else newRent
        val r = com.pesaflow.app.data.finance.scenarioRentChange(old, target)
        val effect = if (r.monthlyDelta >= 0) BuddyStrings.simRentFrees(ksh(r.monthlyDelta), lang)
        else BuddyStrings.simRentCosts(ksh(-r.monthlyDelta), lang)
        return BuddyOutcome.Answer(
            BuddyStrings.simRent(ksh(old), ksh(target), effect, lang)
        )
    }

    fun summarize(command: BuddyCommand, lang: com.pesaflow.app.data.models.AppLanguage = com.pesaflow.app.data.models.AppLanguage.ENGLISH): String = when (command) {
        is BuddyCommand.UpdatePreference -> when (command.key) {
            PrefKey.THEME -> "Use ${command.value.lowercase()} theme?"
            PrefKey.HIDE_BALANCES ->
                if (command.value == "true") "Hide all balances?" else "Show balances again?"
            PrefKey.LANGUAGE -> "Change language to ${command.value.lowercase()}?"
        }
        is BuddyCommand.AddTransaction -> {
            val kind = if (command.income) "income" else "expense"
            val who = command.merchant?.let { " · $it" }.orEmpty()
            BuddyStrings.summarizeAdd(kind, ksh(command.amount), command.category, who, lang)
        }
        is BuddyCommand.ReclassifyTransaction ->
            BuddyStrings.summarizeReclassify(command.txIds.size, command.category, lang)
        is BuddyCommand.DeleteTransaction ->
            BuddyStrings.summarizeDelete(command.txIds.size, lang)
        is BuddyCommand.CreateBudget ->
            BuddyStrings.summarizeBudget(command.category, ksh(command.amount), lang)
        is BuddyCommand.AddBill ->
            BuddyStrings.summarizeBill(command.name, ksh(command.amount), command.dueDays, lang)
        is BuddyCommand.AddDebt ->
            BuddyStrings.summarizeDebt(command.iOwe, command.person, ksh(command.amount), lang)
        is BuddyCommand.CreateGoal ->
            BuddyStrings.summarizeGoal(command.title, ksh(command.amount), lang)
        is BuddyCommand.AddIncomeSource -> {
            val whenText = when (command.frequency) {
                "DAILY" -> BuddyStrings.incomeWhenDaily(lang)
                "WEEKLY" -> BuddyStrings.incomeWhenWeekly(lang)
                "ONCE" -> BuddyStrings.incomeWhenOnce(lang)
                else -> BuddyStrings.incomeWhenMonthly(command.dayOfMonth, lang)
            }
            BuddyStrings.summarizeIncome(command.label, ksh(command.amount), whenText, lang)
        }
        is BuddyCommand.ApplyPlan ->
            BuddyStrings.summarizeApply(
                command.horizonDays, ksh(command.budgetTotal), ksh(command.daily), lang
            )
        is BuddyCommand.MarkBillPaid ->
            "Mark bill paid?\nFiles the payment as an expense."
        is BuddyCommand.EditBill ->
            "Change bill amount?\nFiles the new figure going forward."
        is BuddyCommand.RecordDebtPayment ->
            "Record debt payment?\nReduces the tracked debt."
        is BuddyCommand.EditBillDate ->
            BuddyStrings.summarizeBillDate(lang)
        is BuddyCommand.CreateReminder ->
            BuddyStrings.summarizeReminder(command.title, command.scheduleLabel, lang)
        BuddyCommand.CancelAllReminders ->
            BuddyStrings.summarizeCancelReminders(lang)
    }
}
