package com.pesaflow.app.data.meals

// Researched campus food-spot + plate directory (student price lists,
// Oct 2026). A mess, cafeteria, kibanda or known plate name on a row
// means Food even when the generic food words never appear — "NYAYO
// MESS" and "SMOCHA" file as Food the same way "SUPER METRO" files as
// Transport. Append-only: add spots/plates, never loosen matching.
//
// Matching mirrors the transport directory: single-token keywords match
// whole tokens only ("mess" must not fire inside "message", "egg" not
// inside "egghead"), multi-word phrases match substrings.
data class FoodSpot(
    val displayName: String,
    val keywords: List<String>,
    val kind: String // MESS | CAFETERIA | KIBANDA | CAFE | RESTAURANT | PLATE
)

val FOOD_SPOTS: List<FoodSpot> = listOf(
    // ---- UoN: Klabu + street food ----
    FoodSpot("Klabu (Club 36)", listOf("klabu", "club 36"), "KIBANDA"),
    FoodSpot("Chapati Farm", listOf("chapati farm"), "KIBANDA"),
    FoodSpot("Globe Vibandas", listOf("globe"), "KIBANDA"),
    FoodSpot("Big Knife", listOf("big knife", "shawarma"), "CAFE"),
    // ---- KU: mess halls ----
    FoodSpot("Nyayo Mess", listOf("nyayo mess", "nyayo"), "MESS"),
    FoodSpot("Eastern Mess", listOf("eastern mess"), "MESS"),
    FoodSpot("Western Mess", listOf("western mess"), "MESS"),
    FoodSpot("BSSC Food Court", listOf("bssc", "food court"), "CAFE"),
    FoodSpot("Mugumo Restaurant", listOf("mugumo"), "RESTAURANT"),
    // ---- JKUAT: mess + Juja spots ----
    FoodSpot("JKUAT Main Mess", listOf("main mess"), "MESS"),
    FoodSpot("NSC Cafeteria", listOf("nsc cafeteria", "nsc"), "CAFETERIA"),
    // Full-phrase keywords so named cafeterias beat the generic
    // "cafeteria" entry on longest-wins (Chui > Canteen, always).
    FoodSpot("Student Centre", listOf("student centre"), "CAFETERIA"),
    FoodSpot("Tuck Shop", listOf("tuck shop"), "KIBANDA"),
    FoodSpot("Hall 7 Cafeteria", listOf("hall 7"), "CAFETERIA"),
    FoodSpot("Candle Cafeteria", listOf("candle cafeteria", "candle"), "CAFETERIA"),
    FoodSpot("Serena Cafeteria", listOf("serena cafeteria"), "CAFETERIA"),
    FoodSpot("Chips Palace", listOf("chips palace"), "CAFE"),
    FoodSpot("Cafe Aticas", listOf("aticas"), "CAFE"),
    FoodSpot("Juja Fish and Chips", listOf("juja fish"), "CAFE"),
    FoodSpot("Silva Chips", listOf("silva chips", "silva"), "CAFE"),
    FoodSpot("Flappy Fries", listOf("flappy fries", "flappy"), "CAFE"),
    FoodSpot("Moriah Chicken Grill", listOf("moriah", "grill"), "RESTAURANT"),
    // ---- Maseno ----
    FoodSpot("College Cafeteria", listOf("college cafeteria"), "CAFETERIA"),
    FoodSpot("Siriba Cafeteria", listOf("siriba cafeteria"), "CAFETERIA"),
    FoodSpot("Kisumu Cafeteria", listOf("kisumu cafeteria"), "CAFETERIA"),
    FoodSpot("Mum's Cafe", listOf("mum's cafe", "mums cafe", "mum cafe"), "CAFE"),
    FoodSpot("Maseno Chafua", listOf("chafua"), "KIBANDA"),
    // ---- Egerton ----
    FoodSpot("Chui Cafeteria", listOf("chui cafeteria", "chui"), "CAFETERIA"),
    FoodSpot("Kiboko Cafeteria", listOf("kiboko cafeteria", "kiboko"), "CAFETERIA"),
    FoodSpot("Simba Restaurant", listOf("simba restaurant"), "RESTAURANT"),
    FoodSpot("Mara Restaurant", listOf("mara restaurant"), "RESTAURANT"),
    FoodSpot("Egerton Midway", listOf("midway restaurant", "midway"), "RESTAURANT"),
    FoodSpot("Havannas", listOf("havannas"), "RESTAURANT"),
    FoodSpot("Check Point", listOf("check point restaurant", "check point"), "RESTAURANT"),
    FoodSpot("Kool Vash", listOf("kool vash"), "RESTAURANT"),
    FoodSpot("Safina", listOf("safina bar", "safina restaurant", "safina"), "RESTAURANT"),
    FoodSpot("Gilanis", listOf("gilanis"), "RESTAURANT"),
    // ---- Generic canteen words ----
    FoodSpot("Canteen", listOf("mess", "cafeteria", "canteen", "catering", "eatery", "eateries"), "CAFETERIA"),
    // ---- Known plates (beyond the parser's generic food words) ----
    FoodSpot(
        "Plates",
        listOf(
            "smocha", "githeri", "omena", "matumbo", "mandazi", "pambana",
            "beans", "samosa", "sausage", "choma", "uji", "kebab",
            "fries", "boiro", "mayai", "eggs", "fish", "meat", "beef", "chicken"
        ),
        "PLATE"
    )
)

private fun foodTokens(s: String): Set<String> =
    s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }.toSet()

private fun foodKeywordHit(haystack: String, keyword: String): Boolean {
    val low = haystack.lowercase()
    return if (!keyword.contains(" ")) foodTokens(low).contains(keyword) else low.contains(keyword)
}

/** Best-matching food spot/plate for a merchant+description, longest keyword wins. */
fun foodSpotHit(haystack: String): FoodSpot? {
    var best: FoodSpot? = null
    var bestLen = 0
    for (entry in FOOD_SPOTS) {
        val longest = entry.keywords.filter { foodKeywordHit(haystack, it) }
            .maxOfOrNull { it.length } ?: 0
        if (longest > bestLen) {
            bestLen = longest
            best = entry
        }
    }
    return best
}

fun isFoodSpot(haystack: String): Boolean = foodSpotHit(haystack) != null
