package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.NaturalLanguageParser
import org.junit.Assert.*
import org.junit.Test


class NaturalLanguageParserTest {

    @Test
    fun ` Sheng food purchase parses amount and category`() {
        val tx = NaturalLanguageParser.parse("nimebuy lunch 250")
        assertNotNull(tx)
        assertEquals(250.0, tx!!.amount, 0.001)
        assertEquals("Food", tx.category)
        assertEquals(TransactionType.EXPENSE, tx.type)
    }

    @Test
    fun `mpesa keyword sets mpesa payment`() {
        val tx = NaturalLanguageParser.parse("nimebuy lunch ya 250 mpesa")
        assertNotNull(tx)
        assertEquals(PaymentMethod.MPESA, tx!!.paymentMethod)
        assertEquals(TransactionSource.NLP, tx.source)
    }

    @Test
    fun `received salary parses as income`() {
        val tx = NaturalLanguageParser.parse("received salary 5000")
        assertNotNull(tx)
        assertEquals(5000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, tx.type)
    }

    @Test
    fun `savings phrase parses as saving`() {
        val tx = NaturalLanguageParser.parse("nimeweka 2000 kwa savings")
        assertNotNull(tx)
        assertEquals(2000.0, tx!!.amount, 0.001)
        assertEquals(TransactionType.SAVING, tx.type)
    }

    @Test
    fun `transport fare detected`() {
        val tx = NaturalLanguageParser.parse("leo fare ilikuwa 150")
        assertNotNull(tx)
        assertEquals(150.0, tx!!.amount, 0.001)
        assertEquals("Transport", tx.category)
    }

    @Test
    fun `text without amount returns null`() {
        assertNull(NaturalLanguageParser.parse("nimebuy lunch"))
        assertNull(NaturalLanguageParser.parse(""))
        assertNull(NaturalLanguageParser.parse("hello there"))
    }

    @Test
    fun `sheng staples map to new categories`() {
        assertEquals("Shopping", NaturalLanguageParser.parse("nunua supermarket 800")!!.category)
        assertEquals("Rent", NaturalLanguageParser.parse("rent 3500")!!.category)
        assertEquals("School", NaturalLanguageParser.parse("fees 2000")!!.category)
        assertEquals("Electricity", NaturalLanguageParser.parse("kplc tokens 500")!!.category)
        assertEquals("Water", NaturalLanguageParser.parse("maji 200")!!.category)
        assertEquals("Clothes", NaturalLanguageParser.parse("shirt 1200")!!.category)
        assertEquals("Kujibamba", NaturalLanguageParser.parse("kinyozi 300")!!.category)
        assertEquals("Health", NaturalLanguageParser.parse("dawa 450")!!.category)
    }

    @Test
    fun `chama contribution parses as saving`() {
        val tx = NaturalLanguageParser.parse("chama 1000")
        assertNotNull(tx)
        assertEquals(TransactionType.SAVING, tx!!.type)
        assertEquals("Savings", tx.category)
    }
}
