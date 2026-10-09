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

// Swahili words for any non-negative int. Was 0..99 only: callers pass
// thousands/hundreds straight through (moneyWords on KSh 140k+), and
// n >= 100 indexed swTens past its 10 entries — ArrayIndexOutOfBounds on
// the Reports screen for real ledgers. Now total: chunk by thousands and
// recurse (strictly smaller every call), single digits index 0..9 only.
fun swahiliUnder100(n: Int): String {
    if (n <= 0) return "sifuri"
    if (n < 10) return swOnes[n]
    if (n < 100) {
        val ten = n / 10
        val one = n % 10
        return if (one == 0) swTens[ten] else "${swTens[ten]} na ${swOnes[one]}"
    }
    val thousands = n / 1000
    val rem = n % 1000
    val hundreds = rem / 100
    val rest = rem % 100
    val parts = mutableListOf<String>()
    if (thousands > 0) parts.add(if (thousands == 1) "elfu moja" else "elfu ${swahiliUnder100(thousands)}")
    if (hundreds > 0) parts.add(if (hundreds == 1 && thousands == 0 && rest == 0) "mia moja" else "mia ${swOnes[hundreds]}")
    if (rest > 0) parts.add(swahiliUnder100(rest))
    return parts.joinToString(" ")
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
        "daily" -> Copy4(
            en = "Daily summary 💰",
            sw = "Muhtasari wa kila siku 💰",
            sh = "Daily summary 💰",
            mix = "Daily summary 💰"
        ).pick(lang)
        "weekly" -> Copy4(
            en = "Weekly recap 💰",
            sw = "Muhtasari wa wiki 💰",
            sh = "Weekly recap 💰",
            mix = "Weekly recap 💰"
        ).pick(lang)
        "budget-watch" -> Copy4(
            en = "Budget watch ⚠️",
            sw = "Angalia bajeti ⚠️",
            sh = "Watch budget ⚠️",
            mix = "Budget watch ⚠️"
        ).pick(lang)
        "debts" -> Copy4(
            en = "Debts nudging you 🧹",
            sw = "Madeni yanakusukuma 🧹",
            sh = "Madeni yanakuchochea 🧹",
            mix = "Madeni yanakusukuma 🧹"
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

// ---------- Detection-pipeline notifications (context has prefs; titles
// mirror notifTitle kinds, bodies carry preformatted figures) ----------

fun autoLoggedTitle(lang: AppLanguage): String = Copy4(
    en = "Auto-logged ✅",
    sw = "Imerekodiwa yenyewe ✅",
    sh = "Imeautolog ✅",
    mix = "Auto-logged ✅"
).pick(lang)

fun autoLoggedBody(fig: String, cat: String, merchant: String, lang: AppLanguage): String = Copy4(
    en = "KSh $fig ($cat) from $merchant saved.",
    sw = "KSh $fig ($cat) kutoka $merchant imehifadhiwa.",
    sh = "KSh $fig ($cat) kutoka $merchant imesaveiwa.",
    mix = "KSh $fig ($cat) from $merchant saved."
).pick(lang)

fun pendingConfirmTitle(fig: String, merchant: String, lang: AppLanguage): String = Copy4(
    en = "Confirm: KSh $fig to $merchant?",
    sw = "Thibitisha: KSh $fig kwa $merchant?",
    sh = "Confirm: KSh $fig kwa $merchant?",
    mix = "Confirm: KSh $fig to $merchant?"
).pick(lang)

fun pendingWhen(timestamp: Long, dayStart: Long, lang: AppLanguage): String {
    val day = 24L * 60 * 60 * 1000
    return when {
        timestamp >= dayStart -> Copy4(en = "today", sw = "leo", sh = "leo", mix = "today").pick(lang)
        timestamp >= dayStart - day -> Copy4(en = "yesterday", sw = "jana", sh = "jana", mix = "yesterday").pick(lang)
        else -> java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }
}

fun pendingConfirmText(cat: String, whenSent: String, lang: AppLanguage): String = Copy4(
    en = "$cat · sent $whenSent · Confirm to save, Ignore to drop.",
    sw = "$cat · imetumwa $whenSent · Thibitisha kuhifadhi, Puuza kuacha.",
    sh = "$cat · imesent $whenSent · Confirm kusave, Ignore kudrop.",
    mix = "$cat · sent $whenSent · Confirm to save, Ignore to drop."
).pick(lang)

fun pendingConfirmBig(fig: String, merchant: String, cat: String, whenSent: String, lang: AppLanguage): String = Copy4(
    en = "Did you spend KSh $fig at $merchant ($cat)? Sent $whenSent. Confirm saves it to your ledger, Ignore drops it.",
    sw = "Ulitumia KSh $fig kwa $merchant ($cat)? Imetumwa $whenSent. Thibitisha inahifadhiwa kwenye ledger yako, Puuza inatupwa.",
    sh = "Ulispend KSh $fig kwa $merchant ($cat)? Imesent $whenSent. Confirm inasave kwa ledger yako, Ignore inadrop.",
    mix = "Did you spend KSh $fig at $merchant ($cat)? Sent $whenSent. Confirm saves it to your ledger, Ignore drops it."
).pick(lang)

fun actionConfirm(lang: AppLanguage): String = Copy4(
    en = "Confirm ✅", sw = "Thibitisha ✅", sh = "Confirm ✅", mix = "Confirm ✅"
).pick(lang)

fun actionIgnore(lang: AppLanguage): String = Copy4(
    en = "Ignore ❌", sw = "Puuza ❌", sh = "Ignore ❌", mix = "Ignore ❌"
).pick(lang)

fun fulizaTitle(lang: AppLanguage): String = Copy4(
    en = "Fuliza logged as deni ⚠️",
    sw = "Fuliza imerekodiwa kama deni ⚠️",
    sh = "Fuliza imerecordiwa kama deni ⚠️",
    mix = "Fuliza logged as deni ⚠️"
).pick(lang)

fun fulizaBody(fig: String, lang: AppLanguage): String = Copy4(
    en = "KSh $fig in your ledger AND as a loan you owe. Toggle budgeting use under More → Income.",
    sw = "KSh $fig kwenye ledger yako NA kama mkopo unaodaiwa. Badilisha matumizi ya bajeti chini ya More → Income.",
    sh = "KSh $fig kwa ledger yako AND kama loan unadaiwa. Toggle budgeting use chini ya More → Income.",
    mix = "KSh $fig in your ledger AND as a loan you owe. Toggle budgeting use under More → Income."
).pick(lang)

fun savedTitle(lang: AppLanguage): String = Copy4(
    en = "Saved ✅", sw = "Imehifadhiwa ✅", sh = "Imesaveiwa ✅", mix = "Saved ✅"
).pick(lang)

fun kitchenUpdatedTitle(lang: AppLanguage): String = Copy4(
    en = "Kitchen updated 🍳", sw = "Jikoni imesasishwa 🍳", sh = "Kitchen imeupdateiwa 🍳", mix = "Kitchen updated 🍳"
).pick(lang)

fun kitchenUpdatedBody(count: String, lang: AppLanguage): String = Copy4(
    en = "Burned a cooking day off $count item(s) — nice, no typing needed.",
    sw = "Siku moja ya kupika imeungua kwa vitu $count — poa, bila kuandika.",
    sh = "Siku moja ya kupika imeburn kwa items $count — poa, bila kutype.",
    mix = "Burned a cooking day off $count item(s) — nice, no typing needed."
).pick(lang)

fun notedTitle(lang: AppLanguage): String = Copy4(
    en = "Noted 🍲", sw = "Imetambuliwa 🍲", sh = "Noted 🍲", mix = "Noted 🍲"
).pick(lang)

fun notedBody(lang: AppLanguage): String = Copy4(
    en = "Ate out — nothing deducted, report stood down.",
    sw = "Umekula nje — hakuna kilichokatwa, ripoti imesimama.",
    sh = "Umekula out — hakuna kilichokatwa, report imesimama.",
    mix = "Ate out — nothing deducted, report stood down."
).pick(lang)

fun asPlannedTitle(lang: AppLanguage): String = Copy4(
    en = "As planned ✅", sw = "Kama ilivyopangwa ✅", sh = "Kama planned ✅", mix = "As planned ✅"
).pick(lang)

fun asPlannedBody(planned: String, lang: AppLanguage): String {
    val stood = if (planned.isNotBlank()) Copy4(
        en = "$planned stood. ", sw = "$planned imesimama. ",
        sh = "$planned imesimama. ", mix = "$planned stood. "
    ).pick(lang) else ""
    return stood + Copy4(
        en = "Nice — no typing, no guessing.",
        sw = "Poa — bila kuandika, bila kubahatisha.",
        sh = "Poa — bila kutype, bila kuguess.",
        mix = "Nice — no typing, no guessing."
    ).pick(lang)
}

fun restWellTitle(lang: AppLanguage): String = Copy4(
    en = "Rest well 😴", sw = "Pumzika 😴", sh = "Rest well 😴", mix = "Rest well 😴"
).pick(lang)

fun restWellBody(lang: AppLanguage): String = Copy4(
    en = "No more pings tonight — see you tomorrow.",
    sw = "Hakuna arifa zaidi usiku huu — tutaonana kesho.",
    sh = "Hakuna pings zaidi tonight — tutaonana kesho.",
    mix = "No more pings tonight — see you tomorrow."
).pick(lang)

fun snoozedTitle(lang: AppLanguage): String = Copy4(
    en = "Snoozed ⏰", sw = "Imesitishwa ⏰", sh = "Imesnooziwa ⏰", mix = "Snoozed ⏰"
).pick(lang)

fun snoozedBody(count: String, lang: AppLanguage): String = Copy4(
    en = "Back in an hour ($count/2 tonight).",
    sw = "Nitarudi baada ya saa moja ($count/2 usiku huu).",
    sh = "Nitarudi in an hour ($count/2 tonight).",
    mix = "Back in an hour ($count/2 tonight)."
).pick(lang)

fun widgetSafe(lang: AppLanguage): String = Copy4(
    en = "Safe balance", sw = "Salio salama", sh = "Safe balance", mix = "Safe balance"
).pick(lang)

fun widgetOutToday(fig: String, lang: AppLanguage): String = Copy4(
    en = "Out today KSh $fig", sw = "Zimetoka leo KSh $fig", sh = "Out today KSh $fig", mix = "Out today KSh $fig"
).pick(lang)

fun widgetMpesaPocket(fig: String, lang: AppLanguage): String = Copy4(
    en = "M-Pesa pocket KSh $fig", sw = "Mkoba wa M-Pesa KSh $fig", sh = "M-Pesa pocket KSh $fig", mix = "M-Pesa pocket KSh $fig"
).pick(lang)

fun widgetOpen(lang: AppLanguage): String = Copy4(
    en = "Open →", sw = "Fungua →", sh = "Open →", mix = "Open →"
).pick(lang)

fun nightCooked(lang: AppLanguage): String = Copy4(
    en = "Cooked 🍳", sw = "Nimepika 🍳", sh = "Nimecook 🍳", mix = "Cooked 🍳"
).pick(lang)

fun nightAteOut(lang: AppLanguage): String = Copy4(
    en = "Ate out 🍲", sw = "Nimekula nje 🍲", sh = "Nimekula out 🍲", mix = "Ate out 🍲"
).pick(lang)

fun nightLater(lang: AppLanguage): String = Copy4(
    en = "Later ⏰", sw = "Baadaye ⏰", sh = "Later ⏰", mix = "Later ⏰"
).pick(lang)

fun nightPlanned(lang: AppLanguage): String = Copy4(
    en = "Planned ✅", sw = "Iliyopangwa ✅", sh = "Planned ✅", mix = "Planned ✅"
).pick(lang)

fun savedBody(fig: String, cat: String, lang: AppLanguage): String = Copy4(
    en = "KSh $fig ($cat) is in your ledger.",
    sw = "KSh $fig ($cat) iko kwenye ledger yako.",
    sh = "KSh $fig ($cat) iko kwa ledger yako.",
    mix = "KSh $fig ($cat) is in your ledger."
).pick(lang)

// ---------- Daily / weekly worker bodies (figures preformatted by callers) ----------

fun weeklyBody(week: String, balance: String, topTail: String, deltaTail: String, lang: AppLanguage): String = Copy4(
    en = "Spent KSh $week in 7 days · Balance KSh $balance.$topTail$deltaTail",
    sw = "Umetumia KSh $week katika siku 7 · Salio KSh $balance.$topTail$deltaTail",
    sh = "Umespend KSh $week in 7 days · Balance KSh $balance.$topTail$deltaTail",
    mix = "Spent KSh $week in 7 days · Balance KSh $balance.$topTail$deltaTail"
).pick(lang)

fun weeklyTop(key: String, value: String, lang: AppLanguage): String = Copy4(
    en = " Top: $key $value.",
    sw = " Juu: $key $value.",
    sh = " Top: $key $value.",
    mix = " Top: $key $value."
).pick(lang)

fun weeklyDelta(up: Boolean, pct: String, lang: AppLanguage): String = if (up) Copy4(
    en = " Up $pct% vs prior week.",
    sw = " Juu $pct% dhidi ya wiki iliyopita.",
    sh = " Up $pct% vs wiki iliyopita.",
    mix = " Up $pct% vs prior week."
).pick(lang) else Copy4(
    en = " Down $pct% vs prior week. 👌",
    sw = " Chini $pct% dhidi ya wiki iliyopita. 👌",
    sh = " Down $pct% vs wiki iliyopita. 👌",
    mix = " Down $pct% vs prior week. 👌"
).pick(lang)

fun dailyBody(today: String, balance: String, topTail: String, deltaTail: String, lang: AppLanguage): String = Copy4(
    en = "Today KSh $today · Balance KSh $balance.$topTail$deltaTail",
    sw = "Leo KSh $today · Salio KSh $balance.$topTail$deltaTail",
    sh = "Leo KSh $today · Balance KSh $balance.$topTail$deltaTail",
    mix = "Today KSh $today · Balance KSh $balance.$topTail$deltaTail"
).pick(lang)

fun dailyDelta(up: Boolean, pct: String, lang: AppLanguage): String = if (up) Copy4(
    en = " Up $pct% vs yesterday.",
    sw = " Juu $pct% dhidi ya jana.",
    sh = " Up $pct% vs jana.",
    mix = " Up $pct% vs yesterday."
).pick(lang) else Copy4(
    en = " Down $pct% vs yesterday. 👌",
    sw = " Chini $pct% dhidi ya jana. 👌",
    sh = " Down $pct% vs jana. 👌",
    mix = " Down $pct% vs yesterday. 👌"
).pick(lang)

fun budgetWatchBody(who: String, pct: String, limit: String, lang: AppLanguage): String = Copy4(
    en = "${who}you've used $pct% of your KSh $limit monthly budget.",
    sw = "${who}umefikia $pct% ya bajeti yako ya mwezi ya KSh $limit.",
    sh = "${who}umefika $pct% ya monthly budget yako ya KSh $limit.",
    mix = "${who}you've used $pct% of your KSh $limit monthly budget."
).pick(lang)

fun debtsNudgeBody(who: String, count: String, total: String, person: String, lang: AppLanguage): String = Copy4(
    en = "${who}$count overdue totalling KSh $total — clear the smallest first: $person.",
    sw = "${who}$count zimechelewa zenye jumla ya KSh $total — futa ndogo kwanza: $person.",
    sh = "${who}$count overdue totalling KSh $total — clear ndogo kwanza: $person.",
    mix = "${who}$count overdue totalling KSh $total — clear the smallest first: $person."
).pick(lang)

// ---------- Meal, budget-crossing, digest and monthly workers ----------

fun lunchTitle(lang: AppLanguage): String = Copy4(
    en = "Lunch time 🍲", sw = "Chakula cha mchana 🍲", sh = "Lunch time 🍲", mix = "Lunch time 🍲"
).pick(lang)

fun breakfastTitle(lang: AppLanguage): String = Copy4(
    en = "Breakfast time 🍳", sw = "Kifungua kinywa 🍳", sh = "Breakfast time 🍳", mix = "Breakfast time 🍳"
).pick(lang)

fun mealEmptyBody(lang: AppLanguage): String = Copy4(
    en = "What are you eating? Add foods in Meal Planner first.",
    sw = "Unakula nini? Weka vyakula kwenye Meal Planner kwanza.",
    sh = "Unakula nini? Weka foods kwa Meal Planner kwanza.",
    mix = "What are you eating? Add foods in Meal Planner first."
).pick(lang)

fun mealTitleAsk(title: String, lang: AppLanguage): String = Copy4(
    en = "$title — what are you eating?",
    sw = "$title — unakula nini?",
    sh = "$title — unakula nini?",
    mix = "$title — what are you eating?"
).pick(lang)

fun mealTapPlate(lang: AppLanguage): String = Copy4(
    en = "Tap your plate, expense logs itself.",
    sw = "Gusa sahani yako, matumizi yanajirekodi.",
    sh = "Tap plate yako, expense inajilog.",
    mix = "Tap your plate, expense logs itself."
).pick(lang)

fun budgetCrossedTitle(lang: AppLanguage): String = Copy4(
    en = "Budget crossed ⛔", sw = "Bajeti imevukwa ⛔", sh = "Budget imecross ⛔", mix = "Budget crossed ⛔"
).pick(lang)

fun budgetWarningTitle(lang: AppLanguage): String = Copy4(
    en = "Budget warning ⚠️", sw = "Tahadhari ya bajeti ⚠️", sh = "Budget warning ⚠️", mix = "Budget warning ⚠️"
).pick(lang)

fun budgetCrossedBody(who: String, spent: String, limit: String, lang: AppLanguage): String = Copy4(
    en = "${who}you've spent KSh $spent of KSh $limit this month. Essentials only!",
    sw = "${who}umetumia KSh $spent kati ya KSh $limit mwezi huu. Muhimu tu!",
    sh = "${who}umespend KSh $spent out of KSh $limit hii month. Essentials only!",
    mix = "${who}you've spent KSh $spent of KSh $limit this month. Essentials only!"
).pick(lang)

fun budgetWarningBody(who: String, pct: String, limit: String, lang: AppLanguage): String = Copy4(
    en = "${who}you've used $pct% of your KSh $limit monthly budget. Slow down!",
    sw = "${who}umefikia $pct% ya bajeti yako ya mwezi ya KSh $limit. Punguza mwendo!",
    sh = "${who}umefika $pct% ya monthly budget yako ya KSh $limit. Slow down!",
    mix = "${who}you've used $pct% of your KSh $limit monthly budget. Slow down!"
).pick(lang)

fun digestTitle(lang: AppLanguage): String = Copy4(
    en = "Daily Digest ☀️", sw = "Muhtasari wa Kila Siku ☀️", sh = "Daily Digest ☀️", mix = "Daily Digest ☀️"
).pick(lang)

fun digestBase(spent: String, lang: AppLanguage): String = Copy4(
    en = "Yesterday: KSh $spent spent.",
    sw = "Jana: KSh $spent zimetumika.",
    sh = "Jana: KSh $spent zimespendiwa.",
    mix = "Yesterday: KSh $spent spent."
).pick(lang)

fun digestBills(count: String, total: String, lang: AppLanguage): String = Copy4(
    en = " Bills due soon: $count (KSh $total).",
    sw = " Bili zinazodaiwa hivi karibuni: $count (KSh $total).",
    sh = " Bills due soon: $count (KSh $total).",
    mix = " Bills due soon: $count (KSh $total)."
).pick(lang)

fun digestDebts(count: String, total: String, lang: AppLanguage): String = Copy4(
    en = " Debts due soon: $count (KSh $total).",
    sw = " Madeni yanayodaiwa hivi karibuni: $count (KSh $total).",
    sh = " Debts due soon: $count (KSh $total).",
    mix = " Debts due soon: $count (KSh $total)."
).pick(lang)

fun digestOverdue(count: String, lang: AppLanguage): String = Copy4(
    en = " Overdue: $count 🧹.",
    sw = " Zimechelewa: $count 🧹.",
    sh = " Overdue: $count 🧹.",
    mix = " Overdue: $count 🧹."
).pick(lang)

fun digestClean(lang: AppLanguage): String = Copy4(
    en = " Nothing due — clean slate. 🎉",
    sw = " Hakuna kinachodaiwa — ukurasa safi. 🎉",
    sh = " Hakuna inadaiwa — clean slate. 🎉",
    mix = " Nothing due — clean slate. 🎉"
).pick(lang)

fun monthlySpent(spent: String, income: String, lang: AppLanguage): String = Copy4(
    en = "Spent KSh $spent, in KSh $income.",
    sw = "Umetumia KSh $spent, ndani KSh $income.",
    sh = "Umespend KSh $spent, in KSh $income.",
    mix = "Spent KSh $spent, in KSh $income."
).pick(lang)

fun monthlyTop(list: String, lang: AppLanguage): String = Copy4(
    en = " Top: $list.",
    sw = " Juu: $list.",
    sh = " Top: $list.",
    mix = " Top: $list."
).pick(lang)

fun monthlyNone(lang: AppLanguage): String = Copy4(
    en = " No spending logged.",
    sw = " Hakuna matumizi yaliyorekodiwa.",
    sh = " Hakuna spending imelog.",
    mix = " No spending logged."
).pick(lang)

fun monthlyKept(pct: String, lang: AppLanguage): String = Copy4(
    en = " Kept $pct%.",
    sw = " Umebakiza $pct%.",
    sh = " Umekeep $pct%.",
    mix = " Kept $pct%."
).pick(lang)

fun monthlyHead(month: String, spent: String, income: String, lang: AppLanguage): String = Copy4(
    en = "$month: spent KSh $spent, in KSh $income.",
    sw = "$month: umetumia KSh $spent, ndani KSh $income.",
    sh = "$month: umespend KSh $spent, in KSh $income.",
    mix = "$month: spent KSh $spent, in KSh $income."
).pick(lang)

fun monthlySplit(mpesa: String, manual: String, lang: AppLanguage): String = Copy4(
    en = " M-Pesa KSh $mpesa, manual KSh $manual.",
    sw = " M-Pesa KSh $mpesa, mkononi KSh $manual.",
    sh = " M-Pesa KSh $mpesa, manual KSh $manual.",
    mix = " M-Pesa KSh $mpesa, manual KSh $manual."
).pick(lang)

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
