package com.pesaflow.app.parsers

import com.pesaflow.app.data.finance.campusFareHint
import com.pesaflow.app.data.finance.transportFor
import org.junit.Assert.*
import org.junit.Test

class CampusTransportTest {

    @Test
    fun `ku matches before nairobi collision`() {
        assertEquals("KU", transportFor("Kenyatta University")?.campus)
        assertEquals("UoN", transportFor("UoN Main")?.campus)
    }

    @Test
    fun `moi and uoe disambiguate`() {
        assertEquals("Moi", transportFor("Moi University")?.campus)
        assertEquals("UoE", transportFor("University of Eldoret")?.campus)
        assertEquals("UoE", transportFor("Eldoret")?.campus)
    }

    @Test
    fun `rental areas feed rent matching`() {
        val ku = transportFor("KU")!!
        assertTrue(ku.rentalAreas.contains("Kahawa Sukari"))
        val egerton = transportFor("Egerton")!!
        assertTrue(egerton.rentalAreas.contains("Njoro"))
    }

    @Test
    fun `fare hints carry bands`() {
        val hint = campusFareHint("JKUAT")!!
        assertTrue(hint.contains("20") && hint.contains("100"))
        assertTrue(hint.contains("verify"))
    }

    @Test
    fun `unknown universities yield nothing`() {
        assertNull(transportFor("Hogwarts"))
        assertNull(transportFor(""))
        assertNull(campusFareHint("Hogwarts"))
    }

    @Test
    fun `final campuses resolve with corrected bands`() {
        assertEquals("Pwani", transportFor("Pwani University")?.campus)
        assertEquals("Embu", transportFor("University of Embu")?.campus)
        assertEquals("20–50", transportFor("Embu")!!.let { "${it.fareOneWayMin}–${it.fareOneWayMax}" })
        assertTrue(transportFor("Pwani")!!.rentalAreas.contains("Kilifi"))
    }

    @Test
    fun `report campuses resolve`() {
        assertEquals("MKU", transportFor("Mount Kenya University Thika")?.campus)
        assertEquals("USIU", transportFor("USIU-Africa")?.campus)
        assertEquals("DeKUT", transportFor("Dedan Kimathi")?.campus)
        assertEquals("SEKU", transportFor("South Eastern Kenya University")?.campus)
        assertEquals("MMUST", transportFor("Masinde Muliro")?.campus)
    }
}
