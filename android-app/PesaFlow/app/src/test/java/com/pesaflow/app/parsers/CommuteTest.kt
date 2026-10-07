package com.pesaflow.app.parsers

import com.pesaflow.app.data.schedule.CommutePlan
import com.pesaflow.app.data.schedule.WeekPlan
import com.pesaflow.app.data.schedule.commuteSummary
import com.pesaflow.app.data.schedule.formatClock
import com.pesaflow.app.data.schedule.monthlyFare
import com.pesaflow.app.data.schedule.parseClock
import com.pesaflow.app.data.schedule.schoolDaysFromPlan
import org.junit.Assert.*
import org.junit.Test

class CommuteTest {
    @Test
    fun `school days come from the timetable`() {
        val plan = mapOf(
            "Mon" to setOf(WeekPlan.MORNING, WeekPlan.AFTERNOON),
            "Tue" to setOf(WeekPlan.MORNING),
            "Wed" to emptySet(),
            "Sat" to setOf(WeekPlan.EVENING)
        )
        assertEquals(setOf("Mon", "Tue", "Sat"), schoolDaysFromPlan(plan))
        assertTrue(schoolDaysFromPlan(emptyMap()).isEmpty())
    }

    @Test
    fun `monthly fare is fare times trips times weeks`() {
        // 100 one-way × 2 trips × 5 days × 4.33 = 4330.
        assertEquals(4330.0, monthlyFare(100.0, 5), 0.01)
        assertEquals(0.0, monthlyFare(100.0, 0), 0.001)
        assertEquals(0.0, monthlyFare(0.0, 5), 0.001)
        assertEquals(0.0, monthlyFare(-50.0, 5), 0.001)
    }

    @Test
    fun `clocks parse forgivingly`() {
        assertEquals(7 to 30, parseClock("07:30"))
        assertEquals(7 to 30, parseClock("7:30"))
        assertEquals(7 to 0, parseClock("7"))
        assertEquals(19 to 30, parseClock("7:30pm"))
        assertEquals(17 to 0, parseClock("1700"))
        assertEquals(0 to 0, parseClock("12am"))
        assertEquals(12 to 0, parseClock("12pm"))
    }

    @Test
    fun `clocks reject nonsense`() {
        assertNull(parseClock(""))
        assertNull(parseClock("25:00"))
        assertNull(parseClock("7:99"))
        assertNull(parseClock("morning"))
        assertNull(parseClock("3"))
    }

    @Test
    fun `clocks format with leading zeros`() {
        assertEquals("07:30", formatClock(7, 30))
        assertEquals("17:00", formatClock(17, 0))
    }

    @Test
    fun `summary reads like a sentence`() {
        val s = commuteSummary(
            CommutePlan(
                days = setOf("Mon", "Tue", "Wed", "Thu", "Fri"),
                fareOneWay = 100.0,
                goTime = "07:30",
                backTime = "17:00"
            )
        )
        assertTrue(s.contains("07:30"))
        assertTrue(s.contains("17:00"))
        assertTrue(s.contains("Mon–Fri"))
        assertTrue(s.contains("10 trips/wk"))
    }
}
