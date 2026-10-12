package com.pesaflow.app.data.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.Calendar
import java.util.concurrent.TimeUnit

// Generic user reminders ("remind me about rent every 1st"). The shipped
// engine only had fixed workers; this adds user-created ones with
// prefs-backed metadata (allowed: user preferences) so they can be listed
// and cancelled without a new database table. Conditional triggers
// ("when X gets tight") are NOT supported and must be declined honestly.
data class BuddyReminderMeta(
    val key: String,
    val title: String,
    val scheduleLabel: String,
    val createdAt: Long
)

class BuddyReminderWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val title = inputData.getString("title").orEmpty().ifBlank { "PesaBuddy reminder" }
        val body = inputData.getString("body").orEmpty()
        NotificationHelper.show(applicationContext, inputData.getString("key").hashCode(), title, body)
        return Result.success()
    }
}

object BuddyReminderScheduler {
    private const val PREFS = "pesaflow_prefs"
    private const val META_KEY = "buddy_reminders"
    private const val TAG_PREFIX = "buddy_reminder_"

    fun schedule(
        context: Context,
        kind: com.pesaflow.app.ui.buddy.ReminderKind,
        title: String,
        body: String,
        scheduleLabel: String
    ): String {
        val key = TAG_PREFIX + System.currentTimeMillis()
        val data = workDataOf("key" to key, "title" to title, "body" to body)
        val wm = WorkManager.getInstance(context)
        when (kind) {
            is com.pesaflow.app.ui.buddy.ReminderKind.Once -> {
                val req = OneTimeWorkRequestBuilder<BuddyReminderWorker>()
                    .setInputData(data)
                    .setInitialDelay(kind.delayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                    .addTag(key)
                    .build()
                wm.enqueueUniqueWork(key, ExistingWorkPolicy.REPLACE, req)
            }
            is com.pesaflow.app.ui.buddy.ReminderKind.Daily -> {
                val req = PeriodicWorkRequestBuilder<BuddyReminderWorker>(24, TimeUnit.HOURS)
                    .setInputData(data)
                    .setInitialDelay(delayToNext(kind.hour, kind.minute), TimeUnit.MILLISECONDS)
                    .addTag(key)
                    .build()
                wm.enqueueUniquePeriodicWork(key, ExistingPeriodicWorkPolicy.REPLACE, req)
            }
            is com.pesaflow.app.ui.buddy.ReminderKind.Weekly -> {
                val req = PeriodicWorkRequestBuilder<BuddyReminderWorker>(7, TimeUnit.DAYS)
                    .setInputData(data)
                    .setInitialDelay(delayToNextWeekday(kind.weekday, kind.hour, kind.minute), TimeUnit.MILLISECONDS)
                    .addTag(key)
                    .build()
                wm.enqueueUniquePeriodicWork(key, ExistingPeriodicWorkPolicy.REPLACE, req)
            }
            is com.pesaflow.app.ui.buddy.ReminderKind.MonthlyDay -> {
                val req = OneTimeWorkRequestBuilder<BuddyReminderWorker>()
                    .setInputData(data)
                    .setInitialDelay(delayToNextMonthDay(kind.day), TimeUnit.MILLISECONDS)
                    .addTag(key)
                    .build()
                wm.enqueueUniqueWork(key, ExistingWorkPolicy.REPLACE, req)
            }
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cur = prefs.getStringSet(META_KEY, emptySet()).orEmpty().toMutableSet()
        cur.add(listOf(key, title.take(60), scheduleLabel.take(60), System.currentTimeMillis().toString()).joinToString("|"))
        prefs.edit().putStringSet(META_KEY, cur).apply()
        return key
    }

    fun list(context: Context): List<BuddyReminderMeta> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(META_KEY, emptySet()).orEmpty().mapNotNull { line ->
            val p = line.split("|")
            if (p.size < 4) null else BuddyReminderMeta(p[0], p[1], p[2], p[3].toLongOrNull() ?: 0L)
        }.sortedBy { it.createdAt }
    }

    fun cancelAll(context: Context): Int {
        val keys = list(context).map { it.key }
        val wm = WorkManager.getInstance(context)
        keys.forEach { wm.cancelUniqueWork(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(META_KEY).apply()
        return keys.size
    }

    fun notificationsEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        return manager.areNotificationsEnabled()
    }

    private fun delayToNext(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return (next.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
    }

    private fun delayToNextWeekday(weekday: Int, hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            do {
                add(Calendar.DAY_OF_YEAR, 1)
            } while (get(Calendar.DAY_OF_WEEK) != weekday || !after(now))
        }
        return (next.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
    }

    private fun delayToNextMonthDay(day: Int): Long {
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_MONTH, day.coerceIn(1, 28))
            if (!after(now)) add(Calendar.MONTH, 1)
        }
        return (next.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
    }
}
