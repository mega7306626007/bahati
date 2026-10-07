package com.pesaflow.app.data.schedule

// Timetable-driven transport: school days come from the WeekPlan (which the
// user pastes/confirms once), trip times are asked and editable, and the
// monthly fare falls out of days × trips × fare. No more guessing "days/wk".
data class CommutePlan(
    val days: Set<String>,
    val fareOneWay: Double,
    val goTime: String,
    val backTime: String,
    val tripsPerDay: Int = 2
)

fun schoolDaysFromPlan(plan: Map<String, Set<String>>): Set<String> =
    WeekPlan.DAYS.filter { plan[it].orEmpty().isNotEmpty() }.toSet()

fun monthlyFare(fareOneWay: Double, daysPerWeek: Int, tripsPerDay: Int = 2): Double =
    if (fareOneWay <= 0 || daysPerWeek <= 0 || tripsPerDay <= 0) 0.0
    else fareOneWay * tripsPerDay * daysPerWeek * 4.33

// Clock input, forgiving: "7", "07:30", "7:30pm", "1730" → (7,30)/(19,30).
// Null = unparseable, the UI keeps the old value and says so.
fun parseClock(raw: String): Pair<Int, Int>? {
    return try {
        val s = raw.trim().lowercase().replace(" ", "")
        if (s.isEmpty()) return null
        val isPm = s.endsWith("pm")
        val isAm = s.endsWith("am")
        val core = s.removeSuffix("pm").removeSuffix("am")
        val (h, m) = if (":" in core) {
            val parts = core.split(":")
            if (parts.size != 2) return null
            (parts[0].toIntOrNull() ?: return null) to (parts[1].toIntOrNull() ?: return null)
        } else if (core.length == 4 && core.all { it.isDigit() }) {
            core.substring(0, 2).toInt() to core.substring(2, 4).toInt()
        } else if (core.length <= 2 && core.all { it.isDigit() }) {
            core.toInt() to 0
        } else return null
        var hh = h
        if (isPm && hh < 12) hh += 12
        if (isAm && hh == 12) hh = 0
        if (hh !in 0..23 || m !in 0..59) return null
        // Bare numbers are school hours: "7" = 07:00, "17" = 17:00.
        // "19"–"23" stay evening; "0"–"4" are not commute times.
        if (!isPm && !isAm && ":" !in core && core.length <= 2 && hh in 0..4) return null
        hh to m
    } catch (e: Exception) {
        null
    }
}

fun formatClock(h: Int, m: Int): String = "%02d:%02d".format(h, m)

// "" in → "" out (flexible hours); valid clock → normalized; garbage → null.
fun normTime(raw: String): String? {
    if (raw.isBlank()) return ""
    val p = parseClock(raw) ?: return null
    return formatClock(p.first, p.second)
}

fun commuteSummary(plan: CommutePlan): String {
    val ordered = WeekPlan.DAYS.filter { it in plan.days }
    val span = if (ordered.size >= 2 && ordered == listOf("Mon", "Tue", "Wed", "Thu", "Fri")) "Mon–Fri"
    else ordered.joinToString(", ")
    return "Leave ~${plan.goTime}, back ~${plan.backTime} · $span (${plan.days.size} days, ${plan.days.size * plan.tripsPerDay} trips/wk)"
}

// Extra trips: life is not one route. Church Sundays, town Saturdays,
// shags runs — each with its own days, fare and (optional) times. Blank
// times mean flexible hours (± a few hours, no guilt). Stored as one prefs
// string: "name|Mon,Sun|fare|go|back;...". Bad rows are skipped, never fatal.
data class TripRoute(
    val name: String,
    val days: Set<String>,
    val fareOneWay: Double,
    val goTime: String = "",
    val backTime: String = ""
)

fun routeMonthly(r: TripRoute): Double = monthlyFare(r.fareOneWay, r.days.size)

fun totalMonthly(routes: List<TripRoute>): Double = routes.sumOf { routeMonthly(it) }

fun routeSummary(r: TripRoute): String {
    val ordered = WeekPlan.DAYS.filter { it in r.days }
    val whenStr = if (r.goTime.isBlank() && r.backTime.isBlank()) "flexible hours"
    else "~${r.goTime.ifBlank { "?"}} → ~${r.backTime.ifBlank { "?" }}"
    return "${r.name}: ${ordered.joinToString(", ")} · KSh ${r.fareOneWay.toInt()} ×2 · $whenStr · ≈KSh ${routeMonthly(r).toInt()}/mo"
}

fun encodeRoutes(routes: List<TripRoute>): String = routes.joinToString(";") { r ->
    listOf(
        r.name.replace(";", ",").replace("|", ",").trim(),
        r.days.sortedBy { WeekPlan.DAYS.indexOf(it) }.joinToString(","),
        r.fareOneWay.toString(),
        r.goTime.trim(),
        r.backTime.trim()
    ).joinToString("|")
}

fun decodeRoutes(raw: String?): List<TripRoute> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(";").mapNotNull { row ->
        try {
            val parts = row.split("|")
            if (parts.size != 5) return@mapNotNull null
            val name = parts[0].trim()
            val days = parts[1].split(",").map { it.trim() }.filter { it in WeekPlan.DAYS }.toSet()
            val fare = parts[2].toDoubleOrNull() ?: return@mapNotNull null
            if (name.isEmpty() || days.isEmpty() || fare <= 0) return@mapNotNull null
            val go = normTime(parts[3].trim()) ?: return@mapNotNull null
            val back = normTime(parts[4].trim()) ?: return@mapNotNull null
            TripRoute(name, days, fare, go, back)
        } catch (e: Exception) {
            null
        }
    }
}
