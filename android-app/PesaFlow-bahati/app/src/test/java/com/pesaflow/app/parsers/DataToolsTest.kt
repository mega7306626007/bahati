package com.pesaflow.app.parsers

import com.pesaflow.app.data.exports.ExportEngine
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.CsvImporter
import com.pesaflow.app.data.parsers.PendingPolicy
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar


/** Data extraction: range exports, delimiter sniffing, stale pendings. */
class DataToolsTest {

    private fun at(y: Int, m: Int, d: Int, h: Int = 12): Long {
        return Calendar.getInstance().apply {
            set(y, m, d, h, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun tx(amount: Double, category: String, ts: Long) = Transaction(
        amount = amount, type = TransactionType.EXPENSE, category = category,
        dateTimestamp = ts, merchant = "Test"
    )

    @Test
    fun `range export filters by window and category`() {
        val txs = listOf(
            tx(100.0, "Food", at(2026, Calendar.SEPTEMBER, 5)),
            tx(200.0, "Food", at(2026, Calendar.SEPTEMBER, 20)),
            tx(300.0, "Transport", at(2026, Calendar.SEPTEMBER, 25)),
            tx(400.0, "Food", at(2026, Calendar.OCTOBER, 5))
        )
        val engine = ExportEngine()
        val all = engine.exportCsv(txs)
        assertTrue(all.contains("Food"))
        assertTrue(all.contains("Transport"))

        val sept = engine.exportRange(txs, startMs = at(2026, Calendar.SEPTEMBER, 1), endMs = at(2026, Calendar.SEPTEMBER, 30, 23))
        assertTrue(sept.contains("KSh 100.0") || sept.contains("100.0"))
        assertFalse("October row leaked", sept.contains("400.0"))

        val foodOnly = engine.exportRange(txs, categories = setOf("Food"))
        assertTrue(foodOnly.contains("Food"))
        assertFalse("Transport filtered out", foodOnly.contains("Transport"))
    }

    @Test
    fun `open ended ranges work`() {
        val txs = listOf(
            tx(100.0, "Food", at(2026, Calendar.SEPTEMBER, 5)),
            tx(200.0, "Food", at(2026, Calendar.OCTOBER, 5))
        )
        val engine = ExportEngine()
        val fromSept = engine.exportRange(txs, startMs = at(2026, Calendar.SEPTEMBER, 1))
        assertTrue(fromSept.contains("200.0"))
        val untilOct = engine.exportRange(txs, endMs = at(2026, Calendar.SEPTEMBER, 30, 23))
        assertFalse(untilOct.contains("200.0"))
    }

    @Test
    fun `delimiter sniffing picks tab semicolon pipe and comma`() {
        assertEquals("\t", CsvImporter.sniffDelimiter("date\tdesc\tamount"))
        assertEquals(";", CsvImporter.sniffDelimiter("date;desc;amount"))
        assertEquals("|", CsvImporter.sniffDelimiter("date|desc|amount"))
        assertEquals(",", CsvImporter.sniffDelimiter("date,desc,amount"))
        assertEquals(",", CsvImporter.sniffDelimiter(""))
    }

    @Test
    fun `tab delimited csv parses`() {
        val csv = "date\tdesc\tamount\tcategory\n2026-09-12\tJava House\t250.0\tFood\n2026-09-13\tMatatu\t50.0\tTransport"
        val txs = CsvImporter.parseCsvDataAuto(csv)
        assertEquals(2, txs.size)
        assertEquals("Java House", txs[0].merchant)
        assertEquals(250.0, txs[0].amount, 0.001)
        assertEquals("Transport", txs[1].category)
    }

    @Test
    fun `semicolon delimited csv parses`() {
        val csv = "date;desc;amount;category\n2026-09-12;Java House;250.0;Food"
        val txs = CsvImporter.parseCsvDataAuto(csv)
        assertEquals(1, txs.size)
        assertEquals(250.0, txs[0].amount, 0.001)
    }

    private fun pending(amount: Double, ts: Long) = com.pesaflow.app.data.models.PendingTransaction(
        amount = amount, type = TransactionType.EXPENSE, category = "Food",
        merchant = "Test", dateTimestamp = ts,
        paymentMethod = PaymentMethod.MPESA,
        source = TransactionSource.MPESA_SMS,
        sourceTransactionId = null, rawText = "test"
    )

    @Test
    fun `stale pendings are detected by age`() {
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000
        val fresh = pending(100.0, now - 3 * dayMs)
        val stale = pending(200.0, now - 20 * dayMs)
        val result = PendingPolicy.stalePendings(listOf(fresh, stale), now)
        assertEquals(1, result.size)
        assertEquals(200.0, result[0].amount, 0.001)
    }

    @Test
    fun `stale pendings empty when queue is fresh`() {
        val now = System.currentTimeMillis()
        val fresh = pending(100.0, now - 24L * 60 * 60 * 1000)
        assertTrue(PendingPolicy.stalePendings(listOf(fresh), now).isEmpty())
    }
}
