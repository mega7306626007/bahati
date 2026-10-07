package com.pesaflow.app.data.parsers

import com.pesaflow.app.data.models.*
import java.util.Locale


object NaturalLanguageParser {

    private const val DAY_MS = 24L * 60 * 60 * 1000

    // Merchant markers: "sent 500 to John", "paid 200 at Java House",
    // "nimeweka 2000 kwa mshwari" — the payee is the most useful field a
    // human types, and placeholders never match contact memory or dedupe.
    private val merchantMarkers = listOf(" to ", " from ", " at ", " kwa ")
    private val merchantStopwords = setOf(
        "on", "at", "for", "ksh", "with", "from", "and", "the", "a", "an",
        "today", "yesterday", "leo", "jana", "mpesa", "na", "ya", "kwa",
        "costs", "cost", "ilikuwa", "was", "is", "bob", "sh"
    )

    private fun parseAmountToken(token: String): Double? {
        val t = token.lowercase(Locale.getDefault()).replace(",", "")
        // k-suffix multipliers: 500k, 1.5k, 2k — Sheng shorthand for thousands.
        val kMatch = Regex("^(\\d+(?:\\.\\d+)?)k$").find(t)
        if (kMatch != null) return kMatch.groupValues[1].toDoubleOrNull()?.times(1000.0)
        val bare = t.replace("ksh", "").trimEnd('.')
        return bare.toDoubleOrNull()
    }

    private fun extractAmount(tokens: List<String>): Double? {
        val candidates = tokens.mapNotNull { parseAmountToken(it) }
        if (candidates.isEmpty()) return null
        // A bare 1900–2100 token is a year, not money: "invoice 2025 costs
        // 500" must log 500. Fall back to it only when nothing else exists.
        val nonYear = candidates.filter { it < 1900.0 || it > 2100.0 }
        return (nonYear.firstOrNull() ?: candidates.first())
    }

    private fun extractMerchant(input: String): String? {
        val lower = input.lowercase(Locale.getDefault())
        var bestIdx: Int? = null
        for (marker in merchantMarkers) {
            val idx = lower.indexOf(marker)
            if (idx >= 0 && (bestIdx == null || idx < bestIdx)) bestIdx = idx
        }
        val start = bestIdx ?: return null
        val words = lower.substring(start).trim().split("\\s+".toRegex())
            .drop(1) // drop the marker word itself
            .take(3)
            .takeWhile { it.matches(Regex("[a-z].*")) && it !in merchantStopwords }
        if (words.isEmpty()) return null
        return words.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase(Locale.getDefault()) } }
    }

    private fun extractDateMs(inputLower: String): Long {
        val now = System.currentTimeMillis()
        // "yesterday"/"jana" — the purchase happened, just not today.
        return if (inputLower.contains("yesterday") || inputLower.contains("jana")) now - DAY_MS else now
    }


    fun parse(input: String): PendingTransaction? {
        val tokens = input.lowercase(Locale.getDefault()).trim().split("\\s+".toRegex())
        if (tokens.isEmpty()) return null

        var matchedCategory = "Other"

        val derivedAmount = extractAmount(tokens) ?: return null


        // Detect operational context semantic cues
        val inputLower = input.lowercase(Locale.getDefault())
        val derivedType = when {
            inputLower.contains("received") || inputLower.contains("nimetumiwa") || inputLower.contains("salary") || inputLower.contains("helb") -> {
                TransactionType.INCOME
            }
            inputLower.contains("saved") || inputLower.contains("nimeweka") || inputLower.contains("savings") || inputLower.contains("chama") || inputLower.contains("mshwari") -> {
                TransactionType.SAVING
            }
            else -> TransactionType.EXPENSE
        }


        // Segment mapping evaluation parameters
        val catKeywords = mapOf(
            "Food" to listOf("food", "lunch", "dinner", "supper", "breakfast", "kibanda", "chips", "chapati", "mutura", "pilau", "ugali", "githeri", "eat", "kula", "chakula", "sherehe"),
            "Transport" to listOf("fare", "matatu", "mat", "bodaboda", "boda", "uber", "bolt", "stage", "train", "nauli", "parking"),
            "Airtime" to listOf("airtime", "credit", "safari", "credo", "bonga"),
            "Data" to listOf("bundles", "data", "net", "wi-fi", "wifi", "faiba", "unliminet"),
            "Printing" to listOf("printing", "print", "cyber", "photocopy", "assignment", "stationery"),
            "Shopping" to listOf("shopping", "supermarket", "naivas", "quickmart", "carrefour", "market", "duka", "nunua"),
            "Rent" to listOf("rent", "hostel", "house", "pango", "nyumba"),
            "School" to listOf("fees", "school", "tuition", "exam", "books", "shule", "kalamu"),
            "Electricity" to listOf("kplc", "token", "tokens", "stima", "electricity"),
            "Water" to listOf("water", "maji"),
            "Clothes" to listOf("clothes", "shirt", "shoe", "dress", "jacket", "jeans", "nguo", "kiatu"),
            "Kujibamba" to listOf("salon", "barber", "kinyozi", "hair", "nails", "plot", "movie", "game"),
            "Health" to listOf("hospital", "clinic", "pharmacy", "chemist", "medicine", "dawa", "daktari"),
            "Savings" to listOf("chama", "mshwari", "savings", "save")
        )


        outerLoop@ for ((category, keywords) in catKeywords) {
            for (keyword in keywords) {
                if (inputLower.contains(keyword)) {
                    matchedCategory = category
                    break@outerLoop
                }
            }
        }


        // Real payee when the text names one; category placeholder otherwise.
        val merchant = extractMerchant(input) ?: when (matchedCategory) {
            "Food" -> "Food Joint/Kiosk"
            "Transport" -> "Matatu/Boda Stage"
            "Airtime" -> "Safaricom Airtime"
            "Data" -> "Internet Provider"
            "Shopping" -> "Shop/Market"
            "Rent" -> "Landlord/Hostel"
            "School" -> "School/College"
            "Electricity" -> "KPLC"
            "Water" -> "Water Vendor"
            "Clothes" -> "Clothes Shop"
            "Kujibamba" -> "Salon/Plot"
            "Health" -> "Clinic/Pharmacy"
            "Savings" -> "Savings Pot"
            else -> "General Merchant"
        }


        return PendingTransaction(
            amount = derivedAmount,
            type = derivedType,
            category = matchedCategory,
            merchant = merchant,
            dateTimestamp = extractDateMs(inputLower),
            paymentMethod = if (inputLower.contains("mpesa")) PaymentMethod.MPESA else PaymentMethod.CASH,
            source = TransactionSource.NLP,
            sourceTransactionId = null,
            rawText = input,
            confidenceScore = 0.80f
        )
    }
}
