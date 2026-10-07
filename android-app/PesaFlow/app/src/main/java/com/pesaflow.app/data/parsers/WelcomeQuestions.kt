package com.pesaflow.app.data.parsers

// Welcome question engine: ~20 signals exist behind the screen (scan
// monthly/food/rent/transport/breakfast, faces, clusters, campus DB, hostel
// fees, living picks) but the user only ever sees the handful that earn
// their place. Two rules, no exceptions:
//  1. Never ask what we already know (scan, campus DB, or derivation).
//  2. Never ask what is irrelevant (housing × commute gates).
// One-tap chips (housing, commute, worry, chama) always show — a tap is
// not a question. Typed amounts must justify themselves.
enum class WelcomeQ { SPEND, INCOME, SPONSOR, RENT, TRANSPORT, AIRTIME, SAVE }

data class WelcomeFacts(
    val housing: String,
    val commute: String,
    val scanMonthly: Boolean = false,
    val scanFood: Boolean = false,
    val scanRent: Boolean = false,
    val scanTransport: Boolean = false
)

fun relevantAmounts(f: WelcomeFacts): List<WelcomeQ> {
    val out = mutableListOf<WelcomeQ>()
    // Daily spend seeds the master budget — unless the scan already
    // measured the month. It is the only droppable core question.
    if (!f.scanMonthly) out.add(WelcomeQ.SPEND)
    // Inflows are unknowable from outflows: always earned.
    out.add(WelcomeQ.INCOME)
    out.add(WelcomeQ.SPONSOR)
    if (shouldAskRent(f.housing)) out.add(WelcomeQ.RENT)
    if (shouldAskTransport(f.commute)) out.add(WelcomeQ.TRANSPORT)
    out.add(WelcomeQ.AIRTIME)
    out.add(WelcomeQ.SAVE)
    return out
}

// Prefill precedence for the review/finish block, shared so the screen and
// the saver can never disagree: typed > guess > M-Pesa scan. Chain it for
// three levels: pickAmount(typed, pickAmount(guess, scan, "guess")?.first).
fun pickAmount(typed: Double?, scanned: Double?, typedName: String = "you"): Pair<Double, String>? =
    when {
        typed != null && typed > 0 -> typed to typedName
        scanned != null && scanned > 0 -> scanned to "scan"
        else -> null
    }
