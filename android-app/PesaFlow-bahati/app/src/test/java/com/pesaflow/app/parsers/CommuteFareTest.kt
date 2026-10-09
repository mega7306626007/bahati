package com.pesaflow.app.parsers

import com.pesaflow.app.data.schedule.countClassDays
import com.pesaflow.app.data.schedule.expectedCommuteSpend
import com.pesaflow.app.data.schedule.fareBand
import com.pesaflow.app.data.schedule.isWithinClassCommuteWindow
import com.pesaflow.app.data.schedule.matchesCommuteSpend
import com.pesaflow.app.data.schedule.matchesDeclaredCommuteFare
import com.pesaflow.app.data.schedule.tripWindows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CommuteFareTest {
    private fun at(day: Int, hour: Int): Long = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.OCTOBER, day, hour, 0)
    }.timeInMillis

    private val timetable = mapOf("Mon" to (9 to 11))

    @Test
    fun `class commute window accepts two hours either side on scheduled day`() {
        assertTrue(isWithinClassCommuteWindow(at(5, 7), timetable))
        assertTrue(isWithinClassCommuteWindow(at(5, 13), timetable))
        assertFalse(isWithinClassCommuteWindow(at(5, 6), timetable))
        assertFalse(isWithinClassCommuteWindow(at(6, 9), timetable))
    }

    @Test
    fun `band runs plus fifty minus ten with user minimum floor`() {
        // Peak hikes run hot (+50); nothing below the minimum ever matches.
        assertEquals(90.0, fareBand(100.0).start, 0.001)
        assertEquals(150.0, fareBand(100.0).endInclusive, 0.001)
        assertTrue(120.0 in fareBand(100.0))
        assertFalse(30.0 in fareBand(100.0))
        // User minimum overrides the −10 default floor.
        assertEquals(80.0, fareBand(100.0, 80.0).start, 0.001)
        assertFalse(75.0 in fareBand(100.0, 80.0))
        assertEquals(0.0 to 0.0, fareBand(0.0).start to fareBand(0.0).endInclusive)
    }

    @Test
    fun `trip windows split to and from around lectures`() {
        // 9–11 lectures, 1h each way, 1h margin: to [7,10], from [10,13].
        assertEquals(listOf(7..10, 10..13), tripWindows(9, 11))
    }

    @Test
    fun `commute match needs fare band and trip window on class day`() {
        assertTrue(matchesCommuteSpend(100.0, at(5, 7), 100.0, timetable))
        assertTrue(matchesCommuteSpend(120.0, at(5, 18 - 10), 100.0, timetable))
        // 70 under a 100 fare is below the −10 floor, never this ride.
        assertFalse(matchesCommuteSpend(30.0, at(5, 7), 100.0, timetable))
        // User minimum floor wins over the default.
        assertFalse(matchesCommuteSpend(85.0, at(5, 7), 100.0, timetable, minFare = 90.0))
        assertTrue(matchesCommuteSpend(95.0, at(5, 7), 100.0, timetable, minFare = 90.0))
        // 7pm supper near campus is not a bus ride.
        assertFalse(matchesCommuteSpend(250.0, at(5, 19), 100.0, timetable))
        // No classes that day, no commute.
        assertFalse(matchesCommuteSpend(100.0, at(6, 9), 100.0, timetable))
    }

    @Test
    fun `expected spend moves with class days not calendar days`() {
        // Fare 50 × to-and-fro × 3 elapsed class days.
        assertEquals(300.0, expectedCommuteSpend(50.0, 3), 0.001)
        assertEquals(0.0, expectedCommuteSpend(50.0, 0), 0.001)
        val mon = at(5, 9)
        val wed = at(7, 9)
        assertEquals(3, countClassDays(mon, wed, setOf("Mon", "Tue", "Wed")))
        assertEquals(0, countClassDays(mon, wed, setOf("Sun")))
    }

    @Test
    fun `declared fare accepts bounded hikes only in commute window`() {
        assertTrue(matchesDeclaredCommuteFare(150.0, at(5, 7), 100.0, timetable))
        assertTrue(matchesDeclaredCommuteFare(50.0, at(5, 13), 100.0, timetable))
        assertFalse(matchesDeclaredCommuteFare(151.0, at(5, 7), 100.0, timetable))
        assertFalse(matchesDeclaredCommuteFare(100.0, at(5, 16), 100.0, timetable))
        assertFalse(matchesDeclaredCommuteFare(100.0, at(6, 9), 100.0, timetable))
    }
}
