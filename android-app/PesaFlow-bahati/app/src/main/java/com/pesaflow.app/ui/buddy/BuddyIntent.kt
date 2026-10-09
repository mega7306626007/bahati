package com.pesaflow.app.ui.buddy

// Phase-1 typed intents: read-only queries plus navigation. Mutations arrive
// in later phases as new intents — this sealed interface grows, never breaks.
sealed interface BuddyIntent {
    data object QueryBalance : BuddyIntent
    data object QueryFlexible : BuddyIntent
    data object QuerySafeSpend : BuddyIntent
    data object QueryBills : BuddyIntent
    data object QueryDebts : BuddyIntent
    data object QueryGoals : BuddyIntent
    data class OpenScreen(val route: String) : BuddyIntent
    data object Help : BuddyIntent
    // What-if simulations: read-only, never touch the ledger. Null params
    // ask for the missing input instead of guessing.
    // Read-only survival plan over N days: math first, apply later (and
    // only with confirmation). Never executes anything by itself.
    data class BuildPlan(val horizonDays: Int) : BuddyIntent
    // Applies the stored survival plan: daily reminder + monthly envelope,
    // itemized on one confirm card. No stored plan → asks for one first.
    data object ApplyPlan : BuddyIntent
    // Bill/debt actions resolve names against open bills/debts; ambiguity
    // asks with numbered choices. All confirm-card first.
    data class MarkBillPaid(val billName: String?, val amount: Double? = null) : BuddyIntent
    data class EditBill(val billName: String?, val amount: Double?) : BuddyIntent
    data class EditBillDate(val billName: String?, val dueTimestamp: Long?) : BuddyIntent
    data class RecordDebtPayment(val person: String?, val amount: Double?) : BuddyIntent
    data class ResolvedDebtPay(val debtId: String, val amount: Double) : BuddyIntent
    // Income: earned-this-month query plus expected-payday source drafts.
    // A payday draft ALWAYS confirm-cards; "record salary received" stays a
    // plain transaction (no landing words → AddTransaction owns it).
    data object QueryIncome : BuddyIntent
    data class AddIncomeSource(
        val label: String?,
        val kind: String,
        val amount: Double?,
        val frequency: String,
        val dayOfMonth: Int
    ) : BuddyIntent
    // Three spending scenarios over a horizon: conservative/normal/
    // aggressive burn, each with end-flexible and runway. Read-only.
    data class CompareSpendingPlans(val horizonDays: Int) : BuddyIntent
    // Semester outlook from the canonical runway engine. Null runway (no
    // dates set) asks the user to set them instead of inventing.
    data object SemesterOutlook : BuddyIntent
    // Command-center orchestration: read-only briefings and comparisons
    // composed from canonical state. Never executes, never predicts.
    data object MorningBriefing : BuddyIntent
    data object WhatChanged : BuddyIntent
    data object ComparePeriods : BuddyIntent
    // Creation drafts: null fields ask instead of acting; complete drafts
    // ALWAYS confirm-card first.
    data class CreateBudget(val category: String?, val amount: Double?) : BuddyIntent
    data class AddBill(val name: String?, val amount: Double?, val dueDays: Int, val frequency: String) : BuddyIntent
    data class AddDebt(val person: String?, val amount: Double?, val iOwe: Boolean) : BuddyIntent
    data class CreateGoal(val title: String?, val amount: Double?) : BuddyIntent
    // Reminder drafts: conditional ones (when-budget-tight) are declined
    // honestly — the engine only does time-based reminders.
    data class CreateReminder(
        val title: String?,
        val kind: ReminderKind?,
        val scheduleLabel: String?,
        val conditional: Boolean = false
    ) : BuddyIntent
    data object ListReminders : BuddyIntent
    data object CancelReminders : BuddyIntent
    data class SimulatePurchase(val amount: Double?) : BuddyIntent
    data class SimulateIncomeLate(val days: Int?) : BuddyIntent
    data class SimulateRentChange(val newRent: Double?, val delta: Boolean) : BuddyIntent
    // Transaction control. Find is read-only. Reclassify/Delete carry
    // merchant+amount hints; the resolver turns them into txIds (asking with
    // numbered choices on ambiguity). Resolved* come from candidate replies
    // ("2", "all"). Mutations ALWAYS confirm-card first.
    data class FindTransactions(val filter: TxnFilter) : BuddyIntent
    data class Reclassify(val category: String, val merchant: String?, val amount: Double?) : BuddyIntent
    data class Delete(val merchant: String?, val amount: Double?) : BuddyIntent
    data class ResolvedReclassify(val txIds: List<String>, val category: String) : BuddyIntent
    data class ResolvedDelete(val txIds: List<String>) : BuddyIntent
    // Phase 2: low-risk display control, executes immediately.
    data class UpdatePreference(val key: PrefKey, val value: String) : BuddyIntent
    // Phase 3: financial mutation, ALWAYS confirm-card first. Amount is
    // nullable so missing entities route to smart clarification, never action.
    data class AddTransaction(
        val amount: Double?,
        val category: String?,
        val merchant: String?,
        val income: Boolean
    ) : BuddyIntent
    data object Unknown : BuddyIntent
}
