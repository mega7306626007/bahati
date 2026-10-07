package com.pesaflow.app.data.ml

/**
 * Canonical merchant catalogue for Kenya, derived from:
 * - existing parser keyword lists (MpesaParser, NaturalLanguageParser audit)
 * - web research: Naivas 105 branches, Quickmart 60, Carrefour 23,
 *   Chandarana 26 (Cytonn Kenya Retail Report 2024; Tuko 2025)
 * - Nairobi matatu fare bands KES 30-200 (KenyaHub, KNBS CPI Dec 2024)
 * - M-Pesa SMS templates (Safaricom, reply.cash docs)
 *
 * Each entry maps alias spellings -> canonical name + default category.
 * Source provenance is kept per entry; user corrections override via
 * ModelFeedback (not yet wired — Phase 3).
 */
data class MerchantEntry(
    val canonical: String,
    val category: String,
    val aliases: List<String>,
    val source: String = "BUNDLED_DATASET_v1"
)

object MerchantDictionary {
    val entries: List<MerchantEntry> = listOf(
        // Supermarkets (Cytonn 2024 branch counts)
        MerchantEntry("Naivas", "Shopping", listOf("naivas", "naivas supermarket", "rongai self service")),
        MerchantEntry("Quickmart", "Shopping", listOf("quickmart", "quick mart", "tumaini")),
        MerchantEntry("Carrefour", "Shopping", listOf("carrefour", "majid al futtaim")),
        MerchantEntry("Chandarana", "Shopping", listOf("chandarana", "chandarana foodplus")),
        MerchantEntry("Eastmatt", "Shopping", listOf("eastmatt", "eastmatt supermarket")),
        MerchantEntry("Cleanshelf", "Shopping", listOf("cleanshelf")),
        MerchantEntry("Magunas", "Shopping", listOf("magunas")),
        MerchantEntry("Khetia's", "Shopping", listOf("khetia", "khethia")),
        MerchantEntry("Woolmatt", "Shopping", listOf("woolmatt")),
        MerchantEntry("Jumaa", "Shopping", listOf("jumaa")),
        // Food chains
        MerchantEntry("Java House", "Food", listOf("java", "java house", "java express", "kukito", "360 degrees")),
        MerchantEntry("KFC", "Food", listOf("kfc", "kentucky")),
        MerchantEntry("Chicken Cottage", "Food", listOf("chicken cottage")),
        MerchantEntry("ChicKing", "Food", listOf("chicking", "chic king")),
        MerchantEntry("Mama Mboga", "Food", listOf("mama mboga", "mboga", "kibanda", "vibanda", "smocha", "sukuma")),
        MerchantEntry("Campus Mess", "Food", listOf("mess", "main mess", "annex", "campus canteen", "canteen")),
        // Transport
        MerchantEntry("Matatu", "Transport", listOf("matatu", "mat", "stage", "fare", "nauli", "makanga", "embassava", "kbs", "city hoppa", "city shuttle", "tawala", "lavender", "ndebe")),
        MerchantEntry("Boda", "Transport", listOf("boda", "bodaboda", "boda boda", "motorbike")),
        MerchantEntry("Uber", "Transport", listOf("uber")),
        MerchantEntry("Bolt", "Transport", listOf("bolt", "taxify")),
        MerchantEntry("SGR", "Transport", listOf("sgr", "madaraka express")),
        MerchantEntry("Easy Coach", "Transport", listOf("easy coach", "easycoach")),
        MerchantEntry("North Rift Shuttle", "Transport", listOf("north rift", "northrift")),
        MerchantEntry("Great Rift Shuttle", "Transport", listOf("great rift", "greatrift")),
        // Utilities
        MerchantEntry("KPLC Prepaid", "Electricity", listOf("kplc", "token", "stima", "888880")),
        MerchantEntry("KPLC Postpaid", "Electricity", listOf("kplc postpaid", "888888")),
        MerchantEntry("Nairobi Water", "Water", listOf("nairobi water", "water vendor", "maji")),
        MerchantEntry("Safaricom", "Airtime", listOf("safaricom", "saf", "airtime", "okoa", "okoa jahazi", "bonga", "credo")),
        MerchantEntry("Airtel", "Airtime", listOf("airtel")),
        MerchantEntry("Telkom", "Airtime", listOf("telkom", "t-kash", "tkash")),
        MerchantEntry("Faiba", "Data", listOf("faiba", "jtl", "330330")),
        MerchantEntry("Zuku", "Data", listOf("zuku", "wifi", "fibre")),
        // Banks & lenders
        MerchantEntry("KCB", "Transfer", listOf("kcb", "kcb m-pesa")),
        MerchantEntry("Equity", "Transfer", listOf("equity")),
        MerchantEntry("Co-operative Bank", "Transfer", listOf("co-op", "coop", "cooperative")),
        MerchantEntry("ABSA", "Transfer", listOf("absa")),
        MerchantEntry("Stanbic", "Transfer", listOf("stanbic")),
        MerchantEntry("Family Bank", "Transfer", listOf("family bank")),
        MerchantEntry("DTB", "Transfer", listOf("dtb")),
        MerchantEntry("NCBA", "Transfer", listOf("ncba")),
        MerchantEntry("M-Shwari", "Savings", listOf("m-shwari", "mshwari", "shwari")),
        MerchantEntry("KCB M-Pesa Loan", "Debt", listOf("kcb m-pesa loan")),
        MerchantEntry("M-Shwari Loan", "Debt", listOf("m-shwari loan", "mshwari loan")),
        MerchantEntry("Fuliza", "Debt", listOf("fuliza")),
        MerchantEntry("Tala", "Debt", listOf("tala")),
        MerchantEntry("Branch", "Debt", listOf("branch")),
        MerchantEntry("Zenka", "Debt", listOf("zenka")),
        MerchantEntry("Hustler Fund", "Debt", listOf("hustler", "hustler fund")),
        MerchantEntry("HELB", "HELB", listOf("helb", "higher education loans board")),
        // Income sources
        MerchantEntry("Parent Upkeep", "Parent", listOf("mum", "dad", "mother", "father", "parent", "upkeep", "allowance")),
        MerchantEntry("Pochi La Biashara", "Business", listOf("pochi", "pochi la biashara")),
        // Health / school / personal
        MerchantEntry("Pharmacy", "Health", listOf("pharmacy", "chemist", "clinic", "hospital", "dawa", "daktari")),
        MerchantEntry("Cyber Cafe", "Printing", listOf("cyber", "print", "photocopy", "stationery", "kalamu")),
        MerchantEntry("Salon", "Kujibamba", listOf("salon", "barber", "kinyozi", "nails", "plot")),
        MerchantEntry("Chama", "Savings", listOf("chama", "sacco")),
        // Verified paybills (paybillke 2026, KPLC vendor list, Safaricom bank codes PDF)
        MerchantEntry("KPLC Prepaid 888880", "Electricity", listOf("888880", "kplc prepaid", "kplc tokens")),
        MerchantEntry("KPLC Postpaid 888888", "Electricity", listOf("888888", "kplc postpaid")),
        MerchantEntry("eCitizen Government", "School", listOf("222222", "government paybill", "ecitizen government")),
        MerchantEntry("SHA Health", "Health", listOf("sha", "social health authority", "200222")),
        MerchantEntry("HELB Repayment", "Debt", listOf("200800", "helb repayment")),
        MerchantEntry("NSSF", "Savings", listOf("333300", "nssf")),
        MerchantEntry("Huduma Kenya", "School", listOf("191919", "huduma")),
        MerchantEntry("Safaricom Postpaid", "Airtime", listOf("200200", "safaricom postpaid")),
        MerchantEntry("KCB Bank Paybill", "Transfer", listOf("522522", "kcb paybill")),
        MerchantEntry("Airtel Paybill", "Airtime", listOf("220220", "airtel paybill")),
        // Extra retail + food coverage (Cytonn 2024, campus price research)
        MerchantEntry("Jaza Stores", "Shopping", listOf("jaza", "jaza stores")),
        MerchantEntry("Panda Mart", "Shopping", listOf("panda mart", "panda")),
        MerchantEntry("China Square", "Shopping", listOf("china square")),
        MerchantEntry("Ugali Pambana", "Food", listOf("ugali pambana", "pambana")),
        MerchantEntry("Chafua", "Food", listOf("chafua", "chapati beans")),
        MerchantEntry("Chapo Choma", "Food", listOf("chapo choma")),
        MerchantEntry("Smocha", "Food", listOf("smocha", "smokie chapati")),
        MerchantEntry("Mutura", "Food", listOf("mutura")),
        MerchantEntry("Mahindi Choma", "Food", listOf("mahindi choma", "mahindi")),
        MerchantEntry("Mandazi", "Food", listOf("mandazi", "andazi"))
    )

    private val aliasIndex: Map<String, MerchantEntry> by lazy {
        entries.flatMap { e -> e.aliases.map { it.lowercase() to e } }.toMap()
    }

    /** Exact alias lookup. Returns null when unknown — caller falls back to classifier. */
    fun lookup(raw: String): MerchantEntry? = aliasIndex[raw.lowercase().trim()]

    /**
     * Fuzzy match via normalized Levenshtein. maxDistance 2 keeps precision high
     * on short matatu/sheng spellings without over-matching (cf. audit note on
     * loose 'ng'/'rest' substrings in inferCategory).
     */
    fun fuzzy(raw: String, maxDistance: Int = 2): MerchantEntry? {
        val q = raw.lowercase().trim()
        lookup(q)?.let { return it }
        var best: MerchantEntry? = null
        var bestDist = Int.MAX_VALUE
        for (e in entries) {
            for (a in e.aliases) {
                val d = levenshtein(q, a)
                if (d < bestDist && d <= maxDistance && q.length >= 4) {
                    bestDist = d
                    best = e
                }
            }
        }
        return best
    }

    internal fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }
}
