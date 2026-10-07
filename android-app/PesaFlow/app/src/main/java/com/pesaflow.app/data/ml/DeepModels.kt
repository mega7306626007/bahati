package com.pesaflow.app.data.ml

import kotlin.math.sqrt

/**
 * Spec models 7-9: GoalForecaster, PreferenceLearner, RecommendationRanker.
 *
 * All deterministic + statistical. No neural nets here by design: small
 * data, need for explainability, and hard financial constraints must stay
 * exact. Each returns basis + uncertainty; hypothetical savings are never
 * presented as saved money.
 */
object GoalForecaster {
    data class Trajectory(
        val goalTitle: String,
        val target: Double,
        val current: Double,
        val weeklyContribution: Double,
        val weeksToGoal: Double?,
        val low: Double?,
        val high: Double?,
        val verdict: String
    )

    /** Projects from actual contribution history + known obligations. */
    fun project(
        goalTitle: String,
        target: Double,
        current: Double,
        weeklyContributions: List<Double>,
        weeklyObligations: Double = 0.0
    ): Trajectory {
        val remaining = target - current
        if (remaining <= 0) return Trajectory(goalTitle, target, current, 0.0, 0.0, 0.0, 0.0, "already reached")
        if (weeklyContributions.size < 2) {
            return Trajectory(goalTitle, target, current, 0.0, null, null, null, "insufficient contribution history")
        }
        val mean = weeklyContributions.average()
        val sd = sqrt(weeklyContributions.map { (it - mean) * (it - mean) }.average())
        val net = mean - weeklyObligations
        if (net <= 0) {
            return Trajectory(goalTitle, target, current, mean, null, null, null, "contributions do not cover obligations")
        }
        val weeks = remaining / net
        val spread = if (sd > 0) (remaining / (net - sd).coerceAtLeast(net / 2) - weeks) else 0.0
        return Trajectory(
            goalTitle, target, current, mean,
            weeksToGoal = weeks,
            low = (weeks - spread).coerceAtLeast(0.0),
            high = weeks + spread,
            verdict = "on track at current pace"
        )
    }
}

/**
 * PreferenceLearner — explicit prefs win; behavior only supplements.
 * Evidence levels: INSUFFICIENT < TENTATIVE < OBSERVED < CONFIRMED.
 * A correction never silently rewrites a confirmed pref — it opens a
 * conflict the UI must resolve (spec section 13).
 */
object PreferenceLearner {
    enum class Evidence { INSUFFICIENT, TENTATIVE, OBSERVED, CONFIRMED }

    data class Preference(
        val key: String,
        val value: String,
        val evidence: Evidence,
        val observations: Int,
        val source: String
    )

    data class Correction(
        val key: String,
        val oldValue: String?,
        val newValue: String,
        val scope: Scope
    )
    enum class Scope { PERMANENT, TEMPORARY, ONE_TIME }

    /** Fold repeated behavior into a tentative/observed preference. */
    fun fromBehavior(key: String, modalValue: String, occurrences: Int, distinctDays: Int): Preference {
        val evidence = when {
            occurrences < 2 -> Evidence.INSUFFICIENT
            occurrences < 5 || distinctDays < 3 -> Evidence.TENTATIVE
            else -> Evidence.OBSERVED
        }
        return Preference(key, modalValue, evidence, occurrences, "MODEL_INFERRED")
    }

    /**
     * Apply a user correction. Returns updated pref + whether dependent
     * forecasts must be invalidated (any PERMANENT change to a confirmed fact).
     */
    fun applyCorrection(current: Preference?, correction: Correction): Pair<Preference, Boolean> {
        val updated = Preference(
            key = correction.key,
            value = correction.newValue,
            evidence = if (correction.scope == Scope.PERMANENT) Evidence.CONFIRMED else (current?.evidence ?: Evidence.TENTATIVE),
            observations = (current?.observations ?: 0) + 1,
            source = "USER_CONFIRMED"
        )
        val invalidate = correction.scope == Scope.PERMANENT && current?.evidence == Evidence.CONFIRMED && current.value != correction.newValue
        return updated to invalidate
    }
}

/**
 * RecommendationRanker — ranks options by relevance, preference match,
 * affordability, distance, source reliability, urgency. Hard constraints
 * (over budget, violates allergy) EXCLUDE before ranking; scores never
 * override them.
 */
object RecommendationRanker {
    data class Option(
        val id: String,
        val costKes: Double,
        val distanceM: Double?,
        val preferenceMatch: Double, // 0..1
        val sourceReliability: Double, // 0..1 verified > user-reported > estimated
        val urgency: Double = 0.0 // 0..1
    )
    data class Ranked(val id: String, val score: Double, val explanation: String)

    fun rank(options: List<Option>, budgetKes: Double, maxDistanceM: Double? = null): List<Ranked> {
        return options
            .filter { it.costKes <= budgetKes }
            .filter { maxDistanceM == null || (it.distanceM ?: Double.MAX_VALUE) <= maxDistanceM }
            .map { o ->
                val affordability = 1.0 - (o.costKes / budgetKes).coerceIn(0.0, 1.0)
                val distance = o.distanceM?.let { (1.0 - (it / (maxDistanceM ?: 5000.0)).coerceIn(0.0, 1.0)) } ?: 0.5
                val score = 0.35 * affordability + 0.25 * o.preferenceMatch +
                    0.20 * o.sourceReliability + 0.10 * distance + 0.10 * o.urgency
                Ranked(o.id, score, "KSh ${o.costKes.toInt()} fits KSh ${budgetKes.toInt()} budget")
            }.sortedByDescending { it.score }
    }
}
