package com.pesaflow.app.ui.language

import android.content.Context
import com.pesaflow.app.data.models.AppLanguage

// Central trilingual voice: every user-facing string goes through here.
// Tone rules (campus peer, never lecturer):
// - Choma polepole: gentle roast of the SITUATION (HELB delays, cravings),
//   never of the PERSON. Debt is neutral ("deni"), never shame.
// - Money figures always carry the KSh number; words ride along, never replace.
// - Sheng money words count in hundreds: soo moja (100), soo kumi na mbili (1,200).
data class Copy4(val en: String, val sw: String, val sh: String, val mix: String) {
    fun pick(lang: AppLanguage): String = when (lang) {
        AppLanguage.SHENG -> sh
        AppLanguage.KISWAHILI -> sw
        AppLanguage.MIXED -> mix
        else -> en
    }
}

private val swOnes = listOf("", "moja", "mbili", "tatu", "nne", "tano", "sita", "saba", "nane", "tisa")
private val swTens = listOf("", "kumi", "ishirini", "thelathini", "arobaini", "hamsini", "sitini", "sabini", "themanini", "tisini")

// 0..99 in Swahili words, no prefix.
fun swahiliUnder100(n: Int): String {
    if (n <= 0) return "sifuri"
    if (n < 10) return swOnes[n]
    val ten = n / 10
    val one = n % 10
    return if (one == 0) swTens[ten] else "${swTens[ten]} na ${swOnes[one]}"
}

// Whole-KSh amount rendered as words. Sheng counts hundreds ("soo"),
// Swahili uses full form ("elfu moja mia mbili").
fun moneyWords(amount: Double, lang: AppLanguage): String {
    val k = amount.toInt()
    if (k <= 0) return when (lang) {
        AppLanguage.SHENG -> "soo zero"
        AppLanguage.KISWAHILI -> "sifuri"
        else -> "zero"
    }
    if (lang == AppLanguage.SHENG) {
        val hundreds = k / 100
        val rest = k % 100
        if (hundreds == 0) return "soo ${swahiliUnder100(k)}"
        val base = "soo ${swahiliUnder100(hundreds)}"
        return if (rest == 0) base else "$base na ${swahiliUnder100(rest)}"
    }
    // Swahili / Mixed / English-word fallback
    val thousands = k / 1000
    val rem = k % 1000
    val hundreds = rem / 100
    val rest = rem % 100
    val parts = mutableListOf<String>()
    if (thousands > 0) parts.add(if (thousands == 1) "elfu moja" else "elfu ${swahiliUnder100(thousands)}")
    if (hundreds > 0) parts.add(if (hundreds == 1 && thousands == 0 && rest == 0) "mia moja" else "mia ${swOnes[hundreds]}")
    if (rest > 0) parts.add(sweyaRest(rest, hundreds > 0 || thousands > 0))
    if (parts.isEmpty()) return "sifuri"
    return parts.joinToString(" ")
}

private fun sweyaRest(rest: Int, hasBigger: Boolean): String {
    if (!hasBigger) return swahiliUnder100(rest)
    return swahiliUnder100(rest)
}

// "KSh 1,200 (soo kumi na mbili)" in Sheng, "KSh 1,200 (elfu moja mia mbili)" in Swahili.
fun moneySpoken(amount: Double, lang: AppLanguage): String {
    val fig = "KSh ${amount.toInt()}"
    return when (lang) {
        AppLanguage.ENGLISH -> fig
        else -> "$fig (${moneyWords(amount, lang)})"
    }
}

// ---------- Reports ----------

fun reportsTitle(lang: AppLanguage): String = Copy4(
    en = "Reports",
    sw = "Ripoti",
    sh = "Repoti",
    mix = "Repoti"
).pick(lang)

fun periodName(period: String, lang: AppLanguage): String {
    val key = period.lowercase()
    return when (key) {
        "week" -> Copy4("Week", "Wiki", "Wiki", "Week").pick(lang)
        "semester" -> Copy4("Semester", "Muhula", "Sem", "Semester").pick(lang)
        else -> Copy4("Month", "Mwezi", "Mwezi", "Month").pick(lang)
    }
}

fun verdictUnderPace(lang: AppLanguage): Copy4 = Copy4(
    en = "Under pace — solid",
    sw = "Chini ya kasi — safi",
    sh = "Chini ya pace — uko rada",
    mix = "Uko radar — solid"
)

fun verdictOverPace(lang: AppLanguage): Copy4 = Copy4(
    en = "Over pace — ease off",
    sw = "Umevuka kasi — tulia",
    sh = "Umepitisha pace — choma polepole",
    mix = "Over pace — tulia kidogo"
)

fun verdictBodyUnder(spent: Double, expected: Double, lang: AppLanguage): String {
    val s = moneySpoken(spent, lang)
    val e = moneySpoken(expected, lang)
    return when (lang) {
        AppLanguage.SHENG -> "Umeburn $s kwa $e expected — uko rada, keep it ivo. 🟢"
        AppLanguage.KISWAHILI -> "Umetumia $s kati ya $e iliyotarajiwa — safi sana. 🟢"
        AppLanguage.MIXED -> "Ume-burn $s of $e expected — uko radar, keep it ivo. 🟢"
        else -> "Spent $s of $e expected — nicely under. 🟢"
    }
}

fun verdictBodyOver(spent: Double, expected: Double, lang: AppLanguage): String {
    val s = moneySpoken(spent, lang)
    val e = moneySpoken(expected, lang)
    return when (lang) {
        AppLanguage.SHENG -> "Umeburn $s kwa $e expected — HELB inakuja, tulia na cravings. 🙂"
        AppLanguage.KISWAHILI -> "Umetumia $s kati ya $e — polepole na matumizi wiki hii. 🙂"
        AppLanguage.MIXED -> "Ume-burn $s of $e — tulia na hizo cravings, HELB inakuja. 🙂"
        else -> "Spent $s of $e expected — slow down a touch this week. 🙂"
    }
}

fun lensHelbTitle(lang: AppLanguage): String = Copy4(
    en = "HELB runway",
    sw = "HELB iliyobaki",
    sh = "HELB runway",
    mix = "HELB runway"
).pick(lang)

fun lensHustleTitle(lang: AppLanguage): String = Copy4(
    en = "Hustle vs upkeep",
    sw = "Kipato vs matumizi",
    sh = "Hustle vs kuburn",
    mix = "Hustle vs kuburn"
).pick(lang)

fun lensFoodMoveTitle(lang: AppLanguage): String = Copy4(
    en = "Food vs movement",
    sw = "Chakula vs usafiri",
    sh = "Munch vs mathree",
    mix = "Food vs transport"
).pick(lang)

fun lensChamaTitle(lang: AppLanguage): String = Copy4(
    en = "Chama & deni",
    sw = "Chama na deni",
    sh = "Chama na deni",
    mix = "Chama & deni"
).pick(lang)

fun alertsTitle(lang: AppLanguage): String = Copy4(
    en = "Get these as alerts",
    sw = "Pokea kama arifa",
    sh = "Zile alerts bro",
    mix = "Get these as alerts"
).pick(lang)

fun emptyReportsBody(lang: AppLanguage): String = when (lang) {
    AppLanguage.SHENG -> "Hakuna data hii period — log lunch ata soo mbili hii card i-amke. 🍲"
    AppLanguage.KISWAHILI -> "Hakuna data kipindi hiki — weka chakula cha mchana ili ukurasa huu uamke. 🍲"
    AppLanguage.MIXED -> "Hakuna data hii period — log hata lunch ya soo mbili hii card iamke. 🍲"
    else -> "No data this period — log even lunch and this card comes alive. 🍲"
}

// ---------- Savings ----------

fun savingsTitle(lang: AppLanguage): String = Copy4(
    en = "Savings", sw = "Akiba", sh = "Savings", mix = "Savings"
).pick(lang)

fun savingsEmptyTitle(lang: AppLanguage): String = Copy4(
    en = "No goals yet", sw = "Hakuna malengo", sh = "Hakuna goals", mix = "No goals yet"
).pick(lang)

fun savingsEmptyBody(lang: AppLanguage): String = Copy4(
    en = "Name it — laptop, rent buffer, December — and every coin you add lands in your ledger too.",
    sw = "Ipe jina — laptop, akiba ya rent — na kila coin unaweka inaingia pia.",
    sh = "Ipe jina — laptop, buffer — na kila coin unaweka inaingia ledger pia.",
    mix = "Ipe jina — laptop, buffer — kila coin unaweka inaingia ledger pia."
).pick(lang)

fun newGoalBtn(lang: AppLanguage): String = Copy4(
    en = "New goal", sw = "Lengo jipya", sh = "Goal mpya", mix = "New goal"
).pick(lang)

fun addCashBtn(lang: AppLanguage): String = Copy4(
    en = "Add cash", sw = "Weka pesa", sh = "Weka mullah", mix = "Add cash"
).pick(lang)

fun goalPaceLine(daysLeft: Long, perDay: Double, lang: AppLanguage): String {
    val pd = moneySpoken(perDay, lang)
    return when (lang) {
        AppLanguage.SHENG -> "Siku $daysLeft zimebaki — $pd/day inatosha. 💪"
        AppLanguage.KISWAHILI -> "Siku $daysLeft zimebaki — $pd kwa siku inatosha. 💪"
        AppLanguage.MIXED -> "$daysLeft days — $pd/day inatosha. 💪"
        else -> "$daysLeft days left — $pd/day gets you there. 💪"
    }
}

fun goalOverdueLine(days: Long, lang: AppLanguage): String = when (lang) {
    AppLanguage.SHENG -> "Ili-pitwa na siku $days — bado unaweza, weka kidogo leo. 🙂"
    AppLanguage.KISWAHILI -> "Imechelewa siku $days — bado unaweza, weka kidogo leo. 🙂"
    AppLanguage.MIXED -> "Overdue $days days — bado unaweza, weka kidogo leo. 🙂"
    else -> "Overdue by $days days — still possible, add a little today. 🙂"
}

// M-Pesa wallet readout for any screen: null when no SMS has ever carried a
// balance. Display-only everywhere — the ledger stays the math source of truth.
fun walletText(context: Context, hide: Boolean = false): String? {
    val bal = com.pesaflow.app.data.parsers.readMpesaBalance(context) ?: return null
    if (hide) return "M-Pesa wallet: KSh ••••"
    return "M-Pesa wallet: KSh ${bal.first.toInt()} · " +
        com.pesaflow.app.data.parsers.balanceAgeText(bal.second, System.currentTimeMillis())
}

// ---------- Notifications ----------

fun notifTitle(kind: String, lang: AppLanguage): String {
    val k = kind.lowercase()
    return when (k) {
        "night" -> Copy4(
            en = "Night check-in — what did you eat today?",
            sw = "Hali ya usiku — umekula nini leo?",
            sh = "Night check-in — umemunch nini leo?",
            mix = "Night check-in — umekula nini leo?"
        ).pick(lang)
        "sunday" -> Copy4(
            en = "Sunday report",
            sw = "Ripoti ya Jumapili",
            sh = "Repoti ya Sunday",
            mix = "Sunday repoti"
        ).pick(lang)
        else -> Copy4(
            en = "Monthly report",
            sw = "Ripoti ya mwezi",
            sh = "Repoti ya mwezi",
            mix = "Monthly repoti"
        ).pick(lang)
    }
}

fun langOf(prefsLang: String): AppLanguage = try {
    AppLanguage.valueOf(prefsLang)
} catch (e: Exception) {
    AppLanguage.MIXED
}

// ---------- Dashboard (single source; FinanceViewModel delegates here) ----------

fun dashStatus(daily: String, serious: String, balance: Double, lang: AppLanguage): String {
    val nm = daily.ifBlank { null }
    val sn = serious.ifBlank { daily }.ifBlank { null }
    return when (lang) {
        AppLanguage.ENGLISH -> when {
            balance < 0 -> "${sn?.let { "$it, " } ?: ""}you're overdrawn — log income or cut spending."
            balance == 0.0 -> "Fresh start — log income and spending to see advice."
            else -> if (nm != null) "$nm, you are doing great this month!" else "You are doing great this month!"
        }
        AppLanguage.KISWAHILI -> when {
            balance < 0 -> "${sn?.let { "$it, " } ?: ""}umeoverdraw — weka income au punguza matumizi."
            balance == 0.0 -> "Mwanzo mpya — weka income na matumizi uone ushauri."
            else -> "Uko salama mwezi huu!"
        }
        AppLanguage.SHENG -> when {
            balance < 0 -> "${sn?.let { "$it, " } ?: ""}uko kwa red — ingiza pesa ama tulia na spending."
            balance == 0.0 -> "Freshi — weka income na spending uone advice."
            else -> "Uko rada safi mwezi huu!"
        }
        else -> when {
            balance < 0 -> if (sn != null) "Eish $sn, uko kwa red — weka mullah ama cut spending, sai." else "Uko kwa red — weka mullah ama cut spending, sai."
            balance == 0.0 -> "Clean slate 👌 — log income + spending uone advice."
            else -> if (nm != null) "$nm, uko radar 👌. Spending iko stable." else "Uko radar 👌. Spending iko stable."
        }
    }
}

fun dashKey(key: String, arg: String, lang: AppLanguage): String {
    val c = Copy4("", "", "", "")
    return when (key) {
        "spending_today" -> Copy4(
            "Today you spent KSh $arg.", "Leo umetumia KSh $arg.",
            "Leo umeburn KSh $arg.", "Leo ume-burn KSh $arg."
        ).pick(lang)
        "budget_safe" -> Copy4(
            "Your budget is safe so far.", "Bajeti yako iko salama bado.",
            "Budget imetulia mbaya.", "Budget iko safe so far."
        ).pick(lang)
        "balance_label" -> Copy4("AVAILABLE BALANCE", "SALIO", "BALANCE", "BALANCE").pick(lang)
        "income_label" -> Copy4("This Month Income", "Mapato", "Mullah in", "Income").pick(lang)
        "expense_label" -> Copy4("Expenses Logged", "Matumizi", "Spent", "Spent").pick(lang)
        "expense_btn" -> Copy4("− Expense", "− Tumia", "− Spend", "− Spend").pick(lang)
        "income_btn" -> Copy4("+ Income", "+ Weka", "+ Mullah", "+ Mullah").pick(lang)
        else -> c.pick(lang)
    }
}
