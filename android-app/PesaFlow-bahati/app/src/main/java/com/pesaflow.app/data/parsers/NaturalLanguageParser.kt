package com.pesaflow.app.data.parsers

import com.pesaflow.app.data.models.*
import java.util.Locale


object NaturalLanguageParser {


    fun parse(input: String): PendingTransaction? {
        // Last parseable token wins ("2 chapo 100" → 100). Understands
        // "1,250", "2k", "KSh 250" via the shared AmountParser.
        val derivedAmount = com.pesaflow.app.data.ledger.AmountParser.parseLast(input)
            ?: return null
        var derivedType = TransactionType.EXPENSE
        var matchedCategory = "Other"
        var matchedKeyword = false


        // Detect operational context semantic cues — full Swahili verb set,
        // aligned with MpesaParser.inferCategory's keyword coverage.
        val inputLower = input.lowercase(Locale.getDefault())
        when {
            inputLower.contains("received") || inputLower.contains("nimetumiwa") || inputLower.contains("nimepewa") ||
                inputLower.contains("nimepokea") || inputLower.contains("nimeongea") ||
                inputLower.contains("salary") || inputLower.contains("helb") || inputLower.contains("bonus") ||
                inputLower.contains("refund") || inputLower.contains("allowance") -> {
                derivedType = TransactionType.INCOME
            }
            inputLower.contains("saved") || inputLower.contains("nimeweka") || inputLower.contains("savings") ||
                inputLower.contains("chama") || inputLower.contains("mshwari") -> {
                derivedType = TransactionType.SAVING
            }
        }

        // Segment mapping evaluation parameters — keyword set mirrors
        // MpesaParser.inferCategory so NLP and SMS filing agree.
        val catKeywords = mapOf(
            "Food" to listOf("food", "lunch", "dinner", "supper", "breakfast", "kibanda", "chips", "chapati", "mutura", "pilau", "ugali", "githeri", "eat", "kula", "chakula", "sherehe", "smokie", "sukuma", "nyama", "kuku", "mama", "rest", "hotel", "cafe", "kiosk", "vibanda", "choma"),
            "Transport" to listOf("fare", "matatu", "mat", "bodaboda", "boda", "uber", "bolt", "stage", "train", "nauli", "parking", "grability", "ride", "car", "fuel", "petrol", "shell", "rubis", "totalenergies", "ola energy"),
            "Airtime" to listOf("airtime", "credit", "safari", "credo", "bonga"),
            "Data" to listOf("bundles", "data", "wi-fi", "wifi", "faiba", "unliminet"),
            "Printing" to listOf("printing", "print", "cyber", "photocopy", "assignment", "stationery"),
            "Shopping" to listOf("shopping", "supermarket", "naivas", "quickmart", "carrefour", "market", "duka", "nunua", "choppies", "jumia", "kilimani", "jiji", "eastmatt", "cleanshelf", "magunas", "kibo", "lipa mdogo"),
            "Rent" to listOf("rent", "hostel", "house", "pango", "nyumba"),
            "School" to listOf("fees", "school", "tuition", "exam", "books", "shule", "kalamu", "university"),
            "Electricity" to listOf("kplc", "token", "tokens", "stima", "electricity"),
            "Water" to listOf("water", "maji"),
            "Clothes" to listOf("clothes", "shirt", "shoe", "dress", "jacket", "jeans", "nguo", "kiatu"),
            "Kujibamba" to listOf("salon", "barber", "kinyozi", "hair", "nails", "plot", "movie", "game", "netflix", "spotify", "showmax", "dstv", "gotv", "startimes", "sportpesa", "betika"),
            "Health" to listOf("hospital", "clinic", "pharmacy", "chemist", "medicine", "dawa", "daktari", "goodlife", "haltons", "mydawa", "khan", "shah"),
            "Savings" to listOf("chama", "mshwari", "savings", "save")
        )


        outerLoop@ for ((category, keywords) in catKeywords) {
            for (keyword in keywords) {
                if (inputLower.contains(keyword)) {
                    matchedCategory = category
                    matchedKeyword = true
                    break@outerLoop
                }
            }
        }


        // Merchant hint: "at Java", "kwa MAMA MBOGA", "from HELB" — the text
        // often names the counterparty even when the category is generic.
        val merchantHint = Regex("(?i)\\b(?:at|kwa|from)\\s+([A-Za-z' .]+?)(?:\\s+(?:mpesa|cash|today|leo|jana|yesterday|kesho|tomorrow)\\b|[.,]|$)")
            .find(input)?.groupValues?.get(1)?.trim()?.takeIf { it.length >= 2 }
            ?.take(24)

        // Backdate words: relative days first, then specific dates
        // ("on 12/9", "tarehe 12/9") which land on exact past days.
        val dayMs = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        val specificDate = Regex("(?i)\\b(?:on|tarehe|date)\\s+(\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?|\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{2,4})")
            .find(input)?.groupValues?.get(1)
        val stamp = when {
            specificDate != null -> MpesaParser.parseDateTime(specificDate, null).takeIf { it < now - dayMs / 2 } ?: now
            inputLower.contains("day before") || inputLower.contains("juzi") -> now - 2 * dayMs
            inputLower.contains("yesterday") || inputLower.contains("jana") -> now - dayMs
            inputLower.contains("tomorrow") || inputLower.contains("kesho") -> now + dayMs
            else -> now
        }

        // Isolate dynamic target merchant signatures — hint wins when the text
        // names one; otherwise the category's usual suspect.
        val merchant = merchantHint ?: when (matchedCategory) {
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
            dateTimestamp = stamp,
            paymentMethod = if (inputLower.contains("mpesa")) PaymentMethod.MPESA else PaymentMethod.CASH,
            source = TransactionSource.NLP,
            sourceTransactionId = null,
            rawText = input,
            // Honest confidence: keyword hit = high, fallback guess = review me.
            confidenceScore = if (matchedKeyword && matchedCategory != "Other") 0.9f else 0.55f
        )
    }
}
