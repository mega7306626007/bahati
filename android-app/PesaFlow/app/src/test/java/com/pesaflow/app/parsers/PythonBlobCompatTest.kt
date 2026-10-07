package com.pesaflow.app.parsers

import com.pesaflow.app.data.ml.TfIdfClassifier
import org.junit.Assert.*
import org.junit.Test


/**
 * End-to-end proof for the offline retrain loop (spec section 18):
 * blobs produced by scripts/retrain_nb.py (checked in under
 * src/test/resources/ml_blobs) must load in TfIdfClassifier.deserialize
 * and predict the labels they were trained on.
 *
 * Fresh classifier instances only — the global singletons are never
 * touched, so test order cannot pollute other suites.
 */
class PythonBlobCompatTest {

    private fun loadBlob(name: String): String {
        val loader = requireNotNull(javaClass.classLoader) { "no classloader" }
        val stream = loader.getResourceAsStream("ml_blobs/$name")
            ?: throw AssertionError("missing test resource ml_blobs/$name")
        return stream.bufferedReader().readText()
    }

    @Test
    fun `python type blob loads and predicts`() {
        val clf = TfIdfClassifier()
        clf.deserialize(loadBlob("type_NBv2.txt"))
        assertTrue(clf.vocabSize() > 10)
        assertEquals("EXPENSE", clf.predict("lunch pilau beans kibanda", 1).firstOrNull()?.label)
        assertEquals("INCOME", clf.predict("HELB upkeep disbursed", 1).firstOrNull()?.label)
        assertEquals("BILL", clf.predict("paybill KPLC tokens account", 1).firstOrNull()?.label)
    }

    @Test
    fun `python category blob loads and predicts`() {
        val clf = TfIdfClassifier()
        clf.deserialize(loadBlob("cat_NBv2.txt"))
        assertTrue(clf.vocabSize() > 10)
        assertEquals("Food", clf.predict("lunch pilau beans kibanda", 1).firstOrNull()?.label)
        assertEquals("Transport", clf.predict("matatu fare stage", 1).firstOrNull()?.label)
        assertEquals("Electricity", clf.predict("paybill KPLC tokens account", 1).firstOrNull()?.label)
    }
}
