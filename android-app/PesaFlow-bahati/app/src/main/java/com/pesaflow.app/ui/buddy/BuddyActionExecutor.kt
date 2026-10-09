package com.pesaflow.app.ui.buddy

import com.pesaflow.app.data.finance.Money
import com.pesaflow.app.data.finance.MoneyFormatter
import com.pesaflow.app.data.models.AppLanguage
import com.pesaflow.app.data.models.AppTheme
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.viewmodels.FinanceViewModel

// Runs confirmed (or no-confirmation) commands through the SAME ViewModel
// methods the screens call — one financial truth, no direct Room writes.
// Success is reported only after the domain call returns; every run is
// audit-logged with its confirmation state. Result strings follow the
// resolved voice; figures never change with it. Thin by design.
object BuddyActionExecutor {

    fun execute(
        viewModel: FinanceViewModel,
        command: BuddyCommand,
        confirmed: Boolean,
        lang: AppLanguage = AppLanguage.ENGLISH
    ): BuddyActionResult {
        val id = commandId(command)
        return try {
            val message = when (command) {
                is BuddyCommand.AddIncomeSource -> addIncomeSource(viewModel, command, lang)
                is BuddyCommand.UpdatePreference -> applyPreference(viewModel, command, lang)
                is BuddyCommand.AddTransaction -> addTransaction(viewModel, command, lang)
                is BuddyCommand.ReclassifyTransaction -> reclassify(viewModel, command, lang)
                is BuddyCommand.DeleteTransaction -> delete(viewModel, command, lang)
                is BuddyCommand.CreateBudget -> createBudget(viewModel, command, lang)
                is BuddyCommand.AddBill -> addBill(viewModel, command, lang)
                is BuddyCommand.AddDebt -> addDebt(viewModel, command, lang)
                is BuddyCommand.CreateGoal -> createGoal(viewModel, command, lang)
                is BuddyCommand.CreateReminder -> createReminder(viewModel, command, lang)
                BuddyCommand.CancelAllReminders -> cancelAllReminders(viewModel, lang)
                is BuddyCommand.ApplyPlan -> applyPlan(viewModel, command, lang)
                is BuddyCommand.MarkBillPaid -> markBillPaid(viewModel, command, lang)
                is BuddyCommand.EditBillDate -> editBillDate(viewModel, command, lang)
                is BuddyCommand.EditBill -> editBill(viewModel, command, lang)
                is BuddyCommand.RecordDebtPayment -> recordDebtPayment(viewModel, command, lang)
            }
            BuddyAuditLog.record(
                BuddyAuditEntry(
                    timestamp = System.currentTimeMillis(),
                    commandId = id,
                    parameters = command.toString().take(200),
                    confirmed = confirmed,
                    success = true,
                    detail = message.take(200)
                )
            )
            BuddyActionResult(true, message, id)
        } catch (t: Throwable) {
            val reason = t.message?.take(160) ?: t.javaClass.simpleName
            BuddyAuditLog.record(
                BuddyAuditEntry(
                    timestamp = System.currentTimeMillis(),
                    commandId = id,
                    parameters = command.toString().take(200),
                    confirmed = confirmed,
                    success = false,
                    detail = reason
                )
            )
            BuddyActionResult(false, BuddyStrings.failed(reason, lang), id)
        }
    }

    private fun commandId(command: BuddyCommand): String = when (command) {
        is BuddyCommand.AddIncomeSource -> "income.add_source"
        is BuddyCommand.UpdatePreference -> "app.preference:${command.key.name.lowercase()}"
        is BuddyCommand.AddTransaction -> "txn.add"
        is BuddyCommand.ReclassifyTransaction -> "txn.reclassify"
        is BuddyCommand.DeleteTransaction -> "txn.delete"
        is BuddyCommand.CreateBudget -> "budget.create"
        is BuddyCommand.AddBill -> "bill.add"
        is BuddyCommand.AddDebt -> "debt.add"
        is BuddyCommand.CreateGoal -> "goal.create"
        is BuddyCommand.CreateReminder -> "reminder.create"
        BuddyCommand.CancelAllReminders -> "reminder.cancel"
        is BuddyCommand.ApplyPlan -> "plan.apply"
        is BuddyCommand.MarkBillPaid -> "bill.pay"
        is BuddyCommand.EditBillDate -> "bill.move_date"
        is BuddyCommand.EditBill -> "bill.edit"
        is BuddyCommand.RecordDebtPayment -> "debt.pay"
    }

    private fun applyPreference(
        viewModel: FinanceViewModel,
        command: BuddyCommand.UpdatePreference,
        lang: AppLanguage
    ): String {
        when (command.key) {
            PrefKey.THEME -> {
                val mode = runCatching { AppTheme.valueOf(command.value) }.getOrNull() ?: AppTheme.DARK
                viewModel.setThemeMode(mode)
                check(viewModel.themeMode.value == mode) { "theme did not apply" }
                return BuddyStrings.themeOn(command.value, lang)
            }
            PrefKey.HIDE_BALANCES -> {
                val hidden = command.value == "true"
                viewModel.setHideBalances(hidden)
                check(viewModel.hideBalances.value == hidden) { "preference did not apply" }
                return BuddyStrings.balancesHidden(hidden, lang)
            }
            PrefKey.LANGUAGE -> {
                val newLang = runCatching { AppLanguage.valueOf(command.value) }.getOrNull() ?: AppLanguage.MIXED
                viewModel.setLanguage(newLang)
                check(viewModel.currentLanguage.value == newLang) { "language did not apply" }
                return BuddyStrings.languageSet(command.value, lang)
            }
        }
    }

    private fun addTransaction(
        viewModel: FinanceViewModel,
        command: BuddyCommand.AddTransaction,
        lang: AppLanguage
    ): String {
        require(command.amount > 0) { "amount must be positive" }
        viewModel.addManualTransaction(
            amount = command.amount,
            type = if (command.income) TransactionType.INCOME else TransactionType.EXPENSE,
            category = command.category,
            merchant = command.merchant ?: command.category,
            method = PaymentMethod.MPESA
        )
        val kind = if (command.income) "income" else "expense"
        val who = command.merchant?.let { " ($it)" }.orEmpty()
        return BuddyStrings.txnAdded(
            MoneyFormatter.compact(Money.of(command.amount)), kind, who, command.category, lang
        )
    }

    private fun reclassify(
        viewModel: FinanceViewModel,
        command: BuddyCommand.ReclassifyTransaction,
        lang: AppLanguage
    ): String {
        require(command.txIds.isNotEmpty()) { "no transactions selected" }
        val txs = viewModel.allTransactions.value
        var done = 0
        command.txIds.forEach { id ->
            val tx = txs.firstOrNull { it.id == id } ?: return@forEach
            viewModel.reclassifyWithUndo(tx, command.category)
            done++
        }
        check(done > 0) { "those rows already cleared — the ledger moved on" }
        return BuddyStrings.reclassified(done, command.category, lang)
    }

    private fun delete(
        viewModel: FinanceViewModel,
        command: BuddyCommand.DeleteTransaction,
        lang: AppLanguage
    ): String {
        require(command.txIds.isNotEmpty()) { "no transactions selected" }
        val txs = viewModel.allTransactions.value
        var done = 0
        command.txIds.forEach { id ->
            val tx = txs.firstOrNull { it.id == id } ?: return@forEach
            viewModel.deleteTransactionWithUndo(tx)
            done++
        }
        check(done > 0) { "those rows already cleared — the ledger moved on" }
        return BuddyStrings.deleted(done, lang)
    }

    private fun createBudget(
        viewModel: FinanceViewModel,
        command: BuddyCommand.CreateBudget,
        lang: AppLanguage
    ): String {
        require(command.amount > 0) { "amount must be positive" }
        viewModel.upsertBudget(
            command.category, command.amount,
            com.pesaflow.app.data.models.BudgetType.MONTHLY
        )
        return BuddyStrings.budgetSet(
            command.category, MoneyFormatter.compact(Money.of(command.amount)), lang
        )
    }

    private fun addBill(
        viewModel: FinanceViewModel,
        command: BuddyCommand.AddBill,
        lang: AppLanguage
    ): String {
        require(command.amount > 0) { "amount must be positive" }
        val due = System.currentTimeMillis() + command.dueDays * 24L * 60 * 60 * 1000
        viewModel.addBill(command.name, command.amount, due, "Bills", command.frequency)
        return BuddyStrings.billAdded(
            command.name, MoneyFormatter.compact(Money.of(command.amount)), command.dueDays, lang
        )
    }

    private fun addDebt(
        viewModel: FinanceViewModel,
        command: BuddyCommand.AddDebt,
        lang: AppLanguage
    ): String {
        require(command.amount > 0) { "amount must be positive" }
        val due = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
        viewModel.addDebt(
            command.person, command.amount, due, "",
            if (command.iOwe) "I_OWE" else "THEY_OWE"
        )
        return BuddyStrings.debtRecorded(
            command.iOwe, command.person, MoneyFormatter.compact(Money.of(command.amount)), lang
        )
    }

    private fun createGoal(
        viewModel: FinanceViewModel,
        command: BuddyCommand.CreateGoal,
        lang: AppLanguage
    ): String {
        require(command.amount > 0) { "amount must be positive" }
        viewModel.addSavingsGoal(command.title, command.amount, 90)
        return BuddyStrings.goalCreated(
            command.title, MoneyFormatter.compact(Money.of(command.amount)), lang
        )
    }

    private fun addIncomeSource(
        viewModel: FinanceViewModel,
        command: BuddyCommand.AddIncomeSource,
        lang: AppLanguage
    ): String {
        require(command.amount > 0) { "amount must be positive" }
        viewModel.addIncomeSource(
            com.pesaflow.app.data.income.IncomeSource(
                kind = command.kind,
                label = command.label,
                expectedAmount = command.amount,
                frequency = command.frequency,
                dayOfMonth = command.dayOfMonth,
                useInBudget = true
            )
        )
        return BuddyStrings.incomeTracked(
            command.label, MoneyFormatter.compact(Money.of(command.amount)), lang
        )
    }

    private fun appContext(viewModel: FinanceViewModel): android.content.Context =
        viewModel.getApplication<android.app.Application>().applicationContext

    private fun createReminder(
        viewModel: FinanceViewModel,
        command: BuddyCommand.CreateReminder,
        lang: AppLanguage
    ): String {
        require(command.title.isNotBlank()) { "reminder needs a title" }
        val ctx = appContext(viewModel)
        com.pesaflow.app.data.notifications.BuddyReminderScheduler.schedule(
            ctx, command.kind, command.title, command.body, command.scheduleLabel
        )
        val permNote = !com.pesaflow.app.data.notifications.BuddyReminderScheduler.notificationsEnabled(ctx)
        return BuddyStrings.reminderSet(command.title, command.scheduleLabel, permNote, lang)
    }

    private fun cancelAllReminders(viewModel: FinanceViewModel, lang: AppLanguage): String {
        val n = com.pesaflow.app.data.notifications.BuddyReminderScheduler.cancelAll(appContext(viewModel))
        return BuddyStrings.remindersCancelled(n, lang)
    }

    private fun applyPlan(
        viewModel: FinanceViewModel,
        command: BuddyCommand.ApplyPlan,
        lang: AppLanguage
    ): String {
        require(command.budgetTotal > 0) { "plan has no spendable envelope to set" }
        viewModel.upsertBudget(
            "ALL", command.budgetTotal,
            com.pesaflow.app.data.models.BudgetType.MONTHLY
        )
        val ctx = appContext(viewModel)
        com.pesaflow.app.data.notifications.BuddyReminderScheduler.schedule(
            ctx,
            ReminderKind.Daily(20, 0),
            "Daily spend check",
            "Keep today under ${MoneyFormatter.compact(Money.of(command.daily))} to hold the ${command.horizonDays}-day plan.",
            "daily 20:00"
        )
        return BuddyStrings.planApplied(MoneyFormatter.compact(Money.of(command.budgetTotal)), lang)
    }

    private fun markBillPaid(
        viewModel: FinanceViewModel,
        command: BuddyCommand.MarkBillPaid,
        lang: AppLanguage
    ): String {
        val bill = viewModel.bills.value.firstOrNull { it.id == command.billId }
            ?: throw IllegalStateException("that bill already cleared — the queue moved on")
        viewModel.markBillPaid(bill.id)
        return BuddyStrings.billPaid(bill.name, MoneyFormatter.compact(Money.of(bill.amount)), lang)
    }

    private fun editBillDate(
        viewModel: FinanceViewModel,
        command: BuddyCommand.EditBillDate,
        lang: AppLanguage
    ): String {
        require(command.dueTimestamp > 0) { "no due date given" }
        val bill = viewModel.bills.value.firstOrNull { it.id == command.billId }
            ?: throw IllegalStateException("that bill already cleared — the queue moved on")
        viewModel.updateBillDueDate(bill, command.dueTimestamp)
        val day = java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault())
            .format(java.util.Date(command.dueTimestamp))
        return BuddyStrings.billMoved(bill.name, day, lang)
    }

    private fun editBill(
        viewModel: FinanceViewModel,
        command: BuddyCommand.EditBill,
        lang: AppLanguage
    ): String {
        require(command.amount > 0) { "amount must be positive" }
        val bill = viewModel.bills.value.firstOrNull { it.id == command.billId }
            ?: throw IllegalStateException("that bill already cleared — the queue moved on")
        viewModel.updateBillDetails(
            bill, bill.name, command.amount, bill.category,
            bill.frequency, bill.paybill, bill.paidBy
        )
        return BuddyStrings.billEdited(
            bill.name, MoneyFormatter.compact(Money.of(command.amount)), lang
        )
    }

    private fun recordDebtPayment(
        viewModel: FinanceViewModel,
        command: BuddyCommand.RecordDebtPayment,
        lang: AppLanguage
    ): String {
        require(command.amount > 0) { "amount must be positive" }
        val debt = viewModel.debts.value.firstOrNull { it.id == command.debtId }
            ?: throw IllegalStateException("that debt already cleared — the ledger moved on")
        viewModel.settleDebtPartial(debt, command.amount, PaymentMethod.MPESA)
        return BuddyStrings.debtPaid(
            debt.person, MoneyFormatter.compact(Money.of(command.amount)), lang
        )
    }
}
