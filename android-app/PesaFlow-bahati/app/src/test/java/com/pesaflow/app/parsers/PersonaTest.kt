package com.pesaflow.app.parsers

import com.pesaflow.app.ui.budgets.Persona
import com.pesaflow.app.ui.budgets.parsePersona
import org.junit.Assert.*
import org.junit.Test


/** Persona derivation: stated beats derived, derivations stay sane. */
class PersonaTest {

    @Test
    fun `explicit persona wins over answers`() {
        assertEquals(
            Persona.RENT_WALK,
            parsePersona("home=PARENTS|commute=FAR|cooking=NO|persona=RENT_WALK")
        )
        // Unknown names fall through to derivation, never crash.
        assertEquals(
            Persona.PARENTS_FAR,
            parsePersona("home=PARENTS|commute=FAR|cooking=NO|persona=SPACESHIP")
        )
    }

    @Test
    fun `derivation covers all six setups`() {
        assertEquals(Persona.PARENTS_FAR, parsePersona("home=PARENTS|commute=FAR|cooking=YES"))
        assertEquals(Persona.PARENTS_NEAR, parsePersona("home=PARENTS|commute=NEAR|cooking=NO"))
        assertEquals(Persona.RENT_COMMUTE, parsePersona("home=RENTAL|commute=FAR|cooking=YES"))
        assertEquals(Persona.RENT_WALK, parsePersona("home=RENTAL|commute=NEAR|cooking=YES"))
        assertEquals(Persona.HOSTEL_COOK, parsePersona("home=HOSTEL|commute=NEAR|cooking=YES"))
        assertEquals(Persona.HOSTEL_NOCOOK, parsePersona("home=HOSTEL|commute=NEAR|cooking=NO"))
    }

    @Test
    fun `legacy binary answers still resolve`() {
        assertEquals(Persona.RENT_COMMUTE, parsePersona("living=COMMUTER"))
        assertEquals(Persona.HOSTEL_COOK, parsePersona("living=HOSTEL"))
        assertEquals(Persona.HOSTEL_COOK, parsePersona(""))
    }
}
