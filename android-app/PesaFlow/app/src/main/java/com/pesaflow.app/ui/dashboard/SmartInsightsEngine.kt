package com.pesaflow.app.ui.dashboard

import com.pesaflow.app.data.academic.ExamSignal
import com.pesaflow.app.data.academic.FreeDayDividend
import com.pesaflow.app.data.academic.RoutineChange
import com.pesaflow.app.data.academic.ScanWindow
import com.pesaflow.app.data.academic.SpendProfile
import com.pesaflow.app.data.models.AppLanguage
import com.pesaflow.app.data.models.Bill
import com.pesaflow.app.data.models.Budget
import com.pesaflow.app.data.models.Debt
import com.pesaflow.app.data.models.SavingsGoal
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.money.NON_STAT_SOURCES
import com.pesaflow.app.ui.analytics.detectRecurring
import com.pesaflow.app.ui.analytics.predictPaydays
import com.pesaflow.app.ui.budgets.LivingSituation
import java.util.Calendar

// Semester-on-semester: current profile vs the prior academic year's rows.
// Silent when there is no prior year on file (first-years, fresh scans).
// At most three lines: pace, fares, school days.
internal fun semesterDiffLines(cur: SpendProfile, prev: SpendProfile, lang: AppLanguage): List<String> {
    fun t(en: String, sh: String, sw: String, mix: String): String =
        when (lang) {
            AppLanguage.SHENG -> sh
            AppLanguage.KISWAHILI -> sw
            AppLanguage.MIXED -> mix
            else -> en
        }
    if (prev.activeDays == 0) return emptyList()
    val out = mutableListOf<String>()
    if (prev.dailyAvg > 0) {
        val pct = ((cur.dailyAvg - prev.dailyAvg) / prev.dailyAvg * 100).toInt()
        if (pct != 0) {
            val dir = if (pct > 0) "+$pct%" else "$pct%"
            out.add(t(
                "vs last year Sep–Apr: daily pace $dir (KSh ${cur.dailyAvg.toInt()} vs ${prev.dailyAvg.toInt()}).",
                "vs last year: pace $dir (KSh ${cur.dailyAvg.toInt()} vs ${prev.dailyAvg.toInt()}).",
                "Uklingalisha na mwaka uliopita: mwendo $dir (KSh ${cur.dailyAvg.toInt()} vs ${prev.dailyAvg.toInt()}).",
                "vs last year Sep–Apr: pace $dir (KSh ${cur.dailyAvg.toInt()} vs ${prev.dailyAvg.toInt()})."
            ))
        }
    }
    if (prev.fareDailyAvg > 0 && cur.fareDailyAvg > 0) {
        val fd = cur.fareDailyAvg - prev.fareDailyAvg
        if (kotlin.math.abs(fd) >= prev.fareDailyAvg * 0.1) {
            out.add(t(
                "Fares KSh ${cur.fareDailyAvg.toInt()}/day vs ${prev.fareDailyAvg.toInt()} last year.",
                "Fare KSh ${cur.fareDailyAvg.toInt()}/day vs ${prev.fareDailyAvg.toInt()} last year.",
                "Nauli KSh ${cur.fareDailyAvg.toInt()}/siku vs ${prev.fareDailyAvg.toInt()} mwaka uliopita.",
                "Fares KSh ${cur.fareDailyAvg.toInt()}/day vs ${prev.fareDailyAvg.toInt()} last year."
            ))
        }
    }
    val dd = cur.schoolDays.size - prev.schoolDays.size
    if (dd != 0) {
        val word = if (kotlin.math.abs(dd) == 1) "day" else "days"
        val dir = if (dd > 0) "$dd more school $word" else "${-dd} fewer school $word"
        out.add(t(
            "$dir (${cur.schoolDays.size} vs ${prev.schoolDays.size} last year).",
            "$dir (${cur.schoolDays.size} vs ${prev.schoolDays.size} last year).",
            "$dir (siku ${cur.schoolDays.size} vs ${prev.schoolDays.size} mwaka uliopita).",
            "$dir (${cur.schoolDays.size} vs ${prev.schoolDays.size} last year)."
        ))
    }
    return out.take(3)
}

// New-term nudge for pre-class-day onboardings: one line, own language,
// pointing at Profile — the cheapest schedule capture there is.
internal fun semesterResetText(lang: AppLanguage): String =
    when (lang) {
        AppLanguage.SHENG -> "New term, no class days on file — set them in Profile in 5 seconds? 🗓️"
        AppLanguage.KISWAHILI -> "Muhula mpya, siku za masomo hazipo — ziweke Profaili? 🗓️"
        AppLanguage.MIXED -> "New term, no class days on file — set them in Profile? 🗓️"
        else -> "New term, no class days on file — set them in Profile in 5 seconds? 🗓️"
    }

// Month-end forecast: pace + open bills vs money actually held. Names the
// broke date when one exists — the single most forward-looking line here.
data class MonthForecast(val projectedTotal: Double, val brokeDay: Int?, val daysLeft: Int)

fun monthEndForecast(
    monthSpent: Double,
    dailyPace: Double,
    balance: Double,
    openBills: Double,
    nowMs: Long = System.currentTimeMillis()
): MonthForecast {
    val c = Calendar.getInstance().apply { timeInMillis = nowMs }
    val dim = c.getActualMaximum(Calendar.DAY_OF_MONTH)
    val today = c.get(Calendar.DAY_OF_MONTH)
    val daysLeft = (dim - today).coerceAtLeast(0)
    val projected = monthSpent + dailyPace * daysLeft + openBills
    var broke: Int? = null
    if (balance > 0 && dailyPace > 0) {
        for (d in today..dim) {
            if (monthSpent + dailyPace * (d - today) + openBills >= balance) {
                broke = d
                break
            }
        }
    }
    return MonthForecast(projected, broke, daysLeft)
}

internal fun ord(day: Int): String = when {
    day in 11..13 -> "${day}th"
    day % 10 == 1 -> "${day}st"
    day % 10 == 2 -> "${day}nd"
    day % 10 == 3 -> "${day}rd"
    else -> "${day}th"
}

internal fun buildInsights(
    txs: List<Transaction>,

    budgets: List<Budget>,
    lang: AppLanguage,
    name: String,
    bills: List<Bill>,
    debts: List<Debt>,
    goals: List<SavingsGoal>,
    weekPlan: Map<String, Set<String>> = emptyMap(),
    group: LivingSituation = LivingSituation.HOSTEL_COOK,
    balance: Double = 0.0
): List<String> {
    fun t(en: String, sh: String, sw: String, mix: String): String =
        when (lang) {
            AppLanguage.SHENG -> sh
            AppLanguage.KISWAHILI -> sw
            AppLanguage.MIXED -> mix
            else -> en
        }
    val nn = if (name.isNotBlank()) "$name, " else ""
    if (txs.isEmpty()) return listOf(t(
        "Add transactions and I'll spot patterns. 👀",
        "Weka transactions ni-spot patterns. 👀",
        "Weka miamala nianze kuchambua. 👀",
        "Weka transactions ni-spot patterns. 👀"
    ))
    val out = mutableListOf<String>()
    val cal = Calendar.getInstance()
    fun inMonth(ts: Long, offset: Int = 0): Boolean {
        val ref = (cal.clone() as Calendar).apply { add(Calendar.MONTH, offset) }
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        return c.get(Calendar.YEAR) == ref.get(Calendar.YEAR) &&
            c.get(Calendar.MONTH) == ref.get(Calendar.MONTH)
    }
    // One counting rule everywhere (see MoneyMath.monthScopedTotal and the
    // budget worker): seeded opening cash and watermarked sample rows move
    // the balance but never budgets, paces or verdicts — otherwise alerts
    // and progress bars disagree on the same month.
    val monthExp = txs.filter { it.type == TransactionType.EXPENSE && it.source !in NON_STAT_SOURCES && inMonth(it.dateTimestamp) }
    val monthTotal = monthExp.sumOf { it.amount }
    val monthIncome = txs.filter { it.type == TransactionType.INCOME && it.source !in NON_STAT_SOURCES && inMonth(it.dateTimestamp) }.sumOf { it.amount }
    if (monthTotal <= 0) return listOf(t(
        "No spending this month yet.",
        "Hujaspend this month.",
        "Hakuna matumizi mwezi huu.",
        "No spending this month."
    ))

    // Top category share — but "Unknown" is never a category to lecture
    // about. Name the unclassified pile and ask for help instead.
    monthExp.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }.maxByOrNull { it.value }?.let {
        val amt = "KSh ${it.value.toInt()}"
        val pct = (it.value / monthTotal * 100).toInt()
        if (it.key.equals("Unknown", ignoreCase = true)) {
            out.add(t(
                "$amt ($pct%) is unclassified — tap it on Home and tell me what it was. 👀",
                "$amt ($pct%) haija-classify — i-tap Home uniambie ilikuwa nini. 👀",
                "$amt ($pct%) haijaainishwa — iguse Home uniambie ilikuwa nini. 👀",
                "$amt ($pct%) unclassified — tap Home uniambie ilikuwa nini. 👀"
            ))
        } else {
            out.add(t(
                "Most spending: ${it.key} — $amt ($pct%).",
                "Mullah mingi: ${it.key} — $amt ($pct%).",
                "Matumizi makubwa: ${it.key} — $amt ($pct%).",
                "Spending kubwa: ${it.key} — $amt ($pct%)."
            ))
        }
    }

    // Payday-spend timing: fast burn, slow stretch, or even spread.
    paydaySpend(txs)?.let { ps ->
        val fast = ps.pctWithin3 >= 60
        val slow = ps.medianLagDays >= 12
        out.add(when {
            fast -> t(
                "Payday burns fast 🔥 ~${ps.pctWithin3.toInt()}% of spending lands within 3 days of money-in. Move savings first, spend what remains.",
                "Payday ina-burn fast 🔥 ~${ps.pctWithin3.toInt()}% ya spending inaenda within 3 days ya money-in. Toa savings kwanza.",
                "Malipo yanaungua haraka 🔥 ~asilimia ${ps.pctWithin3.toInt()} ya matumizi hutumika ndani ya siku 3. Weka akiba kwanza.",
                "Payday burn fast 🔥 ~${ps.pctWithin3.toInt()}% spent within 3 days of money-in. Save first."
            )
            slow -> t(
                "You stretch payday 🐢 median spend lands ${ps.medianLagDays.toInt()} days after money-in. Whatever you're doing — keep it.",
                "Una-stretch payday 🐢 median spend ni siku ${ps.medianLagDays.toInt()} after money-in. Endelea hivyo hivyo.",
                "Unavuta malipo 🐢 wastani wa matumizi ni siku ${ps.medianLagDays.toInt()} baada ya kupokea. Endelea.",
                "Payday stretches 🐢 median ${ps.medianLagDays.toInt()} days after money-in. Keep it up."
            )
            else -> t(
                "Payday spreads evenly (~${ps.pctWithin3.toInt()}% in the first 3 days, median ${ps.medianLagDays.toInt()} days). No burn pattern — steady. 🙂",
                "Payday ina-spread evenly (~${ps.pctWithin3.toInt()}% in 3 days, median siku ${ps.medianLagDays.toInt()}). Steady. 🙂",
                "Malipo yanasambaa sawasawa (~asilimia ${ps.pctWithin3.toInt()} siku 3 za kwanza, wastani siku ${ps.medianLagDays.toInt()}). Nzuri. 🙂",
                "Payday spreads evenly (~${ps.pctWithin3.toInt()}% in 3 days, median ${ps.medianLagDays.toInt()} days). Steady. 🙂"
            )
        })
    }

    // Weekend vs weekday pace
    var weekendSum = 0.0
    var weekdaySum = 0.0
    monthExp.forEach { tx ->
        val d = Calendar.getInstance().apply { timeInMillis = tx.dateTimestamp }.get(Calendar.DAY_OF_WEEK)
        if (d == Calendar.SATURDAY || d == Calendar.SUNDAY) weekendSum += tx.amount else weekdaySum += tx.amount
    }
    var weekendDays = 0
    var weekdayDays = 0
    val cursor = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    while (!cursor.after(cal)) {
        val d = cursor.get(Calendar.DAY_OF_WEEK)
        if (d == Calendar.SATURDAY || d == Calendar.SUNDAY) weekendDays++ else weekdayDays++
        cursor.add(Calendar.DAY_OF_MONTH, 1)
    }
    if (weekendDays > 0 && weekdayDays > 0) {
        val weAvg = weekendSum / weekendDays
        val wdAvg = weekdaySum / weekdayDays
        if (weAvg > wdAvg * 1.2) out.add(t(
            "${nn}weekends cost more: KSh ${weAvg.toInt()}/day vs KSh ${wdAvg.toInt()} weekdays.",
            "${nn}weekend inaburn: KSh ${weAvg.toInt()}/day vs KSh ${wdAvg.toInt()} weekdays.",
            "${nn}wikendi inagharimu: KSh ${weAvg.toInt()}/siku vs KSh ${wdAvg.toInt()} siku za kazi.",
            "${nn}weekend pricey: KSh ${weAvg.toInt()}/day vs KSh ${wdAvg.toInt()} weekdays."
        ))
    }

    // Month-over-month change
    val lastTotal = txs.filter { it.type == TransactionType.EXPENSE && it.source !in NON_STAT_SOURCES && inMonth(it.dateTimestamp, -1) }.sumOf { it.amount }
    if (lastTotal > 0) {
        val change = ((monthTotal - lastTotal) / lastTotal * 100).toInt()
        val top = monthExp.groupBy { it.category }.maxByOrNull { e -> e.value.sumOf { it.amount } }?.key ?: "spending"
        out.add(
            if (change > 0) t(
                "Up $change% vs last month. Watch $top. 📈",
                "Ime Panda $change% vs last month. Watch $top. 📈",
                "Juu $change% kuliko mwezi uliopita. Angalia $top. 📈",
                "Up $change% vs last month. Watch $top. 📈"
            ) else t(
                "Down ${-change}% vs last month. Good job! 📉",
                "Imeshuka ${-change}%. Poa sana! 📉",
                "Chini ${-change}%. Kazi nzuri! 📉",
                "Down ${-change}%. Poa! 📉"
            )
        )
    }

    // Income vs spending verdict + savings rate
    val topName = monthExp.groupBy { it.category }.maxByOrNull { e -> e.value.sumOf { it.amount } }?.key ?: "top categories"
    if (monthIncome > 0) {
        val diff = monthIncome - monthTotal
        if (diff < 0) {
            out.add(t(
                "${nn}Danger: KSh ${-diff.toInt()} more spent than earned. Cut $topName first. ⚠️",
                "${nn}uko red: KSh ${-diff.toInt()} zaidi. Kata $topName. ⚠️",
                "${nn}hatari: KSh ${-diff.toInt()} zaidi. Punguza $topName. ⚠️",
                "${nn}danger: KSh ${-diff.toInt()} over income. Cut $topName. ⚠️"
            ))
        } else {
            val rate = (diff / monthIncome * 100).toInt()
            if (rate >= 20) out.add(t(
                "Great: you saved $rate% (KSh ${diff.toInt()}). 💪",
                "Poa: umesave $rate% (KSh ${diff.toInt()}). 💪",
                "Vizuri: umeweka $rate% (KSh ${diff.toInt()}). 💪",
                "Poa: saved $rate% (KSh ${diff.toInt()}). 💪"
            ))
            else out.add(t(
                "Only $rate% saved (KSh ${diff.toInt()}). Target 20% — cut $topName.",
                "Ish $rate% tu (KSh ${diff.toInt()}). Target 20% — kata $topName.",
                "$rate% tu (KSh ${diff.toInt()}). Lenga 20% — punguza $topName.",
                "Only $rate% saved (KSh ${diff.toInt()}). Target 20% — cut $topName."
            ))
        }
    } else if (monthTotal > 0) {
        out.add(t(
            "Add income (+ Income) to compare in vs out.",
            "Weka income (+ Income) tu-compare.",
            "Weka kipato (+ Income) kulinganisha.",
            "Add income (+ Income) to compare."
        ))
    }

    // Budget pace check
    val dom = cal.get(Calendar.DAY_OF_MONTH)
    val dim = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val paceBudget = budgets.firstOrNull { it.category == "ALL" }
    if (paceBudget != null && paceBudget.limitAmount > 0 && monthTotal > 0 && dim > 0) {
        val expected = paceBudget.limitAmount * dom / dim
        if (monthTotal > expected * 1.15) out.add(t(
            "${nn}too fast: KSh ${monthTotal.toInt()} spent, KSh ${expected.toInt()} expected. Slow down. 🐢",
            "${nn}haraka sana: KSh ${monthTotal.toInt()} vs KSh ${expected.toInt()} expected. Tulia. 🐢",
            "${nn}haraka sana: KSh ${monthTotal.toInt()} badala ya KSh ${expected.toInt()}. Punguza. 🐢",
            "${nn}too fast: KSh ${monthTotal.toInt()} vs KSh ${expected.toInt()}. Slow down. 🐢"
        ))
        else if (monthTotal < expected * 0.7) out.add(t(
            "Good pace: KSh ${monthTotal.toInt()} vs KSh ${expected.toInt()} expected. Save the extra. 🐖",
            "Pace poa: KSh ${monthTotal.toInt()} vs KSh ${expected.toInt()}. Save extra. 🐖",
            "Mwendo mzuri: KSh ${monthTotal.toInt()} vs KSh ${expected.toInt()}. Weka ziada. 🐖",
            "Good pace: KSh ${monthTotal.toInt()} vs KSh ${expected.toInt()}. Save extra. 🐖"
        ))
    }

    // Budget watch
    val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val global = budgets.firstOrNull { it.category == "ALL" }
    if (global != null && global.limitAmount > 0) {
        val pct = (monthTotal / global.limitAmount * 100).toInt()
        if (pct >= 100) out.add(t(
            "${nn}budget finished (KSh ${global.limitAmount.toInt()}). Essentials only. ⚠️",
            "${nn}budget imeisha (KSh ${global.limitAmount.toInt()}). Essentials tu. ⚠️",
            "${nn}bajeti imekwisha (KSh ${global.limitAmount.toInt()}). Muhimu tu. ⚠️",
            "${nn}budget done (KSh ${global.limitAmount.toInt()}). Essentials only. ⚠️"
        ))
        else if (pct >= 80) out.add(t(
            "Budget $pct% used, ${daysInMonth - dayOfMonth + 1} days left.",
            "Budget $pct% used, siku ${daysInMonth - dayOfMonth + 1} zimebaki.",
            "Bajeti $pct% imetumika, siku ${daysInMonth - dayOfMonth + 1} zimebaki.",
            "Budget $pct% used, ${daysInMonth - dayOfMonth + 1} days left."
        ))
        else if (dayOfMonth > 1) {
            val projected = (monthTotal / dayOfMonth * daysInMonth).toInt()
            val inside = projected <= global.limitAmount
            out.add(t(
                "${nn}may end near KSh $projected (${if (inside) "inside" else "above"} budget). Just a guess. 🔮",
                "${nn}uta-end near KSh $projected (${if (inside) "inside" else "above"} budget). Guess tu. 🔮",
                "${nn}huenda ukafikia KSh $projected. Kadirio tu. 🔮",
                "${nn}may end near KSh $projected. Guess tu. 🔮"
            ))
        }
    }

    // Month-end forecast against money actually held: pace + open bills vs
    // balance. Broke date when one exists, reassurance when it doesn't.
    if (balance > 0 && monthTotal > 0 && dayOfMonth >= 1) {
        val pace = monthTotal / dayOfMonth
        val billsTotal = bills.filter { it.status != "PAID" }.sumOf { it.amount }
        val fc = monthEndForecast(monthTotal, pace, balance, billsTotal)
        val broke = fc.brokeDay
        if (broke != null && broke > dayOfMonth) {
            out.add(t(
                "${nn}at this pace you run dry around the ${ord(broke)} (~KSh ${fc.projectedTotal.toInt()} projected vs KSh ${balance.toInt()} at hand). ⚠️",
                "${nn}hii pace uta-dry around ${ord(broke)} (~KSh ${fc.projectedTotal.toInt()} vs KSh ${balance.toInt()} mkononi). ⚠️",
                "${nn}kwa mwendo huu utakauka karibu tarehe $broke (~KSh ${fc.projectedTotal.toInt()} dhidi ya KSh ${balance.toInt()}). ⚠️",
                "${nn}this pace you run dry around ${ord(broke)} (~KSh ${fc.projectedTotal.toInt()} vs KSh ${balance.toInt()}). ⚠️"
            ))
        } else if (broke != null) {
            out.add(t(
                "${nn}already over: KSh ${monthTotal.toInt()} gone + KSh ${billsTotal.toInt()} bills vs KSh ${balance.toInt()} held. Essentials only. 🔴",
                "${nn}tayari over: KSh ${monthTotal.toInt()} gone + KSh ${billsTotal.toInt()} bills vs KSh ${balance.toInt()}. Essentials tu. 🔴",
                "${nn}tayari umezidi: KSh ${monthTotal.toInt()} + bili KSh ${billsTotal.toInt()} dhidi ya KSh ${balance.toInt()}. Muhimu tu. 🔴",
                "${nn}already over: KSh ${monthTotal.toInt()} + KSh ${billsTotal.toInt()} bills vs KSh ${balance.toInt()}. Essentials only. 🔴"
            ))
        } else {
            out.add(t(
                "${nn}pace projects ~KSh ${fc.projectedTotal.toInt()} by month end — inside your KSh ${balance.toInt()}. ✅",
                "${nn}pace ina-project ~KSh ${fc.projectedTotal.toInt()} mwisho wa mwezi — ndani ya KSh ${balance.toInt()}. ✅",
                "${nn}mwendo unaonyesha ~KSh ${fc.projectedTotal.toInt()} mwisho wa mwezi — ndani ya KSh ${balance.toInt()}. ✅",
                "${nn}pace projects ~KSh ${fc.projectedTotal.toInt()} month end — inside KSh ${balance.toInt()}. ✅"
            ))
        }
    }

    // Open bills — nearest due date first, overdue flagged by days.
    val openBills = bills.filter { it.status != "PAID" }
    if (openBills.isNotEmpty()) {
        val nowMsB = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000
        val byDue = openBills.sortedBy { it.dueDate }
        val overdue = byDue.filter { it.dueDate < nowMsB }
        val nearest = byDue.firstOrNull()
        val total = openBills.sumOf { it.amount }.toInt()
        val urgency = when {
            overdue.isNotEmpty() -> {
                val d = ((nowMsB - overdue.minOf { it.dueDate }) / dayMs).toInt()
                " OVERDUE by $d day(s) — lipa sai. 🔴"
            }
            nearest != null && nearest.dueDate - nowMsB < 3 * dayMs -> " Due in ${((nearest.dueDate - nowMsB) / dayMs).toInt()}d: ${nearest.name}. 🟡"
            nearest != null -> " Next: ${nearest.name} KSh ${nearest.amount.toInt()}."
            else -> ""
        }
        out.add(t(
            "${openBills.size} open bill(s): KSh $total.$urgency",
            "Bills ${openBills.size} open: KSh $total.$urgency",
            "Bili ${openBills.size} wazi: KSh $total.$urgency",
            "${openBills.size} open bills: KSh $total.$urgency"
        ))
    }

    // Open debts — split who owes whom, name the smallest you owe.
    val openDebts = debts.filter { it.status != "PAID" }
    if (openDebts.isNotEmpty()) {
        val iOwe = openDebts.filter { it.direction == "I_OWE" }
        val theyOwe = openDebts.filter { it.direction != "I_OWE" }
        val smallest = iOwe.minByOrNull { it.amount }
        val total = openDebts.sumOf { it.amount }.toInt()
        out.add(t(
            "Open debts: KSh $total (you owe KSh ${iOwe.sumOf { it.amount }.toInt()}, owed KSh ${theyOwe.sumOf { it.amount }.toInt()})." +
                (smallest?.let { " Clear ${it.person} KSh ${it.amount.toInt()} first. 🧹" } ?: " Clear the smallest first. 🧹"),
            "Madeni open: KSh $total (unadaiwa KSh ${iOwe.sumOf { it.amount }.toInt()})." +
                (smallest?.let { " Maliza ${it.person} KSh ${it.amount.toInt()} first. 🧹" } ?: " Maliza ndogo first. 🧹"),
            "Madeni wazi: KSh $total." +
                (smallest?.let { " Lipa ${it.person} KSh ${it.amount.toInt()} kwanza. 🧹" } ?: " Lipa ndogo kwanza. 🧹"),
            "Open debts: KSh $total (you owe KSh ${iOwe.sumOf { it.amount }.toInt()})." +
                (smallest?.let { " Clear ${it.person} KSh ${it.amount.toInt()} first. 🧹" } ?: " Clear smallest first. 🧹")
        ))
    }

    // Most urgent goal
    val nowMs = System.currentTimeMillis()
    val urgent = goals
        .map { g -> g to ((g.targetAmount - g.currentAmount).coerceAtLeast(0.0)) }
        .filter { it.second > 0 }
        .minByOrNull { it.first.targetTimestamp }
    if (urgent != null) {
        val days = ((urgent.first.targetTimestamp - nowMs) / (24L * 60 * 60 * 1000)).coerceAtLeast(0)
        val perDay = if (days > 0) urgent.second / days else urgent.second
        if (urgent.first.targetTimestamp < nowMs) {
            val od = ((nowMs - urgent.first.targetTimestamp) / (24L * 60 * 60 * 1000)).toInt()
            out.add(t(
                "${urgent.first.title} OVERDUE by $od day(s) — KSh ${urgent.second.toInt()} still needed. 🔴",
                "${urgent.first.title} ili-pitwa na $od day(s) — KSh ${urgent.second.toInt()} bado. 🔴",
                "${urgent.first.title} imechelewa siku $od — KSh ${urgent.second.toInt()} bado. 🔴",
                "${urgent.first.title} overdue $od days — KSh ${urgent.second.toInt()} remaining. 🔴"
            ))
        }
        out.add(t(
            "${urgent.first.title} needs KSh ${urgent.second.toInt()} in $days day(s) — KSh ${perDay.toInt()}/day.",
            "${urgent.first.title} inahitaji KSh ${urgent.second.toInt()} in $days day(s) — KSh ${perDay.toInt()}/day.",
            "${urgent.first.title} inahitaji KSh ${urgent.second.toInt()} kwa siku $days — KSh ${perDay.toInt()}/siku.",
            "${urgent.first.title} needs KSh ${urgent.second.toInt()} in $days days — KSh ${perDay.toInt()}/day."
        ))
    }

    // Punguza Spending: Category-specific overspending tips
    val categorySpend = monthExp
        .groupBy { it.category }
        .mapValues { e -> e.value.sumOf { it.amount } }
    val topOverspend = categorySpend.entries.maxByOrNull { it.value }
    if (topOverspend != null && monthTotal > 0) {
        val pct = (topOverspend.value / monthTotal * 100).toInt()
        val topCat = topOverspend.key
        // The unclassified pile gets the question above, never a lecture here.
        if (!topCat.equals("Unknown", ignoreCase = true) && pct >= 25) {
            val (tipEn, tipSh, tipSw, tipMix) = getPunguzaTipsForCategory(topCat, group)
            out.add(t(
                "${nn}Punguza spending: $topCat is $pct% of expenses. $tipEn",
                "${nn}Punguza spending: $topCat ni $pct% ya expenses. $tipSh",
                "${nn}Punguza matumizi: $topCat ni $pct% ya matumizi. $tipSw",
                "${nn}Punguza spending: $topCat is $pct% of total. $tipMix"
            ))
        }
    }
    // Per-category envelopes: breach >100%, watch >80%, plus a prorated
    // early-warning tier — ahead of pace but under the static watch line
    // (two loudest only). Dedupe vs the global watch above: when the ALL
    // budget is blown, per-category lines just repeat the same breach.
    run {
        val globalPctNow = global?.takeIf { it.limitAmount > 0 }
            ?.let { (monthTotal / it.limitAmount * 100).toInt() } ?: 0
        if (globalPctNow >= 100) return@run
        val monthByCat = monthExp.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
        budgets.filter { it.limitAmount > 0 && !it.category.equals("ALL", ignoreCase = true) }
            .mapNotNull { b ->
                val spent = monthByCat.entries.firstOrNull { it.key.equals(b.category, ignoreCase = true) }?.value ?: 0.0
                val pct = if (b.limitAmount > 0) (spent / b.limitAmount * 100).toInt() else 0
                if (pct >= 100) "${b.category} blown: KSh ${spent.toInt()} of KSh ${b.limitAmount.toInt()} ($pct%). Kata. ⚠️" to pct
                else if (pct >= 80) "${b.category} at $pct%: KSh ${spent.toInt()} of KSh ${b.limitAmount.toInt()}. Tulia. 🟡" to pct
                else when (com.pesaflow.app.data.money.evaluateCategoryPace(spent, b.limitAmount, dayOfMonth, daysInMonth)) {
                    com.pesaflow.app.data.money.BudgetPace.AT_RISK ->
                        "${b.category} pacing hot: KSh ${spent.toInt()} of KSh ${b.limitAmount.toInt()} ($pct% on day $dayOfMonth). Ease off. 🟡" to pct
                    else -> null
                }
            }
            .sortedByDescending { it.second }.take(2)
            .forEach { (msg, _) -> out.add(t(msg, msg, msg, msg)) }
    }
    if (monthTotal > 0 && out.size == 1) {
        out.add(t(
            "All clear — spending sits inside every line. Keep the rhythm. ✅",
            "Rada safi — spending iko chini ya kila line. Keep it ivo. ✅",
            "Shwari — matumizi yako chini ya kila kikomo. Endelea. ✅",
            "All clear — kila line iko sawa. Keep the rhythm. ✅"
        ))
    }
    // Timetable × ledger: do busy days actually cost more? Your week says
    // what's busy; your money says what it cost. Guiding, not law.
    run {
        val busyNames = weekPlan.filterValues { it.isNotEmpty() }.keys
        if (busyNames.isNotEmpty()) {
            val nameToDow = mapOf("Sun" to 1, "Mon" to 2, "Tue" to 3, "Wed" to 4, "Thu" to 5, "Fri" to 6, "Sat" to 7)
            val busyDows = busyNames.mapNotNull { nameToDow[it] }.toSet()
            if (busyDows.isNotEmpty()) {
                val nowMsH = System.currentTimeMillis()
                val dayMsH = 24L * 60 * 60 * 1000
                fun dayStart(ts: Long): Long {
                    val c = java.util.Calendar.getInstance().apply { timeInMillis = ts }
                    c.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    c.set(java.util.Calendar.MINUTE, 0)
                    c.set(java.util.Calendar.SECOND, 0)
                    c.set(java.util.Calendar.MILLISECOND, 0)
                    return c.timeInMillis
                }
                val busySums = mutableListOf<Double>()
                val freeSums = mutableListOf<Double>()
                (0 until 30).forEach { i ->
                    val ts = nowMsH - i * dayMsH
                    val start = dayStart(ts)
                    val sum = txs.filter {
                        it.type == TransactionType.EXPENSE && it.dateTimestamp >= start && it.dateTimestamp < start + dayMsH
                    }.sumOf { it.amount }
                    val dow = java.util.Calendar.getInstance().apply { timeInMillis = ts }.get(java.util.Calendar.DAY_OF_WEEK)
                    if (dow in busyDows) busySums.add(sum) else freeSums.add(sum)
                }
                if (busySums.size >= 3 && freeSums.size >= 3) {
                    val busyAvg = busySums.average()
                    val freeAvg = freeSums.average()
                    if (freeAvg > 0 && busyAvg > freeAvg * 1.3) {
                        val ratio = String.format(java.util.Locale.US, "%.1f", busyAvg / freeAvg)
                        out.add(t(
                            "Busy days avg KSh ${busyAvg.toInt()} vs free KSh ${freeAvg.toInt()} (×$ratio) — carry lunch those days. Guide, not law 🙂",
                            "Siku busy avg KSh ${busyAvg.toInt()} vs free KSh ${freeAvg.toInt()} (×$ratio) — beba lunch siku hizo. Mwongozo tu 🙂",
                            "Busy days avg KSh ${busyAvg.toInt()} vs free KSh ${freeAvg.toInt()} (×$ratio) — carry lunch hizo days. Guide, not law 🙂",
                            "Busy days ~KSh ${busyAvg.toInt()} vs free KSh ${freeAvg.toInt()} (×$ratio) — carry lunch those days 🙂"
                        ))
                    }
                }
            }
        }
    }
    // Learned rhythms: renewals due + payday landings. Never declared, always observed.
    run {
        val dayR = 24L * 60 * 60 * 1000
        val nowMsR = System.currentTimeMillis()
        val fmt = java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault())
        detectRecurring(txs).filter { it.nextDue() in nowMsR..(nowMsR + 7 * dayR) }.take(2).forEach { s ->
            val whenStr = fmt.format(java.util.Date(s.nextDue()))
            out.add(t(
                "${s.merchant} hits again ~$whenStr: KSh ${s.avgAmount.toInt()} every ~${s.medianGapDays}d. Still using it? 👀",
                "${s.merchant} inarudi ~$whenStr: KSh ${s.avgAmount.toInt()} kila ~siku ${s.medianGapDays}. Bado unaitumia? 👀",
                "${s.merchant} inarudi ~$whenStr: KSh ${s.avgAmount.toInt()}. Bado unaitumia? 👀",
                "${s.merchant} renews ~$whenStr: KSh ${s.avgAmount.toInt()}. Still using it? 👀"
            ))
        }
        predictPaydays(txs).take(2).forEach { (who, amt, at) ->
            val inDays = ((at - nowMsR) / dayR).coerceAtLeast(0)
            val whenStr = fmt.format(java.util.Date(at))
            out.add(t(
                "$who ~KSh ${amt.toInt()} lands ~$whenStr ($inDays d out). Plan it before it lands. 💰",
                "$who ~KSh ${amt.toInt()} inaingia ~$whenStr (siku $inDays). Ipange mapema. 💰",
                "$who ~KSh ${amt.toInt()} inaingia ~$whenStr. Ipange mapema. 💰",
                "$who ~KSh ${amt.toInt()} lands ~$whenStr ($inDays days). Plan it early. 💰"
            ))
        }
    }
    // Living-group nudges: no-rent students should redirect, heavy
    // commuters must envelope fares before a hike wrecks the week.
    if (!group.hasRent && budgets.none { it.category.equals("Savings", ignoreCase = true) && it.limitAmount > 0 }) {
        out.add(t(
            "No rent at home — redirect even KSh 1,000/mo to savings before lifestyle eats it. 🏠",
            "Huna rent home — redirect hata KSh 1,000 monthly kwa savings lifestyle isikule. 🏠",
            "Huna kodi nyumbani — elekeza hata KSh 1,000 mwezi kwa akiba. 🏠",
            "No rent at home — move even 1k monthly to savings. 🏠"
        ))
    }
    if (group.commuteHeavy && budgets.none { it.category.equals("Transport", ignoreCase = true) && it.limitAmount > 0 }) {
        out.add(t(
            "Long route, no Transport envelope — one fare hike wrecks the week. Set it in Budgets. 🚌",
            "Route ndefu bila Transport envelope — fare ikipanda week inaisha. Weka kwa Budgets. 🚌",
            "Njia ndefu bila bajeti ya usafiri — nauli ikipanda wiki inaharibika. Weka Bajeti. 🚌",
            "Long route, no fare envelope — set one in Budgets. 🚌"
        ))
    }
    // Collapse exact duplicates: overlapping blocks can emit the same line
    // twice for one underlying fact — show it once.
    return out.distinct().take(6)
}





private fun getPunguzaTipsForCategory(category: String, group: LivingSituation = LivingSituation.HOSTEL_COOK): Quadruple<String, String, String, String> {
    return when (category.lowercase()) {
        "food" -> if (group.cantCook) Quadruple(
            "No kitchen — fix 2–3 cheap spots (kibanda/mess) and eat there daily instead of roaming. 🍳",
            "Huna jiko — fix spots 2-3 cheap (kibanda/mess) ule hapo daily badala ya kuzurura. 🍳",
            "Huna jiko — chagua sehemu 2-3 za bei nafuu (kibanda/mess) ule hapo kila siku. 🍳",
            "No kitchen — fix 2-3 cheap spots, eat there daily. 🍳"
        ) else Quadruple(
            "Try cooking at home or eating at campus mess — saves up to KSh 2,000/month. 🍳",
            "Try kucook kibanda/mess instead of high-end joints — inasave mullah kibao. 🍳",
            "Pika nyumbani au kula mess ya chuo — utaokoa hadi KSh 2,000 mwezi huu. 🍳",
            "Try cooking at home — saves up to KSh 2,000 monthly. 🍳"
        )
        "shopping" -> Quadruple(
            "Use the 24-hour rule: wait a day before non-essential purchases. 🛒",
            "Tumia 24-hr rule: tulia siku moja kabla kubuy vitu zisizohitajika. 🛒",
            "Subiri masaa 24 kabla ya kununua vitu visivyo vya lazima. 🛒",
            "Wait 24 hours before non-essential shopping buys. 🛒"
        )
        "airtime" -> Quadruple(
            "Buy data/airtime bundles in bulk or use campus Wi-Fi to lower daily spend. 📱",
            "Tumia Wi-Fi ya campus na obuy bundles za mwezi usimwage coin kila siku. 📱",
            "Tumia Wi-Fi ya chuo na nunua vifurushi vya mwezi ili kupunguza matumizi. 📱",
            "Use campus Wi-Fi & buy weekly/monthly airtime bundles. 📱"
        )
        "transport" -> if (group.commuteHeavy) Quadruple(
            "Long route — one skipped trip or a term pass beats daily full fares. Plan trips, batch errands. 🚌",
            "Route ndefu — trip moja skipped ama pass ya term inashinda fare daily. Panga trips. 🚌",
            "Njia ndefu — safari moja iliyorukwa au pasi ya muhula inashinda nauli za kila siku. Panga safari. 🚌",
            "Long route — skip a trip or get a pass; batch errands. 🚌"
        ) else Quadruple(
            "Walk short distances or walk with friends to cut matatu fare. 🚌",
            "Rauka mapema u-walk short distances kucut fare za matatu. 🚌",
            "Mtembee umbali mfupi ili kupunguza nauli za matatu. 🚌",
            "Walk short distances to reduce daily matatu fare. 🚌"
        )
        "rent" -> if (!group.hasRent) Quadruple(
            "No rent at home — chip in a little, save the rest instead of lifestyle creep. 🏠",
            "Huna rent home — changia kidogo, save the rest badala ya lifestyle. 🏠",
            "Huna kodi nyumbani — changia kidogo, weka akiba iliyobaki. 🏠",
            "No rent at home — chip in small, save the rest. 🏠"
        ) else Quadruple(
            "Consider splitting rent/hostel with a roommate to share costs. 🏠",
            "Tafuta roommate m-share rent na hostel bills. 🏠",
            "Fikiria kushiriki pango na mwanafunzi mwenzako ili kupunguza gharama. 🏠",
            "Share hostel/rent expenses with a roommate. 🏠"
        )
        else -> Quadruple(
            "Set a strict weekly limit for $category and track every coin. 💡",
            "Weka limit ya weekly kwa $category u-track kila coin. 💡",
            "Weka kikomo cha kila wiki kwa $category ufuatilie kila sarafu. 💡",
            "Set a weekly limit for $category to stay within budget. 💡"
        )
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)


// Semester read-out: daily / weekly / monthly pace plus the fare, rent and
// payday facts the scan observed — each phrased as "we saw X, confirm?",
// never as declarations. Feeds the onboarding review step and Home.
internal fun buildSemesterInsights(
    profile: SpendProfile,
    window: ScanWindow,
    lang: AppLanguage,
    name: String,
    declaredDays: Set<String> = emptySet(),
    routineChange: RoutineChange? = null,
    faresByDay: Map<Int, Double> = emptyMap(),
    dividend: FreeDayDividend? = null,
    exam: ExamSignal? = null
): List<String> {
    fun t(en: String, sh: String, sw: String, mix: String): String =
        when (lang) {
            AppLanguage.SHENG -> sh
            AppLanguage.KISWAHILI -> sw
            AppLanguage.MIXED -> mix
            else -> en
        }
    fun ord(day: Int): String {
        val suffix = when (day % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$day$suffix"
    }
    val nn = if (name.isNotBlank()) "$name, " else ""
    val out = mutableListOf<String>()
    if (profile.windowDays <= 0 || profile.activeDays == 0) {
        return listOf(t(
            "No semester spending seen yet — approve a few texts and I'll read the rhythm. 👀",
            "Hakuna spending ya sem bado — approve texts kadhaa ni-some rhythm. 👀",
            "Hakuna matumizi ya muhula bado — thibitisha ujumbe nichambue. 👀",
            "No semester spending yet — approve a few texts. 👀"
        ))
    }
    out.add(t(
        "${window.label}: ~KSh ${profile.dailyAvg.toInt()}/day (~KSh ${profile.weeklyAvg.toInt()}/week, ~KSh ${profile.monthlyAvg.toInt()}/month) across ${profile.activeDays} active days.",
        "${window.label}: ~KSh ${profile.dailyAvg.toInt()}/day (~KSh ${profile.weeklyAvg.toInt()}/week, ~KSh ${profile.monthlyAvg.toInt()}/month) kwa siku ${profile.activeDays} active.",
        "${window.label}: ~KSh ${profile.dailyAvg.toInt()}/siku (~KSh ${profile.weeklyAvg.toInt()}/wiki, ~KSh ${profile.monthlyAvg.toInt()}/mwezi) kwa siku ${profile.activeDays}.",
        "${window.label}: ~KSh ${profile.dailyAvg.toInt()}/day (~KSh ${profile.weeklyAvg.toInt()}/week, ~KSh ${profile.monthlyAvg.toInt()}/month), ${profile.activeDays} active days."
    ))
    if (profile.fareDailyAvg > 0 && profile.fareWindowHint != null) {
        out.add(t(
            "${nn}fares run ~KSh ${profile.fareDailyAvg.toInt()}/day ${profile.fareWindowHint} — that the route? Confirm it. 🚌",
            "${nn}fare ~KSh ${profile.fareDailyAvg.toInt()}/day ${profile.fareWindowHint} — ndio route? Confirm. 🚌",
            "${nn}nauli ~KSh ${profile.fareDailyAvg.toInt()}/siku ${profile.fareWindowHint} — ndio njia? Thibitisha. 🚌",
            "${nn}fares ~KSh ${profile.fareDailyAvg.toInt()}/day ${profile.fareWindowHint} — confirm? 🚌"
        ))
    }
    if (profile.rentDay != null && profile.rentMonthly > 0) {
        out.add(t(
            "Rent ~KSh ${profile.rentMonthly.toInt()}/mo lands around the ${ord(profile.rentDay)} — confirm the day? 🏠",
            "Rent ~KSh ${profile.rentMonthly.toInt()}/mo huingia around ${ord(profile.rentDay)} — confirm siku? 🏠",
            "Kodi ~KSh ${profile.rentMonthly.toInt()}/mwezi tarehe ${ord(profile.rentDay)} — thibitisha siku? 🏠",
            "Rent ~KSh ${profile.rentMonthly.toInt()}/mo around ${ord(profile.rentDay)} — confirm? 🏠"
        ))
    }
    if (profile.paydayAmount != null && profile.paydayAmount > 0 && profile.paydayDay != null) {
        val who = profile.paydayWho?.takeIf { it.isNotBlank() } ?: "Money"
        out.add(t(
            "$who ~KSh ${profile.paydayAmount.toInt()} lands around the ${ord(profile.paydayDay)} — plan it before it lands. 💰",
            "$who ~KSh ${profile.paydayAmount.toInt()} inaingia around ${ord(profile.paydayDay)} — ipange mapema. 💰",
            "$who ~KSh ${profile.paydayAmount.toInt()} inaingia tarehe ${ord(profile.paydayDay)} — ipange mapema. 💰",
            "$who ~KSh ${profile.paydayAmount.toInt()} lands ${ord(profile.paydayDay)} — plan early. 💰"
        ))
    }
    if (profile.schoolDays.isNotEmpty()) {
        val short = mapOf(1 to "Sun", 2 to "Mon", 3 to "Tue", 4 to "Wed", 5 to "Thu", 6 to "Fri", 7 to "Sat")
        val days = profile.schoolDays.sorted().mapNotNull { short[it] }
        val span = if (days.size >= 2 && days == listOf("Mon", "Tue", "Wed", "Thu", "Fri")) "Mon–Fri" else days.joinToString(", ")
        out.add(t(
            "Class-day pattern looks like $span (from fare days) — fix it in Profile if a day is off. 🗓️",
            "Pattern ya class days ni kama $span (kutoka fare days) — rekebisha Profile siku ikikosewa. 🗓️",
            "Muonekano wa siku za masomo ni $span — sahihisha Profaili. 🗓️",
            "Class-day pattern: $span (from fares) — fix in Profile if off. 🗓️"
        ))
    }
    profile.skewNote?.let { note ->
        out.add(t(
            "$note",
            "$note",
            "$note",
            "$note"
        ))
    }
    if (profile.excludedWeeks > 0) {
        val n = profile.excludedWeeks
        val wk = if (n == 1) "week" else "weeks"
        out.add(t(
            "Left out $n unusual $wk (KSh ${profile.excludedTotal.toInt()}) so the averages stay honest. 🧹",
            "Nimetoa wiki $n unusual (KSh ${profile.excludedTotal.toInt()}) — averages zibaki honest. 🧹",
            "Nimeacha wiki $n isiyo ya kawaida (KSh ${profile.excludedTotal.toInt()}) ili wastani ubaki wa kweli. 🧹",
            "Left out $n unusual $wk (KSh ${profile.excludedTotal.toInt()}) — averages stay honest. 🧹"
        ))
    }
    // Declared-vs-observed in one line: a marked class day with no fares
    // gets a question, never a silent budget charge. First mismatch only —
    // one question per readout, not an interrogation.
    if (declaredDays.isNotEmpty() && profile.schoolDays.isNotEmpty()) {        val short = mapOf(1 to "Sun", 2 to "Mon", 3 to "Tue", 4 to "Wed", 5 to "Thu", 6 to "Fri", 7 to "Sat")
        val observed = profile.schoolDays.mapNotNull { short[it] }.toSet()
        declaredDays.filter { it != "Sun" }.firstOrNull { it !in observed }?.let { d ->
            out.add(t(
                "$d shows no fares in this window — night class, or unmark $d in Profile? 🚌",
                "$d haina fare hii window — night class, ama unmark $d Profile? 🚌",
                "$d haina nauli kipindi hiki — somo la jioni, au iondoe Profaili? 🚌",
                "$d no fares this window — night class, or unmark $d in Profile? 🚌"
            ))
        }
    }
    routineChange?.let { rc ->
        out.add(t(
            "Fares shifted lately (~KSh ${rc.recentDaily.toInt()}/day vs ~KSh ${rc.priorDaily.toInt()} before) — new route or timetable? Say so and I'll re-learn. 🚌",
            "Fare ime-shift siku hizi (~KSh ${rc.recentDaily.toInt()}/day vs ~KSh ${rc.priorDaily.toInt()} before) — route mpya ama timetable? Niambie ni-re-learn. 🚌",
            "Nauli imebadilika hivi karibuni (~KSh ${rc.recentDaily.toInt()}/siku kutoka ~KSh ${rc.priorDaily.toInt()}) — njia mpya? Niambie. 🚌",
            "Fares shifted (~KSh ${rc.recentDaily.toInt()}/day vs ~KSh ${rc.priorDaily.toInt()}) — new route? Tell me, I'll re-learn. 🚌"
        ))
    }
    // Per-day spread in one line: when weekdays genuinely cost different
    // amounts, name both ends so budgets can lean on the dear days.
    if (faresByDay.size >= 2) {
        val hi = faresByDay.maxByOrNull { it.value }
        val lo = faresByDay.minByOrNull { it.value }
        if (hi != null && lo != null && hi.key != lo.key && lo.value > 0 && hi.value >= lo.value * 1.3) {
            val short = mapOf(1 to "Sun", 2 to "Mon", 3 to "Tue", 4 to "Wed", 5 to "Thu", 6 to "Fri", 7 to "Sat")
            val hiName = short[hi.key] ?: "?"
            val loName = short[lo.key] ?: "?"
            out.add(t(
                "Dearest fare day: $hiName (~KSh ${hi.value.toInt()}), cheapest: $loName (~KSh ${lo.value.toInt()}).",
                "Fare pricey day: $hiName (~KSh ${hi.value.toInt()}), cheap: $loName (~KSh ${lo.value.toInt()}).",
                "Siku ya nauli ghali: $hiName (~KSh ${hi.value.toInt()}), rahisi: $loName (~KSh ${lo.value.toInt()}).",
                "Priciest fare day $hiName (~KSh ${hi.value.toInt()}), cheapest $loName (~KSh ${lo.value.toInt()})."
            ))
        }
    }
    dividend?.let { d ->
        val n = d.freeDows.size
        val dayWord = if (n == 1) "day" else "days"
        out.add(t(
            "$n school $dayWord cost nothing (~KSh ${d.total.toInt()} of fares stayed home) — bank it? 🎉",
            "Siku $n za shule zero spend (~KSh ${d.total.toInt()} ya fare imebaki) — bank it? 🎉",
            "Siku $n za shule bila gharama (~KSh ${d.total.toInt()} ya nauli imebaki) — iweke akiba? 🎉",
            "$n school $dayWord zero spend (~KSh ${d.total.toInt()} fares saved) — bank it? 🎉"
        ))
    }
    exam?.let {
        out.add(t(
            "Smells like exam season (printing + late nights up) — pause fare guards for 2 weeks? 📚",
            "Inakaa exam season (printing + late nights zimepanda) — pause fare guards 2 weeks? 📚",
            "Inaonekana msimu wa mitihani (uchapishaji + usiku umeongezeka) — sitisha ulinzi wa nauli wiki 2? 📚",
            "Looks like exam season (printing + late nights up) — pause fare guards 2 weeks? 📚"
        ))
    }
    // Priority ordering: danger and action items surface first,
    // informational lines scroll behind. If more than 6 fit,
    // the final line says how many more exist — nothing is lost.
    fun priority(line: String): Int = when {
        line.contains("Danger") -> 0
        line.contains("run dry") || line.contains("already over") -> 0
        line.contains("budget finished") || (line.contains("Budget") && line.contains("% used")) -> 1
        line.contains("re-learn") || line.contains("Re-learn") -> 1
        line.contains("exam season") -> 1
        line.contains("unusual") || line.contains("unclassified") -> 2
        else -> 3
    }
    val sorted = out.sortedBy { priority(it) }
    return if (sorted.size > 6) {
        val shown = sorted.take(6)
        val hidden = sorted.size - 6
        shown + listOf(t("…and $hidden more insights — tap to expand.", "…na $hidden zaidi — gusa kuipanua.", "…na $hidden zaidi — gusa kuipanua.", "…and $hidden more insights — tap to expand."))
    } else sorted
}
