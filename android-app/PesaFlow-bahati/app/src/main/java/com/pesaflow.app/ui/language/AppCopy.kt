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

// ---------- Dashboard dictionary (part 1: topbar → pending rows) ----------
// Single-arg (a) + second-arg (b) interpolation; brand names stay invariant.

fun dashT(key: String, lang: AppLanguage, a: String = "", b: String = "", c: String = "", d: String = "", e: String = ""): String = when (key) {
    "hub_title" -> "PesaPlanner Hub ⚡"
    "tune" -> Copy4("Tune", "Rekebisha", "Tune", "Tune").pick(lang)
    "show" -> Copy4("Show", "Onyesha", "Show", "Show").pick(lang)
    "hide" -> Copy4("Hide", "Ficha", "Hide", "Hide").pick(lang)
    "search_cd" -> Copy4("Search transactions", "Tafuta miamala", "Search transactions", "Search").pick(lang)
    "no_pressure" -> Copy4("No upcoming pressure.", "Hakuna shinikizo linalokuja.", "Hakuna pressure inakuja.", "All clear.").pick(lang)
    "overdue" -> Copy4("overdue", "imechelewa", "imepitwa", "overdue").pick(lang)
    "due_today" -> Copy4("due today", "inadaiwa leo", "inadaiwa leo", "due leo").pick(lang)
    "due_tomorrow" -> Copy4("due tomorrow", "inadaiwa kesho", "inadaiwa kesho", "due kesho").pick(lang)
    "due_in_days" -> Copy4("due in $a days", "inadaiwa baada ya siku $a", "inadaiwa after siku $a", "due in $a days").pick(lang)
    "more_obligations" -> Copy4("+ $a more obligation${if (a == "1") "" else "s"}", "+ majukumu $a zaidi", "+ obligations $a more", "+ $a more").pick(lang)
    "sem_fees_out" -> Copy4("Semester fees outstanding: $a (User entered)", "Karo inayodaiwa: $a (Umeingiza)", "Fees zinadaiwa: $a (Umeingiza)", "Fees due: $a").pick(lang)
    "next_title" -> Copy4("Next", "Inayofuata", "Next", "Next").pick(lang)
    "next_reconcile" -> Copy4("Reconcile your current balance.", "Linganisha salio lako la sasa.", "Reconcile balance yako.", "Reconcile balance.").pick(lang)
    "next_review_pending" -> Copy4("Review $a confirming transaction${if (a == "1") "" else "s"} below.", "Pitia miamala $a inayothibitishwa hapa chini.", "Review transactions $a zinaconfirm hapa chini.", "Review $a below.").pick(lang)
    "next_budget" -> Copy4("Create your first budget.", "Tengeneza bajeti yako ya kwanza.", "Tengeneza budget yako ya kwanza.", "First budget.").pick(lang)
    "next_fees" -> Copy4("Add your semester fee.", "Weka karo yako ya muhula.", "Weka fee yako ya sem.", "Add fee.").pick(lang)
    "next_calm" -> Copy4("Nothing urgent — you're up to date.", "Hakuna cha haraka — uko sawa.", "Hakuna urgent — uko up to date.", "All good.").pick(lang)
    "review_ledger_btn" -> Copy4("Review ledger →", "Pitia ledger →", "Review ledger →", "Ledger →").pick(lang)
    "set_budget_btn" -> Copy4("Set a budget →", "Weka bajeti →", "Weka budget →", "Budget →").pick(lang)
    "university_btn" -> Copy4("University →", "Chuo →", "Chuo →", "Chuo →").pick(lang)
    "f_all" -> Copy4("All", "Zote", "Zote", "All").pick(lang)
    "f_income" -> Copy4("Income", "Mapato", "Income", "Income").pick(lang)
    "f_expenses" -> Copy4("Expenses", "Matumizi", "Expenses", "Expenses").pick(lang)
    "f_saving" -> Copy4("Savings", "Akiba", "Savings", "Savings").pick(lang)
    "f_investment" -> Copy4("Investments", "Uwekezaji", "Investments", "Investments").pick(lang)
    "scan_today" -> Copy4("Scan today", "Scan leo", "Scan leo", "Scan").pick(lang)
    "scanning" -> Copy4("Scanning…", "Inascan…", "Inascan…", "Scanning…").pick(lang)
    "pending_btn" -> Copy4(if (a.isBlank()) "Pending" else "Pending ($a)", if (a.isBlank()) "Zinasubiri" else "Zinasubiri ($a)", if (a.isBlank()) "Pending" else "Pending ($a)", if (a.isBlank()) "Pending" else "Pending ($a)").pick(lang)
    "scan_fail" -> Copy4("Scan failed: $a", "Scan imeshindwa: $a", "Scan imefail: $a", "Scan failed.").pick(lang)
    "scanned" -> Copy4("Scanned $a texts: $b new for review.", "Imescan SMS $a: $b mpya za kupitia.", "Imescan texts $a: $b mpya za review.", "Scanned $a: $b new.").pick(lang)
    "scan_fees" -> Copy4(" KSh $a fees.", " Ada KSh $a.", " Fees KSh $a.", " Fees $a.").pick(lang)
    "nothing_waiting" -> Copy4("Nothing waiting — scan to fill the queue.", "Hakuna kinachosubiri — scan ujaze foleni.", "Hakuna inangoja — scan ujaze queue.", "Queue empty — scan.").pick(lang)
    "confirm_each" -> Copy4("Confirm each row below to log it. 👇", "Thibitisha kila safu hapa chini.", "Confirm kila row hapa chini. 👇", "Confirm below. 👇").pick(lang)
    "waiting_in_pending" -> Copy4("$a waiting in Pending", "$a zinasubiri kwenye Pending", "$a zinangoja kwa Pending", "$a in Pending").pick(lang)
    "scan_found" -> Copy4("Your scan found them — confirm and they land here as transactions.", "Scan yako imezipata — zithibitishe ziingie hapa kama miamala.", "Scan yako imezipata — ziconfirm ziingie hapa kama transactions.", "Confirm them in.").pick(lang)
    "confirm_sure_btn" -> Copy4("Confirm $a sure", "Thibitisha $a za uhakika", "Confirm $a sure", "Confirm $a.").pick(lang)
    "review_pending_btn" -> Copy4("Review pending", "Pitia zinazosubiri", "Review pending", "Review.").pick(lang)
    "scroll_pending" -> Copy4("Scroll to Pending 🔔 below and confirm each row.", "Sogeza chini kwa Pending 🔔 uthibitishe kila safu.", "Scroll kwa Pending 🔔 chini uconfirm kila row.", "Pending 🔔 below.").pick(lang)
    "money_positions" -> Copy4("Money positions", "Nafasi za pesa", "Money positions", "Positions").pick(lang)
    "ledger" -> Copy4("Ledger", "Leja", "Ledger", "Ledger").pick(lang)
    "mpesa_wallet" -> Copy4("M-Pesa wallet", "Mkoba wa M-Pesa", "Wallet ya M-Pesa", "M-Pesa").pick(lang)
    "wallet_read" -> Copy4("Wallet read $a · wallet is M-Pesa only, ledger covers everything.", "Mkoba umesomwa $a · mkoba ni M-Pesa tu, leja inashughulikia vyote.", "Wallet imesomwa $a · wallet ni M-Pesa tu, ledger hushughulikia vyote.", "Wallet $a · ledger covers all.").pick(lang)
    "wallet_none" -> Copy4("No wallet reading yet — first M-Pesa text sets it.", "Hakuna usomaji wa mkoba bado — SMS ya kwanza ya M-Pesa ndiyo huiweka.", "Hakuna wallet reading bado — first M-Pesa text ndiyo huiseti.", "First M-Pesa text sets it.").pick(lang)
    "mpesa" -> Copy4("M-Pesa", "M-Pesa", "M-Pesa", "M-Pesa").pick(lang)
    "cash" -> Copy4("Cash", "Taslimu", "Cash", "Cash").pick(lang)
    "bank" -> Copy4("Bank", "Benki", "Bank", "Bank").pick(lang)
    "spendable" -> Copy4("Spendable now: KSh $a (ledger ± $b confirming)", "Inayoweza kutumika sasa: KSh $a (leja ± $b zinathibitisha)", "Spendable sai: KSh $a (ledger ± $b zinaconfirm)", "Spendable: $a (±$b).").pick(lang)
    "why_negative" -> Copy4("Why is this negative?", "Kwa nini hii ni hasi?", "Mbona hii ni negative?", "Why negative?").pick(lang)
    "why_negative_body" -> Copy4("Your recorded transactions show more money leaving than entering. This can happen if your opening balance was not recorded, income is missing, a transaction was duplicated, or a transfer was recorded incorrectly. PesaFlow will never change your ledger by itself — review it and reconcile only what you confirm.", "Miamala yako iliyorekodiwa inaonyesha pesa zaidi zinazotoka kuliko zinazoingia. Hii inaweza kutokea ikiwa salio lako la mwanzo halikurekodiwa, kipato kimekosekana, muamala umerudiwa, au uhamisho umerekodiwa vibaya. PesaFlow haitabadilisha leja yako yenyewe — ipitie na ulinganishe unachothibitisha tu.", "Recorded transactions zako zinaonyesha doh zaidi zinatoka kuliko zinaingia. Inaweza kuwa opening balance haikurecordiwa, income inamiss, transaction imeduplicatiwa, ama transfer imerecordiwa vibaya. PesaFlow haitabadilisha ledger yako yenyewe — ireview na ureconcile unachoconfirm tu.", "More out than in — check opening, income, dupes, transfers.").pick(lang)
    "pending_title" -> Copy4("Pending ($a) 🔔", "Zinasubiri ($a) 🔔", "Pending ($a) 🔔", "Pending ($a) 🔔").pick(lang)
    "remove_dupes" -> Copy4("Remove duplicates", "Ondoa zinazojirudia", "Ondoa duplicates", "Dedup.").pick(lang)
    "no_dupes" -> Copy4("No duplicates — queue is clean.", "Hakuna zinazojirudia — foleni iko safi.", "Hakuna duplicates — queue iko clean.", "Queue clean.").pick(lang)
    "dupes_removed" -> Copy4("$a duplicate${if (a == "1") "" else "s"} removed.", "Zinazojirudia $a zimeondolewa.", "Duplicates $a zimeondolewa.", "$a removed.").pick(lang)
    "confirm_all_sure" -> Copy4("Confirm all sure ($a)", "Thibitisha zote za uhakika ($a)", "Confirm zote sure ($a)", "Sure ($a).").pick(lang)
    "sure_confirmed" -> Copy4("$a confirmed — history vouched.", "$a zimethibitishwa — historia imethibitisha.", "$a zimeconfirmiwa — history imevouch.", "$a done.").pick(lang)
    "confirm_categorized" -> Copy4("Confirm categorized ($a)", "Thibitisha zilizopangwa ($a)", "Confirm zimefile ($a)", "Filed ($a).").pick(lang)
    "categorized_left" -> Copy4("$a confirmed — only uncategorized left.", "$a zimethibitishwa — zilizobaki ni zisizopangwa.", "$a zimeconfirmiwa — zimebaki ni uncategorized.", "$a done.").pick(lang)
    "file_transport" -> Copy4("File transport ($a) 🚌", "Filisha usafiri ($a) 🚌", "File transport ($a) 🚌", "Transport ($a).").pick(lang)
    "transport_filed" -> Copy4("$a rides filed as Transport", "Safari $a zimefilishwa kama Usafiri", "Rides $a zimefile kama Transport", "$a filed.").pick(lang)
    "transport_names" -> Copy4(" · $a name${if (a == "1") "" else "s"} saved to the Transport card 🚌", " · majina $a yamehifadhiwa kwenye kadi ya Usafiri 🚌", " · majina $a yamesaviwa kwa Transport card 🚌", " · $a saved 🚌").pick(lang)
    "transport_known" -> Copy4(" · names already on the Transport card 🚌", " · majina tayari yapo kwenye kadi ya Usafiri 🚌", " · majina zishako kwa Transport card 🚌", " · known 🚌").pick(lang)
    "view_all" -> Copy4("View all", "Ona zote", "View zote", "All").pick(lang)
    "less" -> Copy4("Less", "Kidogo", "Less", "Less").pick(lang)
    "new_sms" -> Copy4("New SMS Detected", "SMS Mpya Imegundulika", "SMS Mpya Imedetectiwa", "New SMS").pick(lang)
    "merchant" -> Copy4("Merchant: $a", "Muuzaji: $a", "Merchant: $a", "Merchant: $a").pick(lang)
    "suggested_cat" -> Copy4("Suggested Category: $a", "Kundi Lililopendekezwa: $a", "Suggested Category: $a", "Category: $a").pick(lang)
    "sure_badge" -> Copy4("✓ Sure — matches your history ($a%).", "✓ Hakika — inalingana na historia yako ($a%).", "✓ Sure — inamatch history yako ($a%).", "✓ Sure ($a%).").pick(lang)
    "unsure_badge" -> Copy4("⚠️ $a% sure — check category and type before confirming.", "⚠️ $a% uhakika — angalia kundi na aina kabla ya kuthibitisha.", "⚠️ $a% sure — check category na type before kuconfirm.", "⚠️ $a% — check first.").pick(lang)
    "confirm_as_cat" -> Copy4("Confirm as category", "Thibitisha kama kundi", "Confirm kama category", "Category").pick(lang)
    "ignore" -> Copy4("Ignore", "Puuza", "Ignore", "Ignore").pick(lang)
    "ignored_msg" -> Copy4("Ignored — pending removed.", "Imepuuzwa — inayosubiri imeondolewa.", "Imeignoreiwa — pending imetolewa.", "Ignored.").pick(lang)
    "confirm_btn" -> Copy4("Confirm", "Thibitisha", "Confirm", "Confirm").pick(lang)
    "saved_msg" -> Copy4("Saved to ledger.", "Imehifadhiwa kwenye leja.", "Imesaviwa kwa ledger.", "Saved.").pick(lang)
    "undo_btn" -> Copy4("Undo", "Tendua", "Undo", "Undo").pick(lang)
    "drift_hidden" -> Copy4("Drift check: KSh ••••", "Ukaguzi: KSh ••••", "Drift check: KSh ••••", "••••").pick(lang)
    "drift_sync" -> Copy4("Drift check: ledger matches SMS ✓", "Ukaguzi: leja inalingana na SMS ✓", "Drift check: ledger inamatch SMS ✓", "In sync ✓").pick(lang)
    "drift_minor" -> Copy4("Drift check: KSh $a small gap — likely a fee or one unlogged row.", "Ukaguzi: KSh $a pengo dogo — labda ada au safu moja.", "Drift check: KSh $a gap ndogo — labda fee ama row moja.", "Gap $a — minor.").pick(lang)
    "drift_major_high" -> Copy4("Drift check: KSh $a (ledger higher — spending missing?)", "Ukaguzi: KSh $a (leja juu — matumizi yamekosekana?)", "Drift check: KSh $a (ledger juu — spending inamiss?)", "Ledger higher $a?").pick(lang)
    "drift_major_low" -> Copy4("Drift check: KSh $a (SMS higher — income missing?)", "Ukaguzi: KSh $a (SMS juu — kipato kimekosekana?)", "Drift check: KSh $a (SMS juu — income inamiss?)", "SMS higher $a?").pick(lang)
    "reconcile_btn" -> Copy4("Reconcile KSh $a → ledger ⚖️", "Linganisha KSh $a → leja ⚖️", "Reconcile KSh $a → ledger ⚖️", "Fix $a ⚖️").pick(lang)
    "reconcile_title" -> Copy4("Reconcile drift?", "Linganisha tofauti?", "Reconcile drift?", "Reconcile?").pick(lang)
    "reconcile_body" -> Copy4("Ledger M-Pesa KSh $a vs SMS KSh $b. Books KSh $c as “Balance adjustment” $d.", "Leja M-Pesa KSh $a dhidi ya SMS KSh $b. Andika KSh $c kama “Balance adjustment” $d.", "Ledger M-Pesa KSh $a vs SMS KSh $b. Book KSh $c kama “Balance adjustment” $d.", "Ledger $a vs SMS $b.").pick(lang)
    "reconcile_spend" -> Copy4("(spending).", "(matumizi).", "(spending).", ".").pick(lang)
    "reconcile_income" -> Copy4("(income).", "(kipato).", "(income).", ".").pick(lang)
    "reconciled" -> Copy4("Reconciled ✓", "Imelinganishwa ✓", "Imereconcile ✓", "Done ✓").pick(lang)
    "book_it" -> Copy4("Book it", "Andika", "Book it", "Book.").pick(lang)
    "cancel" -> Copy4("Cancel", "Ghairi", "Cancel", "Cancel").pick(lang)
    "smart_analyzer" -> Copy4("Smart Analyzer 🧠", "Mchanganuzi 🧠", "Smart Analyzer 🧠", "Analyzer 🧠").pick(lang)
    "weekday_breakdown" -> Copy4("Weekday Spending Breakdown", "Matumizi kwa Siku za Wiki", "Weekday Spending Breakdown", "By weekday").pick(lang)
    "this_week" -> Copy4("This week", "Wiki hii", "Wiki hii", "Week").pick(lang)
    "all_time" -> Copy4("All time", "Zote", "All time", "All").pick(lang)
    "sem_runway" -> Copy4("Semester Runway 🛤️", "Kipimo cha Muhula 🛤️", "Semester Runway 🛤️", "Runway 🛤️").pick(lang)
    "sem_starts" -> Copy4("Semester starts in $a days", "Muhula unaanza baada ya siku $a", "Sem inaanza after siku $a", "Starts in $a days").pick(lang)
    "sem_ended" -> Copy4("Semester ended", "Muhula umeisha", "Sem imeisha", "Ended").pick(lang)
    "sem_days_left" -> Copy4("$a days left in semester", "Siku $a zimebaki muhula", "Siku $a zimebaki sem", "$a days left").pick(lang)
    "after_commit" -> Copy4("After commitments: $a", "Baada ya ahadi: $a", "After commitments: $a", "After: $a").pick(lang)
    "safe_daily" -> Copy4("Safe daily runway: $a/day", "Kipimo salama cha kila siku: $a/siku", "Safe daily runway: $a/day", "$a/day safe").pick(lang)
    "shortfall" -> Copy4("Shortfall: $a/day needed to cover the current gap", "Upungufu: $a/siku inahitajika kufunika pengo", "Shortfall: $a/day needed kufunika gap", "Need $a/day").pick(lang)
    "sem_set_dates" -> Copy4("Set the actual semester start and end dates under More → University to see your semester runway.", "Weka tarehe halisi za mwanzo na mwisho wa muhula chini ya More → University uone kipimo chako.", "Weka actual sem dates kwa More → University uone runway yako.", "Set dates: More → University.").pick(lang)
    "fee_bleed" -> Copy4("Fee bleed 💸", "Ada zinazovuja 💸", "Fee bleed 💸", "Fees 💸").pick(lang)
    "fee_bleed_body" -> Copy4("KSh $a in carrier charges this month — batch withdrawals to cut it.", "KSh $a kwa ada za mitandao mwezi huu — kusanya uondoaji ili kuzipunguza.", "KSh $a kwa carrier charges hii mwezi — batch withdrawals kuikat.", "Fees $a — batch up.").pick(lang)
    "spend_by_cat" -> Copy4("Spending by category", "Matumizi kwa kundi", "Spending by category", "By category").pick(lang)
    "insights_title" -> Copy4("Spending Insights 💡", "Maarifa ya Matumizi 💡", "Spending Insights 💡", "Insights 💡").pick(lang)
    "insights_sub" -> Copy4("Charts, trends and advice from your data", "Chati, mienendo na ushauri kutoka data yako", "Charts, trends na advice kutoka data yako", "Charts + advice").pick(lang)
    "see_insights" -> Copy4("See insights", "Ona maarifa", "Ona insights", "View").pick(lang)
    "tune_title" -> Copy4("Tune your home", "Rekebisha ukurasa", "Tune home", "Tune").pick(lang)
    "tune_body" -> Copy4("Pick the sections you want to see.", "Chagua sehemu unazotaka kuona.", "Pick sections unataka kuona.", "Pick sections.").pick(lang)
    "sec_safe" -> Copy4("Safe-to-spend", "Salama-kutumia", "Safe-to-spend", "Safe").pick(lang)
    "sec_pending" -> Copy4("Pending approvals", "Idhini zinazosubiri", "Pending approvals", "Pending").pick(lang)
    "sec_recent" -> Copy4("Recent activity", "Shughuli za hivi karibuni", "Recent activity", "Recent").pick(lang)
    "done_btn" -> Copy4("Done", "Sawa", "Done", "Done").pick(lang)
    "coach_t1" -> Copy4("1 · Log in seconds ⚡", "1 · Andika kwa sekunde ⚡", "1 · Log in seconds ⚡", "1 · Log ⚡").pick(lang)
    "coach_t2" -> Copy4("2 · Approve, don't type ✅", "2 · Thibitisha, usiandike ✅", "2 · Approve, usitype ✅", "2 · Approve ✅").pick(lang)
    "coach_t3" -> Copy4("3 · Spend what's safe 🎯", "3 · Tumia kilicho salama 🎯", "3 · Spend what's safe 🎯", "3 · Safe 🎯").pick(lang)
    "coach_b1" -> Copy4("Tap + below for any expense. Amount, where, done — under 5 seconds.", "Gusa + hapa chini kwa matumizi yoyote. Kiasi, wapi, imekwisha — chini ya sekunde 5.", "Tap + hapa chini kwa any expense. Amount, wapi, done — under 5 seconds.", "+ for expenses.").pick(lang)
    "coach_b2" -> Copy4("M-Pesa texts land here as pending. Sure ones confirm all at once — the rest get your eyes, one by one.", "SMS za M-Pesa huingia hapa kama zinazosubiri. Za uhakika thibitisha zote mara moja — zingine kwa macho yako, moja moja.", "M-Pesa texts huingia hapa kama pending. Sure ones confirm zote at once — zingine kwa macho yako, moja moja.", "Pending → confirm.").pick(lang)
    "coach_b3" -> Copy4("Safe-to-spend is your one number: what's actually okay to use today.", "Salama-kutumia ndiyo namba yako moja: nini kiko sawa kutumia leo.", "Safe-to-spend ndiyo number yako moja: nini iko okay kutumia leo.", "One safe number.").pick(lang)
    "skip_tour" -> Copy4("Skip tour", "Ruka ziara", "Skip tour", "Skip").pick(lang)
    "next_btn" -> Copy4("Next", "Endelea", "Next", "Next").pick(lang)
    "start_btn" -> Copy4("Start", "Anza", "Start", "Start").pick(lang)
    "jump_to" -> Copy4("Jump to", "Ruka kwa", "Jump kwa", "Jump").pick(lang)
    "open_label" -> Copy4("Open $a", "Fungua $a", "Fungua $a", "Open $a").pick(lang)
    "motiv0" -> Copy4("Every coin saved is a step closer to your degree! 🎓", "Kila senti inayookolewa ni hatua karibu na shahada yako! 🎓", "Kila bob inayosave ni step karibu na degree yako! 🎓", "Save → degree! 🎓").pick(lang)
    "motiv1" -> Copy4("Small cuts today, big freedom tomorrow. 💪", "Kupunguza kidogo leo, uhuru mkubwa kesho. 💪", "Small cuts leo, big freedom kesho. 💪", "Cut small, win big. 💪").pick(lang)
    "motiv2" -> Copy4("Your future self will thank you for this decision. ✨", "Wewe wa baadaye atakushukuru kwa uamuzi huu. ✨", "Future self wako ataku-thank kwa hii decision. ✨", "Future you says thanks. ✨").pick(lang)
    "motiv3" -> Copy4("Consistent small savings beat sporadic big wins. 🌟", "Akiba ndogo thabiti hushinda ushindi mkubwa wa hapa na pale. 🌟", "Consistent small savings hushinda sporadic big wins. 🌟", "Small + steady wins. 🌟").pick(lang)
    "motiv4" -> Copy4("Don't let today's spending steal tomorrow's opportunities. 🚀", "Usiruhusu matumizi ya leo kuiba fursa za kesho. 🚀", "Usiruhusu spending ya leo kuiba opportunities za kesho. 🚀", "Protect tomorrow. 🚀").pick(lang)
    "safe_title" -> Copy4("Safe to Spend 🛡️", "Salama Kutumia 🛡️", "Safe to Spend 🛡️", "Safe 🛡️").pick(lang)
    "day_chip" -> Copy4("Day", "Siku", "Siku", "Day").pick(lang)
    "week_chip" -> Copy4("Week", "Wiki", "Wiki", "Week").pick(lang)
    "safe_anchor" -> Copy4("Based on M-Pesa KSh $a of KSh $b total (Recorded)", "Kulingana na M-Pesa KSh $a kati ya KSh $b jumla (Imerekodiwa)", "Based on M-Pesa KSh $a of KSh $b total (Recorded)", "M-Pesa $a / $b.").pick(lang)
    "safe_set_budget" -> Copy4("Set a Daily budget or a monthly ALL budget on the Budget tab and I'll compute your allowance — including yesterday's rollover, plans and bills.", "Weka bajeti ya Kila Siku au ya mwezi ALL kwenye kichupo cha Bajeti nami nitahesabu posho yako — ikiwa ni pamoja na salio la jana, mipango na bili.", "Weka Daily budget ama monthly ALL kwa Budget tab nami nitacompute allowance yako — including rollover ya jana, plans na bills.", "Set Daily/ALL budget.").pick(lang)
    "left_today" -> Copy4("left of KSh $a today", "zimebaki kati ya KSh $a leo", "zimebaki of KSh $a leo", "left of $a.").pick(lang)
    "plans_keep_day" -> Copy4(" (KSh $a/day kept for plans)", " (KSh $a/siku zimehifadhiwa kwa mipango)", " (KSh $a/day zimekeepiwa plans)", " (plans $a).").pick(lang)
    "left_week" -> Copy4("left of KSh $a this week", "zimebaki kati ya KSh $a wiki hii", "zimebaki of KSh $a hii wiki", "left of $a.").pick(lang)
    "plans_keep_week" -> Copy4(" (plans keep KSh $a/week)", " (mipango huhifadhi KSh $a/wiki)", " (plans huhold KSh $a/week)", " (plans $a).").pick(lang)
    "safe_row_target" -> Copy4("Daily target (budget pace, capped by flexible cash)", "Lengo la kila siku (mwendo wa bajeti, kikomo pesa rahisi)", "Daily target (budget pace, capped na flexible cash)", "Daily target").pick(lang)
    "safe_row_pace" -> Copy4("Weekday pace (×$a today)", "Mwendo wa siku (×$a leo)", "Weekday pace (×$a leo)", "Pace ×$a").pick(lang)
    "safe_row_spent_month" -> Copy4("Spent this month", "Imetumika mwezi huu", "Imespendiwa hii mwezi", "Spent").pick(lang)
    "safe_row_yesterday" -> Copy4("Yesterday", "Jana", "Jana", "Yesterday").pick(lang)
    "safe_row_plans" -> Copy4("Plans reserve", "Akiba ya mipango", "Reserve ya plans", "Plans").pick(lang)
    "safe_row_bills" -> Copy4("Bills share", "Sehemu ya bili", "Share ya mabill", "Bills").pick(lang)
    "safe_row_spent_today" -> Copy4("Spent today", "Imetumika leo", "Imespendiwa leo", "Today").pick(lang)
    "safe_fresh" -> Copy4("No spending logged yet — full KSh $a is available today.", "Hakuna matumizi yaliyorekodiwa bado — KSh $a yote inapatikana leo.", "Hakuna spending imeloggiwa bado — full KSh $a iko available leo.", "Full $a today.").pick(lang)
    "safe_over" -> Copy4("KSh $a over pace (KSh $b of KSh $c with $d left) — essentials only. 🛑", "KSh $a zaidi ya mwendo (KSh $b kati ya KSh $c na $d zimebaki) — muhimu tu. 🛑", "KSh $a over pace (KSh $b of KSh $c na $d zimebaki) — essentials only. 🛑", "Over by $a. 🛑").pick(lang)
    "safe_tight" -> Copy4("KSh $a left — prioritize: Food KSh $b + essentials KSh $c. 💪", "KSh $a zimebaki — weka kipaumbele: Chakula KSh $b + muhimu KSh $c. 💪", "KSh $a zimebaki — prioritize: Food KSh $b + essentials KSh $c. 💪", "$a left. 💪").pick(lang)
    "safe_onpace" -> Copy4("KSh $a of KSh $b with $c left — on pace! 🎉 Today you can spend KSh $d.", "KSh $a kati ya KSh $b na $c zimebaki — uko sawa! 🎉 Leo unaweza kutumia KSh $d.", "KSh $a of KSh $b na $c zimebaki — on pace! 🎉 Leo unaweza kuspend KSh $d.", "On pace! $d today. 🎉").pick(lang)
    "safe_overmonth" -> Copy4("KSh $a of KSh $b with $c left — over pace. Tighten today to KSh $d.$e", "KSh $a kati ya KSh $b na $c zimebaki — umevuka mwendo. Bana leo hadi KSh $d.$e", "KSh $a of KSh $b na $c zimebaki — over pace. Tighten leo hadi KSh $d.$e", "Over. Today: $d.").pick(lang)
    "safe_paused_day" -> Copy4(" You've passed today's allowance — pause till tomorrow. ⏸️", " Umevuka posho ya leo — pumzika hadi kesho. ⏸️", " Umepitisha allowance ya leo — pause hadi kesho. ⏸️", " Paused till tomorrow. ⏸️").pick(lang)
    "safe_unusual" -> Copy4("⚠️ Unusual day: KSh $a already vs KSh $b expected — pause non-essentials. ⏸️", "⚠️ Siku isiyo ya kawaida: KSh $a tayari dhidi ya KSh $b inayotarajiwa — pumzisha yasiyo muhimu. ⏸️", "⚠️ Siku weird: KSh $a already vs KSh $b expected — pause non-essentials. ⏸️", "⚠️ Unusual: $a vs $b.").pick(lang)
    "safe_week_none" -> Copy4("No weekly target set — add a Weekly budget or monthly ALL on the Budget tab and I'll pace it. 🎯", "Hakuna lengo la wiki — weka bajeti ya Wiki au ALL ya mwezi kwenye Bajeti nami nitaiweka sawa. 🎯", "Hakuna weekly target — weka Weekly budget ama monthly ALL kwa Budget tab nami nitaipace. 🎯", "Set Weekly/ALL. 🎯").pick(lang)
    "safe_week_tight" -> Copy4("KSh $a this week (~KSh $b/day) — prioritize: Food KSh $c + essentials KSh $d. 💪", "KSh $a wiki hii (~KSh $b/siku) — weka kipaumbele: Chakula KSh $c + muhimu KSh $d. 💪", "KSh $a hii wiki (~KSh $b/day) — prioritize: Food KSh $c + essentials KSh $d. 💪", "$a this week. 💪").pick(lang)
    "safe_week_good" -> Copy4("Last week cost KSh $a vs KSh $b target — nice! 🎉 This week you can spend KSh $c.", "Wiki iliyopita iligharimu KSh $a dhidi ya KSh $b — nzuri! 🎉 Wiki hii unaweza kutumia KSh $c.", "Last week ilicost KSh $a vs KSh $b target — nice! 🎉 Hii wiki unaweza kuspend KSh $c.", "Nice! $c this week. 🎉").pick(lang)
    "safe_week_over" -> Copy4("Last week went KSh $a over (KSh $b vs KSh $c). This week tighten to KSh $d.$e", "Wiki iliyopita ilizidi KSh $a (KSh $b dhidi ya KSh $c). Wiki hii bana hadi KSh $d.$e", "Last week ilienda KSh $a over (KSh $b vs KSh $c). Hii wiki tighten hadi KSh $d.$e", "Over $a. This week: $d.").pick(lang)
    "safe_paused_week" -> Copy4(" You've passed the weekly allowance — pause till next week. ⏸️", " Umevuka posho ya wiki — pumzika hadi wiki ijayo. ⏸️", " Umepitisha weekly allowance — pause hadi next week. ⏸️", " Paused a week. ⏸️").pick(lang)
    "budgets_title" -> Copy4("Budgets", "Bajeti", "Budget", "Budget").pick(lang)
    "safe_what" -> Copy4("What is safe-to-spend?", "Salama-kutumia ni nini?", "Safe-to-spend ni nini?", "Safe?").pick(lang)
    "safe_what_body" -> Copy4("Income minus budgets, bills due and goals — the amount actually okay to use today. It moves as you log spending.", "Kipato ukiondoa bajeti, bili zinazodaiwa na malengo — kiasi kilicho sawa kutumia leo. Hubadilika unapoandika matumizi.", "Income minus budgets, bills due na goals — doh actually okay kutumia leo. Hubadilika ukilog spending.", "Income − budgets − bills − goals.").pick(lang)
    "rail_ledger" -> Copy4("Ledger", "Leja", "Ledger", "Ledger").pick(lang)
    "rail_buddy" -> Copy4("Buddy", "Buddy", "Buddy", "Buddy").pick(lang)
    else -> a.ifBlank { key }
}

// ---------- Transactions dictionary ----------

fun txnT(key: String, lang: AppLanguage, a: String = "", b: String = "", c: String = "", d: String = ""): String = when (key) {
    "title" -> Copy4("Transactions", "Miamala", "Transactions", "Transactions").pick(lang)
    "ask_buddy" -> Copy4("Ask Buddy", "Uliza Buddy", "Uliza Buddy", "Buddy").pick(lang)
    "search_label" -> Copy4("Search merchant or category", "Tafuta muuzaji au kundi", "Search merchant ama category", "Search").pick(lang)
    "search_everything" -> Copy4("Search everything 🔍 → budgets, bills, screens", "Tafuta kila kitu 🔍 → bajeti, bili, skrini", "Search everything 🔍 → budgets, bills, screens", "Search all 🔍").pick(lang)
    "t_all" -> Copy4("All", "Zote", "Zote", "All").pick(lang)
    "t_in" -> Copy4("In", "Ndani", "In", "In").pick(lang)
    "t_out" -> Copy4("Out", "Nje", "Out", "Out").pick(lang)
    "t_saved" -> Copy4("Saved", "Zimeokolewa", "Saved", "Saved").pick(lang)
    "t_grown" -> Copy4("Grown", "Zilizoongezeka", "Grown", "Grown").pick(lang)
    "t_moved" -> Copy4("Moved", "Zilohamishwa", "Moved", "Moved").pick(lang)
    "newest" -> Copy4("Newest first", "Mpya kwanza", "Mpya kwanza", "Newest").pick(lang)
    "oldest" -> Copy4("Oldest first", "Za kale kwanza", "Za kitambo kwanza", "Oldest").pick(lang)
    "select" -> Copy4("Select", "Chagua", "Select", "Select").pick(lang)
    "done_sel" -> Copy4("Done", "Sawa", "Done", "Done").pick(lang)
    "all_time" -> Copy4("All time", "Zote", "All time", "All").pick(lang)
    "today" -> Copy4("Today", "Leo", "Leo", "Today").pick(lang)
    "days7" -> Copy4("7 days", "Siku 7", "Siku 7", "7 days").pick(lang)
    "days30" -> Copy4("30 days", "Siku 30", "Siku 30", "30 days").pick(lang)
    "scan_today" -> Copy4("Scan today 📥", "Scan leo 📥", "Scan leo 📥", "Scan 📥").pick(lang)
    "scanning_found" -> Copy4("Scanning… $a found", "Inascan… $a zimepatikana", "Inascan… $a zimepatikana", "Scanning… $a").pick(lang)
    "more_scan" -> Copy4("More scan options ▾", "Chaguo zaidi za scan ▾", "More scan options ▾", "More ▾").pick(lang)
    "less" -> Copy4("Less ▴", "Kidogo ▴", "Less ▴", "Less ▴").pick(lang)
    "scan_explainer" -> Copy4("Today checks this morning's texts. Longer scans catch older money — duplicates are skipped automatically by M-Pesa code.", "Leo huangalia SMS za asubuhi. Scan ndefu hukamata pesa za zamani — zinazojirudia hurukwa kiotomatiki kwa nambari ya M-Pesa.", "Leo hucheck texts za morning. Longer scans hushika doh za kitambo — duplicates hurukwa automatically na M-Pesa code.", "Today = morning texts; longer = older.").pick(lang)
    "scan7" -> Copy4("Scan 7 days", "Scan siku 7", "Scan siku 7", "7 days").pick(lang)
    "scan30" -> Copy4("Scan 30 days", "Scan siku 30", "Scan siku 30", "30 days").pick(lang)
    "scan5m" -> Copy4("Scan 5 months", "Scan miezi 5", "Scan miezi 5", "5 months").pick(lang)
    "scan_fail" -> Copy4("Scan failed: $a", "Scan imeshindwa: $a", "Scan imefail: $a", "Failed.").pick(lang)
    "scan_done" -> Copy4("Scanned $a texts ($b): $c new for review, rest already logged.", "Imescan SMS $a ($b): $c mpya za kupitia, zingine zisharekodiwa.", "Imescan texts $a ($b): $c mpya za review, zingine zishaloggiwa.", "Scanned $a: $c new.").pick(lang)
    "scan_fees" -> Copy4(" KSh $a fees seen.", " Ada KSh $a zimeonekana.", " Fees KSh $a zimeonekana.", " Fees $a.").pick(lang)
    "empty_pending_title" -> Copy4("$a waiting in Pending", "$a zinasubiri kwenye Pending", "$a zinangoja kwa Pending", "$a in Pending").pick(lang)
    "empty_pending_body" -> Copy4("Your scan found them — confirm and they land here as transactions.", "Scan yako imezipata — zithibitishe ziingie hapa kama miamala.", "Scan yako imezipata — ziconfirm ziingie hapa kama transactions.", "Confirm them in.").pick(lang)
    "confirm_sure" -> Copy4("Confirm $a sure", "Thibitisha $a za uhakika", "Confirm $a sure", "Confirm $a.").pick(lang)
    "scan_sms_btn" -> Copy4("Scan M-Pesa SMS", "Scan SMS za M-Pesa", "Scan SMS za M-Pesa", "Scan SMS.").pick(lang)
    "confirmed_msg" -> Copy4("$a confirmed.", "$a zimethibitishwa.", "$a zimeconfirmiwa.", "$a done.").pick(lang)
    "no_tx_title" -> Copy4("No transactions yet", "Hakuna miamala bado", "Hakuna transactions bado", "Empty.").pick(lang)
    "no_tx_body" -> Copy4("Scan your M-Pesa messages — rows queue for review, then land here.", "Scan SMS zako za M-Pesa — safu hupangwa kwa mapitio, kisha huingia hapa.", "Scan texts zako za M-Pesa — rows huqueue kwa review, then huingia hapa.", "Scan to begin.").pick(lang)
    "day_today" -> Copy4("TODAY", "LEO", "LEO", "TODAY").pick(lang)
    "day_yesterday" -> Copy4("YESTERDAY", "JANA", "JANA", "YESTERDAY").pick(lang)
    "day_items" -> Copy4("$a item(s) · ", "vipengee $a · ", "items $a · ", "$a · ").pick(lang)
    "day_moved" -> Copy4(" · $a moved", " · $a zimehamishwa", " · $a zimemove", " · $a moved").pick(lang)
    "showing" -> Copy4("Showing $a of $b · In KSh $c · Out KSh $d", "Inaonyesha $a kati ya $b · Ndani KSh $c · Nje KSh $d", "Inaonyesha $a of $b · In KSh $c · Out KSh $d", "$a/$b · +$c −$d").pick(lang)
    "net" -> Copy4(" · Net ", " · Jumla ", " · Net ", " · Net ").pick(lang)
    "share_all" -> Copy4("Share all", "Shiriki zote", "Share zote", "Share.").pick(lang)
    "show_more" -> Copy4("Show 100 more", "Onyesha 100 zaidi", "Onyesha 100 more", "+100.").pick(lang)
    "show_all" -> Copy4("Show all $a", "Onyesha zote $a", "Onyesha zote $a", "All $a.").pick(lang)
    "picked" -> Copy4("$a picked · KSh $b", "$a zimechaguliwa · KSh $b", "$a zimepickiwa · KSh $b", "$a · $b.").pick(lang)
    "share" -> Copy4("Share", "Shiriki", "Share", "Share").pick(lang)
    "delete" -> Copy4("Delete", "Futa", "Delete", "Delete").pick(lang)
    "undo" -> Copy4("Undo", "Tendua", "Undo", "Undo").pick(lang)
    "dup_title" -> Copy4("Possible duplicates ($a)", "Zinazoweza kujirudia ($a)", "Possible duplicates ($a)", "Dupes ($a).").pick(lang)
    "dup_sub" -> Copy4("Same amount + merchant. Merge keeps the newest, deletes the rest.", "Kiasi + muuzaji sawa. Kuunganisha huhifadhi mpya zaidi, hufuta zingine.", "Same amount + merchant. Merge huweka newest, hufuta zingine.", "Merge keeps newest.").pick(lang)
    "auto_remove" -> Copy4("Auto-remove $a exact", "Ondoa $a kiotomatiki", "Auto-remove $a exact", "Auto $a.").pick(lang)
    "restored" -> Copy4("Restored $a row(s)", "Zimerudishwa $a", "Zimerestore $a", "Restored $a.").pick(lang)
    "removed_dupes" -> Copy4("Removed $a exact duplicate(s)", "Zimeondolewa $a zinazofanana", "Zimeondolewa $a exact duplicates", "Removed $a.").pick(lang)
    "review_btn" -> Copy4("Review", "Pitia", "Review", "Review").pick(lang)
    "dedupe_title" -> Copy4("Remove $a duplicates?", "Ondoa $a zinazojirudia?", "Ondoa $a duplicates?", "Remove $a?").pick(lang)
    "dedupe_body" -> Copy4("Same amount, merchant, day and method within minutes. Keeps the earliest of each group — undo brings them back.", "Kiasi, muuzaji, siku na njia sawa ndani ya dakika. Huhifadhi ya kwanza ya kila kundi — tendua huzirudisha.", "Same amount, merchant, day na method within minutes. Huhifadhi earliest ya kila group — undo huzirudisha.", "Keeps earliest; undo works.").pick(lang)
    "remove" -> Copy4("Remove", "Ondoa", "Ondoa", "Remove").pick(lang)
    "keep" -> Copy4("Keep", "Weka", "Keep", "Keep").pick(lang)
    "merge_title" -> Copy4("Merge $a rows?", "Unganisha safu $a?", "Merge rows $a?", "Merge $a?").pick(lang)
    "merge_body" -> Copy4("Keeps the newest $a KSh $b, deletes the other $c. Batch deletes can't be undone.", "Huhifadhi mpya zaidi $a KSh $b, hufuta zingine $c. Ufutaji wa pamoja hautenduliwi.", "Huweka newest $a KSh $b, hufuta other $c. Batch deletes haziundwi.", "Keeps newest; no undo.").pick(lang)
    "merge_btn" -> Copy4("Merge", "Unganisha", "Merge", "Merge").pick(lang)
    "keep_all" -> Copy4("Keep all", "Weka zote", "Keep zote", "Keep.").pick(lang)
    "del_bulk_title" -> Copy4("Delete $a transactions?", "Futa miamala $a?", "Delete transactions $a?", "Delete $a?").pick(lang)
    "del_bulk_body" -> Copy4("KSh $a goes away. Batch deletes can't be undone — singles can.", "KSh $a inaondoka. Ufutaji wa pamoja hautenduliwi — moja moja unaweza.", "KSh $a inaenda. Batch deletes haziundwi — singles zinaweza.", "$a gone; no undo.").pick(lang)
    "del_all_btn" -> Copy4("Delete all", "Futa zote", "Delete zote", "Delete.").pick(lang)
    "del_title" -> Copy4("Delete this transaction?", "Futa muamala huu?", "Delete hii transaction?", "Delete?").pick(lang)
    "deleted_merchant" -> Copy4("Deleted $a.", "Imefutwa $a.", "Imedelete $a.", "Deleted $a.").pick(lang)
    "del_body" -> Copy4("$a · KSh $b — you can undo right after.", "$a · KSh $b — unaweza kutendua mara tu.", "$a · KSh $b — unaweza kuundo right after.", "$a · $b — undoable.").pick(lang)
    "bal" -> Copy4("Bal ", "Salio ", "Bal ", "Bal ").pick(lang)
    "name_sender" -> Copy4("Name this sender", "Mtaje mtumaji huyu", "Name hii sender", "Name").pick(lang)
    "edit_txn" -> Copy4("Edit transaction", "Hariri muamala", "Edit transaction", "Edit").pick(lang)
    "del_txn" -> Copy4("Delete transaction", "Futa muamala", "Delete transaction", "Delete").pick(lang)
    "alias_title" -> Copy4("Who is $a?", "$a ni nani?", "$a ni nani?", "Who is $a?").pick(lang)
    "alias_body" -> Copy4("Give them a name once — every future row from this sender shows it, and their category is remembered too.", "Wape jina mara moja — kila safu ya baadaye kutoka kwa mtumaji huyu itaonyesha, na kundi lao litakumbukwa pia.", "Wapee jina once — kila future row kutoka kwa hii sender itaonyesha, na category yao itakumbukwa pia.", "Name once, remembered.").pick(lang)
    "alias_label" -> Copy4("Name (e.g. Mom)", "Jina (k.m. Mama)", "Jina (e.g. Mama)", "Name").pick(lang)
    "alias_showing" -> Copy4("Now showing as “$a”.", "Sasa inaonyeshwa kama “$a”.", "Sai inaonyeshwa kama “$a”.", "“$a”.").pick(lang)
    "save" -> Copy4("Save", "Hifadhi", "Save", "Save").pick(lang)
    "forget" -> Copy4("Forget", "Sahau", "Sahau", "Forget").pick(lang)
    "cancel" -> Copy4("Cancel", "Ghairi", "Cancel", "Cancel").pick(lang)
    else -> a.ifBlank { key }
}

// ---------- Budgets dictionary ----------

fun budT(key: String, lang: AppLanguage, a: String = "", b: String = "", c: String = "", d: String = "", e: String = ""): String = when (key) {
    "title" -> Copy4("Budgets & Plans", "Bajeti na Mipango", "Budgets na Maplans", "Budgets").pick(lang)
    "ask_buddy" -> Copy4("Ask Buddy", "Uliza Buddy", "Uliza Buddy", "Buddy").pick(lang)
    "add" -> Copy4("Add", "Weka", "Add", "Add").pick(lang)
    "tab_daily" -> Copy4("Daily", "Kila siku", "Daily", "Daily").pick(lang)
    "tab_weekly" -> Copy4("Weekly", "Kila wiki", "Weekly", "Weekly").pick(lang)
    "tab_monthly" -> Copy4("Monthly", "Kila mwezi", "Monthly", "Monthly").pick(lang)
    "tab_semester" -> Copy4("Semester", "Muhula", "Sem", "Semester").pick(lang)
    "tab_annual" -> Copy4("Annual", "Mwaka", "Yearly", "Yearly").pick(lang)
    "win_today" -> Copy4("today", "leo", "leo", "today").pick(lang)
    "win_week" -> Copy4("this week", "wiki hii", "wiki hii", "week").pick(lang)
    "win_120" -> Copy4("last 120 days", "siku 120 zilizopita", "last 120 days", "120d").pick(lang)
    "win_month" -> Copy4("this month", "mwezi huu", "mwezi huu", "month").pick(lang)
    "env_title" -> Copy4("Envelopes", "Bahasha", "Envelopes", "Envelopes").pick(lang)
    "env_sub" -> Copy4("Daily to annual plans", "Mipango ya kila siku hadi mwaka", "Plans za daily hadi yearly", "Plans").pick(lang)
    "how_suggested" -> Copy4("How are budgets suggested?", "Bajeti hupendekezwaje?", "Budgets husuggestiwa aje?", "How?").pick(lang)
    "how_suggested_body" -> Copy4("Your income and past spending set the starting point. Apply, then adjust — the budget learns as your ledger grows.", "Kipato chako na matumizi ya nyuma huweka mwanzo. Tumia, kisha rekebisha — bajeti hujifunza leja yako inavyokua.", "Income yako na past spending huweka starting point. Apply, then adjust — budget hujifunza ledger yako inavyogrow.", "Income + history → plan.").pick(lang)
    "safety_buffer" -> Copy4("Safety buffer", "Akiba ya dharura", "Safety buffer", "Buffer").pick(lang)
    "budget_footer" -> Copy4("A budget is a plan, not cash — it can exceed your balance. Flexible money is what is actually safe to use.", "Bajeti ni mpango, si pesa — inaweza kuzidi salio lako. Pesa rahisi ndiyo salama kutumia.", "Budget ni plan, si cash — inaweza kupitisha balance yako. Flexible money ndiyo actually safe kutumia.", "Plan ≠ cash.").pick(lang)
    "calc_title" -> Copy4("Budget Calculator 🧮", "Kikokotoo cha Bajeti 🧮", "Budget Calculator 🧮", "Calculator 🧮").pick(lang)
    "calc_sub" -> Copy4("Enter monthly income, pick a rule — get suggested budgets, apply in one tap.", "Weka kipato cha mwezi, chagua sheria — pata bajeti zilizopendekezwa, tumia kwa gusa moja.", "Weka monthly income, pick rule — pata suggested budgets, apply in one tap.", "Income → suggestions.").pick(lang)
    "cash_advice" -> Copy4("You have KSh $a flexible after commitments; KSh $b is already spent in this period. At about KSh $c per essential day, plan for $d day(s) and stop before the next expected income$e. That date is an estimate, not money you already have.", "Una KSh $a rahisi baada ya ahadi; KSh $b imeshatumika kipindi hiki. Kwa takriban KSh $c kwa siku muhimu, panga siku $d na usimame kabla ya kipato kijacho$e. Tarehe hiyo ni kadirio, si pesa ulizonazo.", "Una KSh $a flexible after commitments; KSh $b ishspendiwa hii period. At about KSh $c per essential day, plan for siku $d na usimame before next expected income$e. Hiyo date ni estimate, si doh uko nayo.", "Flexible $a; spent $b; plan $d days.").pick(lang)
    "cash_in_days" -> Copy4(" in $a day(s)", " baada ya siku $a", " in siku $a", " in $a days").pick(lang)
    "plan_today_cash" -> Copy4("Plan today's available cash", "Panga pesa inayopatikana leo", "Plan available cash ya leo", "Today's cash").pick(lang)
    "plan_week_cash" -> Copy4("Plan this week's available cash", "Panga pesa inayopatikana wiki hii", "Plan available cash ya hii wiki", "Week's cash").pick(lang)
    "income_label_month" -> Copy4("Monthly income (KSh)", "Kipato cha mwezi (KSh)", "Monthly income (KSh)", "Income").pick(lang)
    "income_label_period" -> Copy4("Available for this period (KSh)", "Inayopatikana kipindi hiki (KSh)", "Available kwa hii period (KSh)", "Available").pick(lang)
    "rule_campus" -> Copy4("Campus Survival", "Kuishi Chuoni", "Campus Survival", "Survival").pick(lang)
    "setup_title" -> Copy4("Your setup — budgets reshape around it", "Hali yako — bajeti hujibadilisha kuizunguka", "Setup yako — budgets hujireshape around it", "Your setup").pick(lang)
    "lifestyle_title" -> Copy4("Lifestyle — same engine, different appetite", "Mtindo — injini moja, hamu tofauti", "Lifestyle — same engine, appetite tofauti", "Lifestyle").pick(lang)
    "life_commuter" -> Copy4("Early bus, home meals — transport first, food light.", "Basi la mapema, milo ya nyumbani — usafiri kwanza, chakula kidogo.", "Early bus, home meals — transport first, food light.", "Transport first.").pick(lang)
    "life_foodie" -> Copy4("Food protected above all.", "Chakula kinalindwa kuliko vyote.", "Food inaprotectiwa above all.", "Food first.").pick(lang)
    "life_saver" -> Copy4("Savings pushed, lifestyle paused.", "Akiba inasukumwa, mtindo umesimama.", "Savings inapushiwa, lifestyle imepause.", "Save first.").pick(lang)
    "life_balanced" -> Copy4("Standard campus split.", "Mgawanyo wa kawaida wa chuo.", "Standard campus split.", "Balanced.").pick(lang)
    "auto_monthly" -> Copy4("Auto from monthly", "Kiotomatiki kutoka mwezi", "Auto kutoka monthly", "Auto").pick(lang)
    "auto_monthly_sub" -> Copy4("Daily = your monthly budgets ÷ 30", "Kila siku = bajeti zako za mwezi ÷ 30", "Daily = monthly budgets zako ÷ 30", "÷30").pick(lang)
    "income_moved" -> Copy4("Income moved — recalculate from KSh $a?", "Kipato kimebadilika — hesabu upya kutoka KSh $a?", "Income imemove — recalculate kutoka KSh $a?", "Recalculate $a?").pick(lang)
    "tight_mode" -> Copy4("Tight mode 🛡️ — money is little so Food + Rent eat first. Savings and lifestyle pause instead of starving you.", "Hali ngumu 🛡️ — pesa ni kidogo kwa hivyo Chakula + Kodi hula kwanza. Akiba na mtindo husimama badala ya kukuacha na njaa.", "Tight mode 🛡️ — doh ni kidogo so Food + Rent hukula kwanza. Savings na lifestyle hupause instead ya kuku-starve.", "Tight: Food + Rent first.").pick(lang)
    "cash_plan" -> Copy4("Cash-constrained plan: KSh $a maximum for this period. This uses current flexible cash, not expected future income.", "Mpango uliobanwa na pesa: KSh $a kiwango cha juu kipindi hiki. Hii inatumia pesa rahisi ya sasa, si kipato kijacho.", "Cash-constrained plan: KSh $a maximum kwa hii period. Hii inatumia current flexible cash, si expected future income.", "Max $a this period.").pick(lang)
    "fund_gap" -> Copy4("Funding gap: this plan exceeds the entered base by KSh $a. Treat the difference as unfunded, not spendable.", "Pengo la ufadhili: mpango huu unazidi msingi ulioingizwa kwa KSh $a. Chukulia tofauti kama isiyofadhiliwa, si ya kutumia.", "Funding gap: hii plan inapita entered base na KSh $a. Treat difference kama unfunded, si spendable.", "Gap $a — unfunded.").pick(lang)
    "paused_for" -> Copy4("Paused to protect Food + Rent: $a", "Imesimamishwa kulinda Chakula + Kodi: $a", "Imepause kuprotect Food + Rent: $a", "Paused: $a.").pick(lang)
    "applied" -> Copy4("Applied $a ✓", "Imetumika $a ✓", "Imeapply $a ✓", "$a ✓").pick(lang)
    "apply_as" -> Copy4("Apply as $a Budgets", "Tumia kama Bajeti za $a", "Apply kama $a Budgets", "Apply $a.").pick(lang)
    "log_income" -> Copy4("Log income first (or type it above) to see suggestions.", "Andika kipato kwanza (au kiandike hapo juu) uone mapendekezo.", "Log income kwanza (ama itype hapo juu) uone suggestions.", "Log income first.").pick(lang)
    "hero_line" -> Copy4("$a budget · $b", "Bajeti ya $a · $b", "Budget ya $a · $b", "$a · $b").pick(lang)
    "no_budget" -> Copy4("No $a budget set yet.", "Hakuna bajeti ya $a bado.", "Hakuna budget ya $a bado.", "No $a yet.").pick(lang)
    "set_limit_hint" -> Copy4("Spent $a so far: KSh $b. Set your $c limit here:", "Imetumika $a hadi sasa: KSh $b. Weka kikomo chako cha $c hapa:", "Imespendiwa $a so far: KSh $b. Weka $c limit yako hapa:", "Spent $b; set $c:").pick(lang)
    "ksh_limit" -> Copy4("KSh limit", "Kikomo (KSh)", "KSh limit", "Limit").pick(lang)
    "set_btn" -> Copy4("Set", "Weka", "Weka", "Set").pick(lang)
    "left_of" -> Copy4("left of KSh $a", "zimebaki kati ya KSh $a", "zimebaki of KSh $a", "left $a.").pick(lang)
    "carry" -> Copy4("＋KSh $a unspent from $b — already inside your balance 🎲", "＋KSh $a hazikutumika kutoka $b — tayari ndani ya salio lako 🎲", "＋KSh $a unspent kutoka $b — already ndani ya balance yako 🎲", "+$a from $b.").pick(lang)
    "last_week" -> Copy4("last week", "wiki iliyopita", "last week", "week").pick(lang)
    "last_month" -> Copy4("last month", "mwezi uliopita", "last month", "month").pick(lang)
    "verdict_over" -> Copy4("Over $a budget by KSh $b ($c%) — essentials only. 🛑", "Umevuka bajeti ya $a kwa KSh $b ($c%) — muhimu tu. 🛑", "Umepitisha budget ya $a na KSh $b ($c%) — essentials only. 🛑", "Over $b. 🛑").pick(lang)
    "verdict_hot" -> Copy4("$a% used — KSh $b left. Slow down. ⚠️", "$a% imetumika — KSh $b zimebaki. Punguza mwendo. ⚠️", "$a% imetumika — KSh $b zimebaki. Slow down. ⚠️", "$a% — slow. ⚠️").pick(lang)
    "verdict_warm" -> Copy4("$a% used — KSh $b left, still within pace. 👌", "$a% imetumika — KSh $b zimebaki, bado uko sawa. 👌", "$a% imetumika — KSh $b zimebaki, still within pace. 👌", "$a% — okay. 👌").pick(lang)
    "verdict_ok" -> Copy4("$a% used — KSh $b left. On track 👌.", "$a% imetumika — KSh $b zimebaki. Uko sawa 👌.", "$a% imetumika — KSh $b zimebaki. On track 👌.", "$a% — on track.").pick(lang)
    "cat_budgets" -> Copy4("Category budgets · $a", "Bajeti za makundi · $a", "Category budgets · $a", "Categories · $a").pick(lang)
    "bar_left" -> Copy4("KSh $a of KSh $b · KSh $c left · $d% used", "KSh $a kati ya KSh $b · KSh $c zimebaki · $d% imetumika", "KSh $a of KSh $b · KSh $c zimebaki · $d% imetumika", "$a/$b · $d%.").pick(lang)
    "bar_over" -> Copy4("KSh $a of KSh $b · Over by KSh $c", "KSh $a kati ya KSh $b · Umevuka kwa KSh $c", "KSh $a of KSh $b · Over na KSh $c", "$a/$b · +$c.").pick(lang)
    "bar_note" -> Copy4("Budget remaining — not your total money (Current balance KSh $a).", "Kilichobaki — si pesa zako zote (Salio KSh $a).", "Budget remaining — si total doh yako (Balance KSh $a).", "≠ total ($a).").pick(lang)
    "fare_covers" -> Copy4("Parent fare ($a KSh $b ≈ KSh $c/mo) covers this envelope first.", "Nauli ya mzazi ($a KSh $b ≈ KSh $c/mwezi) hufunika bahasha hii kwanza.", "Parent fare ($a KSh $b ≈ KSh $c/mo) hucover hii envelope first.", "Fare covers first.").pick(lang)
    "shared_line" -> Copy4("Shared: $a · KSh $b each", "Imeshirikiwa: $a · KSh $b kila mmoja", "Shared: $a · KSh $b each", "Shared $b each.").pick(lang)
    "remove" -> Copy4("Remove", "Ondoa", "Ondoa", "Remove").pick(lang)
    "edit" -> Copy4("Edit", "Hariri", "Edit", "Edit").pick(lang)
    "share_btn" -> Copy4("Share", "Shiriki", "Share", "Share").pick(lang)
    "edit_share" -> Copy4("Edit share", "Hariri mgao", "Edit share", "Share").pick(lang)
    "no_cat" -> Copy4("No $a category budgets yet — give every shilling a job.", "Hakuna bajeti za makundi ya $a bado — patia kila shilingi kazi.", "Hakuna category budgets za $a bado — patia kila bob job.", "No $a cats yet.").pick(lang)
    "create_first" -> Copy4("Create First Budget", "Tengeneza Bajeti ya Kwanza", "Create First Budget", "Create.").pick(lang)
    "plans_title" -> Copy4("My Plans 🎯", "Mipango Yangu 🎯", "Maplans Yangu 🎯", "Plans 🎯").pick(lang)
    "add_plan" -> Copy4("+ Add Plan", "+ Weka Mpango", "+ Add Plan", "+ Plan").pick(lang)
    "no_plans" -> Copy4("No plans yet. A plan is a goal with a deadline — e.g. Laptop by December.", "Hakuna mipango bado. Mpango ni lengo lenye tarehe — k.m. Laptop ifikapo Disemba.", "Hakuna plans bado. Plan ni goal na deadline — e.g. Laptop by December.", "Plans = goals + dates.").pick(lang)
    "goal_line" -> Copy4("KSh $a of KSh $b · KSh $c to go · KSh $d/day for $e days", "KSh $a kati ya KSh $b · KSh $c zimebaki · KSh $d/siku kwa siku $e", "KSh $a of KSh $b · KSh $c to go · KSh $d/day for siku $e", "$a/$b · $d/day.").pick(lang)
    "goal_done" -> Copy4("KSh $a of KSh $b · complete! 🎉", "KSh $a kati ya KSh $b · imekamilika! 🎉", "KSh $a of KSh $b · complete! 🎉", "Done! 🎉").pick(lang)
    "fills_from" -> Copy4("Fills from what you don't spend 💧", "Hujazwa na usichotumia 💧", "Hujazwa na uspendi 💧", "Skimmed 💧").pick(lang)
    "new_budget" -> Copy4("New Budget", "Bajeti Mpya", "Budget Mpya", "New").pick(lang)
    "cat_label" -> Copy4("Category (ALL = everything)", "Kundi (ALL = vyote)", "Category (ALL = everything)", "Category").pick(lang)
    "suggested" -> Copy4("Suggested: KSh $a (your 3-month average) — tap to use", "Inapendekezwa: KSh $a (wastani wako wa miezi 3) — gusa kutumia", "Suggested: KSh $a (3-month average yako) — tap kutumia", "Use $a?").pick(lang)
    "limit_label" -> Copy4("Limit (KSh)", "Kikomo (KSh)", "Limit (KSh)", "Limit").pick(lang)
    "share_with" -> Copy4("Share with (names, comma separated)", "Shiriki na (majina, yametenganishwa na koma)", "Share na (majina, comma separated)", "Share with").pick(lang)
    "save" -> Copy4("Save", "Hifadhi", "Save", "Save").pick(lang)
    "cancel" -> Copy4("Cancel", "Ghairi", "Cancel", "Cancel").pick(lang)
    "new_plan" -> Copy4("New Plan", "Mpango Mpya", "Plan Mpya", "New").pick(lang)
    "plan_what" -> Copy4("What do you want to accomplish?", "Unataka kufanikisha nini?", "Unataka kuaccomplish nini?", "Goal?").pick(lang)
    "plan_target" -> Copy4("Target (KSh)", "Lengo (KSh)", "Target (KSh)", "Target").pick(lang)
    "plan_days" -> Copy4("Days from now", "Siku kuanzia sasa", "Days from sai", "Days").pick(lang)
    "save_plan" -> Copy4("Save Plan", "Hifadhi Mpango", "Save Plan", "Save").pick(lang)
    "edit_title" -> Copy4("Edit \"$a\" budget", "Hariri bajeti ya \"$a\"", "Edit budget ya \"$a\"", "Edit $a").pick(lang)
    "share_title" -> Copy4("Share \"$a\" budget", "Shiriki bajeti ya \"$a\"", "Share budget ya \"$a\"", "Share $a").pick(lang)
    "share_eg" -> Copy4("e.g. Brian, Faith", "k.m. Brian, Faith", "e.g. Brian, Faith", "Names").pick(lang)
    "edit_sub" -> Copy4("New monthly limit. Saving replaces this envelope — never stacks a twin.", "Kikomo kipya cha mwezi. Kuhifadhi huchukua bahasha hii — haiongezi wingu.", "New monthly limit. Saving huchukua hii envelope — never stack twin.", "Replaces, never stacks.").pick(lang)
    "share_sub" -> Copy4("Household names, comma separated. The limit splits evenly between you and them.", "Majina ya watu nyumbani, yametenganishwa na koma. Kikomo hugawanyika sawa kati yako nao.", "Household names, comma separated. Limit hugawanyika evenly kati yako na wao.", "Names split the limit.").pick(lang)
    "r_bill" -> Copy4("Covers your open $a bill", "Hufunika bili yako wazi ya $a", "Hucover open bill yako ya $a", "Covers $a bill.").pick(lang)
    "r_declared" -> Copy4("Kept at your set $a budget", "Imehifadhiwa kwenye bajeti yako ya $a", "Imekeepwa kwa set budget yako ya $a", "Your $a number.").pick(lang)
    "r_food_home" -> Copy4("Home-fed — small plate money, fares eat first", "Unalishwa nyumbani — pesa kidogo ya sahani, nauli hula kwanza", "Home-fed — small plate money, fares hukula kwanza", "Home-fed.").pick(lang)
    "r_food_nocook" -> Copy4("Every meal bought — protect it fully", "Kila mlo unanunuliwa — ulinde kabisa", "Kila meal inanunuliwa — iprotect fully", "All bought.").pick(lang)
    "r_food_tight" -> Copy4("Protected first — eating comes before everything", "Imelindwa kwanza — kula kunatangulia kila kitu", "Imeprotectiwa kwanza — eating inakuja before everything", "Food first.").pick(lang)
    "r_food" -> Copy4("Survival tier — funded first", "Kiwango cha kuishi — hufadhiliwa kwanza", "Survival tier — funded first", "Funded first.").pick(lang)
    "r_rent" -> Copy4("Roof over your head — non-negotiable", "Paa juu ya kichwa chako — haijabadilika", "Roof juu yako — non-negotiable", "Non-negotiable.").pick(lang)
    "r_home" -> Copy4("Chip in at home — keeps the roof happy", "Changia nyumbani — huifanya paa ifurahi", "Chip in home — huweka roof happy", "Chip in.").pick(lang)
    "r_transport_essential" -> Copy4("Non-negotiable commute — fares first", "Safari isiyobadilika — nauli kwanza", "Non-negotiable commute — fares first", "Commute first.").pick(lang)
    "r_transport" -> Copy4("Small fare buffer — remove it if you truly walk everywhere", "Akiba ndogo ya nauli — iondoe ikiwa kweli unatembea kila mahali", "Small fare buffer — iondoe if truly unatembea everywhere", "Buffer.").pick(lang)
    "r_savings_tight" -> Copy4("Paused while money is tight — resume when base grows", "Imesimama pesa ikiwa kidogo — endelea msingi ukikua", "Imepause doh ikiwa tight — resume base ikigrow", "Paused.").pick(lang)
    "r_tier5_tight" -> Copy4("Cut in tight mode — add back when base grows", "Imekatwa hali ikiwa ngumu — rudisha msingi ukikua", "Imekatwa in tight mode — rudisha base ikigrow", "Cut.").pick(lang)
    "r_avg" -> Copy4("Under your 3-month avg (KSh $a) — stretch goal", "Chini ya wastani wako wa miezi 3 (KSh $a) — lengo la kujinyoosha", "Chini ya 3-month avg yako (KSh $a) — stretch goal", "Below avg $a.").pick(lang)
    "r_tier" -> Copy4("Tier $a · $b% weight", "Kiwango $a · uzito $b%", "Tier $a · $b% weight", "T$a $b%.").pick(lang)
    "r_reduced" -> Copy4("Reduced to fit current funds", "Imepunguzwa kutoshea pesa za sasa", "Imereduce kufit current funds", "Reduced.").pick(lang)
    "sum_tight" -> Copy4("Tight mode: KSh $a/mo funds survival first for $b. Savings + lifestyle paused — KSh $c $d planned.", "Hali ngumu: KSh $a/mwezi hufadhili kuishi kwanza kwa $b. Akiba + mtindo umesimama — KSh $c $d imepangwa.", "Tight mode: KSh $a/mo hufund survival first kwa $b. Savings + lifestyle paused — KSh $c $d planned.", "Tight: $c $d.").pick(lang)
    "sum_norm" -> Copy4("KSh $a/mo → KSh $b $c across $d envelopes ($e).", "KSh $a/mwezi → KSh $b $c katika bahasha $d ($e).", "KSh $a/mo → KSh $b $c across envelopes $d ($e).", "$b $c · $d.").pick(lang)
    else -> a.ifBlank { key }
}

fun budPersona(key: String, lang: AppLanguage, blurb: Boolean = false): String = when (key) {
    "PARENTS_FAR" -> if (blurb) Copy4("Parents' roof, long matatu daily", "Paa la wazazi, matatu ndefu kila siku", "Roof ya parents, long matatu daily", "Parents, far").pick(lang) else Copy4("Home · far commute", "Nyumbani · safari ndefu", "Home · far commute", "Home far").pick(lang)
    "PARENTS_NEAR" -> if (blurb) Copy4("Parents' roof, walk or short hop", "Paa la wazazi, tembea au safari fupi", "Roof ya parents, walk ama short hop", "Parents, near").pick(lang) else Copy4("Home · short hop", "Nyumbani · safari fupi", "Home · short hop", "Home near").pick(lang)
    "RENT_WALK" -> if (blurb) Copy4("Own place, campus on foot", "Kwako, chuo kwa miguu", "Keja yako, chuo on foot", "Rent, walk").pick(lang) else Copy4("Rented · walk", "Umekodi · tembea", "Rented · walk", "Rent walk").pick(lang)
    "RENT_COMMUTE" -> if (blurb) Copy4("Own place plus daily fares", "Kwako pamoja na nauli za kila siku", "Keja yako plus daily fares", "Rent, commute").pick(lang) else Copy4("Rented · commute", "Umekodi · safiri", "Rented · commute", "Rent commute").pick(lang)
    "HOSTEL_NOCOOK" -> if (blurb) Copy4("Hostel bed, every meal bought", "Kitanda cha hosteli, kila mlo unanunuliwa", "Bed ya hostel, kila meal inanunuliwa", "Hostel, buy").pick(lang) else Copy4("Hostel · buy food", "Hosteli · nunua chakula", "Hostel · buy food", "Hostel buy").pick(lang)
    "HOSTEL_COOK" -> if (blurb) Copy4("Hostel bed, groceries + cooker", "Kitanda cha hosteli, mboga + jiko", "Bed ya hostel, groceries + cooker", "Hostel, cook").pick(lang) else Copy4("Hostel · cook", "Hosteli · pika", "Hostel · cook", "Hostel cook").pick(lang)
    else -> key
}

fun budLifestyle(key: String, lang: AppLanguage): String = when (key) {
    "COMMUTER_LITE" -> Copy4("Commuter lite", "Msafiri mwepesi", "Commuter lite", "Lite").pick(lang)
    "FOODIE" -> Copy4("Foodie", "Mpenzi wa chakula", "Foodie", "Foodie").pick(lang)
    "SAVER" -> Copy4("Saver", "Mweka akiba", "Saver", "Saver").pick(lang)
    else -> Copy4("Balanced", "Sawazisho", "Balanced", "Balanced").pick(lang)
}

/** Weekday abbreviations follow the app language; keys stay English. */
fun weekdayShort(day: String, lang: AppLanguage): String {
    if (lang != AppLanguage.KISWAHILI) return day
    return when (day) {
        "Mon" -> "JTT"
        "Tue" -> "JNN"
        "Wed" -> "JTN"
        "Thu" -> "ALH"
        "Fri" -> "IJM"
        "Sat" -> "JMS"
        else -> "JMP"
    }
}

// ---------- Bottom tabs + More menu (whole-app language starts here) ----------

fun tabLabel(tab: String, lang: AppLanguage): String = when (tab) {
    "Plan" -> Copy4("Plan", "Mpango", "Plan", "Plan").pick(lang)
    "Money" -> Copy4("Money", "Pesa", "Doh", "Doh").pick(lang)
    "Insight" -> Copy4("Insight", "Ufahamu", "Rada", "Rada").pick(lang)
    "You" -> Copy4("You", "Yangu", "Mimi", "You").pick(lang)
    else -> Copy4("Home", "Nyumbani", "Home", "Home").pick(lang)
}

fun moreSectionName(section: String, lang: AppLanguage): String = when (section) {
    "Planning" -> Copy4("Planning", "Mipango", "Maplan", "Planning").pick(lang)
    "Student life" -> Copy4("Student life", "Maisha ya chuo", "Life ya chuo", "Campus life").pick(lang)
    "Tools" -> Copy4("Tools", "Zana", "Matools", "Tools").pick(lang)
    "App" -> Copy4("App", "App", "App", "App").pick(lang)
    else -> Copy4("Money", "Pesa", "Doh", "Money").pick(lang)
}

/** More-menu entry title + subtitle by route. Unknown routes pass through. */
fun moreEntryTitle(route: String, lang: AppLanguage): String = when (route) {
    "analytics" -> Copy4("Analytics", "Takwimu", "Stats", "Analytics").pick(lang)
    "reports" -> Copy4("Reports", "Ripoti", "Repoti", "Repoti").pick(lang)
    "networth" -> Copy4("Net Worth", "Thamani Halisi", "Net Worth", "Net Worth").pick(lang)
    "income" -> Copy4("Income", "Mapato", "Income", "Income").pick(lang)
    "search" -> Copy4("Search", "Tafuta", "Search", "Search").pick(lang)
    "bills" -> Copy4("Bills", "Bili", "Mabill", "Bills").pick(lang)
    "debt" -> Copy4("Debt Tracking", "Madeni", "Madeni", "Madeni").pick(lang)
    "savings" -> Copy4("Savings", "Akiba", "Savings", "Savings").pick(lang)
    "goals" -> Copy4("Goal Planner", "Malengo", "Maplans", "Goals").pick(lang)
    "recurring" -> Copy4("Recurring", "Yanayojirudia", "Recurring", "Recurring").pick(lang)
    "semester" -> Copy4("Semester", "Muhula", "Sem", "Semester").pick(lang)
    "meals" -> Copy4("Meal Planner", "Mipango ya Milo", "Meal Planner", "Meals").pick(lang)
    "kitchen" -> Copy4("Kitchen Stock", "Stock ya Jikoni", "Stock ya Keja", "Kitchen").pick(lang)
    "things" -> Copy4("My Things", "Vitu Vyangu", "Vitu Zangu", "Things").pick(lang)
    "university" -> Copy4("University", "Chuo", "Chuo", "Chuo").pick(lang)
    "export" -> Copy4("Export & Backup", "Hamisha & Hifadhi", "Export & Backup", "Export").pick(lang)
    "contacts" -> Copy4("Contact Book", "Kitabu cha Majina", "Contacts", "Contacts").pick(lang)
    "review" -> Copy4("Weekly review", "Mapitio ya Wiki", "Review ya Wiki", "Review").pick(lang)
    "info" -> Copy4("App Guide & Formulas", "Mwongozo wa App", "App Guide", "Guide").pick(lang)
    "pesa" -> Copy4("PesaBuddy", "PesaBuddy", "PesaBuddy", "PesaBuddy").pick(lang)
    "notifications" -> Copy4("Notifications", "Arifa", "Notifications", "Alerts").pick(lang)
    "settings" -> Copy4("Settings", "Mipangilio", "Settings", "Settings").pick(lang)
    else -> route
}

fun moreEntrySubtitle(route: String, lang: AppLanguage): String = when (route) {
    "analytics" -> Copy4("Category trends, comparisons and heatmaps", "Mienendo ya makundi, ulinganisho na ramani", "Trends za categories, comparisons na heatmaps", "Trends, comparisons, heatmaps").pick(lang)
    "reports" -> Copy4("Daily, weekly, monthly and annual summaries", "Muhtasari wa kila siku, wiki, mwezi na mwaka", "Summaries za daily, weekly, monthly na yearly", "Daily → yearly summaries").pick(lang)
    "networth" -> Copy4("Cash, savings, investments and debts", "Taslimu, akiba, uwekezaji na madeni", "Cash, savings, investments na madeni", "Cash, savings, debts").pick(lang)
    "income" -> Copy4("Track money sources and expected payments", "Fuatilia vyanzo vya pesa na malipo yanayotarajiwa", "Track sources za doh na payments zinazokuja", "Income sources + expected").pick(lang)
    "search" -> Copy4("Find a transaction by merchant or category", "Tafuta muamala kwa muuzaji au kundi", "Tafuta transaction kwa merchant ama category", "Find by merchant/category").pick(lang)
    "bills" -> Copy4("Due dates, recurring bills and payer details", "Tarehe za malipo, bili zinazojirudia na mlipaji", "Due dates, mabill zinajirudia na payer", "Due dates + recurring").pick(lang)
    "debt" -> Copy4("Money owed, borrowed and repaid", "Pesa unazodaiwa, ulizokopa na ulizolipa", "Doh unadaiwa, umekopa na umelipa", "Owed, borrowed, repaid").pick(lang)
    "savings" -> Copy4("Savings goals and progress", "Malengo ya akiba na maendeleo", "Savings goals na progress", "Goals + progress").pick(lang)
    "goals" -> Copy4("Goal pace, risk and suggestions", "Kasi ya malengo, hatari na mapendekezo", "Goal pace, risk na suggestions", "Pace, risk, tips").pick(lang)
    "recurring" -> Copy4("Spending patterns and monthly commitments", "Mifumo ya matumizi na ahadi za mwezi", "Spending patterns na commitments za mwezi", "Patterns + commitments").pick(lang)
    "semester" -> Copy4("Term plan, runway and fees", "Mpango wa muhula, kipimo na karo", "Plan ya sem, runway na fees", "Plan, runway, fees").pick(lang)
    "meals" -> Copy4("Plan meals with your food budget", "Panga milo kwa bajeti yako ya chakula", "Plan meals na food budget yako", "Meals + food budget").pick(lang)
    "kitchen" -> Copy4("Track staple quantities and refill needs", "Fuatilia kiasi cha vyakula na uhitaji wa kujaza", "Track unga na vitu zimebaki kwa keja", "Staples + refills").pick(lang)
    "things" -> Copy4("Track what you own and still need", "Fuatilia ulivyo navyo na unavyohitaji", "Track uko navyo na unahitaji", "Own vs need").pick(lang)
    "university" -> Copy4("Campus, timetable and allowance planning", "Chuo, ratiba na upangaji wa posho", "Chuo, timetable na allowance planning", "Campus + timetable").pick(lang)
    "export" -> Copy4("Export transactions or back up your data", "Hamisha miamala au hifadhi data yako", "Export transactions ama backup data yako", "Export / backup").pick(lang)
    "contacts" -> Copy4("Remember people and categorize transactions", "Wakumbuke watu na kupanga miamala", "Wakumbuke watu na kuf file transactions", "People + categories").pick(lang)
    "review" -> Copy4("Review uncategorized items and duplicates", "Pitia visivyopangwa na vinavyojirudia", "Review uncategorized na duplicates", "Uncategorized + dupes").pick(lang)
    "info" -> Copy4("Learn how PesaFlow calculates your finances", "Jifunze jinsi PesaFlow inavyohesabu pesa zako", "Jifunze vile PesaFlow huhesabu doh zako", "How calculations work").pick(lang)
    "pesa" -> Copy4("Ask questions about your saved money data", "Uliza maswali kuhusu data yako ya pesa", "Uliza maswali kuhusu saved doh yako", "Ask about your money").pick(lang)
    "notifications" -> Copy4("Configure reminders and financial alerts", "Sanidi vikumbusho na arifa za kifedha", "Sanidi reminders na financial alerts", "Reminders + alerts").pick(lang)
    "settings" -> Copy4("Language, appearance, notifications and data", "Lugha, muonekano, arifa na data", "Language, appearance, notifications na data", "Language + data").pick(lang)
    else -> ""
}

fun moreChrome(key: String, lang: AppLanguage): String = when (key) {
    "title" -> Copy4("More features", "Vipengele Zaidi", "Features Zingine", "More").pick(lang)
    "subtitle" -> Copy4("Find student tools and account settings", "Tafuta zana za mwanafunzi na mipangilio", "Tafuta student tools na settings", "Tools + settings").pick(lang)
    "search" -> Copy4("Search features", "Tafuta vipengele", "Search features", "Search").pick(lang)
    "clear" -> Copy4("Clear", "Futa", "Clear", "Clear").pick(lang)
    "empty_title" -> Copy4("No features found", "Hakuna vipengele vilivyopatikana", "Hakuna features zimepatikana", "Nothing found").pick(lang)
    else -> Copy4("Try another search, or clear the search to browse all tools.", "Jaribu utafutaji mwingine, au futa utafutaji uone zana zote.", "Try search ingine, ama clear usee tools zote.", "Try another search.").pick(lang)
}
