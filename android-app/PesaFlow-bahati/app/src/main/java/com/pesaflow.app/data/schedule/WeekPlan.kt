package com.pesaflow.app.data.schedule

import android.content.Context

// Lecture-week plan: which parts of each day are busy. Stored as JSON in
// SharedPreferences (no migration): {"Mon":["morning","afternoon"],...}.
// Feeds meal suggestions (busy lunch → heavy supper) and Buddy answers.
object WeekPlan {
    const val MORNING = "morning"
    const val AFTERNOON = "afternoon"
    const val EVENING = "evening"

    val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val SLOTS = listOf(MORNING, AFTERNOON, EVENING)

    private const val KEY = "week_plan_json"

    private fun prefs(context: Context) =
        context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE)

    fun load(context: Context): Map<String, Set<String>> {
        return try {
            val o = org.json.JSONObject(prefs(context).getString(KEY, "{}").orEmpty())
            DAYS.associateWith { d ->
                val arr = o.optJSONArray(d) ?: return@associateWith emptySet<String>()
                (0 until arr.length()).map { arr.optString(it) }.filter { it in SLOTS }.toSet()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun toggle(context: Context, day: String, slot: String) {
        val cur = load(context).toMutableMap()
        val set = cur[day].orEmpty().toMutableSet()
        if (!set.add(slot)) set.remove(slot)
        cur[day] = set
        val o = org.json.JSONObject()
        cur.forEach { (d, s) -> o.put(d, org.json.JSONArray(s.toList())) }
        prefs(context).edit().putString(KEY, o.toString()).apply()
    }

    fun isBusy(slots: Set<String>?, vararg which: String): Boolean =
        slots?.any { it in which } == true

    // What should dinner/breakfast do given this weekday? Null = free day, no advice.
    fun suggestionFor(dayName: String, slots: Set<String>): String? {
        val lunchBusy = isBusy(slots, AFTERNOON)
        val mornBusy = isBusy(slots, MORNING)
        val eveBusy = isBusy(slots, EVENING)
        return when {
            lunchBusy && !eveBusy -> "$dayName: lunch busy → supper heavy, grab lunch early. 🍲"
            lunchBusy && eveBusy -> "$dayName: packed day → bulk-cook evening, carry lunch. 🍱"
            mornBusy && !lunchBusy -> "$dayName: mornings packed → heavy breakfast, light lunch. 🍳"
            eveBusy && !lunchBusy -> "$dayName: evening busy → cook lunch double, reheat supper. ♨️"
            mornBusy && lunchBusy -> "$dayName: full day → prep night before, one cooking round. 🎒"
            else -> null
        }
    }

    fun freeEvenings(context: Context): List<String> =
        DAYS.filter { !isBusy(load(context)[it], EVENING) }

    // Class times: first/last lecture hour per day ("Mon" to 7 to 17).
    // Asked once, editable forever — drives commute days + peak exposure.
    private const val TIMES_KEY = "class_times_json"

    fun loadTimes(context: Context): Map<String, Pair<Int, Int>> {
        return try {
            val o = org.json.JSONObject(prefs(context).getString(TIMES_KEY, "{}").orEmpty())
            DAYS.mapNotNull { d ->
                val arr = o.optJSONArray(d) ?: return@mapNotNull null
                val first = arr.optInt(0, -1)
                val last = arr.optInt(1, -1)
                if (first in 5..23 && last in 5..23 && first <= last) d to (first to last)
                else null
            }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun saveTimes(context: Context, times: Map<String, Pair<Int, Int>>) {
        val o = org.json.JSONObject()
        times.forEach { (d, h) ->
            if (d in DAYS && h.first in 5..23 && h.second in 5..23 && h.first <= h.second) {
                o.put(d, org.json.JSONArray(listOf(h.first, h.second)))
            }
        }
        prefs(context).edit().putString(TIMES_KEY, o.toString()).apply()
    }

    // Commute days: days with classes (times win, grid slots backstop).
    // Empty everywhere → Mon–Fri default, never zero.
    fun commuteDays(week: Map<String, Set<String>>, times: Map<String, Pair<Int, Int>>): List<String> {
        val timed = DAYS.filter { times.containsKey(it) }
        if (timed.isNotEmpty()) return timed
        val busy = DAYS.filter { week[it].orEmpty().isNotEmpty() }
        return if (busy.isNotEmpty()) busy else listOf("Mon", "Tue", "Wed", "Thu", "Fri")
    }

    // Peak exposure from the school run: first class at/before 8 means the
    // 7–9am peak; last class at/after 17 catches the evening peak. Either,
    // both, or a calm off-peak run — stated, never priced (fares wobble).
    fun peakNote(firstHour: Int?, lastHour: Int?): String? {
        val morning = firstHour != null && firstHour <= 8
        val evening = lastHour != null && lastHour >= 17
        return when {
            morning && evening -> "Peak both ways — morning + evening fares run hot"
            morning -> "Morning peak — 7–9am fares run hot"
            evening -> "Evening peak — late return fares run hot"
            firstHour != null || lastHour != null -> "Off-peak run — calm fares"
            else -> null
        }
    }

    fun apply(context: Context, suggested: Map<String, Set<String>>) {
        val cur = load(context).toMutableMap()
        suggested.forEach { (d, s) ->
            if (d in DAYS) cur[d] = cur[d].orEmpty() + s.filter { it in SLOTS }.toSet()
        }
        val o = org.json.JSONObject()
        cur.forEach { (d, s) -> o.put(d, org.json.JSONArray(s.toList())) }
        prefs(context).edit().putString(KEY, o.toString()).apply()
    }

    // Heuristic timetable reader: day headers (Mon/Monday/...) own following
    // lines until the next day header; times map to slots (<12 morning,
    // <17 afternoon, else evening). Course codes are ignored naturally.
    // Returns day → slots; the UI always asks the user to confirm.
    fun suggestFromText(raw: String): Map<String, Set<String>> {
        val dayKeys = mapOf(
            "mon" to "Mon", "monday" to "Mon",
            "tue" to "Tue", "tues" to "Tue", "tuesday" to "Tue",
            "wed" to "Wed", "wednesday" to "Wed",
            "thu" to "Thu", "thur" to "Thu", "thursday" to "Thu",
            "fri" to "Fri", "friday" to "Fri",
            "sat" to "Sat", "saturday" to "Sat",
            "sun" to "Sun", "sunday" to "Sun"
        )
        val out = mutableMapOf<String, MutableSet<String>>()
        var current: String? = null
        raw.lines().forEach { line ->
            val low = line.lowercase()
            val header = dayKeys.entries.firstOrNull { (k, _) ->
                Regex("(^|[^a-z])$k([^a-z]|$)").containsMatchIn(low)
            }?.value
            if (header != null) current = header
            val day = current ?: return@forEach
            val slots = mutableSetOf<String>()
            // Times: 8, 8:00, 8am, 8-10, 10:00-13:00, 1400, 2pm
            Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").findAll(low).forEach { m ->
                var h = m.groupValues[1].toIntOrNull() ?: return@forEach
                val ap = m.groupValues[3]
                if (ap == "pm" && h < 12) h += 12
                if (ap == "am" && h == 12) h = 0
                if (h in 5..23) {
                    slots.add(when {
                        h < 12 -> MORNING
                        h < 17 -> AFTERNOON
                        else -> EVENING
                    })
                }
            }
            // Bare 4-digit military times: 0800, 1400.
            Regex("(?<![0-9:])([01][0-9]|2[0-3])[0-5][0-9](?![0-9:])").findAll(low).forEach { m ->
                val h = m.value.substring(0, 2).toIntOrNull() ?: return@forEach
                if (h in 5..23) {
                    slots.add(when {
                        h < 12 -> MORNING
                        h < 17 -> AFTERNOON
                        else -> EVENING
                    })
                }
            }
            if (slots.isNotEmpty()) {
                out.getOrPut(day) { mutableSetOf() }.addAll(slots)
            }
        }
        return out
    }
}
