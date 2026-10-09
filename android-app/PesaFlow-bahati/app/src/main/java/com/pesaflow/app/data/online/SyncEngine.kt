package com.pesaflow.app.data.online

import kotlinx.serialization.Serializable

// Offline-first sync scaffolding. Offline-first is non-negotiable: every
// future online feature checks OnlineGate.shouldSync before touching the
// radio (online + WiFi when wifiOnly), and everything is OFF until real
// credentials land. Pure policy + orchestration, zero Android APIs.
// Borrowed from PesaFlow-online (demo gateways left behind).
@Serializable
data class OnlineConfig(
    val wifiOnly: Boolean = true,
    val demoMode: Boolean = true,
    val darajaEnabled: Boolean = false,
    val backupEnabled: Boolean = false,
    val priceMapEnabled: Boolean = true,
    val watchtowerEnabled: Boolean = true,
    val socialEnabled: Boolean = true,
    val calendarImportEnabled: Boolean = true,
    val analyticsEnabled: Boolean = false,
    val darajaBaseUrl: String = "https://sandbox.safaricom.co.ke",
    val apiBaseUrl: String = "https://api.pesaflow.app"
)

object OnlineGate {
    // Pure policy: no Android APIs, fully unit-tested.
    fun shouldSync(cfg: OnlineConfig, featureOn: Boolean, isOnline: Boolean, isWifi: Boolean): Boolean {
        if (!featureOn) return false
        if (!isOnline) return false
        if (cfg.wifiOnly && !isWifi) return false
        return true
    }

    fun activeFeatures(cfg: OnlineConfig): List<String> = buildList {
        if (cfg.darajaEnabled) add("daraja")
        if (cfg.backupEnabled) add("backup")
        if (cfg.priceMapEnabled) add("price-map")
        if (cfg.watchtowerEnabled) add("watchtower")
        if (cfg.socialEnabled) add("social")
        if (cfg.calendarImportEnabled) add("calendar")
        if (cfg.analyticsEnabled) add("analytics")
    }
}

// One offline-first outbox for every future online feature. Tasks wait here
// until OnlineGate.shouldSync passes, then handlers run per kind.
enum class SyncKind { BACKUP, PRICE, HELB, SOCIAL }

data class SyncRequest(
    val kind: SyncKind,
    val payload: String = "",
    val enqueuedMs: Long = System.currentTimeMillis()
)

data class SyncReport(
    val attempted: Int,
    val succeeded: Int,
    val failed: Int,
    val skippedNoGate: Boolean
)

class SyncOutbox {
    private val queue = ArrayDeque<SyncRequest>()
    fun enqueue(request: SyncRequest) { queue.addLast(request) }
    fun pending(): List<SyncRequest> = queue.toList()
    fun size(): Int = queue.size
    fun clear() { queue.clear() }
    internal fun takeAll(): List<SyncRequest> {
        val all = queue.toList()
        queue.clear()
        return all
    }
    internal fun requeue(requests: List<SyncRequest>) {
        requests.reversed().forEach { queue.addFirst(it) }
    }
}

suspend fun runSync(
    cfg: OnlineConfig,
    isOnline: Boolean,
    isWifi: Boolean,
    outbox: SyncOutbox,
    handlers: Map<SyncKind, suspend (SyncRequest) -> Boolean>
): SyncReport {
    val due = outbox.takeAll()
    if (due.isEmpty()) return SyncReport(0, 0, 0, skippedNoGate = false)
    // Gate once per flush: all-or-nothing keeps accounting honest on
    // metered data — nothing half-uploads on bundles.
    val open = due.any { req ->
        val featureOn = when (req.kind) {
            SyncKind.BACKUP -> cfg.backupEnabled
            SyncKind.PRICE -> cfg.priceMapEnabled
            SyncKind.HELB -> cfg.watchtowerEnabled
            SyncKind.SOCIAL -> cfg.socialEnabled
        }
        OnlineGate.shouldSync(cfg, featureOn, isOnline, isWifi)
    }
    if (!open) {
        outbox.requeue(due)
        return SyncReport(0, 0, 0, skippedNoGate = true)
    }
    var ok = 0
    var failed = 0
    val retry = mutableListOf<SyncRequest>()
    for (req in due) {
        val handler = handlers[req.kind]
        val done = try {
            handler?.invoke(req) ?: false
        } catch (e: Exception) {
            false
        }
        if (done) ok++ else {
            failed++
            retry.add(req)
        }
    }
    outbox.requeue(retry)
    return SyncReport(due.size, ok, failed, skippedNoGate = false)
}
