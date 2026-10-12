package com.pesaflow.app.ui.buddy

// Auditable action history (allowed: action history, not financial truth).
// In-memory ring buffer: timestamp, command id, parameters, confirmation
// state, result. Supports debugging, trust, undo context and support.
// Pure, unit-tested.
data class BuddyAuditEntry(
    val timestamp: Long,
    val commandId: String,
    val parameters: String,
    val confirmed: Boolean,
    val success: Boolean,
    val detail: String
)

object BuddyAuditLog {
    private const val MAX = 100
    private val entries = ArrayDeque<BuddyAuditEntry>()

    @Synchronized
    fun record(entry: BuddyAuditEntry) {
        entries.addLast(entry)
        while (entries.size > MAX) entries.removeFirst()
    }

    @Synchronized
    fun recent(limit: Int = 20): List<BuddyAuditEntry> = entries.takeLast(limit.coerceAtLeast(0))

    @Synchronized
    fun clear() = entries.clear()
}
