package com.pesaflow.app.parsers

import com.pesaflow.app.data.campus.campusHabitsFor
import com.pesaflow.app.data.campus.yearHabitFor
import org.junit.Assert.*
import org.junit.Test

class HabitsTest {

    @Test
    fun `kabete and main share uon habits`() {
        val kabete = campusHabitsFor("Kabete")
        val main = campusHabitsFor("UoN Main Campus")
        assertNotNull(kabete)
        assertNotNull(main)
        assertEquals("UoN Kabete", kabete!!.campus)
        assertEquals("UoN Main", main!!.campus)
    }

    @Test
    fun `every atlas campus resolves to a habit`() {
        val atlas = listOf(
            "UoN" to "UoN Main", "KU" to "KU Main", "JKUAT" to "JKUAT Juja",
            "Maseno" to "Maseno", "Egerton" to "Egerton Njoro",
            "Kirinyaga" to "Kirinyaga", "Strathmore" to "Strathmore",
            "Daystar" to "Daystar Athi River", "Moi" to "Moi Kesses",
            "UoE" to "UoEldoret", "MMUST" to "MMUST", "Kisii" to "Kisii",
            "MKU" to "MKU Thika", "USIU" to "USIU", "KCA" to "KCA",
            "Zetech" to "Zetech", "TUK" to "TUK", "DeKUT" to "DeKUT",
            "Chuka" to "Chuka", "Machakos" to "Machakos", "SEKU" to "SEKU",
            "Embu" to "Embu", "Pwani" to "Pwani Kilifi"
        )
        atlas.forEach { (query, campus) ->
            val habit = campusHabitsFor(query)
            assertNotNull(query, habit)
            assertEquals(query, campus, habit!!.campus)
        }
    }

    @Test
    fun `embu resolves to embu not chuka`() {
        assertEquals("Embu", campusHabitsFor("Embu")!!.campus)
        assertEquals("Chuka", campusHabitsFor("Chuka")!!.campus)
    }

    @Test
    fun `unknown campus yields nothing`() {
        assertNull(campusHabitsFor("Hogwarts"))
        assertNull(campusHabitsFor(""))
    }

    @Test
    fun `each year has a distinct watch and tip`() {
        val tips = (1..4).map { yearHabitFor("Year $it").topTip }
        assertEquals(4, tips.toSet().size)
        assertTrue(yearHabitFor("Year 1").watch.contains("Food", ignoreCase = true))
        assertTrue(yearHabitFor("Year 3").watch.contains("fare", ignoreCase = true))
    }

    @Test
    fun `blank year falls back honestly`() {
        val h = yearHabitFor("")
        assertEquals(0, h.year)
        assertTrue(h.topTip.isNotBlank())
    }
}
