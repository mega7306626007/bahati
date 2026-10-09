package com.pesaflow.app.data.online

import kotlinx.serialization.Serializable

// M-Pesa Daraja integration point. There is no public Daraja endpoint that
// pulls statements — SMS import and statement upload remain the data truth —
// so this file carries only the conflict rule any future pull must obey,
// plus the gateway seam. Borrowed from PesaFlow-online (demo/real gateways
// left behind until credentials exist).
@Serializable
data class DarajaTransaction(
    val receipt: String,
    val amount: Double,
    val direction: String, // IN | OUT
    val counterparty: String,
    val timestampMs: Long,
    val category: String = "Uncategorised"
)

// Conflict rule, pure and tested: a receipt already seen via SMS is dropped
// from any external batch. Case/whitespace-insensitive.
fun mergeDarajaWithSms(
    daraja: List<DarajaTransaction>,
    smsReceipts: Set<String>
): List<DarajaTransaction> {
    val seen = smsReceipts.map { it.trim().uppercase() }.toSet()
    return daraja.filter { it.receipt.trim().uppercase() !in seen }
}

interface DarajaGateway {
    suspend fun fetchSince(sinceMs: Long): List<DarajaTransaction>
}
