package com.pesaflow.app.parsers

import android.content.SharedPreferences
import com.pesaflow.app.data.ledger.MerchantMemory
import org.junit.Assert.*
import org.junit.Test


// Merchant aliases: name a sender once ("Lucy" → "Mom"), every future row
// shows it. Pure prefs-map logic under test with an in-memory fake.
class MerchantMemoryTest {

    private class FakePrefs : SharedPreferences {
        val map = mutableMapOf<String, String>()
        inner class Ed : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, String?>()
            private var clearAll = false
            override fun clear(): SharedPreferences.Editor { clearAll = true; return this }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearAll) { map.clear(); clearAll = false }
                pending.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
                pending.clear()
            }
            override fun putBoolean(k: String, v: Boolean) = this
            override fun putFloat(k: String, v: Float) = this
            override fun putInt(k: String, v: Int) = this
            override fun putLong(k: String, v: Long) = this
            override fun putString(k: String, v: String?): SharedPreferences.Editor { pending[k] = v; return this }
            override fun putStringSet(k: String, v: MutableSet<String>?) = this
            override fun remove(k: String): SharedPreferences.Editor { pending[k] = null; return this }
        }
        override fun contains(k: String) = map.containsKey(k)
        override fun edit(): SharedPreferences.Editor = Ed()
        override fun getAll(): Map<String, *> = map.toMap()
        override fun getBoolean(k: String, d: Boolean) = d
        override fun getFloat(k: String, d: Float) = d
        override fun getInt(k: String, d: Int) = d
        override fun getLong(k: String, d: Long) = d
        override fun getString(k: String, d: String?) = map[k] ?: d
        override fun getStringSet(k: String, d: MutableSet<String>?) = d
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    @Test
    fun `learn then lookup roundtrips case insensitively`() {
        val p = FakePrefs()
        assertTrue(MerchantMemory.learnAlias(p, "Lucy", "Mom"))
        assertEquals("Mom", MerchantMemory.lookup(p, "lucy")?.label)
        assertEquals("Mom", MerchantMemory.lookup(p, "  LUCY  ")?.label)
        assertEquals("Mom", MerchantMemory.displayName(p, "Lucy", "Lucy"))
    }

    @Test
    fun `blank names are rejected and unknown falls back`() {
        val p = FakePrefs()
        assertFalse(MerchantMemory.learnAlias(p, "Lucy", "   "))
        assertFalse(MerchantMemory.learnAlias(p, "   ", "Mom"))
        assertNull(MerchantMemory.lookup(p, "Lucy"))
        assertEquals("Lucy", MerchantMemory.displayName(p, "Lucy", "Lucy"))
    }

    @Test
    fun `separators are stripped so storage never corrupts`() {
        val p = FakePrefs()
        assertTrue(MerchantMemory.learnAlias(p, "A|B=C;D", "Mom|X"))
        assertEquals("MomX", MerchantMemory.lookup(p, "a|b=c;d")?.label)
        assertTrue(MerchantMemory.learnAlias(p, "Zed", "Cousin"))
        assertEquals("Cousin", MerchantMemory.lookup(p, "zed")?.label)
    }

    @Test
    fun `latest teaching wins and forget removes`() {
        val p = FakePrefs()
        MerchantMemory.learnAlias(p, "Lucy", "Mom")
        MerchantMemory.learnAlias(p, "Lucy", "Auntie")
        assertEquals("Auntie", MerchantMemory.lookup(p, "Lucy")?.label)
        MerchantMemory.clear(p, "Lucy")
        assertNull(MerchantMemory.lookup(p, "Lucy"))
    }

    @Test
    fun `cap evicts oldest first`() {
        val p = FakePrefs()
        for (i in 0..205) MerchantMemory.learnAlias(p, "sender$i", "Friend$i")
        assertNull(MerchantMemory.lookup(p, "sender0"))
        assertNull(MerchantMemory.lookup(p, "sender5"))
        assertEquals("Friend205", MerchantMemory.lookup(p, "sender205")?.label)
    }
}
