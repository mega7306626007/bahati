package com.pesaflow.app.data.finance

// Explanation engine (§20, Phase 10): every hero number builds its
// three-level story here (headline → human why → full contributors).
// Extracted from buildSnapshot with identical copy — Level 3 detail
// (full trees) stays available via the contributors lists.
fun explainSafeToday(
    headline: Money,
    liquid: Money,
    committed: Money,
    essentialDaily: Double,
    buffer: Money,
    basis: String,
    quality: DataQuality
) = MetricExplanation(
    label = "Safe to spend today",
    headline = headline,
    horizon = "today",
    why = "Liquid money minus what is already committed, today's share of your usual spending and a safety buffer sized by income assurance.",
    contributors = listOf(
        "Held ${MoneyFormatter.compact(liquid)}",
        "Committed ${MoneyFormatter.compact(committed)}",
        "Usual day ≈ ${MoneyFormatter.compact(Money.of(essentialDaily))}",
        "Buffer ${MoneyFormatter.compact(buffer)}"
    ),
    basis = basis,
    quality = quality
)

fun explainFlexible(
    headline: Money,
    liquid: Money,
    bills: Money,
    debts: Money,
    reserved: Money,
    obligationCount: Int,
    reservationCount: Int,
    quality: DataQuality
) = MetricExplanation(
    label = "Flexible money",
    headline = headline,
    horizon = "now",
    why = "Held cash minus everything already spoken for: bills, debts, goal and fee reservations.",
    contributors = listOf(
        "Held ${MoneyFormatter.compact(liquid)}",
        "Bills ${MoneyFormatter.compact(bills)}",
        "Debts owed ${MoneyFormatter.compact(debts)}",
        "Reserved ${MoneyFormatter.compact(reserved)}"
    ),
    basis = "$obligationCount open obligations, $reservationCount goal reservations",
    quality = quality
)

fun explainForecast(
    headline: Money,
    paceTypical: Double,
    cautious: Money,
    favourable: Money,
    quality: DataQuality
) = MetricExplanation(
    label = "Typical month-end position",
    headline = headline,
    horizon = "month end",
    why = "Current flexible money, typical daily pace, expected income and bills due before month end.",
    contributors = listOf(
        "Typical pace ${MoneyFormatter.compact(Money.of(paceTypical))}/day",
        "Cautious ${MoneyFormatter.compact(cautious)} · favourable ${MoneyFormatter.compact(favourable)}"
    ),
    basis = "28-day median pace, one-offs quarantined",
    quality = quality
)

fun explainNetWorth(
    headline: Money,
    assets: Money,
    liabilities: Money,
    quality: DataQuality
) = MetricExplanation(
    label = "Net worth",
    headline = headline,
    horizon = "now",
    why = "Everything owned (liquid + savings + investments) minus everything owed.",
    contributors = listOf(
        "Assets ${MoneyFormatter.compact(assets)}",
        "Owed ${MoneyFormatter.compact(liabilities)}"
    ),
    basis = "ledger + debt book",
    quality = quality
)
