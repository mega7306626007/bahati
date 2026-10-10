package com.pesaflow.app.data.finance

// Researched Kenyan transport operator directory (operator names and route
// coverage checked against Kenyan transport press, Aug 2026). A matatu
// SACCO, shuttle or coach name on a row means Transport even when the
// amount/time rule never saw it — "ENA COACH 1500 at noon" is still a bus.
// Append-only: add operators, never loosen matching.
//
// Matching mirrors the biller directory: single-token keywords match whole
// tokens only ("rog" must not fire inside "rogue", "ena" not inside
// "general"), multi-word phrases match substrings. Longest keyword wins,
// so "rembo shuttle" beats a bare "shuttle".
data class TransportOperator(
    val displayName: String,
    val keywords: List<String>,
    val kind: String // MATATU | SHUTTLE | COACH | RIDE_HAIL | GENERIC
)

val TRANSPORT_OPERATORS: List<TransportOperator> = listOf(
    // ---- Nairobi matatu SACCOs ----
    TransportOperator("Super Metro", listOf("super metro"), "MATATU"),
    TransportOperator("City Shuttle", listOf("city shuttle"), "MATATU"),
    TransportOperator("Kenya Mpya", listOf("kenya mpya"), "MATATU"),
    TransportOperator("Embassava", listOf("embassava"), "MATATU"),
    TransportOperator("Umoinner", listOf("umoinner"), "MATATU"),
    TransportOperator("Forward Travellers", listOf("forward travellers"), "MATATU"),
    TransportOperator("ROG Sacco", listOf("rog sacco", "rog"), "MATATU"),
    TransportOperator("Zuri Sacco", listOf("zuri sacco", "zuri"), "MATATU"),
    TransportOperator("Metro Trans", listOf("metro trans"), "MATATU"),
    TransportOperator("Rembo Shuttle", listOf("rembo shuttle", "rembo"), "MATATU"),
    TransportOperator("Latema", listOf("latema"), "MATATU"),
    TransportOperator("Lopha", listOf("lopha"), "MATATU"),
    TransportOperator("Makos", listOf("makos"), "MATATU"),
    TransportOperator("2NK Sacco", listOf("2nk"), "MATATU"),
    TransportOperator("Kinatwa", listOf("kinatwa"), "MATATU"),
    TransportOperator("Ngong Travellers", listOf("ngong travellers"), "MATATU"),
    TransportOperator("Buruburu 58", listOf("buruburu"), "MATATU"),
    TransportOperator("City Hopper", listOf("city hopper"), "MATATU"),
    TransportOperator("Kikuyu Travellers", listOf("kikuyu travellers"), "MATATU"),
    TransportOperator("Orokise", listOf("orokise", "serian"), "MATATU"),
    // ---- Upcountry shuttles ----
    TransportOperator("North Rift Shuttle", listOf("north rift"), "SHUTTLE"),
    TransportOperator("Prestige Shuttle", listOf("prestige shuttle", "prestige"), "SHUTTLE"),
    TransportOperator("Climax Coaches", listOf("climax"), "SHUTTLE"),
    TransportOperator("4NTE", listOf("4nte"), "SHUTTLE"),
    // ---- Long-distance coaches ----
    TransportOperator("Easy Coach", listOf("easy coach"), "COACH"),
    TransportOperator("Guardian Coach", listOf("guardian coach", "guardian"), "COACH"),
    TransportOperator("ENA Coach", listOf("ena coach", "transline", "ena"), "COACH"),
    TransportOperator("Modern Coast", listOf("modern coast"), "COACH"),
    TransportOperator("Tahmeed Coach", listOf("tahmeed"), "COACH"),
    TransportOperator("Mash Poa", listOf("mash poa", "mash"), "COACH"),
    TransportOperator("Dreamline", listOf("dreamline"), "COACH"),
    TransportOperator("Coast Bus", listOf("coast bus"), "COACH"),
    TransportOperator("Simba Coach", listOf("simba coach"), "COACH"),
    TransportOperator("BusCar", listOf("buscar"), "COACH"),
    TransportOperator("Chania Coach", listOf("chania"), "COACH"),
    TransportOperator("Eldoret Express", listOf("eldoret express"), "COACH"),
    // ---- Ride-hailing ----
    TransportOperator("Bolt", listOf("bolt"), "RIDE_HAIL"),
    TransportOperator("Uber", listOf("uber"), "RIDE_HAIL"),
    TransportOperator("Little Cab", listOf("little cab"), "RIDE_HAIL"),
    TransportOperator("Faras", listOf("faras"), "RIDE_HAIL"),
    TransportOperator("YEGO", listOf("yego"), "RIDE_HAIL"),
    // ---- Generic ride words (operator-agnostic M-Pesa texts) ----
    // Note: bare "sacco" is deliberately absent — a savings/chama SACCO
    // must keep filing as Savings (parser + memory both rule that way).
    // Named matatu SACCOs hit by name; "Matatu sacco" hits by "matatu".
    TransportOperator(
        "Ride",
        listOf("matatu", "shuttle", "coach", "travellers", "psv", "boda", "tuktuk", "nduthi", "pikipiki", "konda", "conductor", "bus"),
        "GENERIC"
    )
)

private fun transportTokens(s: String): Set<String> =
    s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }.toSet()

private fun transportKeywordHit(haystack: String, keyword: String): Boolean {
    val low = haystack.lowercase()
    return if (!keyword.contains(" ")) transportTokens(low).contains(keyword) else low.contains(keyword)
}

/** Best-matching operator for a merchant+description, longest keyword wins. */
fun transportOperatorHit(haystack: String): TransportOperator? {
    var best: TransportOperator? = null
    var bestLen = 0
    for (entry in TRANSPORT_OPERATORS) {
        val longest = entry.keywords.filter { transportKeywordHit(haystack, it) }
            .maxOfOrNull { it.length } ?: 0
        if (longest > bestLen) {
            bestLen = longest
            best = entry
        }
    }
    return best
}

fun isTransportOperator(haystack: String): Boolean = transportOperatorHit(haystack) != null
