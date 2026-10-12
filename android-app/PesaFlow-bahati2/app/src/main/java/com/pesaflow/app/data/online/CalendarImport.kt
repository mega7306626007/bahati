package com.pesaflow.app.data.online

// Timetable import from exported calendar files (.ics). Parses VEVENT
// DTSTARTs and folds them into WeekPlan-compatible day→slot sets
// (morning/afternoon/evening — the same vocabulary WeekPlan.suggestionFor
// already speaks). All-day events are ignored: a whole-day block is not a
// lecture. Pure, fully unit-tested. Borrowed from PesaFlow-online.
data class CalendarClass(val day: String, val slot: String)

fun slotForHour(hour: Int): String = when (hour) {
    in 0..10 -> "morning"
    in 11..15 -> "afternoon"
    else -> "evening"
}

private val DOW_LABEL = mapOf(
    java.util.Calendar.MONDAY to "Mon",
    java.util.Calendar.TUESDAY to "Tue",
    java.util.Calendar.WEDNESDAY to "Wed",
    java.util.Calendar.THURSDAY to "Thu",
    java.util.Calendar.FRIDAY to "Fri",
    java.util.Calendar.SATURDAY to "Sat",
    java.util.Calendar.SUNDAY to "Sun"
)

// Minimal ICS: understands DTSTART:YYYYMMDDTHHMMSS (floating or Z-suffixed).
// RRULE expansion is out of scope — one VEVENT = one class.
fun parseIcsToWeekSlots(ics: String): Map<String, Set<String>> {
    val out = mutableMapOf<String, MutableSet<String>>()
    val dtstart = Regex("DTSTART[^:]*:(\\d{8})T(\\d{6})Z?")
    for (line in ics.lines()) {
        val m = dtstart.find(line.trim()) ?: continue
        val (date, time) = m.destructured
        try {
            val cal = java.util.Calendar.getInstance().apply {
                set(
                    date.substring(0, 4).toInt(),
                    date.substring(4, 6).toInt() - 1,
                    date.substring(6, 8).toInt(),
                    time.substring(0, 2).toInt(),
                    time.substring(2, 4).toInt(),
                    0
                )
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val day = DOW_LABEL[cal.get(java.util.Calendar.DAY_OF_WEEK)] ?: continue
            out.getOrPut(day) { mutableSetOf() }.add(slotForHour(cal.get(java.util.Calendar.HOUR_OF_DAY)))
        } catch (e: Exception) {
            continue
        }
    }
    return out
}
