package com.pesaflow.app.ui.budgets

import com.pesaflow.app.data.models.BudgetType
import java.util.Calendar
import kotlin.math.round

enum class LivingSituation(val label: String, val short: String) {
    PARENT_FAR("With parents · long commute", "Parents · far"),
    PARENT_NEAR("With parents · short hop", "Parents · near"),
    RENT_WALK("Rent · walk to class", "Rent · walk"),
    RENT_COMMUTE("Rent · commute daily", "Rent · commute"),
    HOSTEL_COOK("Hostel · I cook", "Hostel · cook"),
    HOSTEL_BUY("Hostel · can't cook", "Hostel · buy");

    // Parents pay no rent: the engine must never price a roof they have.
    val hasRent: Boolean get() = this != PARENT_FAR && this != PARENT_NEAR
    // Long daily routes need protected fares; walkers get a token buffer.
    val commuteHeavy: Boolean get() = this == PARENT_FAR || this == RENT_COMMUTE
    val commuteLight: Boolean get() = this == PARENT_NEAR
    val cantCook: Boolean get() = this == HOSTEL_BUY
}

enum class BudgetRule(val label: String) {
    CAMPUS("Campus Survival"),
    SPLIT("50/30/20")
}

// Lifestyle presets: same engine, different appetites. Early commuters who
// skip lunch want transport-first; foodies protect Food; savers push Savings.
enum class LifestylePreset(val label: String) {
    BALANCED("Balanced"),
    COMMUTER_LITE("Commuter lite"),
    FOODIE("Foodie"),
    SAVER("Saver")
 }

data class BudgetSuggestion(val category: String, val amount: Int, val percent: Int, val reason: String, val shielded: Boolean)

data class SmartBudgetResult(
    val suggestions: List<BudgetSuggestion>,
    val dropped: List<String>,
    val tightMode: Boolean,
    val summary: String,
    val periodName: String
)

private data class TierDef(
    val category: String,
    val weight: Double,
    val tier: Int, // 1 survival, 2 study, 3 mobility, 4 stability, 5 lifestyle
    val floorMonthly: Int = 0,
    val commuterOnly: Boolean = false
)

// Survival-first campus tiers. Food + Rent always win (Rent vanishes for
// students living with parents — never budget a roof they already have);
// Transport scales with the real route; Food scales with kitchen access.
private data class GroupProfile(
    val rentWeight: Double, val rentFloor: Int,
    val transportWeight: Double, val transportFloor: Int,
    val foodWeight: Double, val foodFloor: Int,
    val tokenCapTransport: Boolean
)

private fun profileOf(living: LivingSituation) = when (living) {
    // Home-fed students eat at home (~KSh 50/day): food floor 1500, never
    // 2000+. The fare — not food — is the protected envelope for PARENT_FAR.
    LivingSituation.PARENT_FAR -> GroupProfile(0.0, 0, 18.0, 2000, 20.0, 1500, false)
    LivingSituation.PARENT_NEAR -> GroupProfile(0.0, 0, 6.0, 400, 20.0, 1500, true)
    LivingSituation.RENT_WALK -> GroupProfile(26.0, 2000, 5.0, 0, 28.0, 3000, true)
    LivingSituation.RENT_COMMUTE -> GroupProfile(26.0, 2000, 15.0, 1200, 28.0, 3000, false)
    LivingSituation.HOSTEL_COOK -> GroupProfile(26.0, 2000, 5.0, 0, 28.0, 3000, true)
    LivingSituation.HOSTEL_BUY -> GroupProfile(26.0, 2000, 5.0, 0, 34.0, 4500, true)
}

private fun campusTiers(living: LivingSituation, includeTransport: Boolean): List<TierDef> {
    val p = profileOf(living)
    val transportWeight = if (!includeTransport) 0.0 else p.transportWeight
    return listOf(
        TierDef("Food", p.foodWeight, 1, floorMonthly = p.foodFloor),
        TierDef("Rent", p.rentWeight, 1, floorMonthly = p.rentFloor),
        TierDef("Transport", transportWeight, 3, floorMonthly = p.transportFloor, commuterOnly = true),
        TierDef("School", 10.0, 2, floorMonthly = 300),
        TierDef("Data", 4.0, 2),
        TierDef("Airtime", 3.0, 2),
        TierDef("Water", 2.0, 1, floorMonthly = 200),
        TierDef("Electricity", 2.0, 1, floorMonthly = 300),
        TierDef("Health", 3.0, 1, floorMonthly = 200),
        TierDef("Savings", 10.0, 4),
        TierDef("Debt", 4.0, 4),
        TierDef("Entertainment", 3.0, 5),
        TierDef("Shopping", 3.0, 5),
        TierDef("Personal Care", 2.0, 5),
        TierDef("Other", 0.0, 5)
    ).filter { it.weight > 0 }
}

private fun splitTiers(includeTransport: Boolean, hasRent: Boolean = true): List<TierDef> = listOf(
    TierDef("Rent", if (hasRent) 20.0 else 0.0, 1, floorMonthly = if (hasRent) 2000 else 0),
    TierDef("Food", 15.0, 1, floorMonthly = 3000),
    TierDef("Transport", if (includeTransport) 8.0 else 0.0, 3, commuterOnly = true),
    TierDef("Bills", 7.0, 4),
    TierDef("Savings", 20.0, 4),
    TierDef("Entertainment", 12.0, 5),
    TierDef("Shopping", 10.0, 5),
    TierDef("Personal Care", 8.0, 5)
).filter { it.weight > 0 }

/** Monthly suggestion → this period's slice, using the REAL days in the
 * current month — not a hard 30. February's daily slice is 1/28, not 1/30. */
fun periodScale(
    type: BudgetType,
    daysInMonth: Int = Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH)
): Double = when (type) {
    BudgetType.DAILY -> 1.0 / daysInMonth
    BudgetType.WEEKLY -> 7.0 / daysInMonth
    BudgetType.MONTHLY -> 1.0
    BudgetType.SEMESTER -> 4.0
    BudgetType.ANNUAL -> 12.0
}

fun periodNameOf(type: BudgetType): String = when (type) {
    BudgetType.DAILY -> "daily"
    BudgetType.WEEKLY -> "weekly"
    BudgetType.MONTHLY -> "monthly"
    BudgetType.SEMESTER -> "semester"
    else -> "annual"
}

/**
 * Smart budget engine — survival first when money is tight.
 * - Funds tier 1 (Food/Rent/Water/Power/Health) floors before anything else.
 * - Transport is optional: full commuter weight, token hostel amount, removable.
 * - Savings/lifestyle drop to zero in tight mode instead of starving Food.
 * - Never invents spending history; bill floors + averages are passed in by callers.
 */
fun smartBudget(
    monthlyBase: Double,
    rule: BudgetRule,
    period: BudgetType,
    living: LivingSituation,
    includeTransport: Boolean,
    openBillByCategory: Map<String, Int> = emptyMap(),
    avg90ByCategory: Map<String, Int> = emptyMap(),
    style: LifestylePreset = LifestylePreset.BALANCED,
    // The user's own daily fare (onboarding / matatu preset). For heavy
    // commuters it sets a Transport floor of fare × 30 — your number beats
    // thin history so fares are never underestimated.
    fareDaily: Double = 0.0
): SmartBudgetResult {
    val scale = periodScale(period)
    val periodName = periodNameOf(period)
    val step = if (period == BudgetType.DAILY) 10.0 else 50.0
    val tiers = if (rule == BudgetRule.CAMPUS) campusTiers(living, includeTransport)
    else splitTiers(includeTransport, living.hasRent)
    // Lifestyle preset reshapes weights before anything else runs.
    val styledTiers = tiers.map { t ->
        val w = when (style) {
            LifestylePreset.COMMUTER_LITE -> when (t.category) {
                "Food" -> 20.0
                "Transport" -> 22.0
                "Entertainment" -> 1.0
                "Shopping" -> 1.0
                else -> t.weight
            }
            LifestylePreset.FOODIE -> when (t.category) {
                "Food" -> 36.0
                "Entertainment" -> 5.0
                else -> t.weight
            }
            LifestylePreset.SAVER -> when (t.category) {
                "Savings" -> 18.0
                "Entertainment" -> 1.0
                "Shopping" -> 1.0
                "Personal Care" -> 1.0
                else -> t.weight
            }
            else -> t.weight
        }
        t.copy(weight = w)
    }

    // Tight mode is relative, never a fixed shilling "truth": the base is
    // tight when it can't cover 1.5× YOUR survival floors. KSh 8000 is a
    // feast for one student and starvation for another.
    val floorTotal = styledTiers.filter { it.tier == 1 }.sumOf { it.floorMonthly }
    val tightMode = monthlyBase < floorTotal * 1.5

    val totalWeight = styledTiers.sumOf { it.weight }.coerceAtLeast(1.0)
    val ordered = styledTiers.sortedWith(compareBy({ it.tier }, { -it.weight }))

    // Phase 1: floors for survival tier, scaled.
    val floorScaled = ordered.associate { t ->
        t.category to if (t.tier == 1) round(t.floorMonthly * scale / step) * step else 0.0
    }
    var floorSum = floorScaled.values.sum()
    var remaining = (monthlyBase * scale - floorSum).coerceAtLeast(0.0)

    // Phase 2: share remaining by weight, lifestyle last (zeroed in tight mode).
    // Then blend with the user's own 90-day average per category (±30% clamp):
    // history personalises the weights instead of only decorating the reason.
    val alloc = mutableMapOf<String, Double>()
    ordered.forEach { t ->
        var share = if (totalWeight > 0) remaining * (t.weight / totalWeight) else 0.0
        if (tightMode && (t.tier == 5 || (t.tier == 4 && t.category == "Savings"))) share = 0.0
        if (tightMode && t.tier == 3 && t.commuterOnly && profileOf(living).tokenCapTransport) {
            // Token cap scales with the base (3%, min KSh 500): a bigger
            // budget keeps a bigger hop, never a fixed 500 for everyone.
            share = minOf(share, maxOf(500 * scale, monthlyBase * scale * 0.03))
        }
        val avgHist = avg90ByCategory.entries.firstOrNull { it.key.equals(t.category, ignoreCase = true) }?.value?.toDouble() ?: 0.0
        if (avgHist > 0 && share > 0) {
            share = (0.5 * share + 0.5 * avgHist).coerceIn(avgHist * 0.7, avgHist * 1.3)
        }
        // Your fare wins: heavy commuters get at least fare × 30 monthly,
        // even when the ledger's transport history is thin (cash fares).
        if (t.category == "Transport" && fareDaily > 0 && living.commuteHeavy && includeTransport) {
            share = maxOf(share, fareDaily * 30 * scale)
        }
        alloc[t.category] = (floorScaled[t.category] ?: 0.0) + share
    }

    // Phase 3: neat rounding + bill floors (monthly only) + drop dust.
    val suggestions = mutableListOf<BudgetSuggestion>()
    val dropped = mutableListOf<String>()
    ordered.forEach { t ->
        val raw = alloc[t.category] ?: 0.0
        var neat = (round(raw / step) * step).toInt()
        val billFloor = if (period == BudgetType.MONTHLY) {
            openBillByCategory.entries.firstOrNull { it.key.equals(t.category, ignoreCase = true) }?.value ?: 0
        } else 0
        if (neat < billFloor) neat = billFloor
        val avg = avg90ByCategory.entries.firstOrNull { it.key.equals(t.category, ignoreCase = true) }?.value ?: 0
        if (neat <= 0 && billFloor <= 0) {
            dropped.add(t.category)
        } else {
            val pct = if (monthlyBase > 0) ((neat / (monthlyBase * scale)) * 100).toInt() else 0
            val reason = when {
                billFloor > 0 && neat <= billFloor -> "Covers your open ${t.category.lowercase()} bill"
                t.category == "Food" -> if (living.cantCook) "No kitchen — buying every meal costs more, funded first"
                    else if (tightMode) "Protected first — eating comes before everything" else "Survival tier — funded first"
                t.category == "Rent" -> "Roof over your head — non-negotiable"
                t.category == "Transport" && fareDaily > 0 && living.commuteHeavy -> "Your daily fare × 30 — your number, protected"
                t.commuterOnly && living.commuteHeavy -> "Daily long-route reality — protect these fares"
                t.commuterOnly && living.commuteLight -> "Short-hop buffer — remove it if you truly walk"
                t.commuterOnly -> "Small fare buffer — remove it if you truly walk everywhere"
                t.category == "Savings" && tightMode -> "Paused while money is tight — resume when base grows"
                t.tier == 5 && tightMode -> "Cut in tight mode — add back when base grows"
                avg > neat -> "Under your 3-month avg (KSh $avg) — stretch goal"
                else -> "Tier ${t.tier} · ${t.weight.toInt()}% weight"
            }
            suggestions.add(BudgetSuggestion(t.category, neat, pct, reason, t.tier == 1))
        }
    }

    val total = suggestions.sumOf { it.amount }
    val noRentNote = if (!living.hasRent) "No rent — home. 🏠 " else ""
    val routeNote = when {
        includeTransport && living.commuteHeavy -> "Long-route fares in."
        includeTransport && living.commuteLight -> "short-hop fares in."
        includeTransport -> "light fares in."
        else -> "transport out."
    }
    val summary = if (tightMode) {
        "Tight mode: KSh ${monthlyBase.toInt()}/mo funds Food" + (if (living.hasRent) " + Rent" else "") + " first. " +
            noRentNote +
            "${if (includeTransport && living.commuteHeavy) "Transport kept (long route). " else if (!includeTransport) "Transport removed. " else ""}" +
            "Savings + lifestyle paused — KSh ${total.toInt()} $periodName planned."
    } else {
        "KSh ${monthlyBase.toInt()}/mo → KSh ${total.toInt()} $periodName across ${suggestions.size} envelopes " +
            "($noRentNote$routeNote)"
    }
    return SmartBudgetResult(suggestions, dropped, tightMode, summary, periodName)
}

// Daily fare guard: heavy commuters must not "spend" tomorrow's matatu
// money today. Safe-to-spend holds this back first, like bills and plans.
fun dailyFareReserve(group: LivingSituation): Int = when (group) {
    LivingSituation.PARENT_FAR -> 65
    LivingSituation.RENT_COMMUTE -> 40
    LivingSituation.PARENT_NEAR -> 15
    else -> 0
}

// Scaled guard: when YOUR daily fare is known (Transport budget ÷ 30 or the
// onboarding figure), heavy commuters reserve that number instead of the
// group token. 800/day and 200/day students get different shields.
fun scaledFareReserve(group: LivingSituation, fareDaily: Double = 0.0): Int =
    if (fareDaily > 0 && group.commuteHeavy) fareDaily.toInt() else dailyFareReserve(group)

fun parseLiving(answers: String): LivingSituation {
    val token = answers.split("|").firstOrNull { it.startsWith("living=") }?.removePrefix("living=")?.trim()?.uppercase()
    return when (token) {
        "PARENT_FAR" -> LivingSituation.PARENT_FAR
        "PARENT_NEAR" -> LivingSituation.PARENT_NEAR
        "RENT_WALK" -> LivingSituation.RENT_WALK
        "RENT_COMMUTE" -> LivingSituation.RENT_COMMUTE
        "HOSTEL_COOK" -> LivingSituation.HOSTEL_COOK
        "HOSTEL_BUY" -> LivingSituation.HOSTEL_BUY
        // Legacy two-group answers predate the six-group model.
        "COMMUTER" -> LivingSituation.RENT_COMMUTE
        "HOSTEL" -> LivingSituation.HOSTEL_COOK
        else -> LivingSituation.HOSTEL_COOK
    }
}

// Housing × distance × kitchen → one of the six groups. Cooking only splits
// hostel life; parents feed you; renters and solo living decide meal by meal.
fun deriveLivingGroup(housing: String, distance: String, cooks: Boolean): LivingSituation = when (housing) {
    "Parents" -> if (distance == "Long") LivingSituation.PARENT_FAR else LivingSituation.PARENT_NEAR
    "Rent", "Alone" -> if (distance == "Walk") LivingSituation.RENT_WALK else LivingSituation.RENT_COMMUTE
    else -> if (cooks) LivingSituation.HOSTEL_COOK else LivingSituation.HOSTEL_BUY
}
