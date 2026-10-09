package com.pesaflow.app.parsers

import com.pesaflow.app.data.schedule.WeekPlan
import org.junit.Assert.*
import org.junit.Test


class WeekPlanTest {

    @Test
    fun `times win over grid for commute days`() {
        val week = mapOf("Mon" to setOf("morning"), "Tue" to setOf("morning"))
        val times = mapOf("Wed" to (7 to 17), "Thu" to (8 to 16))
        assertEquals(listOf("Wed", "Thu"), WeekPlan.commuteDays(week, times))
    }

    @Test
    fun `grid backs stop when no times set`() {
        val week = mapOf("Mon" to setOf("morning"), "Wed" to setOf("evening"))
        assertEquals(listOf("Mon", "Wed"), WeekPlan.commuteDays(week, emptyMap()))
    }

    @Test
    fun `empty grid defaults to weekdays`() {
        assertEquals(
            listOf("Mon", "Tue", "Wed", "Thu", "Fri"),
            WeekPlan.commuteDays(emptyMap(), emptyMap())
        )
    }

    @Test
    fun `peak verdicts follow the school run`() {
        assertEquals(
            "Peak both ways — morning + evening fares run hot",
            WeekPlan.peakNote(7, 18)
        )
        assertEquals(
            "Morning peak — 7–9am fares run hot",
            WeekPlan.peakNote(8, 15)
        )
        assertEquals(
            "Evening peak — late return fares run hot",
            WeekPlan.peakNote(10, 17)
        )
        assertEquals("Off-peak run — calm fares", WeekPlan.peakNote(10, 15))
        assertNull(WeekPlan.peakNote(null, null))
    }
}
