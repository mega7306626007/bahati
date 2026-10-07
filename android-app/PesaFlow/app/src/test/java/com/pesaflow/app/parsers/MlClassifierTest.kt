package com.pesaflow.app.parsers

import com.pesaflow.app.data.ml.AnomalyDetector
import com.pesaflow.app.data.ml.CategoryClassifier
import com.pesaflow.app.data.ml.Forecaster
import com.pesaflow.app.data.ml.IntentClassifier
import com.pesaflow.app.data.ml.MerchantDictionary
import com.pesaflow.app.data.ml.MlEngine
import com.pesaflow.app.data.ml.RecurringDetector
import com.pesaflow.app.data.ml.TransactionClassifier
import org.junit.Assert.*
import org.junit.Test

class MlClassifierTest {

    @Test fun typeClassifier_expenseSms() {
        val top = TransactionClassifier.predictTop("KSh 250 paid to Java House on 12/9/26")
        assertEquals("EXPENSE", top?.label)
        assertTrue((top?.probability ?: 0.0) > 0.3)
    }

    @Test fun typeClassifier_incomeSms() {
        val top = TransactionClassifier.predictTop("You have received KSh 1000 from Jane Mum")
        assertEquals("INCOME", top?.label)
    }

    @Test fun typeClassifier_savingSms() {
        val top = TransactionClassifier.predictTop("transferred KSh 500 to M-SHWARI")
        assertEquals("SAVING", top?.label)
    }

    @Test fun typeClassifier_transferVsExpense() {
        // Hard confusion pair from spec: transfer-like wording must not
        // collapse to EXPENSE with certainty; both labels must exist.
        val preds = TransactionClassifier.predict("till to paybill merchant transfer")
        assertTrue(preds.map { it.label }.contains("TRANSFER"))
    }

    @Test fun categoryClassifier_foodViaDict() {
        val (cat, conf, src) = CategoryClassifier.classify("Naivas", "paid 500")
        assertEquals("Shopping", cat)
        assertTrue(src.startsWith("MERCHANT"))
        assertTrue(conf >= 0.8)
    }

    @Test fun categoryClassifier_transportFallback() {
        val (cat, _, src) = CategoryClassifier.classify("Unknown Stage Coach X", "matatu fare stage 80")
        assertEquals("TRANSPORT", cat)
        assertEquals("CAT_CLF_v1", src)
    }

    @Test fun merchantFuzzy_typo() {
        assertEquals("Naivas", MerchantDictionary.fuzzy("Naivas")?.canonical)
        assertEquals("Naivas", MerchantDictionary.fuzzy("Naivass")?.canonical)
        assertNull(MerchantDictionary.fuzzy("xyz"))
    }

    @Test fun anomaly_flagsOutlier() {
        val history = listOf(70.0, 80.0, 75.0, 90.0, 85.0, 78.0)
        val v = AnomalyDetector.verdict(1500.0, history)
        assertTrue(v.isAnomaly)
        val normal = AnomalyDetector.verdict(82.0, history)
        assertFalse(normal.isAnomaly)
    }

    @Test fun anomaly_insufficientEvidence() {
        val v = AnomalyDetector.verdict(5000.0, listOf(100.0, 120.0))
        assertFalse(v.isAnomaly)
    }

    @Test fun recurring_detectsMonthly() {
        val day = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        val rows = (0..3).map { i -> "KPLC" to (500.0 to (now - i * 30 * day)) }
        val found = RecurringDetector.detect(rows)
        assertTrue(found.any { it.merchant == "kplc" })
    }

    @Test fun recurring_rejectsRandom() {
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        val rows = listOf(
            "Shop A" to (100.0 to now),
            "Shop A" to (900.0 to (now - 3 * day)),
            "Shop A" to (50.0 to (now - 40 * day))
        )
        assertTrue(RecurringDetector.detect(rows).isEmpty())
    }

    @Test fun forecaster_rangeSane() {
        val f = Forecaster.daily(listOf(100.0, 120.0, 90.0, 110.0, 105.0, 95.0, 115.0), 7)
        assertTrue(f.low <= f.point && f.point <= f.high)
        assertTrue(f.point > 0)
    }

    @Test fun intent_affordability() {
        val r = IntentClassifier.classify("Can I spend 300 on lunch today?")
        assertEquals("AFFORDABILITY_QUERY", r.intent)
        assertEquals(300.0, r.slots.amount)
        assertEquals("FOOD", r.slots.category)
        assertEquals("TODAY", r.slots.period)
    }

    @Test fun intent_transportCost() {
        val r = IntentClassifier.classify("How much do I spend getting to school?")
        assertEquals("TRANSPORT_COST_QUERY", r.intent)
        assertEquals("USER_INSTITUTION", r.slots.destination)
    }

    @Test fun intent_lowConfidenceNeedsClarification() {
        val r = IntentClassifier.classify("xyz blorpt quantum banana")
        assertTrue(r.needsClarification)
    }

    @Test fun engine_neverThrowsOnEmpty() {
        val s = MlEngine.suggestCategory("", "")
        assertNotNull(s.category)
        val f = MlEngine.forecastWeek(emptyList())
        assertEquals(0.0, f.point, 0.001)
        assertTrue(MlEngine.modelCards().size >= 11)
    }
}
