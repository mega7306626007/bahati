package com.pesaflow.app.data.schedule

import java.util.Calendar

private val weekdayKeys = mapOf(
    Calendar.MONDAY to "Mon",
    Calendar.TUESDAY to "Tue",
    Calendar.WEDNESDAY to "Wed",
    Calendar.THURSDAY to "Thu",
    Calendar.FRIDAY to "Fri",
    Calendar.SATURDAY to "Sat",
    Calendar.SUNDAY to "Sun"
)

fun timetableDay(timestamp: Long): String? =
    weekdayKeys[Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.DAY_OF_WEEK)]

fun isWithinClassCommuteWindow(
    timestamp: Long,
    classTimes: Map<String, Pair<Int, Int>>,
    toleranceHours: Int = 2
): Boolean {
    if (toleranceHours < 0) return false
    val bounds = classTimes[timetableDay(timestamp)] ?: return false
    val hour = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.HOUR_OF_DAY)
    return hour in (bounds.first - toleranceHours).coerceAtLeast(0)..(bounds.second + toleranceHours).coerceAtMost(23)
}

fun matchesDeclaredCommuteFare(
    amount: Double,
    timestamp: Long,
    oneWayFare: Double,
    classTimes: Map<String, Pair<Int, Int>>,
    tolerance: Double = 50.0
): Boolean =
    amount > 0 &&
        oneWayFare > 0 &&
        tolerance >= 0 &&
        kotlin.math.abs(amount - oneWayFare) <= tolerance &&
        isWithinClassCommuteWindow(timestamp, classTimes)

// Peak-hike headroom shared by the matcher AND the onboarding fare
// bridge: one number, one meaning — change it here and both follow.
const val FARE_PEAK_HEADROOM = 50.0

// Fare band with a user-set floor: peak hikes run hot (+headroom), but
// nothing below the minimum fare ever reads as this ride (−10 default,
// or the user's own minimum). Fixed ±50 swallowed cheap fares whole.
fun fareBand(oneWayFare: Double, minFare: Double = 0.0): ClosedRange<Double> {
    if (oneWayFare <= 0) return 0.0..0.0
    val lo = (if (minFare > 0) minFare else oneWayFare - 10.0).coerceAtLeast(1.0)
    return lo..(oneWayFare + FARE_PEAK_HEADROOM)
}

// Trip windows from the timetable plus commute durations: the TO trip
// lands in [first − to − margin, first + margin], the FROM trip in
// [last − margin, last + fro + margin]. Replaces the blunt ±2h lecture
// window — a 7pm supper spend near campus no longer reads as a bus ride.
fun tripWindows(
    firstHour: Int,
    lastHour: Int,
    toHours: Int = 1,
    froHours: Int = 1,
    marginHours: Int = 1
): List<IntRange> {
    val to = (firstHour - toHours - marginHours).coerceAtLeast(0)..(firstHour + marginHours).coerceAtMost(23)
    val fro = (lastHour - marginHours).coerceAtLeast(0)..(lastHour + froHours + marginHours).coerceAtMost(23)
    return listOf(to, fro)
}

// Commute spend matcher: fare-like amount (calculated margin) inside a
// trip window on a class day. toHours/froHours default to 1 when the
// student never stated them — asked once in the timetable editor.
fun matchesCommuteSpend(
    amount: Double,
    timestamp: Long,
    oneWayFare: Double,
    classTimes: Map<String, Pair<Int, Int>>,
    toHours: Int = 1,
    froHours: Int = 1,
    marginHours: Int = 1,
    minFare: Double = 0.0
): Boolean {
    if (amount <= 0 || oneWayFare <= 0) return false
    if (amount !in fareBand(oneWayFare, minFare)) return false
    val bounds = classTimes[timetableDay(timestamp)] ?: return false
    // Default-zone clock like the rest of the commute stack (tests pin
    // hours in the same zone they construct them — KenyaTime only where
    // the existing timetableDay already uses it).
    val hour = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.HOUR_OF_DAY)
    return tripWindows(bounds.first, bounds.second, toHours, froHours, marginHours).any { hour in it }
}

// Moving budget: expected commute spend is fare × trips × elapsed class
// days — never fare × calendar days. Counts class days in [fromMs, toMs].
fun countClassDays(fromMs: Long, toMs: Long, classDays: Set<String>): Int {
    if (fromMs > toMs || classDays.isEmpty()) return 0
    var n = 0
    var cursor = fromMs
    val dayMs = 24L * 60 * 60 * 1000
    while (cursor <= toMs) {
        if (timetableDay(cursor) in classDays) n++
        cursor += dayMs
    }
    return n
}

fun expectedCommuteSpend(oneWayFare: Double, classDaysElapsed: Int, tripsPerDay: Int = 2): Double =
    if (oneWayFare <= 0 || classDaysElapsed <= 0 || tripsPerDay <= 0) 0.0
    else oneWayFare * tripsPerDay * classDaysElapsed
