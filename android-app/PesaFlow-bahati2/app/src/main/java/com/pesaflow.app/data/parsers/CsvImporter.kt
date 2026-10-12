package com.pesaflow.app.data.parsers

import com.pesaflow.app.data.models.*
import java.text.SimpleDateFormat
import java.util.Locale


object CsvImporter {

    /**
     * Delimiter sniffing: bank exports arrive comma-, tab- or
     * semicolon-separated. The delimiter with the most occurrences in the
     * header line wins; comma is the fallback.
     */
    fun sniffDelimiter(line: String): String {
        val candidates = listOf("\t", ";", "|", ",")
        val best = candidates.maxByOrNull { d -> line.count { it.toString() == d } } ?: return ","
        // No delimiter present at all (empty/single-cell line) — comma default.
        return if (line.count { it.toString() == best } == 0) "," else best
    }

    fun parseCsvData(
        csvContent: String,
        dateIndex: Int,
        descIndex: Int,
        amountIndex: Int,
        categoryIndex: Int
    ): List<Transaction> {
        val transactions = mutableListOf<Transaction>()
        val lines = csvContent.split("\n")
        val delimiter = lines.firstOrNull { it.isNotBlank() }?.let { sniffDelimiter(it) } ?: ","

        for ((index, line) in lines.withIndex()) {
            if (index == 0 || line.trim().isEmpty()) continue // Skip headers or empty trailing segments safely
            val columns = line.split(delimiter)
            try {
                if (columns.size > maxOf(dateIndex, descIndex, amountIndex, categoryIndex)) {
                    val amountRaw = columns[amountIndex].replace("\"", "").trim()
                    val amount = amountRaw.toDoubleOrNull() ?: continue
                    val rawDate = columns[dateIndex].replace("\"", "").trim()
                    
                    val timestamp = parseFlexibleDate(rawDate)


                    transactions.add(
                        Transaction(
                            amount = kotlin.math.abs(amount),
                            type = if (amount < 0) TransactionType.EXPENSE else TransactionType.INCOME,
                            category = columns[categoryIndex].replace("\"", "").trim(),
                            merchant = columns[descIndex].replace("\"", "").trim(),
                            description = "CSV Imported Row Context",
                            paymentMethod = PaymentMethod.OTHER,
                            source = TransactionSource.CSV_IMPORT,
                            dateTimestamp = timestamp
                        )
                    )
                }
            } catch (e: Exception) {
                // Drop malformed record rows cleanly without interrupting file processing loops
            }
        }
        return transactions
    }


    private val DATE_FORMATS = listOf(
        "yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "dd-MM-yyyy",
        "yyyy/MM/dd", "dd MMM yyyy", "dd-MM-yy", "MM-dd-yyyy"
    )


    /** Bank statements never agree on dates — try every common shape. */
    fun parseFlexibleDate(raw: String): Long {
        val s = raw.replace("\"", "").trim()
        if (s.isEmpty()) return System.currentTimeMillis()
        for (fmt in DATE_FORMATS) {
            try {
                SimpleDateFormat(fmt, Locale.US).isLenient = false
                val t = SimpleDateFormat(fmt, Locale.US).parse(s)?.time
                if (t != null && t > 946684800000L && t < System.currentTimeMillis() + 24L * 60 * 60 * 1000) return t
            } catch (e: Exception) { /* next format */ }
        }
        return System.currentTimeMillis()
    }


    /**
     * Header sniffing: finds date/description/amount/category columns by
     * name (English + Kiswahili statement headers). The header splits on
     * the SNIFFED delimiter — splitting a tab header on commas yields one
     * blob column that matches everything and collapses all indices.
     * Falls back to 0,1,2,3 unless 2+ columns positively match.
     */
    fun sniffColumns(header: String, delimiter: String = ","): IntArray {
        val cols = header.split(delimiter).map { it.replace("\"", "").trim().lowercase() }
        fun find(vararg keys: String) = cols.indexOfFirst { c -> keys.any { c.contains(it) } }
        val date = find("date", "tarehe", "day", "time", "posted")
        val desc = find("description", "desc", "merchant", "narration", "details", "particulars", "payee", "narrative")
        val amount = find("amount", "amt", "money", "value", "debit", "credit", "ksh", "kes", "balance")
        val cat = find("category", "type", "class", "ain")
        val matched = listOf(date, desc, amount, cat).count { it >= 0 }
        if (matched < 2) return intArrayOf(0, 1, 2, 3)
        return intArrayOf(
            date.takeIf { it >= 0 } ?: 0,
            desc.takeIf { it >= 0 } ?: 1,
            amount.takeIf { it >= 0 } ?: 2,
            cat.takeIf { it >= 0 } ?: 3
        )
    }

    /** Auto-map entry point: sniffs the first line, then parses normally. */
    fun parseCsvDataAuto(csvContent: String): List<Transaction> {
        val first = csvContent.lineSequence().firstOrNull { it.isNotBlank() } ?: return emptyList()
        val delimiter = sniffDelimiter(first)
        val idx = sniffColumns(first, delimiter)
        return parseCsvData(csvContent, idx[0], idx[1], idx[2], idx[3])
    }
}