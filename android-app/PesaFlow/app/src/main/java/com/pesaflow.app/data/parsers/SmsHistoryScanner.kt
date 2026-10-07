package com.pesaflow.app.data.parsers

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.TransactionType

// First-run M-Pesa history scan: up to ~5 months of SMS for figure
// confirmation and gentle re-adjustment of onboarding estimates.
// Read-only — nothing is written here; callers queue via tryQueuePending
// (which dedupes) and use the aggregates as editable starting suggestions,
// never silent facts. Monthly math always divides by the actual window
// (daysBack / 30), so a 150-day scan and a 60-day scan pace identically.
data class SmsScanResult(
    val found: Int = 0,
    val parsed: List<PendingTransaction> = emptyList(),
    val unreadable: Int = 0,
    val incomeTotal: Double = 0.0,
    val expenseTotal: Double = 0.0,
    val byCategory: Map<String, Double> = emptyMap(),
    val daysBack: Int = 60,
    // Honesty + grouping: cap flag, row limit, month buckets, top senders,
    // and the ACTUAL day-span the texts cover (not the requested window).
    val maxRows: Int = 5000,
    val byMonth: Map<String, Int> = emptyMap(),
    val topSenders: List<Pair<String, Int>> = emptyList(),
    val spanDays: Int = 0
) {
    val capped: Boolean get() = found >= maxRows
    private val months: Double get() = (daysBack / 30.0).coerceAtLeast(1.0)
    val monthlyIncome: Double get() = incomeTotal / months
    val monthlyExpense: Double get() = expenseTotal / months
    fun monthlyFor(vararg names: String): Double {
        val keys = names.map { it.lowercase() }.toSet()
        return byCategory.filterKeys { it.lowercase() in keys }.values.sum() / months
    }
    // 100%-reading report card: share of texts that became dated cashflows.
    // Anything left in `unreadable` had no KSh amount or was promo/balance.
    val readRate: Double get() = if (found > 0) parsed.size.toDouble() / found else 0.0
    val unknownCount: Int get() = parsed.count { it.category == "Unknown" }
}

suspend fun scanRecentSms(
    context: Context,
    daysBack: Int = 150,
    maxRows: Int = 5000
): SmsScanResult {
    if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
        return SmsScanResult()
    }
    val since = System.currentTimeMillis() - daysBack * 24L * 60 * 60 * 1000
    val parsed = mutableListOf<PendingTransaction>()
    val stamps = mutableListOf<Long>()
    val senders = mutableMapOf<String, Int>()
    var found = 0
    var unreadable = 0
    try {
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf("_id", "address", "body", "date"),
            "(address LIKE ? OR address LIKE ? OR address LIKE ? OR address LIKE ? OR body LIKE ? OR body LIKE ? OR body LIKE ? OR body LIKE ?) AND date >= ?",
            arrayOf("%MPESA%", "%Safaricom%", "%M-PESA%", "%KCB%", "%EQUITY%", "%KSh%", "%KES%", "%HELB%", since.toString()),
            "date DESC LIMIT $maxRows"
        )?.use { c ->
            val bodyIdx = c.getColumnIndexOrThrow("body")
            val addrIdx = c.getColumnIndexOrThrow("address")
            val dateIdx = c.getColumnIndexOrThrow("date")
            while (c.moveToNext()) {
                found++
                val body = c.getString(bodyIdx) ?: ""
                val sender = (try { c.getString(addrIdx) } catch (e: Exception) { null }) ?: "?"
                val ts = try { c.getLong(dateIdx) } catch (e: Exception) { 0L }
                if (ts > 0) stamps.add(ts)
                senders[sender] = (senders[sender] ?: 0) + 1
                // The inbox clock rides along: dateless texts land on their
                // real day instead of scan day (sender too — bank SMS bodies
                // omit the bank name, the address carries it).
                val p = MpesaParser.parseMessage(body, sender, ts)
                if (p == null) unreadable++ else parsed.add(p)
            }
        }
    } catch (e: SecurityException) {
        return SmsScanResult()
    } catch (e: Exception) {
        return SmsScanResult(found = found)
    }
    var income = 0.0
    var expense = 0.0
    val cats = mutableMapOf<String, Double>()
    parsed.forEach { p ->
        if (p.type == TransactionType.INCOME) income += p.amount else expense += p.amount
        if (p.type != TransactionType.INCOME) cats[p.category] = (cats[p.category] ?: 0.0) + p.amount
    }
    val months = bucketByMonth(stamps)
    val top = senders.entries.sortedByDescending { it.value }.take(5).map { it.key to it.value }
    // Face memory: rows from already-labelled people arrive pre-named
    // ("Nancy · Mother") with their usual category — only strangers and
    // "ask me" people need your eyes.
    val remembered = try {
        readContactMemories(
            context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE).all
        )
    } catch (e: Exception) { emptyMap() }
    val named = applyContactMemory(parsed, remembered)
    // Distinct calendar days via the shared timeline (midnight clusterers count).
    val span = com.pesaflow.app.data.academic.distinctDays(stamps)
    return SmsScanResult(found, named, unreadable, income, expense, cats, daysBack, maxRows, months, top, span)
}
