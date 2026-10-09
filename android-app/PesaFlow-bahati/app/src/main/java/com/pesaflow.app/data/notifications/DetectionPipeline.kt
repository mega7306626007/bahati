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
internal suspend fun handleDetectedTransaction(context: Context, pending: PendingTransaction) {
    val database = AppDatabase.getDatabase(context)
    val repo = FinanceRepository(database)
    val prefs = context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE)
    // Identity memory: named senders arrive resolved — only strangers ping raw.
    val mems = com.pesaflow.app.data.ledger.readContactMemories(
        prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
    )
    val known = com.pesaflow.app.data.ledger.applyContactMemory(listOf(pending), mems).first()
    // Auto-confirm familiar faces: a remembered person whose usual category
    // applies skips Pending straight to the ledger. Strangers, "ask me"
    // people and out-of-scope rows still wait for your tap.
    val faceAuto = prefs.getBoolean("auto_confirm_faces", false) &&
        known.displayMerchant.isNotBlank() && known.category.isNotBlank()
    if (prefs.getBoolean("auto_approve_mpesa", false) || faceAuto) {
        val tx = Transaction(
            amount = known.amount,
            type = known.type,
            category = known.category,
            subcategory = known.subcategory,
            dateTimestamp = known.dateTimestamp,
            merchant = known.displayMerchant.ifBlank { known.merchant },
            description = known.rawText,
            paymentMethod = known.paymentMethod,
            source = known.source,
            sourceTransactionId = known.sourceTransactionId,
            simSlot = known.simSlot,
            confirmed = true
        )
        val code = tx.sourceTransactionId.orEmpty()
        val dup = code.isNotEmpty() &&
            (database.pendingTransactionDao().findBySourceCodeAmountType(code, tx.amount, tx.type) != null ||
                database.transactionDao().findBySourceCodeAmountType(code, tx.amount, tx.type) != null)
        if (!dup) {
            repo.insertTransaction(tx)
            // Auto-approvals teach too — the user trusts this stream.
            com.pesaflow.app.data.ledger.CategoryMemory.learn(prefs, tx.merchant, tx.category)
            com.pesaflow.app.data.ledger.ConfidenceMemory.record(prefs, tx.merchant, true)
            val lang = com.pesaflow.app.ui.language.langOf(prefs.getString("app_language", "MIXED") ?: "MIXED")
            NotificationHelper.show(
                context, 15, com.pesaflow.app.ui.language.autoLoggedTitle(lang),
                com.pesaflow.app.ui.language.autoLoggedBody(tx.amount.toInt().toString(), tx.category, known.displayMerchant.ifBlank { known.merchant }, lang)
            )
        }
    } else if (repo.insertPendingTransaction(known)) {
        showPendingNotification(context, known)
    }
    // Every funneled SMS may carry a balance tail — harvest it for display.
    // Scans replay newest-first, so only a same-or-newer tail may overwrite.
    com.pesaflow.app.data.parsers.parseBalance(known.rawText)?.let {
        val storedAt = com.pesaflow.app.data.parsers.readMpesaBalance(context)?.second ?: 0L
        if (known.dateTimestamp >= storedAt) {
            com.pesaflow.app.data.parsers.saveMpesaBalance(context, it)
        }
    }
    // Transaction-cost tails bleed silently — track the monthly fee pot.
    com.pesaflow.app.data.parsers.parseFee(known.rawText)?.let {
        com.pesaflow.app.data.parsers.saveFee(context, it)
    }
}


// Confirm-or-ignore straight from the notification shade: no need to open
// the app. Confirm writes the ledger row; Ignore drops it. Either way the
// ping dismisses itself.
private fun showPendingNotification(context: Context, pending: PendingTransaction) {
    NotificationHelper.ensureChannel(context)
    val prefs = context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE)
    val lang = com.pesaflow.app.ui.language.langOf(prefs.getString("app_language", "MIXED") ?: "MIXED")
    // Relative date so "yesterday's" money never pretends to be today's.
    val dayStart = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val whenSent = com.pesaflow.app.ui.language.pendingWhen(pending.dateTimestamp, dayStart, lang)
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
            putExtra("simSlot", pending.simSlot)
            putExtra("rawText", pending.rawText)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
    val note = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(com.pesaflow.app.ui.language.pendingConfirmTitle(pending.amount.toInt().toString(), pending.merchant.take(24), lang))
        .setContentText(com.pesaflow.app.ui.language.pendingConfirmText(pending.category, whenSent, lang))
        .setStyle(
            NotificationCompat.BigTextStyle().bigText(
                com.pesaflow.app.ui.language.pendingConfirmBig(pending.amount.toInt().toString(), pending.merchant, pending.category, whenSent, lang)
            )
        )
        .setAutoCancel(true)
        .addAction(
            android.R.drawable.ic_dialog_info, com.pesaflow.app.ui.language.actionConfirm(lang),
            actionIntent(PendingApproveReceiver.ACTION_APPROVE, base)
        )
        .addAction(
            android.R.drawable.ic_dialog_info, com.pesaflow.app.ui.language.actionIgnore(lang),
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
                    database.pendingTransactionDao().findBySourceCodeAmountType(code, pending.amount, pending.type) == null
                } else {
                    // Codeless rows (e.g. bundles) can't be looked up by code —
                    // match the ledger on amount + merchant + day instead.
                    database.transactionDao().getAllTransactions().first().any {
                        it.amount == pending.amount && it.merchant == pending.merchant &&
                            kotlin.math.abs(it.dateTimestamp - pending.dateTimestamp) < 24L * 60 * 60 * 1000
                    }
                }
                if (!gone) {
                    when (intent.action) {
                        ACTION_APPROVE -> {
                            repo.approvePendingTransaction(pending, pending.category)
                            // Fuliza is borrowed, not earned: confirming it also opens a
                            // matching deni row (guarded — one loan, one row).
                            if (pending.merchant == "Fuliza" && pending.type == com.pesaflow.app.data.models.TransactionType.INCOME) {
                                val nowMs = System.currentTimeMillis()
                                val dup = database.debtDao().getAllDebts().first().any {
                                    it.person == "Fuliza (M-Pesa)" && it.status != "PAID" && it.amount == pending.amount
                                }
                                if (!dup) {
                                    repo.insertDebt(
                                        Debt(
                                            person = "Fuliza (M-Pesa)",
                                            amount = pending.amount,
                                            dateBorrowed = nowMs,
                                            dueDate = nowMs + 30L * 24 * 60 * 60 * 1000,
                                            description = "Fuliza advance from SMS",
                                            status = "OWING",
                                            direction = "I_OWE"
                                        )
                                    )
                                }
                                val lang = com.pesaflow.app.ui.language.langOf(context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE).getString("app_language", "MIXED") ?: "MIXED")
                                NotificationHelper.show(
                                    context, 15, com.pesaflow.app.ui.language.fulizaTitle(lang),
                                    com.pesaflow.app.ui.language.fulizaBody(pending.amount.toInt().toString(), lang)
                                )
                            } else {
                                val lang = com.pesaflow.app.ui.language.langOf(context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE).getString("app_language", "MIXED") ?: "MIXED")
                                NotificationHelper.show(
                                    context, 15, com.pesaflow.app.ui.language.savedTitle(lang),
                                    com.pesaflow.app.ui.language.savedBody(pending.amount.toInt().toString(), pending.category, lang)
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
                simSlot = getIntExtra("simSlot", -1),
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
        val pending = MpesaParser.parseMessage(body) ?: return
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
