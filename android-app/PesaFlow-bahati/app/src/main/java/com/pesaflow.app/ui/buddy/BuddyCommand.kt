package com.pesaflow.app.ui.buddy

// Strongly typed command objects: one per supported mutation. The chat layer
// builds them, the user confirms them on a card, and the executor runs them
// through the canonical domain methods — never raw strings, never direct
// Room writes. Pure data, unit-tested.
sealed interface BuddyCommand {

    // Low-risk display preference. Executes immediately, no confirmation.
    data class UpdatePreference(val key: PrefKey, val value: String) : BuddyCommand

    // Financial mutation. ALWAYS renders a confirm card first.
    data class AddTransaction(
        val amount: Double,
        val category: String,
        val merchant: String?,
        val income: Boolean
    ) : BuddyCommand

    // One or many ledger rows filed under a new category (canonical
    // replaceTransaction: row rewritten, category memory learns).
    data class ReclassifyTransaction(val txIds: List<String>, val category: String) : BuddyCommand

    // Undoable ledger delete (deleteTransactionWithUndo — "Undo lives on Home").
    data class DeleteTransaction(val txIds: List<String>) : BuddyCommand

    // Monthly envelope (canonical upsertBudget).
    data class CreateBudget(val category: String, val amount: Double) : BuddyCommand

    // Bill with concrete due date (canonical addBill).
    data class AddBill(val name: String, val amount: Double, val dueDays: Int, val frequency: String) : BuddyCommand

    // Debt record (canonical addDebt).
    data class AddDebt(val person: String, val amount: Double, val iOwe: Boolean) : BuddyCommand

    // Savings goal, 90-day default like the Goals screen (canonical addSavingsGoal).
    data class CreateGoal(val title: String, val amount: Double) : BuddyCommand

    // Expected payday (canonical addIncomeSource, counted in planning).
    data class AddIncomeSource(
        val label: String,
        val kind: String,
        val amount: Double,
        val frequency: String,
        val dayOfMonth: Int
    ) : BuddyCommand

    // User reminder via BuddyReminderScheduler (WorkManager + prefs metadata).
    data class CreateReminder(
        val title: String,
        val body: String,
        val kind: ReminderKind,
        val scheduleLabel: String
    ) : BuddyCommand

    // Cancels every Buddy-created reminder. Confirm card first.
    data object CancelAllReminders : BuddyCommand

    // Applies a stored survival plan: sets the monthly ALL envelope to the
    // plan total and schedules a daily spend-check reminder. One card, both
    // steps itemized, both through canonical methods.
    data class ApplyPlan(val horizonDays: Int, val daily: Double, val budgetTotal: Double) : BuddyCommand

    // Canonical payBill: creates the expense leg and marks the bill paid.
    data class MarkBillPaid(val billId: String) : BuddyCommand

    // Canonical bill edit (amount only via chat; dates stay on the screen).
    data class EditBill(val billId: String, val amount: Double) : BuddyCommand

    // Canonical bill due-date move (canonical updateBillDueDate).
    data class EditBillDate(val billId: String, val dueTimestamp: Long) : BuddyCommand

    // Canonical partial/full debt settlement.
    data class RecordDebtPayment(val debtId: String, val amount: Double) : BuddyCommand
}

// A computed survival plan, held in memory until applied or replaced.
data class BuiltPlan(val horizonDays: Int, val daily: Double, val spendable: Double)

enum class PrefKey { THEME, HIDE_BALANCES, LANGUAGE }

// When a user reminder fires. Pure data — the scheduler maps these onto
// WorkManager. Conditional triggers are deliberately absent: the engine
// cannot do them, so the router must decline instead of pretending.
sealed interface ReminderKind {
    data class Once(val delayMs: Long) : ReminderKind
    data class Daily(val hour: Int, val minute: Int) : ReminderKind
    data class Weekly(val weekday: Int, val hour: Int, val minute: Int) : ReminderKind
    data class MonthlyDay(val day: Int) : ReminderKind
}

// What the executor reports back. Success is claimed only after the domain
// call returns; failures carry the real reason. Feeds the audit log.
data class BuddyActionResult(
    val success: Boolean,
    val message: String,
    val commandId: String
)
