package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.NaturalLanguageParser
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar


/** NLP input: Swahili verbs, merchant hints, specific dates, keyword parity. */
class NlpParserTest {

    private val dayMs = 24L * 60 * 60 * 1000

    private fun daysAgo(n: Int): Long = System.currentTimeMillis() - n * dayMs

    @Test
    fun `basic expense parses with amount and category`() {
        val tx = NaturalLanguageParser.parse("nimebuy lunch ya 250 mpesa")
        assertNotNull(tx)
        assertEquals(TransactionType.EXPENSE, tx!!.type)
        assertEquals(250.0, tx.amount, 0.001)
        assertEquals("Food", tx.category)
        assertEquals(PaymentMethod.MPESA, tx.paymentMethod)
    }

    @Test
    fun `income verbs parse as income`() {
        assertEquals(TransactionType.INCOME, NaturalLanguageParser.parse("nimepewa 500 kwa HELB")!!.type)
        assertEquals(TransactionType.INCOME, NaturalLanguageParser.parse("nimepokea salary 20000")!!.type)
        assertEquals(TransactionType.INCOME, NaturalLanguageParser.parse("nimeongea 1000 bonus")!!.type)
    }

    @Test
    fun `saving verbs parse as saving`() {
        val tx = NaturalLanguageParser.parse("nimeweka 500 mshwari")
        assertNotNull(tx)
        assertEquals(TransactionType.SAVING, tx!!.type)
        assertEquals("Savings", tx.category)
    }
    @Test
    fun `merchant hint wins over generic suspect`() {
        val tx = NaturalLanguageParser.parse("nimepewa 500 kwa HELB")
        assertNotNull(tx)
        assertEquals("HELB", tx!!.merchant)
    }

    @Test
    fun `at hint extracts merchant`() {
        val tx = NaturalLanguageParser.parse("fare 50 at stage")
        assertNotNull(tx)
        assertEquals("stage", tx!!.merchant)
    }

    @Test
    fun `yesterday backdates one day`() {
        val tx = NaturalLanguageParser.parse("jana lunch 200")
        assertNotNull(tx)
        val c = Calendar.getInstance().apply { timeInMillis = tx!!.dateTimestamp }
        val expected = Calendar.getInstance().apply { timeInMillis = daysAgo(1) }
        assertEquals(expected.get(Calendar.DAY_OF_MONTH), c.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `juzi backdates two days`() {
        val tx = NaturalLanguageParser.parse("juzi fare 100")
        assertNotNull(tx)
        val c = Calendar.getInstance().apply { timeInMillis = tx!!.dateTimestamp }
        val expected = Calendar.getInstance().apply { timeInMillis = daysAgo(2) }
        assertEquals(expected.get(Calendar.DAY_OF_MONTH), c.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `specific date lands on that day`() {
        val tx = NaturalLanguageParser.parse("on 12/9/26 lunch 200")
        assertNotNull(tx)
        val c = Calendar.getInstance().apply { timeInMillis = tx!!.dateTimestamp }
        assertEquals(12, c.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.SEPTEMBER, c.get(Calendar.MONTH))
        assertEquals(2026, c.get(Calendar.YEAR))
    }

    @Test
    fun `tarehe date lands on that day`() {
        val tx = NaturalLanguageParser.parse("tarehe 1/9/26 nimeweka 500")
        assertNotNull(tx)
        val c = Calendar.getInstance().apply { timeInMillis = tx!!.dateTimestamp }
        assertEquals(1, c.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.SEPTEMBER, c.get(Calendar.MONTH))
    }

    @Test
    fun `tomorrow lands in the future`() {
        val tx = NaturalLanguageParser.parse("kesho lunch 200")
        assertNotNull(tx)
        assertTrue(tx!!.dateTimestamp > System.currentTimeMillis())
    }

    @Test
    fun `keyword parity with sms filing`() {
        // Categories the SMS parser knows must answer to NLP too.
        assertEquals("Transport", NaturalLanguageParser.parse("uber 300")!!.category)
        assertEquals("Shopping", NaturalLanguageParser.parse("naivas 1500")!!.category)
        assertEquals("Health", NaturalLanguageParser.parse("pharmacy 400")!!.category)
        assertEquals("Kujibamba", NaturalLanguageParser.parse("netflix 650")!!.category)
        assertEquals("School", NaturalLanguageParser.parse("tuition 5000")!!.category)
        assertEquals("Electricity", NaturalLanguageParser.parse("kplc token 500")!!.category)
        assertEquals("Data", NaturalLanguageParser.parse("wifi 999")!!.category)
        assertEquals("Printing", NaturalLanguageParser.parse("cyber printing 200")!!.category)
        assertEquals("Clothes", NaturalLanguageParser.parse("shirt 1200")!!.category)
        assertEquals("Water", NaturalLanguageParser.parse("maji 100")!!.category)
    }

    @Test
    fun `last amount wins for multi-item lines`() {
        val tx = NaturalLanguageParser.parse("2 chapo 100")
        assertNotNull(tx)
        assertEquals(100.0, tx!!.amount, 0.001)
    }

    @Test
    fun `no amount means no parse`() {
        assertNull(NaturalLanguageParser.parse("nimebuy lunch"))
        assertNull(NaturalLanguageParser.parse(""))
    }

    @Test
    fun `confidence is honest`() {
        val sure = NaturalLanguageParser.parse("nimebuy lunch 250")
        val unsure = NaturalLanguageParser.parse("stuff 250")
        assertTrue(sure!!.confidenceScore >= 0.8f)
        assertTrue(unsure!!.confidenceScore < 0.8f)
    }
}
