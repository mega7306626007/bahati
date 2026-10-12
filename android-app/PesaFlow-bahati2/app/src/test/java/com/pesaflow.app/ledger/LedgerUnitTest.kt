package com.pesaflow.app.ledger

import android.content.SharedPreferences
import com.pesaflow.app.data.ledger.AmountParser
import com.pesaflow.app.data.ledger.CategoryMemory
import com.pesaflow.app.data.ledger.LedgerGateway
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test


/** Pure-JVM tests for the ledger funnel: no Android runtime needed
 *  (SharedPreferences is faked via Proxy). */
class LedgerUnitTest {

    // ---- AmountParser ----

    @Test
    fun `plain amount parses`() {
        assertEquals(250.0, AmountParser.parse("250")!!, 0.001)
    }


    @Test
    fun `grouped thousands parse`() {
        assertEquals(1250.0, AmountParser.parse("1,250")!!, 0.001)
    }


    @Test
    fun `k suffix multiplies`() {
        assertEquals(2000.0, AmountParser.parse("2k")!!, 0.001)
        assertEquals(1500.0, AmountParser.parse("1.5K")!!, 0.001)
    }


    @Test
    fun `trailing sentence dot is not a decimal`() {
        assertEquals(99.0, AmountParser.parse("KSh99.00.")!!, 0.001)
    }


    @Test
    fun `junk returns null`() {
        assertNull(AmountParser.parse(null))
        assertNull(AmountParser.parse(""))
        assertNull(AmountParser.parse("lunch"))
        assertNull(AmountParser.parse("-50"))
        assertNull(AmountParser.parse("0"))
    }


    @Test
    fun `last token wins`() {
        assertEquals(100.0, AmountParser.parseLast("2 chapo 100")!!, 0.001)
        assertNull(AmountParser.parseLast("no numbers here"))
    }


    // ---- LedgerGateway.normalizeMerchant ----

    @Test
    fun `merchant normalizes case and spacing`() {
        assertEquals("Kibanda", LedgerGateway.normalizeMerchant("  kibanda  ", "Food"))
        assertEquals("Java House", LedgerGateway.normalizeMerchant("java house", "Food"))
    }


    @Test
    fun `blank merchant falls back to category`() {
        assertEquals("Food", LedgerGateway.normalizeMerchant("", "Food"))
        assertEquals("General", LedgerGateway.normalizeMerchant("General", ""))
    }


    // ---- CategoryMemory ----

    @Test
    fun `learned merchant beats future guesses`() {
        val prefs = fakePrefs()
        assertNull(CategoryMemory.lookup(prefs, "Kibanda"))
        CategoryMemory.learn(prefs, "kibanda", "Food")
        assertEquals("Food", CategoryMemory.lookup(prefs, "KIBANDA"))
    }


    @Test
    fun `other is never learned`() {
        val prefs = fakePrefs()
        CategoryMemory.learn(prefs, "Mystery", "Other")
        assertNull(CategoryMemory.lookup(prefs, "Mystery"))
    }


    @Suppress("UNCHECKED_CAST")
    private fun fakePrefs(): SharedPreferences {
        val store = mutableMapOf<String, String>()
        // Editor proxy must return itself for chaining; build it first.
        val editor = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(android.content.SharedPreferences.Editor::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "putString" -> {
                    store[args!![0] as String] = args[1] as String
                    proxy
                }
                "commit" -> true
                else -> if (method.returnType == Boolean::class.javaPrimitiveType) false else proxy
            }
        } as android.content.SharedPreferences.Editor
        return Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getString" -> store[args!![0] as String] ?: args[1] as String?
                "getBoolean" -> (store[args!![0] as String]?.toBoolean() ?: (args[1] as Boolean))
                "edit" -> editor
                "contains" -> store.containsKey(args!![0] as String)
                else -> null
            }
        } as SharedPreferences
    }
}
