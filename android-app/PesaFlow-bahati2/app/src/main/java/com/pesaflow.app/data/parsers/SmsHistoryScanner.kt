package com.pesaflow.app.data.parsers

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.pesaflow.app.data.ledger.applyContactMemory
import com.pesaflow.app.data.ledger.readContactMemories
import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.models.isEarnedIncome
import com.pesaflow.app.data.models.isFeeRow

// First-run M-Pesa history scan: last 60 days of SMS for figure confirmation
// and gentle re-adjustment of onboarding estimates. Read-only — nothing is
// written here; callers queue via tryQueuePending (which dedupes) and use the
// aggregates as editable starting suggestions, never silent facts.
data class SmsScanResult(
    val found: Int = 0,
    val parsed: List<PendingTransaction> = emptyList(),
    val unreadable: Int = 0,
    val incomeTotal: Double = 0.0,
    val expenseTotal: Double = 0.0,
    val byCategory: Map<String, Double> = emptyMap(),
    // Window the totals cover — monthly paces divide by months scanned,
    // not a hardcoded 2 (60-day scans lied for every other window).
    val daysBack: Int = 60,
    // True when the inbox outgrew maxRows: "found" is a floor, not a census.
    // Callers must say "1,500+" instead of a flat "1,500".
    val capped: Boolean = false,
    // Honest report card: volume by month, loudest senders, read rate.
    val byMonth: Map<String, Int> = emptyMap(),
    val topSenders: List<Pair<String, Int>> = emptyList(),
    val error: String? = null,
    // What was counted — "texts" for inbox scans, "rows" for statement
    // files. The welcome report card shares one copy for both.
    val label: String = "texts",
    // Transaction-cost tails seen across the WHOLE scan (every month), apart
    // from the current-month fee pot the insights read. Never in expenses.
    val feeTotal: Double = 0.0,
    // Month-to-date actuals: opening the app mid-month (the 11th) fills
    // from these 11 days instead of waiting for month-end.
    val mtdIncome: Double = 0.0,
    val mtdExpense: Double = 0.0,
    val mtdDays: Int = 0
) {
    private val months: Double get() = (daysBack / 30.0).coerceAtLeast(1.0 / 30)
    val monthlyIncome: Double get() = incomeTotal / months
    val monthlyExpense: Double get() = expenseTotal / months
    val readRate: Double get() = if (found > 0) parsed.size.toDouble() / found else 1.0
    fun monthlyFor(vararg names: String): Double {
        val keys = names.map { it.lowercase() }.toSet()
        return byCategory.filterKeys { it.lowercase() in keys }.values.sum() / months
    }
}

// Dual-SIM without phone-state permission: subscription IDs are stable per
// device, so first-seen order names the slots (SIM 1, SIM 2, …). Pure and
// unit-tested — the scanner persists the returned map, nothing else.
// Unknown/absent sub_id stays -1 (single SIM or old device).
internal fun assignSimSlot(known: Map<Long, Int>, subId: Long): Pair<Int, Map<Long, Int>> {
    if (subId < 0) return -1 to known
    known[subId]?.let { return it to known }
    val slot = (known.values.maxOrNull() ?: -1) + 1
    return slot to (known + (subId to slot))
}

suspend fun scanRecentSms(
    context: Context,
    daysBack: Int = 60,
    maxRows: Int = 500,
    // Chunked paging with honest progress: big inboxes (5-month onboarding
    // scans) report per-page instead of hanging silently. Cancel cooperates
    // between pages; partial results still return.
    pageSize: Int = 150,
    onProgress: (found: Int, parsed: Int) -> Unit = { _, _ -> },
    // Streaming import: invoked per page with that page's newly parsed rows
    // (contact memory already applied, idempotently) so callers can queue
    // each batch as it arrives instead of waiting for the whole scan.
    // Null by default — existing callers are unaffected.
    onPage: (suspend (Int, List<PendingTransaction>) -> Unit)? = null,
    isCancelled: () -> Boolean = { false },
    sinceTimestamp: Long? = null,
    persistDerivedSignals: Boolean = true
): SmsScanResult {
    if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
        return SmsScanResult()
    }
    val since = sinceTimestamp ?: System.currentTimeMillis() - daysBack * 24L * 60 * 60 * 1000
    val parsed = mutableListOf<PendingTransaction>()
    val scannedBodies = mutableListOf<String>()
    var found = 0
    var unreadable = 0
    var newestBalance: Double? = null
    var capped: Boolean
    // Dual-SIM slot map (sub_id → 0/1), seeded from prefs and extended for
    // first-seen IDs. Written back once at the end, never per row.
    val slotPrefs = context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE)
    var slotKnown: Map<Long, Int> = slotPrefs.all.mapNotNull { (k, v) ->
        if (k.startsWith("sim_slot_") && v is Int) k.removePrefix("sim_slot_").toLongOrNull()?.let { it to v } else null
    }.toMap()
    var slotsDirty = false
    // Identity memory loaded once up front so each page can be resolved as
    // it is parsed (stamping is idempotent — re-resolution at the end below
    // is a no-op for already-stamped rows).
    val memPrefs = context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE)
    val memories = readContactMemories(
        memPrefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
    )
    // Newest-first page order: the first balance tail found is the latest wallet figure.
    var pageIndex = 0
    try {
        // MPESA + Airtel only, by sender — bank/HELB/SACCO dragnets and the
        // %KES% body catch-all are gone: statements cover those, and every
        // extra sender was unreviewable noise. Personal senders are skipped
        // in the loop below: a friend's money talk is never a candidate.
        var offset = 0
        var pageRows = 0
        while (offset < maxRows && !isCancelled()) {
            pageRows = 0
            val pageParsed = mutableListOf<PendingTransaction>()
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf("_id", "address", "body", "date", "sub_id"),
                "(address = ? OR address LIKE ?) AND date >= ?",
                arrayOf("MPESA", "AIRTEL%", since.toString()),
                "date DESC LIMIT $pageSize OFFSET $offset"
            )?.use { c ->
                val bodyIdx = c.getColumnIndexOrThrow("body")
                val addrIdx = c.getColumnIndexOrThrow("address")
                val dateIdx = c.getColumnIndexOrThrow("date")
                val subIdIdx = c.getColumnIndex("sub_id")
                while (c.moveToNext()) {
                    pageRows++
                    val body = c.getString(bodyIdx) ?: ""
                    val sender = try { c.getString(addrIdx) ?: "" } catch (e: Exception) { "" }
                    // Personal senders skipped silently: not candidates, not
                    // "found", not "unreadable" — they never enter the funnel.
                    if (sender.isNotBlank() && !MpesaParser.isOfficialSender(sender)) continue
                    found++
                    scannedBodies.add(body)
                    // SIM slot rides the row so each SIM gets its own truth.
                    // sub_id may not exist on all devices — unknown stays -1.
                    val subId = if (subIdIdx >= 0) {
                        try { c.getLong(subIdIdx) } catch (e: Exception) { -1L }
                    } else -1L
                    val beforeSlots = slotKnown
                    val (slot, updatedSlots) = assignSimSlot(beforeSlots, subId)
                    slotKnown = updatedSlots
                    if (updatedSlots !== beforeSlots) slotsDirty = true
                    val smsDate = try { c.getLong(dateIdx) } catch (e: Exception) { 0L }
                    // Sender powers bank-name patterns — dropping it blinds them.
                    // Harvest the wallet balance even from unparseable bodies.
                    if (persistDerivedSignals && newestBalance == null) {
                        parseBalance(body)?.let { newestBalance = it }
                    }
                    // Fee bleed: harvest "Transaction cost, KSh X" tails — this
                    // month's pot only, so a 5-month scan never back-fills it.
                    val monthStart = com.pesaflow.app.data.time.monthRange(System.currentTimeMillis()).startInclusive
                    if (persistDerivedSignals && smsDate >= monthStart) {
                        com.pesaflow.app.data.parsers.parseFee(body)?.let {
                            com.pesaflow.app.data.parsers.saveFee(context, it)
                        }
                    }
                    // The inbox stamp travels as the carrier fallback: text
                    // dates win when sane, otherwise this exact stamp lands —
                    // no more 120-second now-guessing at this layer.
                    val sentAt = if (smsDate > 0) smsDate else System.currentTimeMillis()
                    val p = MpesaParser.parseMessage(body, sender, sentAt)
                    if (p == null) unreadable++ else {
                        val row = p.copy(simSlot = slot)
                        parsed.add(row)
                        pageParsed.add(row)
                    }
                }
            }
            offset += pageSize
            onProgress(found, parsed.size)
            if (pageParsed.isNotEmpty()) {
                onPage?.invoke(pageIndex, applyContactMemory(pageParsed, memories))
            }
            pageIndex++
            if (pageRows < pageSize) break
        }
        // Full last page at the cap means older texts went unscanned.
        capped = offset >= maxRows && pageRows == pageSize
    } catch (e: SecurityException) {
        return SmsScanResult(error = "SMS permission was revoked during the scan.")
    } catch (e: Exception) {
        return SmsScanResult(found = found, error = e.message ?: e.javaClass.simpleName)
    }
    if (slotsDirty) {
        slotPrefs.edit().apply {
            slotKnown.forEach { (id, s) -> putInt("sim_slot_$id", s) }
        }.apply()
    }
    // Identity memory: first-scan namings resolve every later scan — a known
    // "Nancy" arrives as Nancy · Mother with the in-scope category stamped.
    // Totals below run on resolved rows so the override is already reflected.
    // (memories loaded once before the page loop; per-page stamping above is
    // idempotent, so this final pass only catches anything missed.)
    val resolved = applyContactMemory(parsed, memories)
    // Wallet receipts and companion Ziidi/telco notices can describe the same
    // movement with different sender IDs and reference codes. Keep the parsed
    // messages available for confirmation, but count one event in estimates.
    val financialEvents = mutableListOf<PendingTransaction>()
    resolved.sortedBy { it.dateTimestamp }.forEach { row ->
        if (financialEvents.none { row.hasRelatedSmsNotice(it) }) financialEvents.add(row)
    }
    // Wallet display: newest balance tail seen (scan order is newest-first).
    if (persistDerivedSignals) newestBalance?.let { saveMpesaBalance(context, it) }
    var income = 0.0
    var expense = 0.0
    var feeTotal = 0.0
    val cats = mutableMapOf<String, Double>()
    // Fee tails from EVERY body (any month) — the pot stays current-month
    // only, but the scan report totals the whole bleed.
    scannedBodies.forEach { parseFee(it)?.let { fee -> feeTotal += fee } }
    financialEvents.forEach { p ->
        // Only true spending paces budgets — transfers/savings moves are not
        // expenses, and carrier charges bleed through the fee pot instead.
        if (p.isEarnedIncome()) income += p.amount
        else if (p.type == TransactionType.EXPENSE && !p.isFeeRow()) expense += p.amount
        if (p.type == TransactionType.EXPENSE && !p.isFeeRow()) cats[p.category] = (cats[p.category] ?: 0.0) + p.amount
    }
    val monthStart = com.pesaflow.app.data.time.monthRange(System.currentTimeMillis())
    val mtdRows = financialEvents.filter { it.dateTimestamp >= monthStart.startInclusive }
    return SmsScanResult(
        found, resolved, unreadable, income, expense, cats, daysBack, capped,
        byMonth = resolved.groupBy { monthKey(it.dateTimestamp) }.mapValues { it.value.size },
        topSenders = resolved.groupBy { it.displayMerchant.ifBlank { it.merchant.ifBlank { "Unknown" } } }
            .mapValues { it.value.size }.toList().sortedByDescending { it.second }.take(5),
        feeTotal = feeTotal,
        mtdIncome = mtdRows.filter { it.isEarnedIncome() }.sumOf { it.amount },
        mtdExpense = mtdRows.filter { it.type == TransactionType.EXPENSE && !it.isFeeRow() }.sumOf { it.amount },
        mtdDays = ((System.currentTimeMillis() - monthStart.startInclusive) / (24L * 60 * 60 * 1000) + 1).toInt().coerceAtLeast(1)
    )
}
