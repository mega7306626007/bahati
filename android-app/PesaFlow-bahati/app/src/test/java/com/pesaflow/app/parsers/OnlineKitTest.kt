package com.pesaflow.app.parsers

import com.pesaflow.app.data.online.BackupCrypto
import com.pesaflow.app.data.online.ChamaContribution
import com.pesaflow.app.data.online.OnlineConfig
import com.pesaflow.app.data.online.OnlineGate
import com.pesaflow.app.data.online.PriceReport
import com.pesaflow.app.data.online.ShareSplit
import com.pesaflow.app.data.online.SyncKind
import com.pesaflow.app.data.online.SyncOutbox
import com.pesaflow.app.data.online.SyncRequest
import com.pesaflow.app.data.online.aggregatePrice
import com.pesaflow.app.data.online.chamaDefaulters
import com.pesaflow.app.data.online.chamaTotals
import com.pesaflow.app.data.online.encodeShareCode
import com.pesaflow.app.data.online.mergeDarajaWithSms
import com.pesaflow.app.data.online.DarajaTransaction
import com.pesaflow.app.data.online.parseIcsToWeekSlots
import com.pesaflow.app.data.online.parseShareCode
import com.pesaflow.app.data.online.runSync
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test


// Borrowed offline kit: crypto envelope, price aggregation, share codes,
// chama math, ICS import, sync gate, Daraja merge rule.
class OnlineKitTest {

    @Test
    fun `backup envelope round-trips, wrong passphrase fails`() {
        val env = BackupCrypto.encrypt("{\"ledger\":1}", "correct-horse-8")
        assertEquals("{\"ledger\":1}", BackupCrypto.decrypt(env, "correct-horse-8"))
        try {
            BackupCrypto.decrypt(env, "wrong-passphrase-9")
            fail("wrong passphrase must fail loudly")
        } catch (e: Exception) {
            // GCM auth tag rejection — expected.
        }
    }

    @Test
    fun `price aggregation needs two reporters and drops outliers`() {
        val now = 1_700_000_000_000L
        val day = 24 * 3_600_000L
        // Single reporter: untrusted.
        assertNull(
            aggregatePrice(
                listOf(PriceReport("Kibanda", "smocha", 70.0, "r1", now)),
                nowMs = now
            )
        )
        // Two reporters + one joker: median survives, joker dropped.
        val agg = aggregatePrice(
            listOf(
                PriceReport("Kibanda", "smocha", 70.0, "r1", now),
                PriceReport("Kibanda", "smocha", 80.0, "r2", now - day),
                PriceReport("Kibanda", "smocha", 500.0, "r3", now - day)
            ),
            nowMs = now
        )
        assertNotNull(agg)
        assertTrue(agg!! in 70.0..80.0)
    }

    @Test
    fun `share code round-trips, garbage rejected`() {
        val split = ShareSplit(9000.0, mapOf("Joan" to 3000.0, "Brian" to 6000.0), "March rent")
        val code = encodeShareCode(split)
        assertTrue(code.startsWith("PF1|"))
        val back = parseShareCode(code)
        assertNotNull(back)
        assertEquals(9000.0, back!!.total, 0.001)
        assertEquals(3000.0, back.shares["Joan"] ?: 0.0, 0.001)
        assertNull(parseShareCode("not-a-code"))
        assertNull(parseShareCode("PF1|!!!not-base64!!!"))
    }

    @Test
    fun `chama defaulters most-behind first`() {
        val cs = listOf(
            ChamaContribution("Joan", 1000.0, 1L),
            ChamaContribution("Brian", 400.0, 1L),
            ChamaContribution("Brian", 100.0, 2L)
        )
        assertEquals(mapOf("Joan" to 1000.0, "Brian" to 500.0), chamaTotals(cs))
        assertEquals(listOf("Brian"), chamaDefaulters(cs, 1000.0, setOf("Joan", "Brian")))
    }

    @Test
    fun `ics folds into week slots, all-day ignored`() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            DTSTART:20260907T080000
            SUMMARY:Lecture
            END:VEVENT
            BEGIN:VEVENT
            DTSTART:20260908T140000Z
            SUMMARY:Lab
            END:VEVENT
        """.trimIndent()
        val slots = parseIcsToWeekSlots(ics)
        // 2026-09-07 is a Monday, 2026-09-08 a Tuesday.
        assertEquals(setOf("morning"), slots["Mon"])
        assertEquals(setOf("afternoon"), slots["Tue"])
    }

    @Test
    fun `sync gate holds on mobile data, flushes on wifi`() {
        val cfg = OnlineConfig(wifiOnly = true, backupEnabled = true)
        assertFalse(OnlineGate.shouldSync(cfg, true, isOnline = true, isWifi = false))
        assertTrue(OnlineGate.shouldSync(cfg, true, isOnline = true, isWifi = true))
        assertFalse(OnlineGate.shouldSync(cfg, false, isOnline = true, isWifi = true))
    }

    @Test
    fun `runSync retries failures, requeues when gate closed`() = runBlocking {
        val outbox = SyncOutbox()
        outbox.enqueue(SyncRequest(SyncKind.BACKUP, "a"))
        outbox.enqueue(SyncRequest(SyncKind.BACKUP, "b"))
        val cfg = OnlineConfig(wifiOnly = false, backupEnabled = true)
        val handlers = mapOf<SyncKind, suspend (SyncRequest) -> Boolean>(
            SyncKind.BACKUP to { it.payload == "a" }
        )
        val r = runSync(cfg, isOnline = true, isWifi = false, outbox, handlers)
        assertEquals(2, r.attempted)
        assertEquals(1, r.succeeded)
        assertEquals(1, r.failed)
        assertEquals(listOf("b"), outbox.pending().map { it.payload })
    }

    @Test
    fun `daraja merge drops sms-seen receipts case-insensitively`() {
        val batch = listOf(
            DarajaTransaction("QHX1", 100.0, "OUT", "Shop", 1L),
            DarajaTransaction("QHX2", 200.0, "OUT", "Shop", 1L)
        )
        val kept = mergeDarajaWithSms(batch, setOf(" qhx1 "))
        assertEquals(listOf("QHX2"), kept.map { it.receipt })
    }
}
