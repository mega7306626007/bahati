package com.pesaflow.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.pesaflow.app.MainActivity
import com.pesaflow.app.R
import com.pesaflow.app.data.database.AppDatabase
import com.pesaflow.app.data.money.ledgerBalance
import com.pesaflow.app.data.money.monthScopedTotal
import com.pesaflow.app.data.models.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Home-screen widget: the two numbers a student checks most — balance and
// safe-to-spend — without opening the app. Reads the DB directly (Room is
// already initialized by the app process) and renders via RemoteViews.
class PesaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> updateWidget(context, manager, id) }
    }

    override fun onEnabled(context: Context) {
        // First widget placed — the periodic updatePeriodMillis handles refresh.
    }

    companion object {
        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_pesa)
            val appContext = context.applicationContext

            // Tap opens the app.
            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_balance, pending)
            views.setOnClickPendingIntent(R.id.widget_safe, pending)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(appContext)
                    val txs = db.transactionDao().getAllTransactions()
                    // Room Flow — take the first emission.
                    val list = txs.first()
                    val balance = ledgerBalance(list)
                    val now = System.currentTimeMillis()
                    val safe = monthScopedTotal(list, TransactionType.EXPENSE, now)
                    val income = monthScopedTotal(list, TransactionType.INCOME, now)

                    views.setTextViewText(R.id.widget_balance, "KSh ${balance.toInt()}")
                    views.setTextViewText(
                        R.id.widget_safe,
                        "Safe: KSh ${(income - safe).coerceAtLeast(0.0).toInt()}"
                    )
                    views.setTextViewText(
                        R.id.widget_updated,
                        SimpleDateFormat("d MMM h:mm", Locale.US).format(Date(now))
                    )
                } catch (e: Exception) {
                    views.setTextViewText(R.id.widget_balance, "KSh —")
                    views.setTextViewText(R.id.widget_safe, "Safe: KSh —")
                }
                manager.updateAppWidget(widgetId, views)
            }
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, PesaWidgetProvider::class.java)
            )
            ids.forEach { updateWidget(context, manager, it) }
        }
    }
}
