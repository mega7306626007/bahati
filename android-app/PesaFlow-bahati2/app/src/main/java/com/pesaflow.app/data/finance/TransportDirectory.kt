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
    // ---- Nairobi CBD / Eastlands SACCOs (researched roll, Oct 2026) ----
    TransportOperator("KASBOWA", listOf("kasbowa"), "MATATU"),
    TransportOperator("Kawangware Sacco", listOf("kawangware"), "MATATU"),
    TransportOperator("Kariobangi Sacco", listOf("kariobangi"), "MATATU"),
    TransportOperator("Super Highway 45", listOf("super highway"), "MATATU"),
    TransportOperator("Tawala Utawala", listOf("tawala"), "MATATU"),
    TransportOperator("Umoja Innercore", listOf("innercore", "umoja innercore"), "MATATU"),
    TransportOperator("Umowa Sacco", listOf("umowa"), "MATATU"),
    TransportOperator("Walokana", listOf("walokana"), "MATATU"),
    TransportOperator("West Madaraka 14", listOf("west madaraka", "route 14"), "MATATU"),
    TransportOperator("Zuri Genesis", listOf("zuri genesis"), "MATATU"),
    TransportOperator("Utawala By-Pass", listOf("utawala by-pass", "utawala"), "MATATU"),
    TransportOperator("Kangemi Sacco", listOf("kangemi"), "MATATU"),
    TransportOperator("CBET Sacco", listOf("cbet"), "MATATU"),
    TransportOperator("Moonlight Coach", listOf("moonlight"), "MATATU"),
    TransportOperator("Kaka Travellers", listOf("kaka travellers"), "MATATU"),
    TransportOperator("Inagi Sacco", listOf("inagi"), "MATATU"),
    TransportOperator("Dix Hult", listOf("dix hult"), "MATATU"),
    TransportOperator("Allethea Ventures", listOf("allethea"), "MATATU"),
    TransportOperator("En Montee", listOf("en montee", "montee"), "MATATU"),
    TransportOperator("Coolio", listOf("coolio", "langalanga"), "MATATU"),
    TransportOperator("Gesarate", listOf("gesarate"), "MATATU"),
    TransportOperator("Nangus", listOf("nangus"), "MATATU"),
    TransportOperator("Nakam Sacco", listOf("nakam"), "MATATU"),
    // ---- Thika Road corridor SACCOs ----
    TransportOperator("Lopha Multipurpose SACCO", listOf("lopha multipurpose", "lopha"), "MATATU"),
    TransportOperator("Nawaku Sacco", listOf("nawaku"), "MATATU"),
    TransportOperator("Thika Road Sacco", listOf("thika road sacco"), "MATATU"),
    TransportOperator("Matara Sacco", listOf("matara"), "MATATU"),
    TransportOperator("Manchester Sacco", listOf("manchester"), "MATATU"),
    TransportOperator("Thika Road Transporters", listOf("thika road transporters"), "MATATU"),
    TransportOperator("Nicco Movers", listOf("nicco"), "MATATU"),
    TransportOperator("Chania Kibwezi", listOf("chania kibwezi"), "MATATU"),
    TransportOperator("TKN Travellers", listOf("tkn"), "MATATU"),
    TransportOperator("TWN Travellers", listOf("twn"), "MATATU"),
    TransportOperator("Taita Taveta Sacco", listOf("taita taveta"), "MATATU"),
    TransportOperator("Juthi Sacco", listOf("juthi"), "MATATU"),
    TransportOperator("Supercoach Safari", listOf("supercoach"), "MATATU"),
    TransportOperator("KBS", listOf("kbs", "kenya bus"), "MATATU"),
    // ---- Mombasa Road / South B-C ----
    TransportOperator("Akila Transport", listOf("akila"), "MATATU"),
    TransportOperator("Telaviv Travellers", listOf("telaviv"), "MATATU"),
    TransportOperator("Citi Hoppa", listOf("citi hoppa", "hoppa"), "MATATU"),
    // ---- Kiambu County ----
    TransportOperator("Transnomics", listOf("transnomics"), "MATATU"),
    TransportOperator("KACOSE", listOf("kacose"), "MATATU"),
    TransportOperator("Lindena Sacco", listOf("lindena"), "MATATU"),
    TransportOperator("Lopha Travels", listOf("lopha travels"), "MATATU"),
    TransportOperator("Hannover Sacco", listOf("hannover"), "MATATU"),
    TransportOperator("Auto Selection", listOf("auto selection"), "MATATU"),
    TransportOperator("Mitaboni Bus", listOf("mitaboni"), "MATATU"),
    TransportOperator("Peter Tharau", listOf("tharau"), "MATATU"),
    TransportOperator("Mwendo Bus", listOf("mwendo"), "MATATU"),
    TransportOperator("Kiambu Marafiki", listOf("kiambu marafiki", "marafiki sacco"), "MATATU"),
    TransportOperator("K-Unity", listOf("k unity", "k-unity"), "MATATU"),
    TransportOperator("City Hopper", listOf("city hopper"), "MATATU"),
    TransportOperator("NAEKANA", listOf("naekana"), "MATATU"),
    // ---- Central Kenya ----
    TransportOperator("NYENA Sacco", listOf("nyena"), "MATATU"),
    TransportOperator("NYESUMA Sacco", listOf("nyesuma"), "MATATU"),
    TransportOperator("3NCK Sacco", listOf("3nck"), "MATATU"),
    TransportOperator("NIM Sacco", listOf("nim sacco"), "MATATU"),
    TransportOperator("Gakanango Sacco", listOf("gakanango"), "MATATU"),
    TransportOperator("Namuga Sacco", listOf("namuga"), "MATATU"),
    TransportOperator("NYEE Sacco", listOf("nyee"), "MATATU"),
    TransportOperator("MENANY Sacco", listOf("menany"), "MATATU"),
    TransportOperator("MENYA Sacco", listOf("menya"), "MATATU"),
    TransportOperator("KETNNO Sacco", listOf("ketnno"), "MATATU"),
    TransportOperator("MTN Sacco", listOf("mtn sacco"), "MATATU"),
    TransportOperator("SATIMA Sacco", listOf("satima"), "MATATU"),
    TransportOperator("LINGANA Sacco", listOf("lingana"), "MATATU"),
    TransportOperator("KUKENA Sacco", listOf("kukena"), "MATATU"),
    TransportOperator("Namukika Sacco", listOf("namukika"), "MATATU"),
    TransportOperator("NAKONS Sacco", listOf("nakons"), "MATATU"),
    TransportOperator("MURIMA Sacco", listOf("murima"), "MATATU"),
    TransportOperator("KARWIKIKIMU Sacco", listOf("karwikikimu"), "MATATU"),
    TransportOperator("KASAMUTHI Sacco", listOf("kasamuthi"), "MATATU"),
    TransportOperator("KAT MOK Travellers", listOf("kat mok"), "MATATU"),
    TransportOperator("KATUMA Sacco", listOf("katuma"), "MATATU"),
    TransportOperator("Biashara Sacco", listOf("biashara sacco"), "MATATU"),
    TransportOperator("Amica Sacco", listOf("amica", "murata"), "MATATU"),
    TransportOperator("3NS Sacco", listOf("3ns"), "MATATU"),
    // ---- Nakuru area ----
    TransportOperator("Mololine", listOf("mololine"), "COACH"),
    TransportOperator("3NTO Sacco", listOf("3nto"), "MATATU"),
    TransportOperator("Bahama Sacco", listOf("bahama"), "MATATU"),
    TransportOperator("Naloki Sacco", listOf("naloki"), "MATATU"),
    TransportOperator("Phase Two Sacco", listOf("phase two"), "MATATU"),
    TransportOperator("Highway Travellers", listOf("highway travellers"), "MATATU"),
    TransportOperator("Njoro Line Sacco", listOf("njoro line"), "MATATU"),
    TransportOperator("Njoro Operators", listOf("njoro operators"), "MATATU"),
    TransportOperator("Mau-Narok Sacco", listOf("mau narok", "mau-narok"), "MATATU"),
    TransportOperator("Precious Sacco", listOf("precious sacco"), "MATATU"),
    TransportOperator("Shabab Sacco", listOf("shabab"), "MATATU"),
    TransportOperator("Northway Sacco", listOf("northway"), "MATATU"),
    TransportOperator("Nakamata Sacco", listOf("nakamata"), "MATATU"),
    TransportOperator("Kalaswa Sacco", listOf("kalaswa"), "MATATU"),
    TransportOperator("Jokehis Sacco", listOf("jokehis"), "MATATU"),
    TransportOperator("Star Of Jesus", listOf("star of jesus"), "MATATU"),
    TransportOperator("Likana", listOf("likana"), "MATATU"),
    TransportOperator("Supreme Shuttle", listOf("supreme shuttle"), "MATATU"),
    TransportOperator("Moline", listOf("moline"), "MATATU"),
    // ---- Eldoret area ----
    TransportOperator("Kitwek Sacco", listOf("kitwek"), "MATATU"),
    TransportOperator("Chepkoilel Sacco", listOf("chepkoilel"), "MATATU"),
    TransportOperator("Ebenezer Sacco", listOf("ebenezer sacco", "ebenezer matatu"), "MATATU"),
    TransportOperator("Ecosa Sacco", listOf("ecosa"), "MATATU"),
    TransportOperator("Eldoret Cross Road", listOf("eldoret cross road"), "MATATU"),
    TransportOperator("Eldoret Shuttle", listOf("eldoret shuttle"), "MATATU"),
    TransportOperator("Eldoret Victory", listOf("eldoret victory"), "MATATU"),
    TransportOperator("Eldoret Express", listOf("eldoret express"), "COACH"),
    TransportOperator("Nyaru Express", listOf("nyaru"), "MATATU"),
    TransportOperator("Ena Society", listOf("ena sacco", "ena cooperative"), "MATATU"),
    TransportOperator("Kipsinende Sacco", listOf("kipsinende"), "MATATU"),
    TransportOperator("Egesa Shuttle", listOf("egesa"), "MATATU"),
    TransportOperator("Eleventh Hour", listOf("eleventh hour"), "MATATU"),
    TransportOperator("Nandi North Sacco", listOf("nandi north"), "MATATU"),
    TransportOperator("Bliss and Prince", listOf("bliss and prince"), "COACH"),
    TransportOperator("HM Bus", listOf("hm bus"), "COACH"),
    TransportOperator("Vinoi Bus Union", listOf("vinoi"), "MATATU"),
    TransportOperator("Sawe Arap Samoe", listOf("sawe arap samoe"), "MATATU"),
    // ---- Machakos (MAMOA umbrella) ----
    TransportOperator("Mamaru Sacco", listOf("mamaru"), "MATATU"),
    TransportOperator("MAMAO Sacco", listOf("mamao"), "MATATU"),
    TransportOperator("MMG Sacco", listOf("mmg sacco"), "MATATU"),
    TransportOperator("KIM Sacco", listOf("kim sacco"), "MATATU"),
    TransportOperator("KATESH Sacco", listOf("katesh"), "MATATU"),
    TransportOperator("MCG Sacco", listOf("mcg sacco"), "MATATU"),
    TransportOperator("IMARA Sacco", listOf("imara sacco"), "MATATU"),
    TransportOperator("MIAMI BI Sacco", listOf("miami bi"), "MATATU"),
    TransportOperator("MIWAINNER Sacco", listOf("miwinner"), "MATATU"),
    TransportOperator("Travelers Sacco", listOf("travelers sacco"), "MATATU"),
    TransportOperator("Miwana Sacco", listOf("miwana"), "MATATU"),
    TransportOperator("Maingi Kinothya", listOf("maingi kinothya"), "MATATU"),
    TransportOperator("Kilimani Motors", listOf("kilimani motors"), "MATATU"),
    TransportOperator("Masewani Bus", listOf("masewani"), "MATATU"),
    TransportOperator("Ketmo Sacco", listOf("ketmo"), "MATATU"),
    TransportOperator("Mwea Public Road", listOf("mwea public"), "MATATU"),
    TransportOperator("Woni Wa Kiatuni", listOf("woni wa kiatuni", "kiatuni"), "MATATU"),
    TransportOperator("Kivunzya", listOf("kivunzya"), "MATATU"),
    TransportOperator("Green Valley Co", listOf("green valley"), "MATATU"),
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
