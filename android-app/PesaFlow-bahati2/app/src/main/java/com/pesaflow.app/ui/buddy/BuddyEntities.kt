package com.pesaflow.app.ui.buddy

import com.pesaflow.app.ui.dashboard.BuddyBrain

// Structured entities, extracted deterministically. Amounts, days and
// categories reuse BuddyBrain's extractors (one implementation, no drift);
// the screen entity resolves through the canonical navigation registry.
data class BuddyEntities(
    val amount: Double? = null,
    val day: String? = null,
    val category: String? = null,
    val screen: String? = null,
    val dayCount: Int? = null
)

object BuddyEntityExtractor {
    fun extract(raw: String, knownMerchants: List<String> = emptyList()): BuddyEntities {
        val q = BuddyTextNorm.normalize(raw, knownMerchants.map { it.lowercase() }.toSet())
        return BuddyEntities(
            amount = BuddyBrain.extractAmount(q),
            day = BuddyBrain.extractDay(q),
            category = BuddyBrain.extractCategory(q),
            screen = BuddyNavigation.screenFor(q),
            dayCount = extractDayCount(q)
        )
    }

    private val EN_ONES = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12, "fourteen" to 14, "thirty" to 30
    )

    // "7 days", "seven days", "2 weeks" → day counts for simulations/plans.
    fun extractDayCount(normalized: String): Int? {
        var q = " $normalized "
        EN_ONES.forEach { (w, n) ->
            q = q.replace(Regex("(?<![a-z])$w(?![a-z])"), n.toString())
        }
        Regex("(\\d{1,3})\\s*weeks?\\b").find(q)?.let {
            return it.groupValues[1].toIntOrNull()?.times(7)
        }
        return Regex("(\\d{1,3})\\s*days?\\b").find(q)?.groupValues?.get(1)?.toIntOrNull()
    }
}
