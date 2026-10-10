package com.pesaflow.app.parsers

import com.pesaflow.app.data.ledger.ContactBook
import com.pesaflow.app.data.ledger.ContactEntry
import com.pesaflow.app.data.models.TransactionType
import org.junit.Assert.*
import org.junit.Test


class ContactBookTest {

    @Test
    fun `relationships list covers common roles`() {
        val rels = ContactBook.relationships()
        assertTrue(rels.contains("Friend"))
        assertTrue(rels.contains("Brother"))
        assertTrue(rels.contains("Sister"))
        assertTrue(rels.contains("Family"))
        assertTrue(rels.contains("Landlord"))
        assertTrue(rels.contains("Boss"))
        assertTrue(rels.contains("Business"))
        assertTrue(rels.contains("School"))
        assertTrue(rels.contains("Other"))
    }

    @Test
    fun `contact entry holds all fields`() {
        val e = ContactEntry(
            name = "samson",
            displayName = "Samson",
            relationship = "Friend",
            category = "Food",
            scope = "BOTH",
            notes = "College buddy",
            transactionCount = 5,
            lastSeen = 1000L,
            totalIn = 2000.0,
            totalOut = 500.0,
            matchTerms = "Nancy,Daniel Mayhvjh"
        )
        assertEquals("samson", e.name)
        assertEquals("Samson", e.displayName)
        assertEquals("Friend", e.relationship)
        assertEquals("Food", e.category)
        assertEquals("BOTH", e.scope)
        assertEquals("College buddy", e.notes)
        assertEquals(5, e.transactionCount)
        assertEquals(2000.0, e.totalIn, 0.001)
        assertEquals(500.0, e.totalOut, 0.001)
        assertEquals("Nancy,Daniel Mayhvjh", e.matchTerms)
    }

    @Test
    fun `transaction type enum has income and expense`() {
        assertEquals(TransactionType.INCOME, TransactionType.valueOf("INCOME"))
        assertEquals(TransactionType.EXPENSE, TransactionType.EXPENSE)
    }

    private class FakePrefs : android.content.SharedPreferences {
        val map = mutableMapOf<String, String>()
        inner class Ed : android.content.SharedPreferences.Editor {
            private val pending = mutableMapOf<String, String?>()
            private var clearAll = false
            override fun clear(): android.content.SharedPreferences.Editor { clearAll = true; return this }
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
            override fun putString(k: String, v: String?): android.content.SharedPreferences.Editor { pending[k] = v; return this }
            override fun putStringSet(k: String, v: MutableSet<String>?) = this
            override fun remove(k: String): android.content.SharedPreferences.Editor { pending[k] = null; return this }
        }
        override fun contains(k: String) = map.containsKey(k)
        override fun edit(): android.content.SharedPreferences.Editor = Ed()
        override fun getAll(): Map<String, *> = map.toMap()
        override fun getBoolean(k: String, d: Boolean) = d
        override fun getFloat(k: String, d: Float) = d
        override fun getInt(k: String, d: Int) = d
        override fun getLong(k: String, d: Long) = d
        override fun getString(k: String, d: String?) = map[k] ?: d
        override fun getStringSet(k: String, d: MutableSet<String>?) = d
        override fun registerOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    @Test
    fun `pipes in fields never shift columns on re-read`() {
        val p = FakePrefs()
        assertTrue(
            ContactBook.save(
                p, "Joan|Mueni", "Joan | Mueni", "Sis|ter", "Up|keep",
                "BOTH", "note|one", "Nan|cy"
            )
        )
        val all = ContactBook.readAll(p)
        // One entry, never a ghost row from a split line.
        assertEquals(1, all.size)
        val e = all[0]
        assertFalse(e.displayName.contains("|"))
        assertFalse(e.relationship.contains("|"))
        assertFalse(e.category.contains("|"))
        assertFalse(e.matchTerms.contains("|"))
        assertEquals("Joan Mueni", e.displayName)
        // Numeric tail parses from its own columns — never shifted garbage,
        // never collapsed to the 0 / 0.0 defaults of a broken row.
        assertEquals(0, e.transactionCount)
        assertEquals(0.0, e.totalIn, 0.001)
        assertEquals(0.0, e.totalOut, 0.001)
    }

    @Test
    fun `second save keeps first contact intact with real totals`() {
        val p = FakePrefs()
        assertTrue(ContactBook.save(p, "Joan", "Joan", "Sister", "Upkeep", "BOTH", "", ""))
        ContactBook.recordTransaction(p, "Joan", TransactionType.INCOME, 2000.0, 1000L)
        assertTrue(ContactBook.save(p, "Brian|Otis", "Brian | Otis", "Friend", "Food", "OUT", "", "Bri|an"))
        val all = ContactBook.readAll(p)
        assertEquals(2, all.size)
        val joan = all.first { it.name == "joan" }
        assertEquals("Joan", joan.displayName)
        assertEquals("Sister", joan.relationship)
        assertEquals(1, joan.transactionCount)
        assertEquals(2000.0, joan.totalIn, 0.001)
        val brian = all.first { it.name == "brianotis" }
        assertEquals("Brian Otis", brian.displayName)
        assertEquals("Friend", brian.relationship)
    }

    @Test
    fun `match terms tally into the card without one contact per name`() {
        // The Transport card: one entry, operator names as match terms.
        // Rows from any listed operator tally into it.
        val p = FakePrefs()
        assertTrue(
            ContactBook.save(
                p, "Transport", "Transport · rides", "Business", "Transport",
                "OUT", "Auto-collected ride names", "Super Metro, Ena Coach"
            )
        )
        ContactBook.recordTransaction(p, "SUPER METRO", TransactionType.EXPENSE, 80.0, 1000L)
        ContactBook.recordTransaction(p, "ENA COACH", TransactionType.EXPENSE, 1500.0, 2000L)
        ContactBook.recordTransaction(p, "NAIVAS", TransactionType.EXPENSE, 500.0, 3000L)
        val card = ContactBook.readAll(p).first { it.name == "transport" }
        assertEquals(2, card.transactionCount)
        assertEquals(1580.0, card.totalOut, 0.001)
    }

    @Test
    fun `match terms never steal near-miss merchants`() {
        val p = FakePrefs()
        assertTrue(
            ContactBook.save(
                p, "Transport", "Transport · rides", "Business", "Transport",
                "OUT", "", "Rog, Zuri"
            )
        )
        // Whole-word terms: "Rogue Salon" and "Missouri" stay out.
        ContactBook.recordTransaction(p, "Rogue Salon", TransactionType.EXPENSE, 300.0, 1000L)
        ContactBook.recordTransaction(p, "ROG SACCO", TransactionType.EXPENSE, 60.0, 2000L)
        val card = ContactBook.readAll(p).first { it.name == "transport" }
        assertEquals(1, card.transactionCount)
        assertEquals(60.0, card.totalOut, 0.001)
    }

    @Test
    fun `legacy two-entry book recovers both contacts with no ghost`() {
        // Exactly what the old pipe writer persisted for two onboarding
        // contacts with empty notes: entry lines joined with "||".
        val p = FakePrefs()
        p.edit().putString(
            "contact_book",
            "joan|Joan|Sister|Upkeep|BOTH||0|0|0.0|0.0|||brian|Brian|Friend|Food|OUT||0|0|0.0|0.0|"
        ).apply()
        val all = ContactBook.readAll(p)
        assertEquals(2, all.size)
        assertEquals("joan", all[0].name)
        assertEquals("Joan", all[0].displayName)
        assertEquals("Sister", all[0].relationship)
        assertEquals("brian", all[1].name)
        assertEquals("Brian", all[1].displayName)
        assertEquals("Friend", all[1].relationship)
        // No ghost "0"/"0.0" row anywhere.
        assertTrue(all.none { it.name.matches(Regex("^\\d+(\\.\\d+)?$")) })
    }

    @Test
    fun `legacy ghost numeric tail is dropped`() {
        val p = FakePrefs()
        p.edit().putString("contact_book", "0|0|0.0").apply()
        assertTrue(ContactBook.readAll(p).isEmpty())
    }

    @Test
    fun `save then record then save keeps real totals`() {
        val p = FakePrefs()
        assertTrue(ContactBook.save(p, "Joan", "Joan", "Sister", "Upkeep", "BOTH", "", ""))
        ContactBook.recordTransaction(p, "Joan", TransactionType.INCOME, 2000.0, 1000L)
        assertTrue(ContactBook.save(p, "Brian", "Brian", "Friend", "Food", "OUT", "", ""))
        val all = ContactBook.readAll(p)
        assertEquals(2, all.size)
        val joan = all.first { it.name == "joan" }
        assertEquals(1, joan.transactionCount)
        assertEquals(1000L, joan.lastSeen)
        assertEquals(2000.0, joan.totalIn, 0.001)
        assertEquals(0.0, joan.totalOut, 0.001)
        assertEquals("Brian", all.first { it.name == "brian" }.displayName)
    }

    @Test
    fun `insert assigns stable id preserved across rename`() {
        val p = FakePrefs()
        assertTrue(ContactBook.save(p, "Joan", "Joan", "Sister", "Upkeep", "BOTH", "", ""))
        val id = ContactBook.readAll(p).first { it.name == "joan" }.id
        assertEquals("joan", id)
        // Rename (same key, new display): identity must not shift.
        assertTrue(ContactBook.save(p, "Joan", "Joanie", "Sister", "Upkeep", "BOTH", "", ""))
        val after = ContactBook.readAll(p)
        assertEquals(1, after.size)
        assertEquals(id, after[0].id)
        assertEquals("Joanie", after[0].displayName)
    }

    @Test
    fun `legacy entries backfill ids on read`() {
        val p = FakePrefs()
        p.edit().putString(
            "contact_book",
            "joan|Joan|Sister|Upkeep|BOTH||0|0|0.0|0.0|||brian|Brian|Friend|Food|OUT||0|0|0.0|0.0|"
        ).apply()
        val all = ContactBook.readAll(p)
        assertEquals(listOf("Joan", "Brian"), all.map { it.displayName })
        assertTrue(all.all { it.id.isNotBlank() })
        assertEquals("Joan", all.first { it.id == "Joan" }.displayName)
    }

    @Test
    fun `record tallies known contacts, never invents entries`() {
        val p = FakePrefs()
        assertTrue(ContactBook.save(p, "Joan", "Joan", "Sister", "Upkeep", "BOTH", "", ""))
        ContactBook.recordTransaction(p, "NANCY will not match", TransactionType.EXPENSE, 100.0, 5L)
        var all = ContactBook.readAll(p)
        assertEquals(1, all.size)
        assertEquals(0, all[0].transactionCount)
        ContactBook.recordTransaction(p, "Customer Transfer to 0712 - JANE wambui", TransactionType.EXPENSE, 100.0, 5L)
        ContactBook.recordTransaction(p, "Joan", TransactionType.INCOME, 2000.0, 9L)
        all = ContactBook.readAll(p)
        // "Joan" exact-matches; the Jane text matches nobody.
        assertEquals(1, all.first { it.name == "joan" }.transactionCount)
        assertEquals(2000.0, all.first { it.name == "joan" }.totalIn, 0.001)
        assertEquals(9L, all.first { it.name == "joan" }.lastSeen)
    }

    @Test
    fun `rebuild heals stale zero counters from ledger history`() {
        val p = FakePrefs()
        assertTrue(ContactBook.save(p, "Brian", "Brian", "Friend", "Food", "OUT", "", ""))
        val txs = listOf(
            com.pesaflow.app.data.models.Transaction(
                amount = 250.0,
                type = TransactionType.EXPENSE,
                category = "Food",
                dateTimestamp = 77L,
                merchant = "BRIAN OTIS",
                source = com.pesaflow.app.data.models.TransactionSource.MPESA_SMS
            )
        )
        ContactBook.rebuildStats(p, txs)
        val brian = ContactBook.readAll(p).first { it.name == "brian" }
        assertEquals(1, brian.transactionCount)
        assertEquals(77L, brian.lastSeen)
        assertEquals(250.0, brian.totalOut, 0.001)
    }

    @Test
    fun `memory store scrubs pipes the same way`() {
        val p = FakePrefs()
        assertTrue(
            com.pesaflow.app.data.ledger.saveContactMemory(
                p, "Joan|Mueni", "Sis|ter", "Up|keep", "BOTH", "Nan|cy"
            )
        )
        val mems = com.pesaflow.app.data.ledger.readContactMemories(p.all.mapValues { it.value as String })
        val mem = mems["Joan Mueni"] ?: mems.values.firstOrNull()
        assertNotNull(mem)
        assertFalse(mem!!.label.contains("|"))
        assertFalse(mem.category.contains("|"))
        assertTrue(mem.matchTerms.none { it.contains("|") })
    }
}
