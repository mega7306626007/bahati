package com.pesaflow.app.data.parsers

import com.pesaflow.app.data.models.KitchenStock

// One unit: every planner input feeds every other gear. This file holds the
// pure joints — meal log ↔ kitchen stock, campus ↔ food prices, housing ×
// commute ↔ which questions get asked, ledger ↔ starter budgets.

// --- Meal log → stock: "I ate ugali + sukuma" burns one dailyUse of the
// matching pile (Cook meals only). Word-match so "Ugali (copy)" still hits.
fun matchStockForMeal(
    mealName: String,
    stock: List<KitchenStock>
): KitchenStock? {
    val words = mealName.lowercase().split(Regex("[^a-z]+")).filter { it.length > 2 }
    if (words.isEmpty()) return null
    return stock.filter { it.qtyLeft > 0 && it.dailyUse > 0 }.firstOrNull { pile ->
        val n = pile.name.lowercase()
        n in mealName.lowercase() || words.any { w -> w in n || n in w }
    }
}

// --- Campus food map: where to eat, what, at what price. Indicative
// starting prices from student reports — the user confirms, never locked.
data class CampusSpot(val place: String, val item: String, val price: Double)

fun campusFoods(university: String): List<CampusSpot> {
    return when {
        university.contains("nairobi", ignoreCase = true) || university.equals("uon", ignoreCase = true) -> listOf(
            CampusSpot("Klabu", "Smocha", 70.0),
            CampusSpot("Klabu", "Chapo + madondo", 100.0),
            CampusSpot("Main mess", "Full lunch plate", 150.0),
            CampusSpot("Kikomba", "Chips + sausage", 120.0)
        )
        university.contains("kenyatta", ignoreCase = true) || university.equals("ku", ignoreCase = true) -> listOf(
            CampusSpot("Annex", "Smocha", 70.0),
            CampusSpot("Annex", "Pilau", 120.0),
            CampusSpot("Gate C kiosks", "Chips mwitu", 80.0),
            CampusSpot("Mess", "Full lunch plate", 150.0)
        )
        university.contains("jkuat", ignoreCase = true) -> listOf(
            CampusSpot("Juja town", "Smocha", 70.0),
            CampusSpot("Juja town", "Chapo madondo", 100.0),
            CampusSpot("Mess", "Full lunch plate", 140.0)
        )
        university.contains("moi", ignoreCase = true) -> listOf(
            CampusSpot("Stage kiosks", "Smocha", 70.0),
            CampusSpot("Stage kiosks", "Githeri", 80.0),
            CampusSpot("Mess", "Full lunch plate", 140.0)
        )
        university.contains("egerton", ignoreCase = true) -> listOf(
            CampusSpot("Njoro town", "Smocha", 70.0),
            CampusSpot("Njoro town", "Chapo madondo", 90.0),
            CampusSpot("Mess", "Full lunch plate", 130.0)
        )
        university.contains("maseno", ignoreCase = true) -> listOf(
            CampusSpot("Maseno town", "Smocha", 60.0),
            CampusSpot("Maseno town", "Githeri", 70.0),
            CampusSpot("Mess", "Full lunch plate", 130.0)
        )
        university.contains("dedan", ignoreCase = true) || university.contains("kimathi", ignoreCase = true) -> listOf(
            CampusSpot("Kimathi town", "Smocha", 70.0),
            CampusSpot("Kimathi town", "Chapo madondo", 100.0),
            CampusSpot("Mess", "Full lunch plate", 140.0)
        )
        university.contains("kisii", ignoreCase = true) -> listOf(
            CampusSpot("Kisii town", "Smocha", 60.0),
            CampusSpot("Kisii town", "Chapo madondo", 90.0),
            CampusSpot("Mess", "Full lunch plate", 130.0)
        )
        university.contains("chuka", ignoreCase = true) -> listOf(
            CampusSpot("Chuka town", "Smocha", 60.0),
            CampusSpot("Chuka town", "Githeri", 70.0),
            CampusSpot("Mess", "Full lunch plate", 130.0)
        )
        university.contains("technical university of kenya", ignoreCase = true) || university.equals("tuk", ignoreCase = true) -> listOf(
            CampusSpot("Ngara", "Smocha", 70.0),
            CampusSpot("Ngara", "Chips + sausage", 120.0),
            CampusSpot("Mess", "Full lunch plate", 150.0)
        )
        else -> listOf(
            CampusSpot("Campus mess", "Full lunch plate", 150.0),
            CampusSpot("Gate kiosks", "Smocha", 70.0),
            CampusSpot("Gate kiosks", "Chips + sausage", 120.0)
        )
    }
}

// Spots not yet in the planner: matched by dish name so re-taps never
// duplicate. Campus prices become Buy plates the menu can plan with.
fun campusSpotsToAdd(existingNames: List<String>, university: String): List<CampusSpot> {
    val have = existingNames.map { it.trim().lowercase() }.toSet()
    return campusFoods(university).filter {
        it.item.lowercase() !in have && "${it.place} ${it.item}".lowercase() !in have
    }
}

// Typical hostel cost per month, from 2025/26 published rates (shared/
// double rooms). Indicative — the user confirms their real number. Used to
// prefill the rent question and to sanity-check hostel budgets app-wide.
fun hostelMonthly(university: String): Double? {
    val u = university.trim().lowercase()
    return when {
        u.contains("nairobi") || u == "uon" -> 1900.0
        u.contains("kenyatta") || u == "ku" -> 1400.0
        u.contains("jkuat") -> 1600.0
        u.contains("moi") -> 1200.0
        u.contains("egerton") -> 1100.0
        u.contains("maseno") -> 1750.0
        u.contains("masinde") || u.contains("muliro") || u.contains("mmust") -> 1300.0
        u.contains("dedan") || u.contains("kimathi") -> 1500.0
        u.contains("kisii") -> 1400.0
        u.contains("chuka") -> 1200.0
        u.contains("technical university of kenya") || u == "tuk" -> 1800.0
        u.isBlank() -> null
        else -> null
    }
}

// --- Ask-only-what-matters: with parents → no rent question; walking →
// no daily-fare question. Reactive: the form hides the moment you pick.
fun shouldAskRent(housing: String): Boolean = !housing.equals("Parents", ignoreCase = true)

fun shouldAskTransport(commute: String): Boolean = !commute.equals("Walk", ignoreCase = true)

// --- Budgeting welcome: first open builds envelopes from the ledger's
// real monthly pace (90-day average), not from zeros. One tap applies.
fun suggestStarterBudgets(
    monthlyExpense: Double,
    monthlyFood: Double,
    monthlyRent: Double,
    monthlyTransport: Double
): Map<String, Double> {
    val out = mutableMapOf<String, Double>()
    if (monthlyExpense > 0) out["ALL"] = monthlyExpense
    if (monthlyFood > 0) out["Food"] = monthlyFood
    if (monthlyRent > 0) out["Rent"] = monthlyRent
    if (monthlyTransport > 0) out["Transport"] = monthlyTransport
    return out
}
