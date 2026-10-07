package com.pesaflow.app.data.parsers

import com.pesaflow.app.data.models.*
import java.text.SimpleDateFormat
import java.util.Locale


object CsvImporter {
    fun parseCsvData(
        csvContent: String,
        dateIndex: Int,
        descIndex: Int,
        amountIndex: Int,
        categoryIndex: Int
    ): List<Transaction> {
        val transactions = mutableListOf<Transaction>()
        val lines = csvContent.split("\n")
        
        for ((index, line) in lines.withIndex()) {
            if (index == 0 || line.trim().isEmpty()) continue // Skip headers or empty trailing segments safely
            val columns = line.split(",")
            try {
                if (columns.size > maxOf(dateIndex, descIndex, amountIndex, categoryIndex)) {
                    val amountRaw = columns[amountIndex].replace("\"", "").trim()
                    val amount = amountRaw.toDoubleOrNull() ?: continue
                    val rawDate = columns[dateIndex].replace("\"", "").trim()
                    
                    val timestamp = try {
                        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(rawDate)?.time ?: System.currentTimeMillis()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }


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

    // RFC-4180 quoting: wrap when the value carries a comma, quote or
    // newline; double any embedded quote. The old export wrote raw values,
    // so "Java House, Nairobi" split into two columns on re-import.
    private fun csvField(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    // Full-fidelity export: every column the ledger knows, so a re-import or
    // a spreadsheet sees the same row the app does.
    fun buildCsvExport(transactions: List<Transaction>): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sb = StringBuilder("date,type,amount,category,merchant,description,payment_method,source,confirmed\n")
        transactions.forEach { tx ->
            sb.append(
                listOf(
                    format.format(java.util.Date(tx.dateTimestamp)),
                    tx.type.name,
                    tx.amount,
                    csvField(tx.category),
                    csvField(tx.merchant),
                    csvField(tx.description),
                    tx.paymentMethod.name,
                    tx.source.name,
                    tx.confirmed
                ).joinToString(",")
            ).append("\n")
        }
        return sb.toString()
    }
}