package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.Bill
import com.pesaflow.app.data.models.Debt
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.ui.buddy.BuiltPlan
import com.pesaflow.app.ui.dashboard.BuddyMemory
import com.pesaflow.app.ui.NavRoutes
import com.pesaflow.app.ui.buddy.BuddyAuditEntry
import com.pesaflow.app.ui.buddy.BuddyTxnQuery
import com.pesaflow.app.ui.buddy.TxnFilter
import com.pesaflow.app.ui.buddy.BuddyAuditLog
import com.pesaflow.app.ui.buddy.BuddyCapabilities
import com.pesaflow.app.ui.buddy.BuddyCommand
import com.pesaflow.app.ui.buddy.BuddyEntityExtractor
import com.pesaflow.app.ui.buddy.BuddyIntent
import com.pesaflow.app.ui.buddy.BuddyIntentRouter
import com.pesaflow.app.ui.buddy.BuddyNavigation
import com.pesaflow.app.ui.buddy.BuddyOutcome
import com.pesaflow.app.ui.buddy.BuddyPhaseOne
import com.pesaflow.app.ui.buddy.BuddyReadState
import com.pesaflow.app.ui.buddy.BuddyResponseComposer
import com.pesaflow.app.ui.buddy.BuddyValidation
import com.pesaflow.app.ui.buddy.BuddyValidator
import com.pesaflow.app.ui.buddy.PrefKey
import org.junit.Assert.*
import org.junit.Test

class BuddyPhaseOneTest {

    private fun state() = BuddyReadState(
        liquid = 3200.0,
        flexible = 3200.0,
        safeToday = 350.0,
        safeWeek = 2100.0,
        openBillCount = 2,
        openBillTotal = 8000.0,
        openDebtCount = 0,
        openDebtTotal = 0.0,
        goalCount = 1,
        dataQuality = "SPARSE"
    )

    @Test
    fun `balance question routes to balance query`() {
        val routed = BuddyIntentRouter.route("what is my balance?")
        assertEquals(BuddyIntent.QueryBalance, routed.intent)
    }

    @Test
    fun `sheng flexible question routes to flexible query`() {
        val routed = BuddyIntentRouter.route("naweza spend ngapi?")
        assertEquals(BuddyIntent.QueryFlexible, routed.intent)
    }

    @Test
    fun `spend today routes to safe spend`() {
        val routed = BuddyIntentRouter.route("how much can i spend today?")
        assertEquals(BuddyIntent.QuerySafeSpend, routed.intent)
    }

    @Test
    fun `open budgets navigates`() {
        val routed = BuddyIntentRouter.route("open budgets")
        assertEquals(BuddyIntent.OpenScreen(NavRoutes.BUDGETS), routed.intent)
    }

    @Test
    fun `show bills answers while where are bills navigates`() {
        assertEquals(BuddyIntent.QueryBills, BuddyIntentRouter.route("show my bills").intent)
        assertEquals(
            BuddyIntent.OpenScreen(NavRoutes.BILLS),
            BuddyIntentRouter.route("where are my bills").intent
        )
    }

    @Test
    fun `unwired mutation stays unknown for later phases`() {
        assertEquals(BuddyIntent.Unknown, BuddyIntentRouter.route("rename that transaction").intent)
    }

    @Test
    fun `entities extract amount day and screen`() {
        assertEquals(500.0, BuddyEntityExtractor.extract("Afford 500?").amount)
        assertEquals("yesterday", BuddyEntityExtractor.extract("and yesterday").day)
        assertEquals(NavRoutes.SETTINGS, BuddyEntityExtractor.extract("go to settings").screen)
        assertNull(BuddyNavigation.screenFor("budgets left?"))
    }

    @Test
    fun `read and navigation pass validation without confirmation`() {
        assertEquals(BuddyValidation.Allowed, BuddyValidator.validate(BuddyIntent.QueryBalance))
        assertEquals(
            BuddyValidation.Allowed,
            BuddyValidator.validate(BuddyIntent.OpenScreen(NavRoutes.BUDGETS))
        )
        assertTrue(BuddyValidator.validate(BuddyIntent.Unknown) is BuddyValidation.Unsupported)
    }

    @Test
    fun `composer answers carry canonical figures and provenance`() {
        val balance = BuddyResponseComposer.compose(BuddyIntent.QueryBalance, state()) as BuddyOutcome.Answer
        assertTrue(balance.text.contains("KSh 3.2k"))
        assertTrue(balance.text.contains("Recorded"))
        val flexible = BuddyResponseComposer.compose(BuddyIntent.QueryFlexible, state()) as BuddyOutcome.Answer
        assertTrue(flexible.text.contains("calculated"))
        assertTrue(flexible.text.contains("rough estimate"))
        val bills = BuddyResponseComposer.compose(BuddyIntent.QueryBills, state()) as BuddyOutcome.Answer
        assertTrue(bills.text.contains("2 open bills"))
    }

    @Test
    fun `registry only advertises wired capabilities`() {
        val described = BuddyCapabilities.describe()
        assertTrue(described.contains("check balance"))
        assertTrue(described.contains("open a screen"))
        assertEquals(BuddyIntent.OpenScreen(NavRoutes.BUDGETS), BuddyIntentRouter.route("open budgets").intent)
    }

    @Test
    fun `hide balances routes to immediate preference command`() {
        val routed = BuddyIntentRouter.route("hide my balances")
        assertEquals(BuddyIntent.UpdatePreference(PrefKey.HIDE_BALANCES, "true"), routed.intent)
        val outcome = BuddyPhaseOne.handle("hide my balances", state())
        assertEquals(
            BuddyOutcome.ExecuteNow(BuddyCommand.UpdatePreference(PrefKey.HIDE_BALANCES, "true")),
            outcome
        )
    }

    @Test
    fun `dark mode routes to theme command`() {
        assertEquals(
            BuddyIntent.UpdatePreference(PrefKey.THEME, "DARK"),
            BuddyIntentRouter.route("turn on dark mode").intent
        )
    }

    @Test
    fun `add expense drafts a confirmable command`() {
        val routed = BuddyIntentRouter.route("add KSh 500 transport")
        val intent = routed.intent as BuddyIntent.AddTransaction
        assertEquals(500.0, intent.amount)
        assertEquals("Transport", intent.category)
        assertFalse(intent.income)
        val outcome = BuddyPhaseOne.handle("add KSh 500 transport", state())
        assertTrue(outcome is BuddyOutcome.ProposeCommand)
        assertTrue((outcome as BuddyOutcome.ProposeCommand).summary.contains("KSh 500"))
    }

    @Test
    fun `missing amount asks instead of acting`() {
        val routed = BuddyIntentRouter.route("add transport expense")
        assertTrue(routed.intent is BuddyIntent.AddTransaction)
        assertNull((routed.intent as BuddyIntent.AddTransaction).amount)
        val outcome = BuddyPhaseOne.handle("add transport expense", state())
        assertTrue(outcome is BuddyOutcome.Ask)
    }

    @Test
    fun `bill and goal phrases draft typed commands`() {
        val bill = BuddyIntentRouter.route("add bill rent 8000").intent
        assertTrue(bill is BuddyIntent.AddBill)
        assertEquals(8000.0, (bill as BuddyIntent.AddBill).amount)
        val goal = BuddyIntentRouter.route("add goal laptop 50000").intent
        assertTrue(goal is BuddyIntent.CreateGoal)
    }

    @Test
    fun `mutations require confirmation while prefs do not`() {
        assertTrue(
            BuddyValidator.validate(BuddyIntent.AddTransaction(500.0, "Transport", null, false))
                is BuddyValidation.NeedsConfirmation
        )
        assertEquals(
            BuddyValidation.Allowed,
            BuddyValidator.validate(BuddyIntent.UpdatePreference(PrefKey.THEME, "DARK"))
        )
    }

    @Test
    fun `audit log records entries newest last`() {
        BuddyAuditLog.clear()
        BuddyAuditLog.record(BuddyAuditEntry(1L, "txn.add", "amount=500", true, true, "ok"))
        BuddyAuditLog.record(BuddyAuditEntry(2L, "txn.add", "amount=0", true, false, "bad"))
        val recent = BuddyAuditLog.recent()
        assertEquals(2, recent.size)
        assertFalse(recent.last().success)
        BuddyAuditLog.clear()
    }

    private fun ledgerTx(
        id: String,
        merchant: String,
        amount: Double,
        category: String,
        ts: Long,
        type: TransactionType = TransactionType.EXPENSE
    ) = Transaction(
        id = id, amount = amount, type = type, category = category,
        dateTimestamp = ts, merchant = merchant, source = TransactionSource.MPESA_SMS
    )

    private fun ledger() = listOf(
        ledgerTx("t1", "NAIVAS MOI AVENUE", 850.0, "Shopping", 1000L),
        ledgerTx("t2", "BRIAN OTIS", 500.0, "Food", 2000L),
        ledgerTx("t3", "BRIAN MWANGI", 1200.0, "Transport", 3000L),
        ledgerTx("t4", "MATATU SACCO", 150.0, "Transport", 4000L)
    )

    private fun merchants() = ledger().map { it.merchant }.distinct()

    @Test
    fun `find by category summarizes count and total`() {
        val outcome = BuddyPhaseOne.handle("show my transport spending", state(), ledger(), merchants())
        assertTrue(outcome is BuddyOutcome.Answer)
        val text = (outcome as BuddyOutcome.Answer).text
        assertTrue(text.contains("2 transactions"))
        assertTrue(text.contains("KSh 1.4k"))
    }

    @Test
    fun `transactions with person filters by merchant`() {
        val outcome = BuddyPhaseOne.handle("show transactions with Brian", state(), ledger(), merchants())
        assertTrue(outcome is BuddyOutcome.Answer)
        assertTrue((outcome as BuddyOutcome.Answer).text.contains("2 transactions"))
    }

    @Test
    fun `query filters combine merchant category and amount`() {
        val matches = BuddyTxnQuery.filter(
            ledger(),
            TxnFilter(merchant = "Brian", category = "Food", amount = 500.0)
        )
        assertEquals(listOf("t2"), matches.map { it.id })
        assertTrue(BuddyTxnQuery.filter(ledger(), TxnFilter(merchant = "Nobody")).isEmpty())
    }

    @Test
    fun `reclassify single match proposes a confirmable command`() {
        val outcome = BuddyPhaseOne.handle("change Naivas to Food", state(), ledger(), merchants())
        assertTrue(outcome is BuddyOutcome.ProposeCommand)
        val cmd = (outcome as BuddyOutcome.ProposeCommand).command
        assertTrue(cmd is com.pesaflow.app.ui.buddy.BuddyCommand.ReclassifyTransaction)
        assertEquals(listOf("t1"), (cmd as com.pesaflow.app.ui.buddy.BuddyCommand.ReclassifyTransaction).txIds)
    }

    @Test
    fun `reclassify ambiguity asks numbered then resolves pick and all`() {
        val ask = BuddyPhaseOne.handle("mark Brian as Upkeep", state(), ledger(), merchants())
        assertTrue(ask is BuddyOutcome.Ask)
        val text = (ask as BuddyOutcome.Ask).text
        assertTrue(text.contains("1.") && text.contains("2."))
        val pick = BuddyPhaseOne.handle("2", state(), ledger(), merchants())
        assertTrue(pick is BuddyOutcome.ProposeCommand)
        val pickCmd = (pick as BuddyOutcome.ProposeCommand).command
            as com.pesaflow.app.ui.buddy.BuddyCommand.ReclassifyTransaction
        assertEquals(listOf("t2"), pickCmd.txIds)
        // Fresh ambiguity, then "all" bulk-resolves.
        BuddyPhaseOne.handle("mark Brian as Upkeep", state(), ledger(), merchants())
        val all = BuddyPhaseOne.handle("all", state(), ledger(), merchants())
        assertTrue(all is BuddyOutcome.ProposeCommand)
        val allCmd = (all as BuddyOutcome.ProposeCommand).command
            as com.pesaflow.app.ui.buddy.BuddyCommand.ReclassifyTransaction
        assertEquals(2, allCmd.txIds.size)
    }

    @Test
    fun `delete proposes with history warning`() {
        val outcome = BuddyPhaseOne.handle("delete the Naivas transaction", state(), ledger(), merchants())
        assertTrue(outcome is BuddyOutcome.ProposeCommand)
        val proposed = outcome as BuddyOutcome.ProposeCommand
        assertTrue(proposed.command is com.pesaflow.app.ui.buddy.BuddyCommand.DeleteTransaction)
        assertTrue(proposed.summary.contains("financial history"))
    }

    @Test
    fun `destructive wipe phrases never route to delete`() {
        assertEquals(BuddyIntent.Unknown, BuddyIntentRouter.route("delete all data").intent)
    }

    @Test
    fun `find is free while reclassify and delete need confirmation`() {
        assertEquals(
            BuddyValidation.Allowed,
            BuddyValidator.validate(BuddyIntent.FindTransactions(TxnFilter(category = "Food")))
        )
        assertTrue(
            BuddyValidator.validate(BuddyIntent.Reclassify("Food", "Naivas", null))
                is BuddyValidation.NeedsConfirmation
        )
        assertTrue(
            BuddyValidator.validate(BuddyIntent.Delete("Naivas", null))
                is BuddyValidation.NeedsConfirmation
        )
    }

    private fun simState() = state().copy(dailyBurn = 200.0, currentRent = 5500.0)

    @Test
    fun `what-if spend routes with amount`() {
        val routed = BuddyIntentRouter.route("what happens if I spend 2000 today?")
        assertEquals(BuddyIntent.SimulatePurchase(2000.0), routed.intent)
    }

    @Test
    fun `late income routes with day count`() {
        val routed = BuddyIntentRouter.route("what if my allowance is 7 days late?")
        assertEquals(BuddyIntent.SimulateIncomeLate(7), routed.intent)
        assertEquals(14, com.pesaflow.app.ui.buddy.BuddyEntityExtractor.extractDayCount("2 weeks late"))
    }

    @Test
    fun `rent change routes absolute and delta`() {
        assertEquals(
            BuddyIntent.SimulateRentChange(7000.0, false),
            BuddyIntentRouter.route("what if rent was 7000?").intent
        )
        assertEquals(
            BuddyIntent.SimulateRentChange(1000.0, true),
            BuddyIntentRouter.route("what if rent increases by 1000?").intent
        )
    }

    @Test
    fun `simulation answers show math assumptions and simulated tag`() {
        val purchase = BuddyPhaseOne.handle("what if I spend 2000?", simState())
        assertTrue(purchase is BuddyOutcome.Answer)
        val ptext = (purchase as BuddyOutcome.Answer).text
        assertTrue(ptext.contains("KSh 3.2k → KSh 1.2k"))
        assertTrue(ptext.contains("SIMULATED"))
        val late = BuddyPhaseOne.handle("what if income is 7 days late?", simState())
        assertTrue((late as BuddyOutcome.Answer).text.contains("KSh 1.8k left"))
        val rent = BuddyPhaseOne.handle("what if rent was 7000?", simState())
        assertTrue((rent as BuddyOutcome.Answer).text.contains("KSh 1.5k/mo extra"))
    }

    @Test
    fun `overspend simulation warns about commitments`() {
        val outcome = BuddyPhaseOne.handle("what if I spend 5000?", simState())
        assertTrue((outcome as BuddyOutcome.Answer).text.contains("exceeds flexible money"))
    }

    @Test
    fun `simulation without inputs asks minimally`() {
        assertTrue(BuddyPhaseOne.handle("what if I spend?", simState()) is BuddyOutcome.Ask)
        assertTrue(BuddyPhaseOne.handle("what if income is late?", simState()) is BuddyOutcome.Ask)
        assertTrue(BuddyPhaseOne.handle("what if rent changes?", simState()) is BuddyOutcome.Ask)
    }

    @Test
    fun `simulations need no confirmation`() {
        assertEquals(
            BuddyValidation.Allowed,
            BuddyValidator.validate(BuddyIntent.SimulatePurchase(2000.0))
        )
    }

    @Test
    fun `survival requests route to plan with horizon`() {
        assertEquals(BuddyIntent.BuildPlan(14), BuddyIntentRouter.route("help me survive the next 14 days").intent)
        assertEquals(BuddyIntent.BuildPlan(7), BuddyIntentRouter.route("plan my next seven days").intent)
        assertEquals(BuddyIntent.BuildPlan(14), BuddyIntentRouter.route("prepare me for rent").intent)
    }

    @Test
    fun `meal planning stays on its legacy screen`() {
        assertEquals(BuddyIntent.Unknown, BuddyIntentRouter.route("plan my meals until Friday").intent)
    }

    @Test
    fun `export my data navigates to export`() {
        assertEquals(
            BuddyIntent.OpenScreen(NavRoutes.EXPORT),
            BuddyIntentRouter.route("export my data").intent
        )
    }

    @Test
    fun `plan answer shows daily spendable and pace verdict`() {
        val rich = simState().copy(flexible = 5000.0, openBillTotal = 1000.0)
        val outcome = BuddyPhaseOne.handle("help me survive the next 14 days", rich)
        assertTrue(outcome is BuddyOutcome.Answer)
        val text = (outcome as BuddyOutcome.Answer).text
        assertTrue(text.contains("14-day plan"))
        assertTrue(text.contains("KSh 4k spendable"))
        assertTrue(text.contains("Safe daily spend"))
        assertTrue(text.contains("I changed nothing"))
    }

    @Test
    fun `plan answer protects bills first when they exceed flexible`() {
        val outcome = BuddyPhaseOne.handle("help me survive the next 14 days", simState())
        assertTrue((outcome as BuddyOutcome.Answer).text.contains("Bills need protecting first"))
    }

    @Test
    fun `plan needs no confirmation`() {
        assertEquals(BuddyValidation.Allowed, BuddyValidator.validate(BuddyIntent.BuildPlan(14)))
    }

    @Test
    fun `set food budget drafts a confirmable command`() {
        val outcome = BuddyPhaseOne.handle("set food budget 6000", state())
        assertTrue(outcome is BuddyOutcome.ProposeCommand)
        val cmd = (outcome as BuddyOutcome.ProposeCommand).command
        assertEquals(
            com.pesaflow.app.ui.buddy.BuddyCommand.CreateBudget("Food", 6000.0),
            cmd
        )
    }

    @Test
    fun `budget without amount asks minimally`() {
        val outcome = BuddyPhaseOne.handle("set a food budget", state())
        assertTrue(outcome is BuddyOutcome.Ask)
    }

    @Test
    fun `add bill drafts with default weekly due`() {
        val outcome = BuddyPhaseOne.handle("add bill rent 8000", state())
        assertTrue(outcome is BuddyOutcome.ProposeCommand)
        val cmd = (outcome as BuddyOutcome.ProposeCommand).command
            as com.pesaflow.app.ui.buddy.BuddyCommand.AddBill
        assertEquals("Rent", cmd.name)
        assertEquals(8000.0, cmd.amount, 0.001)
        assertEquals(7, cmd.dueDays)
    }

    @Test
    fun `monthly bill computes days to the first`() {
        val outcome = BuddyPhaseOne.handle("add bill rent 5500 every first", state())
        val cmd = (outcome as BuddyOutcome.ProposeCommand).command
            as com.pesaflow.app.ui.buddy.BuddyCommand.AddBill
        assertEquals("MONTHLY", cmd.frequency)
        assertTrue(cmd.dueDays in 1..31)
    }

    @Test
    fun `i owe drafts a debt against me`() {
        val outcome = BuddyPhaseOne.handle("i owe Brian 2000", state())
        val cmd = (outcome as BuddyOutcome.ProposeCommand).command
            as com.pesaflow.app.ui.buddy.BuddyCommand.AddDebt
        assertEquals("Brian", cmd.person)
        assertTrue(cmd.iOwe)
    }

    @Test
    fun `save for drafts a goal`() {
        val outcome = BuddyPhaseOne.handle("save for laptop 80000", state())
        val cmd = (outcome as BuddyOutcome.ProposeCommand).command
            as com.pesaflow.app.ui.buddy.BuddyCommand.CreateGoal
        assertEquals("Laptop", cmd.title)
        assertEquals(80000.0, cmd.amount, 0.001)
    }

    @Test
    fun `creation commands require confirmation`() {
        assertTrue(
            BuddyValidator.validate(BuddyIntent.CreateBudget("Food", 6000.0))
                is BuddyValidation.NeedsConfirmation
        )
        assertTrue(
            BuddyValidator.validate(BuddyIntent.CreateGoal("Laptop", 80000.0))
                is BuddyValidation.NeedsConfirmation
        )
    }

    @Test
    fun `remind about rent every first drafts a monthly reminder`() {
        val routed = BuddyIntentRouter.route("remind me about rent every 1st")
        val intent = routed.intent as BuddyIntent.CreateReminder
        assertEquals("Rent", intent.title)
        assertEquals(
            com.pesaflow.app.ui.buddy.ReminderKind.MonthlyDay(1),
            intent.kind
        )
        val outcome = BuddyPhaseOne.handle("remind me about rent every 1st", state())
        assertTrue(outcome is BuddyOutcome.ProposeCommand)
        assertTrue((outcome as BuddyOutcome.ProposeCommand).summary.contains("every 1st"))
    }

    @Test
    fun `conditional reminders decline honestly`() {
        val outcome = BuddyPhaseOne.handle("remind me when my transport budget is getting tight", state())
        assertTrue(outcome is BuddyOutcome.Ask)
        assertTrue((outcome as BuddyOutcome.Ask).text.contains("condition-based"))
    }

    @Test
    fun `reminder without title or time asks minimally`() {
        assertTrue(BuddyPhaseOne.handle("remind me", state()) is BuddyOutcome.Ask)
        assertTrue(BuddyPhaseOne.handle("remind me about rent", state()) is BuddyOutcome.Ask)
    }

    @Test
    fun `empty reminder list says how to create one`() {
        val outcome = BuddyPhaseOne.handle("what reminders do i have", state())
        assertTrue((outcome as BuddyOutcome.Answer).text.contains("No Buddy reminders"))
    }

    @Test
    fun `cancel with nothing set answers instead of proposing`() {
        assertTrue(BuddyPhaseOne.handle("cancel all reminders", state()) is BuddyOutcome.Answer)
    }

    @Test
    fun `reminder creation requires confirmation`() {
        assertTrue(
            BuddyValidator.validate(
                BuddyIntent.CreateReminder("Rent", com.pesaflow.app.ui.buddy.ReminderKind.MonthlyDay(1), "every 1st")
            ) is BuddyValidation.NeedsConfirmation
        )
        assertEquals(BuddyValidation.Allowed, BuddyValidator.validate(BuddyIntent.ListReminders))
    }

    @Test
    fun `bare why from budgets answers with screen math`() {
        val answered = BuddyPhaseOne.handle(
            "why", state(), emptyList(), emptyList(),
            NavRoutes.BUDGETS, "Monthly budget progress uses the monthly ALL envelope."
        )
        assertTrue(answered is BuddyOutcome.Answer)
        assertTrue((answered as BuddyOutcome.Answer).text.contains("monthly ALL envelope"))
    }

    @Test
    fun `bare why without context falls through to legacy`() {
        val outcome = BuddyPhaseOne.handle("why", state(), emptyList(), emptyList(), null, null)
        assertEquals(BuddyOutcome.Unhandled, outcome)
    }

    private fun briefingState() = state().copy(
        pendingCount = 3,
        thisMonthExpense = 9000.0,
        thisMonthTop = "Food",
        lastMonthExpense = 6000.0,
        lastMonthTop = "Transport",
        billsDueSoonCount = 1,
        billsDueSoonTotal = 5500.0
    )

    @Test
    fun `briefing compare and changed route correctly`() {
        assertEquals(BuddyIntent.MorningBriefing, BuddyIntentRouter.route("give me my morning briefing").intent)
        assertEquals(BuddyIntent.WhatChanged, BuddyIntentRouter.route("what changed?").intent)
        assertEquals(BuddyIntent.ComparePeriods, BuddyIntentRouter.route("compare with last month").intent)
    }

    @Test
    fun `briefing leads with pressure and one action`() {
        val outcome = BuddyPhaseOne.handle("give me my morning briefing", briefingState())
        assertTrue(outcome is BuddyOutcome.Answer)
        val text = (outcome as BuddyOutcome.Answer).text
        assertTrue(text.contains("Morning briefing"))
        assertTrue(text.contains("1 bill due within 7 days"))
        assertTrue(text.contains("One action: protect KSh 5.5k"))
    }

    @Test
    fun `changed reports month over month honestly`() {
        val text = (BuddyPhaseOne.handle("what changed?", briefingState()) as BuddyOutcome.Answer).text
        assertTrue(text.contains("KSh 9k this month vs KSh 6k last month (+50%)"))
        assertTrue(text.contains("3 waiting for review"))
    }

    @Test
    fun `compare names top categories`() {
        val text = (BuddyPhaseOne.handle("compare with last month", briefingState()) as BuddyOutcome.Answer).text
        assertTrue(text.contains("Food now vs Transport last month"))
    }

    @Test
    fun `briefing intents need no confirmation`() {
        assertEquals(BuddyValidation.Allowed, BuddyValidator.validate(BuddyIntent.MorningBriefing))
        assertEquals(BuddyValidation.Allowed, BuddyValidator.validate(BuddyIntent.ComparePeriods))
    }

    private fun bills() = listOf(
        Bill(id = "b1", name = "Rent", amount = 5500.0, dueDate = 9_999_999_999_999L, category = "Rent"),
        Bill(id = "b2", name = "Wifi", amount = 1500.0, dueDate = 9_999_999_999_999L, category = "Bills")
    )

    private fun debts() = listOf(
        Debt(id = "d1", person = "Brian", amount = 2000.0, dateBorrowed = 1L, dueDate = 9_999_999_999_999L),
        Debt(id = "d2", person = "Brian", amount = 500.0, dateBorrowed = 2L, dueDate = 9_999_999_999_999L)
    )

    @Test
    fun `mark rent paid proposes the matching bill`() {
        val outcome = BuddyPhaseOne.handle("mark rent paid", state(), emptyList(), emptyList(), null, null, bills(), debts())
        assertTrue(outcome is BuddyOutcome.ProposeCommand)
        val text = (outcome as BuddyOutcome.ProposeCommand).summary
        assertTrue(text.contains("Pay 'Rent' KSh 5.5k"))
    }

    @Test
    fun `change bill amount proposes edit`() {
        val outcome = BuddyPhaseOne.handle("change rent bill to 6000", state(), emptyList(), emptyList(), null, null, bills(), debts())
        val cmd = (outcome as BuddyOutcome.ProposeCommand).command
            as com.pesaflow.app.ui.buddy.BuddyCommand.EditBill
        assertEquals("b1", cmd.billId)
        assertEquals(6000.0, cmd.amount, 0.001)
    }

    @Test
    fun `debt payment ambiguity asks numbered then resolves`() {
        val ask = BuddyPhaseOne.handle("paid Brian 1500", state(), emptyList(), emptyList(), null, null, bills(), debts())
        assertTrue(ask is BuddyOutcome.Ask)
        assertTrue((ask as BuddyOutcome.Ask).text.contains("1."))
        val pick = BuddyPhaseOne.handle("1", state(), emptyList(), emptyList(), null, null, bills(), debts())
        val cmd = (pick as BuddyOutcome.ProposeCommand).command
            as com.pesaflow.app.ui.buddy.BuddyCommand.RecordDebtPayment
        assertEquals("d1", cmd.debtId)
        assertEquals(1500.0, cmd.amount, 0.001)
    }

    @Test
    fun `apply without a plan asks for one first`() {
        BuddyMemory.lastPlan = null
        val outcome = BuddyPhaseOne.handle("apply plan", state())
        assertTrue(outcome is BuddyOutcome.Ask)
        assertTrue((outcome as BuddyOutcome.Ask).text.contains("No plan on the table"))
    }

    @Test
    fun `apply with a stored plan itemizes envelope and reminder`() {
        BuddyMemory.lastPlan = BuiltPlan(14, 285.0, 4000.0)
        val outcome = BuddyPhaseOne.handle("apply plan", state().copy(openBillTotal = 1000.0))
        assertTrue(outcome is BuddyOutcome.ProposeCommand)
        val cmd = (outcome as BuddyOutcome.ProposeCommand).command
            as com.pesaflow.app.ui.buddy.BuddyCommand.ApplyPlan
        assertEquals(5000.0, cmd.budgetTotal, 0.001)
        assertTrue((outcome as BuddyOutcome.ProposeCommand).summary.contains("Daily spend-check reminder"))
        BuddyMemory.lastPlan = null
    }

    @Test
    fun `bill and debt actions require confirmation`() {
        assertTrue(
            BuddyValidator.validate(BuddyIntent.MarkBillPaid("Rent", null)) is BuddyValidation.NeedsConfirmation
        )
        assertTrue(
            BuddyValidator.validate(BuddyIntent.RecordDebtPayment("Brian", 1500.0)) is BuddyValidation.NeedsConfirmation
        )
    }

    @Test
    fun `plan comparison routes with day horizon`() {
        assertEquals(
            BuddyIntent.CompareSpendingPlans(30),
            BuddyIntentRouter.route("compare three spending plans").intent
        )
        assertEquals(
            BuddyIntent.CompareSpendingPlans(14),
            BuddyIntentRouter.route("compare conservative vs aggressive plans over 14 days").intent
        )
        // Plain period compare is untouched.
        assertEquals(BuddyIntent.ComparePeriods, BuddyIntentRouter.route("compare with last month").intent)
    }

    @Test
    fun `semester outlook routes`() {
        assertEquals(BuddyIntent.SemesterOutlook, BuddyIntentRouter.route("will I survive this semester").intent)
        assertEquals(BuddyIntent.SemesterOutlook, BuddyIntentRouter.route("will my money last").intent)
    }

    @Test
    fun `compare plans shows three scenarios with runway`() {
        val text = (BuddyPhaseOne.handle("compare three spending plans", simState()) as BuddyOutcome.Answer).text
        assertTrue(text.contains("Three 30-day plans"))
        assertTrue(text.contains("Conservative: KSh 140/day"))
        assertTrue(text.contains("Normal: KSh 200/day"))
        assertTrue(text.contains("Aggressive: KSh 260/day"))
        assertTrue(text.contains("SIMULATED"))
    }

    @Test
    fun `semester without dates asks instead of inventing`() {
        val outcome = BuddyPhaseOne.handle("will I survive this semester", simState())
        assertTrue(outcome is BuddyOutcome.Ask)
        assertTrue((outcome as BuddyOutcome.Ask).text.contains("won't invent"))
    }

    @Test
    fun `new intents need no confirmation`() {
        assertEquals(BuddyValidation.Allowed, BuddyValidator.validate(BuddyIntent.CompareSpendingPlans(30)))
        assertEquals(BuddyValidation.Allowed, BuddyValidator.validate(BuddyIntent.SemesterOutlook))
    }

    @Test
    fun `how much do i earn queries income`() {
        assertEquals(BuddyIntent.QueryIncome, BuddyIntentRouter.route("how much do i earn").intent)
        val text = (BuddyPhaseOne.handle("how much do i earn", state().copy(monthIncome = 12000.0)) as BuddyOutcome.Answer).text
        assertTrue(text.contains("Earned KSh 12k this month"))
        assertTrue(text.contains("No expected paydays"))
    }

    @Test
    fun `salary landing drafts an income source`() {
        val routed = BuddyIntentRouter.route("my salary of 45000 lands on the 25th")
        val intent = routed.intent as BuddyIntent.AddIncomeSource
        assertEquals(45000.0, intent.amount ?: 0.0, 0.001)
        assertEquals("MONTHLY", intent.frequency)
        assertEquals(25, intent.dayOfMonth)
        val outcome = BuddyPhaseOne.handle("my salary of 45000 lands on the 25th", state())
        assertTrue(outcome is BuddyOutcome.ProposeCommand)
        assertTrue((outcome as BuddyOutcome.ProposeCommand).summary.contains("every 25"))
    }

    @Test
    fun `record salary received stays a plain transaction`() {
        val routed = BuddyIntentRouter.route("record salary 4500 received")
        val intent = routed.intent as BuddyIntent.AddTransaction
        assertTrue(intent.income)
        assertEquals(4500.0, intent.amount ?: 0.0, 0.001)
    }

    @Test
    fun `income source without amount asks`() {
        assertTrue(BuddyPhaseOne.handle("add hustle income weekly", state()) is BuddyOutcome.Ask)
    }

    @Test
    fun `income intents validate correctly`() {
        assertEquals(BuddyValidation.Allowed, BuddyValidator.validate(BuddyIntent.QueryIncome))
        assertTrue(
            BuddyValidator.validate(BuddyIntent.AddIncomeSource("Salary", "OTHER", 45000.0, "MONTHLY", 25))
                is BuddyValidation.NeedsConfirmation
        )
    }

    @Test
    fun `move rent to the 5th drafts a date move`() {
        val routed = BuddyIntentRouter.route("move rent to the 5th")
        val intent = routed.intent as BuddyIntent.EditBillDate
        assertEquals("Rent", intent.billName)
        assertTrue((intent.dueTimestamp ?: 0L) > System.currentTimeMillis())
        val outcome = BuddyPhaseOne.handle(
            "move rent to the 5th", state(), emptyList(), emptyList(), null, null, bills(), debts()
        )
        assertTrue(outcome is BuddyOutcome.ProposeCommand)
        assertTrue((outcome as BuddyOutcome.ProposeCommand).summary.contains("Move 'Rent' to"))
    }

    @Test
    fun `date parser resolves tomorrow and weekdays`() {
        val now = System.currentTimeMillis()
        val tomorrow = com.pesaflow.app.ui.buddy.BuddyIntentRouter.parseBillDate("rent tomorrow")
        assertTrue(tomorrow != null && tomorrow > now)
        val friday = com.pesaflow.app.ui.buddy.BuddyIntentRouter.parseBillDate("pay friday")
        assertTrue(friday != null && friday > now)
        assertNull(com.pesaflow.app.ui.buddy.BuddyIntentRouter.parseBillDate("rent sometime"))
    }

    @Test
    fun `bill date moves require confirmation`() {
        assertTrue(
            BuddyValidator.validate(BuddyIntent.EditBillDate("Rent", 1L)) is BuddyValidation.NeedsConfirmation
        )
    }

    @Test
    fun `typos still route`() {
        assertEquals(BuddyIntent.QueryBalance, BuddyIntentRouter.route("what is my balnce?").intent)
        assertEquals(
            BuddyIntent.OpenScreen(NavRoutes.BUDGETS),
            BuddyIntentRouter.route("open budjets").intent
        )
        val txn = BuddyIntentRouter.route("ad 500 transpot").intent as BuddyIntent.AddTransaction
        assertEquals(500.0, txn.amount ?: 0.0, 0.001)
        assertEquals("Transport", txn.category)
    }

    @Test
    fun `punctuation does not break routing`() {
        assertEquals(
            BuddyIntent.OpenScreen(NavRoutes.BUDGETS),
            BuddyIntentRouter.route("open...budgets!!!").intent
        )
        val txn = BuddyIntentRouter.route("add, 500, transport.").intent as BuddyIntent.AddTransaction
        assertEquals(500.0, txn.amount ?: 0.0, 0.001)
    }

    @Test
    fun `scrambled word order still drafts`() {
        val txn = BuddyIntentRouter.route("500 transport add").intent as BuddyIntent.AddTransaction
        assertEquals(500.0, txn.amount ?: 0.0, 0.001)
        assertEquals("Transport", txn.category)
        val bill = BuddyIntentRouter.route("rent 8000 bill add").intent as BuddyIntent.AddBill
        assertEquals(8000.0, bill.amount ?: 0.0, 0.001)
        val nav = BuddyIntentRouter.route("budgets open please").intent
        assertEquals(BuddyIntent.OpenScreen(NavRoutes.BUDGETS), nav)
    }

    @Test
    fun `names are never typo corrected`() {
        assertEquals("Brian", com.pesaflow.app.ui.buddy.BuddyTextNorm.correctToken("Brian"))
        assertEquals("Wanjiku", com.pesaflow.app.ui.buddy.BuddyTextNorm.correctToken("Wanjiku"))
        // Ambiguous corrections stay as typed.
        assertEquals("xyzabc", com.pesaflow.app.ui.buddy.BuddyTextNorm.correctToken("xyzabc"))
    }

    @Test
    fun `real words are never corrected`() {
        val norm = com.pesaflow.app.ui.buddy.BuddyTextNorm
        assertEquals("save for laptop", norm.normalize("save for laptop"))
        assertEquals("turn on dark mode", norm.normalize("turn on dark mode"))
        assertEquals("cancel all reminders", norm.normalize("cancel all reminders"))
        // Merchants ride protected.
        assertEquals("naivas", norm.normalize("naivas", setOf("naivas")))
    }

    @Test
    fun `word boundaries prevent substring false fires`() {
        // "remark" is not "mark"; "parent" is not "rent" + plan.
        assertEquals(BuddyIntent.Unknown, BuddyIntentRouter.route("remark on my spending").intent)
    }

    @Test
    fun `clauses split on connectors but not between digits`() {
        val split = com.pesaflow.app.ui.buddy.BuddyPhaseOne.splitClauses(
            "show my balance and open budgets then hide my balances; what changed?"
        )
        assertEquals(3, split.size) // capped at 3
        assertEquals(
            listOf("show my balance", "open budgets", "hide my balances"),
            com.pesaflow.app.ui.buddy.BuddyPhaseOne.splitClauses("show my balance and open budgets and hide my balances and more")
        )
        assertEquals(
            1,
            com.pesaflow.app.ui.buddy.BuddyPhaseOne.splitClauses("find transactions between 100 and 200").size
        )
    }

    @Test
    fun `two queries compound into one outcome`() {
        val outcome = BuddyPhaseOne.handle("show my bills and show my debts", state())
        assertTrue(outcome is BuddyOutcome.Compound)
        val compound = outcome as BuddyOutcome.Compound
        assertEquals(2, compound.parts.size)
        assertFalse(compound.partial)
        assertTrue(compound.parts.all { it is BuddyOutcome.Answer })
    }

    @Test
    fun `singular balance stays a figure while plural hides`() {
        assertEquals(BuddyIntent.QueryBalance, BuddyIntentRouter.route("show my balance").intent)
        assertEquals(
            BuddyIntent.UpdatePreference(
                com.pesaflow.app.ui.buddy.PrefKey.HIDE_BALANCES, "true"
            ),
            BuddyIntentRouter.route("hide my balances").intent
        )
    }

    @Test
    fun `query plus mutation compounds answer with card`() {
        val outcome = BuddyPhaseOne.handle("show my balance and add 500 transport", state())
        assertTrue(outcome is BuddyOutcome.Compound)
        val parts = (outcome as BuddyOutcome.Compound).parts
        assertTrue(parts[0] is BuddyOutcome.Answer)
        assertTrue(parts[1] is BuddyOutcome.ProposeCommand)
    }

    @Test
    fun `partial compound flags the uncaught clause`() {
        val outcome = BuddyPhaseOne.handle("show my balance and dance a jig", state())
        assertTrue(outcome is BuddyOutcome.Compound)
        assertTrue((outcome as BuddyOutcome.Compound).partial)
    }

    @Test
    fun `all unknown compound falls through to legacy`() {
        assertEquals(
            BuddyOutcome.Unhandled,
            BuddyPhaseOne.handle("dance a jig and sing a song", state())
        )
    }

    @Test
    fun `query language detection`() {
        val lang = com.pesaflow.app.ui.buddy.BuddyLanguage
        assertEquals(
            com.pesaflow.app.data.models.AppLanguage.SHENG,
            lang.detectQuery("niaje, niko na pesa ngapi?")
        )
        assertEquals(
            com.pesaflow.app.data.models.AppLanguage.KISWAHILI,
            lang.detectQuery("niko na salio gani?")
        )
        assertNull(lang.detectQuery("what is my balance?"))
        // A lone English "na" never flips: needs word boundaries + company.
        assertNull(lang.detectQuery("show my balance"))
    }

    @Test
    fun `resolution prefers query over global setting`() {
        val lang = com.pesaflow.app.ui.buddy.BuddyLanguage
        assertEquals(
            com.pesaflow.app.data.models.AppLanguage.SHENG,
            lang.resolve("niaje, pesa ngapi?", com.pesaflow.app.data.models.AppLanguage.ENGLISH)
        )
        assertEquals(
            com.pesaflow.app.data.models.AppLanguage.KISWAHILI,
            lang.resolve("salio langu ni kiasi gani?", com.pesaflow.app.data.models.AppLanguage.MIXED)
        )
        assertEquals(
            com.pesaflow.app.data.models.AppLanguage.ENGLISH,
            lang.resolve("what is my balance?", com.pesaflow.app.data.models.AppLanguage.MIXED)
        )
    }

    @Test
    fun `swahili answers carry figures and provenance`() {
        val st = state().copy(lang = com.pesaflow.app.data.models.AppLanguage.KISWAHILI)
        val balance = BuddyPhaseOne.handle("niko na salio gani?", st)
        assertTrue(balance is BuddyOutcome.Answer)
        val text = (balance as BuddyOutcome.Answer).text
        assertTrue(text.contains("KSh 3.2k"))
        assertTrue(text.contains("Salio lililorekodiwa"))
    }

    @Test
    fun `sheng answers carry figures`() {
        val st = state().copy(lang = com.pesaflow.app.data.models.AppLanguage.SHENG)
        val flexible = BuddyPhaseOne.handle("flexible doh ni ngapi?", st)
        assertTrue(flexible is BuddyOutcome.Answer)
        val text = (flexible as BuddyOutcome.Answer).text
        assertTrue(text.contains("KSh 3.2k"))
        assertTrue(text.contains("tumeicalculate"))
    }

    @Test
    fun `localized asks keep their meaning`() {
        val st = state().copy(lang = com.pesaflow.app.data.models.AppLanguage.KISWAHILI)
        val ask = BuddyPhaseOne.handle("ongeza matumizi", st)
        assertTrue(ask is BuddyOutcome.Ask)
        assertTrue((ask as BuddyOutcome.Ask).text.contains("kiasi gani"))
    }

    @Test
    fun `swahili keywords survive typo correction`() {
        val norm = com.pesaflow.app.ui.buddy.BuddyTextNorm
        // Exact forms pass through untouched.
        assertEquals("salio", norm.correctToken("salio"))
        assertEquals("helb", norm.correctToken("helb"))
        assertEquals("weka", norm.correctToken("weka"))
        // Near typos correct toward Swahili, not English.
        assertEquals("weka", norm.correctToken("wekka"))
        assertEquals("baki", norm.correctToken("bakii"))
        // Regression: "helb" sat one edit from "help" and used to corrupt.
        // (BuddyBrain.normalize appends " income" — the synonym expansion
        // that routes HELB queries to income; the word itself is intact.)
        assertEquals("helb imeingia income", norm.normalize("helb imeingia?"))
    }

    @Test
    fun `follow-ups switch category and week`() {
        val brain = com.pesaflow.app.ui.dashboard.BuddyBrain
        // Seed memory as a confident spend turn would.
        com.pesaflow.app.ui.dashboard.BuddyMemory.lastIntent = "spend"
        // Category switch replays the last topic for the new category.
        assertEquals("Food spending", brain.rewriteFollowUp("and food?", "and food?"))
        assertEquals("Transport spending", brain.rewriteFollowUp("what about transport?", "what about transport?"))
        // Time-window switch replays the spend question for the week.
        assertEquals("spend this week", brain.rewriteFollowUp("and this week?", "and this week?"))
        assertEquals("spend this week", brain.rewriteFollowUp("na wiki hii?", "na wiki hii?"))
        // Budget context asks per-category envelopes instead.
        com.pesaflow.app.ui.dashboard.BuddyMemory.lastIntent = "budget"
        assertEquals("Food budget", brain.rewriteFollowUp("and food?", "and food?"))
        // Strong queries still pass through untouched.
        assertNull(brain.rewriteFollowUp("food spending", "food spending"))
        com.pesaflow.app.ui.dashboard.BuddyMemory.lastIntent = null
    }

    @Test
    fun `small talk answers in all three voices`() {
        val strings = com.pesaflow.app.ui.buddy.BuddyStrings
        val en = com.pesaflow.app.data.models.AppLanguage.ENGLISH
        val sw = com.pesaflow.app.data.models.AppLanguage.KISWAHILI
        val sh = com.pesaflow.app.data.models.AppLanguage.SHENG
        assertTrue(strings.whoAreYou(en).contains("PesaBuddy"))
        assertTrue(strings.whoAreYou(sw).contains("PesaBuddy"))
        // Joke rotation is deterministic: three distinct jokes cycle.
        val jokes = (0..2).map { strings.joke(it, en) }.toSet()
        assertEquals(3, jokes.size)
        assertEquals(strings.joke(0, en), strings.joke(3, en))
        assertTrue(strings.sorryReply(sw).contains("Hakuna shida"))
        assertTrue(strings.howAreYou(sh).contains("fiti"))
        assertTrue(strings.goodMorning(en).contains("Good morning"))
        assertTrue(strings.goodNight(sw).contains("Usiku mwema"))
        assertTrue(strings.loveYou(en).contains("budget"))
    }

    @Test
    fun `expanded markers detect everyday queries`() {
        val lang = com.pesaflow.app.ui.buddy.BuddyLanguage
        assertEquals(
            com.pesaflow.app.data.models.AppLanguage.KISWAHILI,
            lang.detectQuery("baki ngapi?")
        )
        assertEquals(
            com.pesaflow.app.data.models.AppLanguage.SHENG,
            lang.detectQuery("wasee, pesa ngapi?")
        )
        // English control queries still detect nothing.
        assertNull(lang.detectQuery("what is my balance?"))
        assertNull(lang.detectQuery("show my balance"))
    }

    @Test
    fun `end to end phase one handles balance and navigation`() {
        val answer = BuddyPhaseOne.handle("what is my balance?", state())
        assertTrue(answer is BuddyOutcome.Answer)
        val nav = BuddyPhaseOne.handle("open budgets", state())
        assertEquals(BuddyOutcome.Navigate(NavRoutes.BUDGETS, "Opening budgets…"), nav)
        assertEquals(BuddyOutcome.Unhandled, BuddyPhaseOne.handle("rename that transaction", state()))
    }
}
