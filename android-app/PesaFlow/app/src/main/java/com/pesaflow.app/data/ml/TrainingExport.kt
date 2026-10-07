package com.pesaflow.app.data.ml

import com.pesaflow.app.data.models.ModelFeedback

/**
 * TrainingExport — opt-in anonymized export of confirmed model feedback
 * (spec §18). Pure Kotlin, JVM-testable.
 *
 * Privacy rules (hard, no exceptions):
 * - Export NOTHING unless the user toggled training opt-in in Settings.
 * - Only rows with accepted=true leave the device (corrections stay local
 *   until confirmed — a rejected suggestion is not a label).
 * - PII scrub: phone-like digit runs → PHONE, M-Pesa codes → REF,
 *   amounts → N, account digits → ACCT. Merchant names survive (needed
 *   for dictionary growth) but digit runs inside them are scrubbed too.
 * - Output is a versioned JSON envelope (schema v1) so a Python twin can
 *   retrain offline and ship weights back via TfIdfClassifier.deserialize.
 */
object TrainingExport {
    const val SCHEMA_VERSION = 1

    data class ExportRow(val text: String, val type: String, val category: String)

    fun anonymize(text: String): String {
        var s = text
        // Phone numbers first: 07../01.. (10 digits) or +254... A pure-digit
        // run must never fall through to the REF pattern below.
        s = s.replace(Regex("\\+254\\d{9}"), "PHONE")
        s = s.replace(Regex("\\b0[17]\\d{8}\\b"), "PHONE")
        // M-Pesa transaction codes: 10 alphanumerics WITH at least one
        // letter (e.g. SH12AB34CD). Pure digit runs are accounts, not refs.
        s = s.replace(Regex("\\b(?=[A-Z0-9]*[A-Z])[A-Z0-9]{10}\\b"), "REF")
        // Long digit runs (accounts, IDs, meters)
        s = s.replace(Regex("\\b\\d{5,}\\b"), "ACCT")
        // Amounts: KSh 1,234 / KSh 250 / 250 (standalone small ints kept only
        // when clearly not amounts — conservative: scrub KSh-prefixed + 3-4 digit)
        s = s.replace(Regex("(?i)\\bKSh\\s?[\\d,]+"), "KSh N")
        s = s.replace(Regex("\\b\\d{3,4}\\b"), "N")
        return s.trim().replace(Regex("\\s+"), " ")
    }

    /** Accepted feedback only. Returns versioned rows ready for JSON encoding. */
    fun toRows(feedback: List<ModelFeedback>): List<ExportRow> {
        return feedback.filter { it.accepted }.map { fb ->
            val raw = listOf(fb.merchant, fb.smsText).filter { it.isNotBlank() }.joinToString(" ")
            ExportRow(
                text = anonymize(raw.ifBlank { fb.suggestedCategory }),
                type = fb.finalType.ifBlank { fb.suggestedType },
                category = fb.finalCategory.ifBlank { fb.suggestedCategory }
            )
        }.filter { it.text.isNotBlank() && it.category.isNotBlank() }
    }

    fun toJson(rows: List<ExportRow>): String {
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
        val body = rows.joinToString(",") { r ->
            "{\"text\":\"${esc(r.text)}\",\"type\":\"${esc(r.type)}\",\"category\":\"${esc(r.category)}\"}"
        }
        return "{\"schema\":$SCHEMA_VERSION,\"rows\":[$body]}"
    }

    /** Minimal parser for the envelope above (import path validation in tests). */
    fun fromJson(json: String): List<ExportRow> {
        val rowRe = Regex("\\{\"text\":\"((?:[^\"\\\\]|\\\\.)*)\",\"type\":\"((?:[^\"\\\\]|\\\\.)*)\",\"category\":\"((?:[^\"\\\\]|\\\\.)*)\"\\}")
        fun unesc(s: String) = s.replace("\\\"", "\"").replace("\\\\", "\\")
        return rowRe.findAll(json).map { m ->
            ExportRow(unesc(m.groupValues[1]), unesc(m.groupValues[2]), unesc(m.groupValues[3]))
        }.toList()
    }
}
