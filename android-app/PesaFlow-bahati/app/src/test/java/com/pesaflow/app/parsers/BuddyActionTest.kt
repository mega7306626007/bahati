package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.ui.dashboard.BuddyBrain
import org.junit.Assert.*
import org.junit.Test


// PesaBuddy as hands: commands parse to actions, questions never do.
class BuddyActionTest {

    private fun pending(merchant: String, amount: Double) = PendingTransaction(
        amount = amount,
        type = TransactionType.EXPENSE,
        category = "Other",
        merchant = merchant,
        dateTimestamp = 1L,
        paymentMethod = PaymentMethod.MPESA,
        source = TransactionSource.MPESA_SMS,
        sourceTransactionId = null,
        rawText = merchant
    )

    private val queue = listOf(
        pending("Nancy Wanjiku", 500.0),
        pending("Naivas Supermarket", 2500.0)
    )

    @Test
    fun `confirm all proposes bulk action`() {
        val p = BuddyBrain.parseAction("confirm all", queue)
        assertTrue(p is BuddyBrain.BuddyProposal.Do)
        assertTrue((p as BuddyBrain.BuddyProposal.Do).action is BuddyBrain.BuddyAction.ConfirmAllSure)
    }

    @Test
    fun `confirm merchant resolves rows`() {
        val p = BuddyBrain.parseAction("confirm nancy", queue)
        assertTrue(p is BuddyBrain.BuddyProposal.Do)
        val a = (p as BuddyBrain.BuddyProposal.Do).action as BuddyBrain.BuddyAction.ConfirmMatch
        assertEquals(1, BuddyBrain.rowsFor(a.merchant, a.amount, queue).size)
    }

    @Test
    fun `confirm amount resolves rows`() {
        val p = BuddyBrain.parseAction("approve the 2500", queue)
        assertTrue(p is BuddyBrain.BuddyProposal.Do)
    }

    @Test
    fun `bare confirm asks instead of guessing`() {
        val p = BuddyBrain.parseAction("confirm", queue)
        assertTrue(p is BuddyBrain.BuddyProposal.Ask)
    }

    @Test
    fun `how-do-i-confirm stays guidance`() {
        assertNull(BuddyBrain.parseAction("how do i confirm transactions", queue))
    }

    @Test
    fun `ignore merchant proposes dismissible action`() {
        val p = BuddyBrain.parseAction("ignore naivas", queue)
        assertTrue(p is BuddyBrain.BuddyProposal.Do)
        assertTrue((p as BuddyBrain.BuddyProposal.Do).action is BuddyBrain.BuddyAction.IgnoreMatch)
    }

    @Test
    fun `clean duplicates proposes sweep`() {
        val p = BuddyBrain.parseAction("remove duplicates", queue)
        assertTrue((p as? BuddyBrain.BuddyProposal.Do)?.action is BuddyBrain.BuddyAction.CleanDuplicates)
    }

    @Test
    fun `categorize needs merchant and category`() {
        val p = BuddyBrain.parseAction("categorize naivas as food", queue)
        val a = (p as? BuddyBrain.BuddyProposal.Do)?.action as? BuddyBrain.BuddyAction.Categorize
        assertNotNull(a)
        assertEquals("Food", a!!.category)
        assertNull(BuddyBrain.parseAction("categorize stuff", queue)?.let {
            (it as? BuddyBrain.BuddyProposal.Do)?.action as? BuddyBrain.BuddyAction.Categorize
        })
    }

    @Test
    fun `set budget needs amount, asks category when missing`() {
        val p = BuddyBrain.parseAction("set food budget 6000", queue)
        val a = (p as? BuddyBrain.BuddyProposal.Do)?.action as? BuddyBrain.BuddyAction.SetBudget
        assertNotNull(a)
        assertEquals(6000.0, a!!.amount, 0.001)
        assertTrue(BuddyBrain.parseAction("set budget", queue) is BuddyBrain.BuddyProposal.Ask)
    }

    @Test
    fun `budget questions never act`() {
        assertNull(BuddyBrain.parseAction("is my budget 5000 safe?", queue))
        assertNull(BuddyBrain.parseAction("how is my budget doing", queue))
    }

    @Test
    fun `add bill debt goal parse with defaults`() {
        val bill = (BuddyBrain.parseAction("add bill rent 8000", queue) as? BuddyBrain.BuddyProposal.Do)?.action
        assertTrue(bill is BuddyBrain.BuddyAction.AddBill)
        val debt = (BuddyBrain.parseAction("i owe brian 2000", queue) as? BuddyBrain.BuddyProposal.Do)?.action as? BuddyBrain.BuddyAction.AddDebt
        assertNotNull(debt)
        assertTrue(debt!!.iOwe)
        val goal = (BuddyBrain.parseAction("save for laptop 80000", queue) as? BuddyBrain.BuddyProposal.Do)?.action
        assertTrue(goal is BuddyBrain.BuddyAction.AddGoal)
    }

    @Test
    fun `review lists, undo proposes`() {
        assertTrue(
            (BuddyBrain.parseAction("review pending", queue) as? BuddyBrain.BuddyProposal.Do)?.action
                is BuddyBrain.BuddyAction.ReviewQueue
        )
        assertTrue(
            (BuddyBrain.parseAction("undo that", queue) as? BuddyBrain.BuddyProposal.Do)?.action
                is BuddyBrain.BuddyAction.Undo
        )
    }

    @Test
    fun `empty queue answers instead of acting`() {
        assertTrue(BuddyBrain.parseAction("confirm all", emptyList()) is BuddyBrain.BuddyProposal.Ask)
        assertTrue(BuddyBrain.parseAction("review", emptyList()) is BuddyBrain.BuddyProposal.Ask)
    }

    @Test
    fun `plain questions fall through to answers`() {
        assertNull(BuddyBrain.parseAction("how much did i spend today", queue))
        assertNull(BuddyBrain.parseAction("my budget is 5000 safe?", queue))
    }
}
