package com.pesaflow.app.parsers

import com.pesaflow.app.data.ml.TrainingExport
import com.pesaflow.app.data.models.ModelFeedback
import org.junit.Assert.*
import org.junit.Test


class TrainingExportTest {

    @Test
    fun `phone codes amounts and accounts are scrubbed`() {
        val raw = "Sent KSh 500 to 0712345678 ref SH12AB34CD acct 123456"
        val clean = TrainingExport.anonymize(raw)
        assertFalse(clean.contains("0712345678"))
        assertFalse(clean.contains("SH12AB34CD"))
        assertFalse(clean.contains("123456"))
        assertTrue(clean.contains("PHONE"))
        assertTrue(clean.contains("REF"))
        assertTrue(clean.contains("ACCT"))
    }

    @Test
    fun `only accepted rows export and round-trip`() {
        val rows = listOf(
            ModelFeedback(
                merchant = "Java House",
                smsText = "lunch 250",
                suggestedType = "EXPENSE",
                suggestedCategory = "Food",
                suggestedConfidence = 0.9,
                suggestedSource = "CAT_CLF_v1",
                finalType = "EXPENSE",
                finalCategory = "Food",
                accepted = true
            ),
            ModelFeedback(
                merchant = "Unknown",
                smsText = "guess",
                suggestedType = "EXPENSE",
                suggestedCategory = "Food",
                suggestedConfidence = 0.4,
                suggestedSource = "CAT_CLF_v1",
                finalType = "EXPENSE",
                finalCategory = "Transport",
                accepted = false
            )
        )
        val exported = TrainingExport.toRows(rows)
        assertEquals(1, exported.size)
        assertEquals("Food", exported.first().category)
        val json = TrainingExport.toJson(exported)
        assertTrue(json.contains("\"schema\":1"))
        val back = TrainingExport.fromJson(json)
        assertEquals(1, back.size)
        assertEquals(exported.first().text, back.first().text)
    }
}
