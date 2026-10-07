package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.CsvImporter
import org.junit.Assert.*
import org.junit.Test


class CsvImporterTest {

    private val sample = "date,description,amount,category\n" +
        "2026-09-01,Kibanda lunch,-250,Food\n" +
        "2026-09-02,HELB,10000,Salary\n"

    @Test
    fun `valid rows import with correct types`() {
        val txs = CsvImporter.parseCsvData(sample, 0, 1, 2, 3)
        assertEquals(2, txs.size)
        val lunch = txs.first { it.category == "Food" }
        assertEquals(250.0, lunch.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, lunch.type)
        assertEquals(TransactionSource.CSV_IMPORT, lunch.source)
        val helb = txs.first { it.category == "Salary" }
        assertEquals(10000.0, helb.amount, 0.001)
        assertEquals(TransactionType.INCOME, helb.type)
    }

    @Test
    fun `malformed rows are skipped without aborting`() {
        val csv = "date,description,amount,category\n" +
            "2026-09-01,Good row,-100,Food\n" +
            "2026-09-02,Bad amount,notanumber,Food\n" +
            "2026-09-03,Short row\n" +
            "2026-09-04,Another good,-50,Transport\n"
        val txs = CsvImporter.parseCsvData(csv, 0, 1, 2, 3)
        assertEquals(2, txs.size)
    }

    @Test
    fun `empty content returns empty list`() {
        assertTrue(CsvImporter.parseCsvData("", 0, 1, 2, 3).isEmpty())
        assertTrue(CsvImporter.parseCsvData("date,description,amount,category\n", 0, 1, 2, 3).isEmpty())
    }

    @Test
    fun `export carries every ledger column`() {
        val txs = CsvImporter.parseCsvData(sample, 0, 1, 2, 3)
        val csv = CsvImporter.buildCsvExport(txs)
        val header = csv.lines().first()
        assertEquals("date,type,amount,category,merchant,description,payment_method,source,confirmed", header)
        assertTrue(csv.contains("EXPENSE"))
        assertTrue(csv.contains("MPESA") || csv.contains("OTHER"))
        assertTrue(csv.contains("CSV_IMPORT"))
    }

    @Test
    fun `export quotes fields with commas per RFC-4180`() {
        val tx = Transaction(
            amount = 250.0,
            type = TransactionType.EXPENSE,
            category = "Food",
            dateTimestamp = 1757000000000L,
            merchant = "Java House, Nairobi",
            description = "lunch \"special\"",
            source = TransactionSource.MANUAL
        )
        val csv = CsvImporter.buildCsvExport(listOf(tx))
        assertTrue(csv.contains("\"Java House, Nairobi\""))
        // Embedded quotes are doubled, and the field is wrapped.
        assertTrue(csv.contains("\"lunch \"\"special\"\"\""))
    }
}
