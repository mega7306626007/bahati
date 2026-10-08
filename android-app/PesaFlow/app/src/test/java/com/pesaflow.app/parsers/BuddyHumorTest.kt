package com.pesaflow.app.parsers

import com.pesaflow.app.ui.dashboard.BuddyHumor
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuddyHumorTest {

    @Test
    fun greetingVariantsAreDeterministicAndWitty() {
        val a = BuddyHumor.greeting(0)
        val b = BuddyHumor.greeting(1)
        assertNotEquals(a, b)
        assertTrue(a.isNotBlank())
        assertTrue(b.isNotBlank())
    }

    @Test
    fun thanksVariantsAreAvailable() {
        assertTrue(BuddyHumor.thanks(0).isNotBlank())
        assertTrue(BuddyHumor.thanks(1).isNotBlank())
    }

    @Test
    fun errorVariantIsAvailable() {
        assertTrue(BuddyHumor.error(0).isNotBlank())
        assertTrue(BuddyHumor.error(1).isNotBlank())
    }

    @Test
    fun occasionalQuipIsBoundedAndDeterministic() {
        val base = "This month you spent KSh 4000 on food."
        val fifthTurn = BuddyHumor.decorate(base, "food", 5)
        val sixthTurn = BuddyHumor.decorate(base, "food", 6)

        assertTrue(fifthTurn.length > base.length)
        assertTrue(sixthTurn.length == base.length)
        assertTrue(fifthTurn.startsWith(base))
    }
}
