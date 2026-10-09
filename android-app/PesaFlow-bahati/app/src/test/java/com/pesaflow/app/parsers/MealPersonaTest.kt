package com.pesaflow.app.parsers

import com.pesaflow.app.ui.budgets.Persona
import com.pesaflow.app.ui.university.defaultMealPersona
import org.junit.Assert.*
import org.junit.Test


// One persona gear: every app-wide setup maps to exactly one planner preset.
class MealPersonaTest {

    @Test
    fun `commuters plan lunch near the stage`() {
        assertEquals("Transport", defaultMealPersona(Persona.PARENTS_FAR))
        assertEquals("Transport", defaultMealPersona(Persona.RENT_COMMUTE))
    }

    @Test
    fun `home near campus eats tight`() {
        assertEquals("Tight", defaultMealPersona(Persona.PARENTS_NEAR))
    }

    @Test
    fun `cooks get the full kitchen`() {
        assertEquals("Hostel", defaultMealPersona(Persona.RENT_WALK))
        assertEquals("Hostel", defaultMealPersona(Persona.HOSTEL_COOK))
    }

    @Test
    fun `buyers get everything`() {
        assertEquals("Full", defaultMealPersona(Persona.HOSTEL_NOCOOK))
    }

    @Test
    fun `every setup maps to a real preset`() {
        val presets = setOf("Transport", "Hostel", "Tight", "Full")
        Persona.values().forEach {
            assertTrue("$it maps outside presets", defaultMealPersona(it) in presets)
        }
    }
}
