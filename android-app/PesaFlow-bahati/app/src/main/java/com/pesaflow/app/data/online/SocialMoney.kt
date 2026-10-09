package com.pesaflow.app.data.online

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Social money: rent-split share codes + chama (group savings) math.
// Share codes are plain data URIs the app can paste into WhatsApp — no
// server round-trip needed to split rent with roommates. Borrowed from
// PesaFlow-online.
@Serializable
data class ShareSplit(
    val total: Double,
    val shares: Map<String, Double>,
    val note: String = ""
)

// "PF1|<base64url(json)>": versioned so v2 codes never misparse as v1.
fun encodeShareCode(split: ShareSplit): String {
    val json = Json.encodeToString(split)
    val b64 = java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString(json.toByteArray(Charsets.UTF_8))
    return "PF1|$b64"
}

fun parseShareCode(code: String): ShareSplit? {
    return try {
        val body = code.trim().removePrefix("PF1|")
        if (body == code.trim()) return null
        val json = java.util.Base64.getUrlDecoder().decode(body).toString(Charsets.UTF_8)
        val split = Json.decodeFromString<ShareSplit>(json)
        if (split.total <= 0 || split.shares.isEmpty()) return null
        if (split.shares.values.any { it < 0 }) return null
        split
    } catch (e: Exception) {
        null
    }
}

@Serializable
data class ChamaContribution(
    val member: String,
    val amount: Double,
    val timestampMs: Long
)

fun chamaTotals(contributions: List<ChamaContribution>): Map<String, Double> =
    contributions.groupBy { it.member.trim() }
        .mapValues { (_, cs) -> cs.sumOf { it.amount } }
        .filterKeys { it.isNotEmpty() }

// Members whose total is below the expected buy-in, most-behind first.
fun chamaDefaulters(
    contributions: List<ChamaContribution>,
    expectedPerMember: Double,
    members: Set<String>
): List<String> {
    val totals = chamaTotals(contributions)
    return members
        .map { it to (totals[it] ?: 0.0) }
        .filter { (_, paid) -> paid + 1e-9 < expectedPerMember }
        .sortedBy { (_, paid) -> paid }
        .map { (m, _) -> m }
}
