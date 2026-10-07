package com.pesaflow.app.data.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.pesaflow.app.data.database.AppDatabase
import com.pesaflow.app.data.models.Debt
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.parsers.MpesaParser
import com.pesaflow.app.data.repositories.FinanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


// Single pipeline every detector (SMS receiver, notification listener, inbox
// scan tester) funnels through: dedupes by M-Pesa code, then either auto-logs
// straight to the ledger or parks in Pending for one-tap approval.
internal suspend fun handleDetectedTransaction(context: Context, raw: PendingTransaction) {
    val database = AppDatabase.getDatabase(context)
    val repo = FinanceRepository(database)
    val prefs = context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE)
    // Face memory: a live SMS from a labelled person arrives pre-named
    // with its usual category (inside its scope).
    val pending = com.pesaflow.app.data.parsers.applyContactMemory(
        listOf(raw),
        mapOf(raw.merchant to com.pesaflow.app.data.parsers.ContactMemory(
            label = prefs.getString(
                com.pesaflow.app.data.parsers.contactLabelKey(raw.merchant), ""
            ).orEmpty(),
            category = prefs.getString(
                com.pesaflow.app.data.parsers.contactCatKey(raw.merchant), ""
            ).orEmpty(),
            scope = prefs.getString(
                com.pesaflow.app.data.parsers.contactScopeKey(raw.merchant), "BOTH"
            ).orEmpty()
        ))
    ).first()
    // Auto-confirm familiar faces: a remembered person whose usual
    // category applies skips Pending straight to the ledger. Strangers,
    // "ask me" people and out-of-scope rows still wait for your tap.
    val faceAuto = prefs.getBoolean("auto_confirm_faces", false) &&
        pending.displayMerchant.isNotBlank() && pending.category.isNotBlank()
    if (prefs.getBoolean("auto_approve_mpesa", false) || faceAuto) {
        val tx = Transaction(
            amount = pending.amount,
            type = pending.type,
            category = pending.category,
            dateTimestamp = pending.dateTimestamp,
            merchant = pending.merchant,
            description = pending.rawText,
            paymentMethod = pending.paymentMethod,
            source = pending.source,
            sourceTransactionId = pending.sourceTransactionId,
            confirmed = true
        )
        val code = tx.sourceTransactionId.orEmpty()
        val dup = code.isNotEmpty() &&
            (database.pendingTransactionDao().findBySourceCode(code) != null ||
                database.transactionDao().findBySourceCode(code) != null)
        if (!dup) {
            repo.insertTransaction(tx)
            NotificationHelper.show(
                context, 15, "Auto-logged ✅",
                "KSh ${tx.amount.toInt()} (${tx.category}) from ${pending.displayMerchant.ifBlank { pending.merchant }} saved."
            )
        }
    } else if (repo.insertPendingTransaction(pending)) {
        showPendingNotification(context, pending)
    }
    // Every funneled SMS may carry a balance tail — harvest it for display.
    com.pesaflow.app.data.parsers.parseBalance(pending.rawText)?.let {
        com.pesaflow.app.data.parsers.saveMpesaBalance(context, it)
    }
    // Transaction-cost tails bleed silently — track the monthly fee pot.
    // Fallback covers odd tails: a KSh 0.01..9.99 amount next to a
    // cost/charge/fee word is the fee, never the movement.
    com.pesaflow.app.data.parsers.parseFeeWithFallback(pending.rawText)?.let {
        com.pesaflow.app.data.parsers.saveFee(context, it)
    }
}


// Confirm-or-ignore straight from the notification shade: no need to open
// the app. Confirm writes the ledger row; Ignore drops it. Either way the
// ping dismisses itself.
private fun showPendingNotification(context: Context, pending: PendingTransaction) {
    NotificationHelper.ensureChannel(context)
    // Relative date so "yesterday's" money never pretends to be today's.
    val dayStart = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val day = 24L * 60 * 60 * 1000
    val whenSent = when {
        pending.dateTimestamp >= dayStart -> "today"
        pending.dateTimestamp >= dayStart - day -> "yesterday"
        else -> java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault())
            .format(java.util.Date(pending.dateTimestamp))
    }
    val base = pending.id.hashCode()
    fun actionIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, PendingApproveReceiver::class.java).apply {
            this.action = action
            putExtra("pid", pending.id)
            putExtra("amount", pending.amount)
            putExtra("type", pending.type.name)
            putExtra("category", pending.category)
            putExtra("merchant", pending.merchant)
            putExtra("dateTimestamp", pending.dateTimestamp)
            putExtra("paymentMethod", pending.paymentMethod.name)
            putExtra("source", pending.source.name)
            putExtra("sourceTransactionId", pending.sourceTransactionId)
            putExtra("rawText", pending.rawText)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
    val note = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Confirm: KSh ${pending.amount.toInt()} to ${pending.merchant.take(24)}?")
        .setContentText("${pending.category} · sent $whenSent · Confirm to save, Ignore to drop.")
        .setStyle(
            NotificationCompat.BigTextStyle().bigText(
                "Did you spend KSh ${pending.amount.toInt()} at ${pending.merchant} (${pending.category})? Sent $whenSent. Confirm saves it to your ledger, Ignore drops it."
            )
        )
        .setAutoCancel(true)
        .addAction(
            android.R.drawable.ic_dialog_info, "Confirm ✅",
            actionIntent(PendingApproveReceiver.ACTION_APPROVE, base)
        )
        .addAction(
            android.R.drawable.ic_dialog_info, "Ignore ❌",
            actionIntent(PendingApproveReceiver.ACTION_REJECT, base + 9973)
        )
        .build()
    try {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(15, note)
    } catch (e: SecurityException) {
        // Permission missing — the row still waits in Home → Pending.
    }
}


class PendingApproveReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_APPROVE = "com.pesaflow.app.APPROVE_PENDING"
        const val ACTION_REJECT = "com.pesaflow.app.REJECT_PENDING"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        scope.launch {
            try {
                val pending = intent.toPending() ?: return@launch
                val database = AppDatabase.getDatabase(context)
                val repo = FinanceRepository(database)
                // Already handled inside the app? Just dismiss, never double-post.
                val code = pending.sourceTransactionId.orEmpty()
                val gone = if (code.isNotEmpty()) {
                    database.pendingTransactionDao().findBySourceCode(code) == null
                } else {
                    // Codeless rows (e.g. bundles) can't be looked up by code —
                    // match the ledger on amount + merchant within a 24h window.
                    val windowStart = pending.dateTimestamp - 24L * 60 * 60 * 1000
                    val windowEnd = pending.dateTimestamp + 24L * 60 * 60 * 1000
                    database.transactionDao().getTransactionsInTimeframe(windowStart, windowEnd).first().any {
                        it.amount == pending.amount && it.merchant == pending.merchant
                    }
                }
                if (!gone) {
                    when (intent.action) {
                        ACTION_APPROVE -> {
                            repo.approvePendingTransaction(pending, pending.category)
                            // Borrowed money is a loan, whoever lent it: confirming opens a
                            // matching deni row (guarded — one loan, one row).
                            val loanLender = when (pending.merchant) {
                                "Fuliza" -> "Fuliza (M-Pesa)"
                                "Tala", "Branch", "Zenka", "Hustler Fund", "M-Shwari Loan", "KCB M-Pesa" -> pending.merchant
                                else -> null
                            }
                            if (pending.type == com.pesaflow.app.data.models.TransactionType.INCOME && loanLender != null) {
                                val nowMs = System.currentTimeMillis()
                                val dup = database.debtDao().getAllDebts().first().any {
                                    it.person == loanLender && it.status != "PAID" && it.amount == pending.amount
                                }
                                if (!dup) {
                                    repo.insertDebt(
                                        Debt(
                                            person = loanLender,
                                            amount = pending.amount,
                                            dateBorrowed = nowMs,
                                            dueDate = nowMs + 30L * 24 * 60 * 60 * 1000,
                                            description = "$loanLender advance from SMS",
                                            status = "OWING",
                                            direction = "I_OWE"
                                        )
                                    )
                                }
                                NotificationHelper.show(
                                    context, 15, "$loanLender logged as deni",
                                    "KSh ${pending.amount.toInt()} in your ledger AND as a loan you owe. Toggle budgeting use under More -> Income."
                                )
                            } else {
                                NotificationHelper.show(
                                    context, 15, "Saved ✅",
                                    "KSh ${pending.amount.toInt()} (${pending.category}) is in your ledger."
                                )
                            }
                            return@launch
                        }
                        ACTION_REJECT -> repo.rejectPendingTransaction(pending.id)
                    }
                }
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(15)
            } catch (e: Exception) {
                // Never crash the receiver; the row still waits in Home → Pending.
            } finally {
                result.finish()
            }
        }
    }

    private fun Intent.toPending(): PendingTransaction? {
        return try {
            PendingTransaction(
                id = getStringExtra("pid") ?: return null,
                amount = getDoubleExtra("amount", Double.NaN).takeIf { !it.isNaN() } ?: return null,
                type = runCatching { TransactionType.valueOf(getStringExtra("type") ?: "") }.getOrNull() ?: return null,
                category = getStringExtra("category") ?: "Other",
                merchant = getStringExtra("merchant") ?: "Unknown",
                dateTimestamp = getLongExtra("dateTimestamp", System.currentTimeMillis()),
                paymentMethod = runCatching { PaymentMethod.valueOf(getStringExtra("paymentMethod") ?: "") }.getOrNull() ?: PaymentMethod.MPESA,
                source = runCatching { TransactionSource.valueOf(getStringExtra("source") ?: "") }.getOrNull() ?: TransactionSource.MPESA_SMS,
                sourceTransactionId = getStringExtra("sourceTransactionId"),
                rawText = getStringExtra("rawText") ?: ""
            )
        } catch (e: Exception) {
            null
        }
    }
}


// Second detection path for phones where SMS access is denied: reads posted
// notifications from messaging/bank apps, parses anything that looks like a
// money move, and funnels it through the same pipeline above.
class MpesaNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        if (pkg == applicationContext.packageName) return
        val extras = sbn.notification.extras
        val body = listOf(
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        ).filterNotNull().joinToString(" ").trim()
        if (body.isEmpty() || !looksFinancial(body)) return
        // Notification post time is the closest event clock available.
        val pending = MpesaParser.parseMessage(body, "", sbn.postTime) ?: return
        scope.launch { handleDetectedTransaction(applicationContext, pending) }
    }

    private fun looksFinancial(body: String): Boolean {
        val hasMoney = body.contains("KSh", ignoreCase = true) ||
            body.contains("KES", ignoreCase = true) ||
            body.contains("M-PESA", ignoreCase = true)
        if (!hasMoney) return false
        val verbs = listOf("confirmed", "sent", "received", "paid", "transfer", "withdraw", "borrow", "deposit")
        return verbs.any { body.contains(it, ignoreCase = true) }
    }
}
