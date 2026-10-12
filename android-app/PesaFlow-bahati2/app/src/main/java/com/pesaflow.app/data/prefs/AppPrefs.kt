package com.pesaflow.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map


private val Context.prefsStore: DataStore<Preferences> by preferencesDataStore("pesaflow_prefs")


/** Typed replacement for the stringly SharedPreferences soup (coach flags,
 *  sticky method, approval prefs). Migrates lazily: readers check DataStore
 *  first, fall back to the legacy XML once, then persist forward. */
object AppPrefs {

    private val COACH_DONE = booleanPreferencesKey("coach_done")
    private val LAST_METHOD = stringPreferencesKey("last_method")
    private val AUTO_APPROVE = booleanPreferencesKey("auto_approve_mpesa")

    private const val LEGACY = "pesaflow_prefs"


    fun coachDone(context: Context): Flow<Boolean> =
        context.prefsStore.data.map { it[COACH_DONE] ?: legacy(context).getBoolean("coach_done", false) }


    suspend fun setCoachDone(context: Context) {
        context.prefsStore.edit { it[COACH_DONE] = true }
    }


    fun lastMethod(context: Context): Flow<String> =
        context.prefsStore.data.map {
            it[LAST_METHOD] ?: legacy(context).getString("last_method", "MPESA") ?: "MPESA"
        }


    suspend fun setLastMethod(context: Context, method: String) {
        context.prefsStore.edit { it[LAST_METHOD] = method }
    }


    fun autoApprove(context: Context): Flow<Boolean> =
        context.prefsStore.data.map { it[AUTO_APPROVE] ?: legacy(context).getBoolean("auto_approve_mpesa", false) }


    suspend fun setAutoApprove(context: Context, value: Boolean) {
        context.prefsStore.edit { it[AUTO_APPROVE] = value }
    }


    /** One-shot read for non-composable call sites (validators, workers). */
    suspend fun coachDoneOnce(context: Context): Boolean = coachDone(context).first()


    private fun legacy(context: Context) =
        context.getSharedPreferences(LEGACY, Context.MODE_PRIVATE)
}
