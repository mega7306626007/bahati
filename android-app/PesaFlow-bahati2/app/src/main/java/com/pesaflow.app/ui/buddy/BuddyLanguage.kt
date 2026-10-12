package com.pesaflow.app.ui.buddy

import com.pesaflow.app.data.models.AppLanguage

// Per-message language resolution. Buddy understands three languages; now
// it answers in the one the user just used. Sheng wins ties (it borrows
// Swahili grammar), clear Swahili needs 2+ markers so a lone "na" inside
// English never flips the answer. Anything unclear falls back to the global
// setting; MIXED + unclear keeps the current English-with-flavor voice.
// Pure, unit-tested.
object BuddyLanguage {

    // Note: "sasa" is deliberately absent — it means "now" in Swahili and
    // only sometimes a greeting in Sheng. Greetings below are unambiguous.
    private val SHENG_MARKERS = listOf(
        "niaje", "mambo", "poa", "sawa", "mullah", "doh", "chapaa",
        "ganji", "cheddar", "bob", "soo", "mathree", "fiti", "noma",
        "maze", "mbogi", "form", "janje", "wasee", "buda", "mdosi", "mbesha"
    )

    private val SWAHILI_MARKERS = listOf(
        "niko", "ngapi", "gani", "yangu", "yako", "yetu", "kwa", "habari",
        "asante", "shukran", "tafadhali", "hujambo", "sijambo", "ndiyo",
        "hapana", "kiasi", "lini", "wapi", "nini", "kwanini", "mbona",
        "pole", "karibu", "safi", "nzuri", "nyingi", "kidogo", "pesa",
        "fedha", "hela", "salio", "baki", "deni", "madeni", "matumizi",
        "gharama", "mapato", "akiba", "bajeti", "mshahara", "posho",
        "nauli", "chakula", "kula", "kunywa", "lipa", "lipia", "kopa",
        "tumia", "ongeza", "weka", "toa", "tuma", "kumbusha", "fungua",
        "angalia", "onyesha", "nipe", "nionyeshe", "eleza", "nisaidie",
        "saidia", "unaeza", "naeza", "nataka", "ninaomba", "naomba",
        "tarehe", "siku", "wiki", "mwezi", "mwaka", "leo", "jana", "kesho",
        "shilingi", "bei", "ninahitaji", "unga", "mboga"
    )

    fun detectQuery(raw: String): AppLanguage? {
        val q = " ${raw.lowercase()} "
        fun hits(words: List<String>): Int =
            words.count { w ->
                Regex("(?<![a-z])${Regex.escape(w)}(?![a-z])").containsMatchIn(q)
            }
        if (hits(SHENG_MARKERS) > 0) return AppLanguage.SHENG
        if (hits(SWAHILI_MARKERS) >= 2) return AppLanguage.KISWAHILI
        return null
    }

    fun resolve(raw: String, global: AppLanguage): AppLanguage =
        detectQuery(raw) ?: if (global == AppLanguage.MIXED) AppLanguage.ENGLISH else global
}
