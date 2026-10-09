package com.pesaflow.app.ui.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pesaflow.app.MainActivity
import com.pesaflow.app.R
import com.pesaflow.app.data.database.AppDatabase
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.TransactionType
import java.util.Calendar
import kotlinx.coroutines.flow.first


/** Home-screen widget: safe-to-spend + today's out-go, tap to open.
 *  Reads the ledger directly (same process, Room suspend funs are
 *  main-safe); refreshes on the 30-min schedule plus the ↻ button. */
class PesaWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val txs = try {
            AppDatabase.getDatabase(context).transactionDao().getAllTransactions().first()
        } catch (e: Exception) {
            emptyList()
        }
        val real = txs.filter { !it.isSample }
        val dayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todayOut = real
            .filter { it.type == TransactionType.EXPENSE && it.dateTimestamp >= dayStart }
            .sumOf { it.amount }
        // M-Pesa pocket: same sign rules as the ledger balance.
        val mpesaPocket = real.filter { it.paymentMethod == PaymentMethod.MPESA }.sumOf {
            when (it.type) {
                TransactionType.INCOME -> it.amount
                TransactionType.EXPENSE -> -it.amount
                TransactionType.SAVING -> -it.amount
                TransactionType.INVESTMENT -> -it.amount
                TransactionType.TRANSFER -> 0.0
                else -> 0.0
            }
        }
        val balance = real.sumOf {
            when (it.type) {
                TransactionType.INCOME -> it.amount
                TransactionType.EXPENSE -> -it.amount
                TransactionType.SAVING -> -it.amount
                TransactionType.INVESTMENT -> -it.amount
                TransactionType.TRANSFER -> 0.0
                else -> 0.0
            }
        }
        val lang = try {
            com.pesaflow.app.ui.language.langOf(
                context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE)
                    .getString("app_language", "MIXED") ?: "MIXED"
            )
        } catch (e: Exception) {
            com.pesaflow.app.data.models.AppLanguage.MIXED
        }
        provideContent {
            Column(
                modifier = GlanceModifier.fillMaxSize()
                    .background(ColorProvider(R.color.pesaplanner_icon_background))
                    .padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = com.pesaflow.app.ui.language.widgetSafe(lang),
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = ColorProvider(R.color.widget_gold)
                    )
                )
                Text(
                    text = "KSh ${balance.toInt()}",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = ColorProvider(R.color.widget_white)
                    )
                )
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = com.pesaflow.app.ui.language.widgetOutToday(todayOut.toInt().toString(), lang),
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = ColorProvider(R.color.widget_mist)
                    )
                )
                Text(
                    text = com.pesaflow.app.ui.language.widgetMpesaPocket(mpesaPocket.toInt().toString(), lang),
                    style = TextStyle(
                        fontSize = 13.sp,
                        color = ColorProvider(R.color.widget_gold)
                    )
                )
                Spacer(GlanceModifier.height(12.dp))
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "↻",
                        style = TextStyle(fontSize = 16.sp, color = ColorProvider(R.color.widget_white)),
                        modifier = GlanceModifier.padding(8.dp).clickable(actionRunCallback<RefreshCallback>())
                    )
                    Text(
                        text = com.pesaflow.app.ui.language.widgetOpen(lang),
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ColorProvider(R.color.widget_gold)),
                        modifier = GlanceModifier.padding(8.dp).clickable(actionStartActivity<MainActivity>())
                    )
                }
            }
        }
    }
}


class RefreshCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        PesaWidget().update(context, glanceId)
    }
}


class PesaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PesaWidget()
}
