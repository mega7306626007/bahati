package com.pesaflow.app.parsers

import com.pesaflow.app.data.finance.Commute
import com.pesaflow.app.data.finance.IncomeStability
import com.pesaflow.app.data.finance.ProfileSignals
import com.pesaflow.app.data.finance.applySuggestions
import com.pesaflow.app.data.finance.observeSignals
import com.pesaflow.app.data.finance.suggestProfileUpdates
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar


// Behaviour precedence (§13): declaration > confirmation > observation.
// Observation proposes; only the user disposes.
class BehaviourEngineTest {

    private fun dayAt(daysAgo: Int, dow: Int, hour: Int = 8): Long {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_MONTH, -daysAgo)
        while (c.get(Calendar.DAY_OF_WEEK) != dow) c.add(Calendar.DAY_OF_MONTH, -1)
        c.set(Calendar.HOUR_OF_DAY, hour)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun fare(daysAgo: Int, dow: Int, amount: Double = 60.0) = Transaction(
        amount = amount, type = TransactionType.EXPENSE, category = "Transport",
        dateTimestamp = dayAt(daysAgo, dow), merchant = "Matatu", description = "",
        paymentMethod = PaymentMethod.MPESA, source = TransactionSource.MANUAL
    )

    private fun fareTs(ts: Long, amount: Double = 60.0) = Transaction(
        amount = amount, type = TransactionType.EXPENSE, category = "Transport",
        dateTimestamp = ts, merchant = "Matatu", description = "",
        paymentMethod = PaymentMethod.MPESA, source = TransactionSource.MANUAL
    )

    // Monday of the week containing (today - 7*weeksAgo), 8am. Anchoring at
    // Monday keeps the whole Mon–Thu run inside one week regardless of which
    // day the test runs — walking back per-weekday from a weekday offset
    // splits Tue/Wed/Thu across two weeks when today IS Monday.
    private fun mondayWeeksAgo(weeksAgo: Int): Long {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_MONTH, -7 * weeksAgo)
        while (c.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) c.add(Calendar.DAY_OF_MONTH, -1)
        c.set(Calendar.HOUR_OF_DAY, 8)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun fourDayCommute(): List<Transaction> {
        val rows = mutableListOf<Transaction>()
        (1..3).forEach { w ->
            val monday = mondayWeeksAgo(w)
            (0..3).forEach { day ->
                rows.add(fareTs(monday + day * 24L * 60 * 60 * 1000))
            }
        }
        return rows
    }

    @Test
    fun `four day fares suggest long commute`() {
        val observed = observeSignals(fourDayCommute())
        assertTrue(observed.commuteDaysPerWeek >= 2.0)
        val out = suggestProfileUpdates(
            ProfileSignals(commute = Commute.WALK),
            observed
        )
        assertTrue(out.any { it.id == "commute:LONG" && it.reason.isNotBlank() })
    }

    @Test
    fun `thin data suggests nothing`() {
        val observed = observeSignals(
            listOf(fare(2, Calendar.getInstance().get(Calendar.DAY_OF_WEEK)))
        )
        val out = suggestProfileUpdates(ProfileSignals(commute = Commute.WALK), observed)
        assertTrue(out.none { it.dimension == "commute" })
    }

    @Test
    fun `unconfirmed suggestion never applies`() {
        val observed = observeSignals(fourDayCommute())
        val all = suggestProfileUpdates(ProfileSignals(commute = Commute.WALK), observed)
        assertTrue(all.isNotEmpty())
        val effective = applySuggestions(ProfileSignals(commute = Commute.WALK), emptySet(), all)
        assertEquals(Commute.WALK, effective.commute)
    }

    @Test
    fun `confirmed suggestion applies and unknown ids ignored`() {
        val observed = observeSignals(fourDayCommute())
        val all = suggestProfileUpdates(ProfileSignals(commute = Commute.WALK), observed)
        val effective = applySuggestions(
            ProfileSignals(commute = Commute.WALK),
            setOf("commute:LONG", "commute:MOON"),
            all
        )
        assertEquals(Commute.LONG, effective.commute)
    }

    @Test
    fun `missing salary suggests variable income`() {
        val observed = observeSignals(fourDayCommute())
        val out = suggestProfileUpdates(
            ProfileSignals(incomeStability = IncomeStability.FIXED),
            observed
        )
        assertTrue(out.any { it.id == "incomeStability:VARIABLE" })
    }
}
