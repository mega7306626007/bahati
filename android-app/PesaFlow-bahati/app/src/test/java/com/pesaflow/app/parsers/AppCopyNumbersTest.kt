package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.AppLanguage
import com.pesaflow.app.ui.language.moneySpoken
import com.pesaflow.app.ui.language.moneyWords
import com.pesaflow.app.ui.language.swahiliUnder100
import org.junit.Assert.*
import org.junit.Test


// Regression: swahiliUnder100 indexed swTens past its 10 entries for any
// n >= 100, crashing Reports (ArrayIndexOutOfBounds length=10) on real
// ledgers the moment a KSh 140k+ figure was spoken in Swahili/Sheng/Mixed.
class AppCopyNumbersTest {

    @Test
    fun `small numbers keep their exact words`() {
        assertEquals("sifuri", swahiliUnder100(0))
        assertEquals("moja", swahiliUnder100(1))
        assertEquals("kumi na nne", swahiliUnder100(14))
        assertEquals("tisini", swahiliUnder100(90))
    }

    @Test
    fun `former crash magnitudes now render`() {
        // Thousands/hundreds flow straight through moneyWords on big ledgers.
        assertTrue(swahiliUnder100(147).contains("mia"))
        assertTrue(swahiliUnder100(1724).contains("elfu"))
        assertTrue(moneyWords(140000.0, AppLanguage.KISWAHILI).contains("elfu"))
        assertTrue(moneySpoken(172416.0, AppLanguage.KISWAHILI).contains("KSh 172416"))
        assertTrue(moneySpoken(219523.0, AppLanguage.SHENG).contains("KSh 219523"))
        assertTrue(moneySpoken(47107.0, AppLanguage.MIXED).contains("KSh 47107"))
    }

    @Test
    fun `no amount in ledger range ever throws`() {
        val langs = listOf(AppLanguage.ENGLISH, AppLanguage.KISWAHILI, AppLanguage.SHENG, AppLanguage.MIXED)
        val boundaries = listOf(0, 1, 9, 10, 99, 100, 999, 1000, 9999, 10000, 99999, 100000, 140000, 147107, 172416, 219523, 999999, 1000000, 2500000)
        boundaries.forEach { k ->
            langs.forEach { lang ->
                val spoken = moneySpoken(k.toDouble(), lang)
                assertTrue(spoken.contains("KSh $k"))
            }
        }
        var k = 0
        while (k <= 300000) {
            langs.forEach { lang -> moneySpoken(k.toDouble(), lang) }
            k += 137
        }
    }
}
