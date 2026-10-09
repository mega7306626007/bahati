package com.pesaflow.app.ui.buddy

import com.pesaflow.app.data.models.AppLanguage

// Every Buddy sentence in three voices. EN branches are byte-identical to
// the long-standing strings (tests pin them); SW is standard Kiswahili, SH
// is light campus Sheng. Figures slot in unchanged via MoneyFormatter, and
// entity names (people, bills) always pass through as typed. Pure,
// unit-tested: every template must carry its figure and its provenance word.
object BuddyStrings {

    private fun pick(lang: AppLanguage, en: String, sw: String, sh: String): String = when (lang) {
        AppLanguage.KISWAHILI -> sw
        AppLanguage.SHENG -> sh
        else -> en
    }

    // ---------- balance / flexible / safe ----------

    fun recordedBalance(fig: String, why: String, lang: AppLanguage): String {
        val base = pick(
            lang,
            "Recorded balance is $fig.",
            "Salio lililorekodiwa ni $fig.",
            "Balance yako kwa ledger ni $fig."
        )
        return if (why.isBlank()) base else "$base $why"
    }

    fun flexibleMoney(fig: String, qualityNote: String, lang: AppLanguage): String = pick(
        lang,
        "Flexible money is $fig (calculated).$qualityNote",
        "Pesa inayoweza kupangwa ni $fig (imehesabiwa).$qualityNote",
        "Flexible doh ni $fig (tumeicalculate).$qualityNote"
    )

    fun safeSpend(today: String, week: String, qualityNote: String, lang: AppLanguage): String = pick(
        lang,
        "Safe today is $today; safe this week $week (calculated).$qualityNote",
        "Salama kutumia leo ni $today; wiki hii $week (imehesabiwa).$qualityNote",
        "Leo unaeza tumia $today; wiki hii $week (tumeicalculate).$qualityNote"
    )

    fun qualityNote(dataQuality: String, lang: AppLanguage): String {
        if (dataQuality != "PARTIAL" && dataQuality != "SPARSE") return ""
        return pick(
            lang,
            if (dataQuality == "PARTIAL") " Based on partial history — treat this as an estimate."
            else " Based on sparse history — a rough estimate, not a promise.",
            if (dataQuality == "PARTIAL") " Kwa historia isiyokamilika — lichukulie kama kadirio."
            else " Kwa historia haba — kadirio tu, si ahadi.",
            if (dataQuality == "PARTIAL") " History ni partial — ichukue kama estimate."
            else " History ni sparse — estimate rough, si promise."
        )
    }

    // ---------- bills / debts / goals ----------

    fun noOpenBills(lang: AppLanguage): String = pick(
        lang, "No open bills. ✅", "Hakuna bili zinazodaiwa. ✅", "Hakuna bill open. ✅"
    )

    fun openBills(count: Int, total: String, lang: AppLanguage): String = pick(
        lang,
        "$count open bills totaling $total (your share, recorded).",
        "Bili $count ambazo hazijalipwa, jumla $total (fungu lako, zimerekodiwa).",
        "Una bill $count open, total $total (share yako, zimerecordiwa)."
    )

    fun noOpenDebts(lang: AppLanguage): String = pick(
        lang, "No open debts. ✅", "Hakuna madeni. ✅", "Hakuna deni. ✅"
    )

    fun openDebts(count: Int, total: String, lang: AppLanguage): String = pick(
        lang,
        "$count open debts totaling $total (recorded).",
        "Madeni $count, jumla $total (yemerekodiwa).",
        "Madeni $count, total $total (imerecordiwa)."
    )

    fun noGoals(lang: AppLanguage): String = pick(
        lang,
        "No savings goals yet — open Goals to create one.",
        "Hakuna malengo ya akiba bado — fungua Goals uanze moja.",
        "Hakuna goals za savings bado — fungua Goals ucreate moja."
    )

    fun goals(count: Int, lang: AppLanguage): String = pick(
        lang,
        "You have $count savings goals — open Goals to see pace and risk.",
        "Una malengo $count ya akiba — fungua Goals uone mwendo na hatari.",
        "Una goals $count za savings — fungua Goals uone pace na risk."
    )

    // ---------- transaction search ----------

    fun txnQuery(head: String, lines: List<String>, lang: AppLanguage): String {
        val text = (listOf(head) + lines).joinToString("\n")
        return text
    }

    fun txnQueryHead(count: Int, label: String, total: String, lang: AppLanguage): String = pick(
        lang,
        "$count transaction${if (count == 1) "" else "s"} $label: $total (recorded).",
        "Miamala $count $label: $total (imerekodiwa).",
        "Transactions $count $label: $total (imerecordiwa)."
    )

    fun txnQueryLabel(merchant: String?, category: String?, amount: String?, lang: AppLanguage): String {
        val parts = listOfNotNull(
            merchant?.let {
                pick(lang, "with $it", "na $it", "na $it")
            },
            category,
            amount?.let {
                pick(lang, "of $it", "ya $it", "ya $it")
            }
        ).joinToString(" · ")
        return parts.ifBlank {
            pick(lang, "matching", "yanayolingana", "zinamatch")
        }
    }

    fun noTxnMatch(lang: AppLanguage): String = pick(
        lang,
        "No recorded transactions match that.",
        "Hakuna miamala iliyorekodiwa inayolingana.",
        "Hakuna transaction imerecordiwa inamatch."
    )

    fun noTransactionsYet(lang: AppLanguage): String = pick(
        lang,
        "No recorded transactions yet.",
        "Hakuna miamala iliyorekodiwa bado.",
        "Hakuna transactions zimerecordiwa bado."
    )

    // Choice verbs arrive in English ("delete", "reclassify to Food",
    // "record the KSh … payment on"); map the known ones, pass the rest
    // through next to numbered choices (the user replies with numbers).
    fun askVerb(verb: String, lang: AppLanguage): String {
        if (lang == AppLanguage.ENGLISH) return verb
        if (verb == "delete") return if (lang == AppLanguage.KISWAHILI) "ufute" else "udelete"
        val reclass = Regex("^reclassify to (.+)$").find(verb)?.groupValues?.get(1)
        if (reclass != null) return if (lang == AppLanguage.KISWAHILI) "upange upya chini ya $reclass"
        else "ureclassify kwenda $reclass"
        val record = Regex("^record the (.+) payment on$").find(verb)?.groupValues?.get(1)
        if (record != null) return if (lang == AppLanguage.KISWAHILI) "urekodi malipo ya $record kwa"
        else "urecord payment ya $record kwa"
        return verb
    }

    fun askWhich(candidates: Int, numbered: String, verb: String, scope: String, lang: AppLanguage): String {
        val v = askVerb(verb, lang)
        return pick(
            lang,
            "Here are the $scope transactions — which one should I $v?\n$numbered\nReply with the number — or 'all' to do every one.",
            "Hii ndiyo miamala $scope — ni ipi $v?\n$numbered\nJibu kwa namba — au 'zote' kwa zote.",
            "Hizi ndizo transactions $scope — ni gani $v?\n$numbered\nReply na number — ama 'zote' kwa zote."
        )
    }

    fun pctLabel(now: Double, before: Double, lang: AppLanguage): String {
        if (before <= 0) return if (now > 0) pick(
            lang,
            "new spending vs none recorded",
            "matumizi mapya dhidi ya hakuna yaliyorekodiwa",
            "spend mpya vs hakuna iliyorecordiwa"
        ) else pick(lang, "no change", "hakuna mabadiliko", "hakuna change")
        val pct = ((now - before) / before * 100).toInt()
        if (pct == 0) return pick(lang, "flat", "sawasawa", "flat")
        val signed = (if (pct > 0) "+" else "") + "$pct%"
        return signed
    }

    fun missingInputs(items: List<String>, lang: AppLanguage): String {
        if (items.isEmpty() || lang == AppLanguage.ENGLISH) return items.joinToString("; ")
        val map = mapOf(
            "daily burn rate" to "kiwango cha matumizi ya kila siku",
            "positive monthly surplus" to "ziada chanya ya kila mwezi",
            "usual one-way fare" to "nauli ya kawaida ya uelekeo mmoja",
            "average bought-meal price" to "bei ya wastani ya chakula kinachonunuliwa",
            "grocery cost per home meal" to "gharama ya mboga kwa mlo wa nyumbani",
            "lunches per week" to "chakula cha mchana kwa wiki"
        )
        return items.joinToString("; ") { map[it] ?: it }
    }

    fun billPaySummary(name: String, fig: String, lang: AppLanguage): String = pick(
        lang,
        "Pay '$name' $fig?\nFiles the payment as an expense.",
        "Kulipa '$name' $fig?\nInahifadhiwa kama matumizi.",
        "Kulipa '$name' $fig?\nInafilwa kama expense."
    )

    fun billPayLines(lines: String, lang: AppLanguage): String = pick(
        lang,
        "Which one — reply with the amount:\n$lines",
        "Ni ipi — jibu kwa kiasi:\n$lines",
        "Ni gani — reply na amount:\n$lines"
    )

    fun billEditSummary(name: String, fig: String, lang: AppLanguage): String = pick(
        lang,
        "Change '$name' to $fig?",
        "Kubadilisha '$name' kuwa $fig?",
        "Kuchange '$name' kuwa $fig?"
    )

    fun billEditWhich(fig: String, names: String, lang: AppLanguage): String = pick(
        lang,
        "Which bill should become $fig? Reply with its exact name:\n$names",
        "Bili ipi iwe $fig? Jibu kwa jina lake kamili:\n$names",
        "Bill gani iwe $fig? Reply na jina lake exact:\n$names"
    )

    fun billMoveSummary(name: String, day: String, lang: AppLanguage): String = pick(
        lang,
        "Move '$name' to $day?",
        "Kuhamisha '$name' hadi $day?",
        "Kuhamisha '$name' hadi $day?"
    )

    fun billMoveWhich(day: String, names: String, lang: AppLanguage): String = pick(
        lang,
        "Which bill moves to $day? Reply with its exact name:\n$names",
        "Bili ipi ihamie $day? Jibu kwa jina lake kamili:\n$names",
        "Bill gani ioptions $day? Reply na jina lake exact:\n$names"
    )

    fun debtPaySummary(who: String, fig: String, lang: AppLanguage): String = pick(
        lang,
        "Record $fig paid on $who?",
        "Kurekodi $fig imelipwa kwa $who?",
        "Kurecord $fig imelipwa kwa $who?"
    )

    fun opening(route: String, lang: AppLanguage): String = pick(
        lang,
        "Opening $route…",
        "Ninafungua $route…",
        "Ninafungua $route…"
    )

    fun needsConfirmation(prompt: String, lang: AppLanguage): String = pick(
        lang,
        "That needs your confirmation first ($prompt).",
        "Hiyo inahitaji uthibitisho wako kwanza ($prompt).",
        "Hiyo inahitaji confirmation yako kwanza ($prompt)."
    )

    fun bulkDeleteUnsupported(lang: AppLanguage): String = pick(
        lang,
        "I can't bulk-delete from chat yet — reply with one number, or delete them in Transactions.",
        "Siwezi kufuta kwa wingi hapa — jibu namba moja, au futa kwenye Transactions.",
        "Siwezi delete bulk kwa chat — reply na number moja, ama delete kwa Transactions."
    )

    // ---------- tap-to-run follow-ups (every voice; EN pinned) ----------

    fun confirmNone(lang: AppLanguage): String = pick(
        lang,
        "Nothing sure enough right now — the rest need your eyes on Home → Pending.",
        "Hakuna lililo hakika sasa — mengine yanahitaji macho yako Home → Pending.",
        "Hakuna sure saa hii — hizo zingine zi-check Home → Pending."
    )

    fun confirmingSure(count: Int, lang: AppLanguage): String = pick(
        lang,
        "Confirming $count sure row${if (count == 1) "" else "s"} — watch Home → Pending clear. Undo lives there if I overreached.",
        "Nathibitisha safu $count zilizo hakika — angalia Home → Pending zikifutika. Undo iko huko nikikosea.",
        "Naconfirm rows $count sure — watch Home → Pending ziclear. Undo iko huko nikioverreach."
    )

    fun rowsAlreadyCleared(lang: AppLanguage): String = pick(
        lang,
        "Those rows already cleared — the queue moved on. Say 'review' to see what's left.",
        "Hizo safu tayari zimefutika — foleni imeendelea. Sema 'review' uone zilizoachwa.",
        "Hizo rows zishaclear — queue imeenda mbele. Sema 'review' uone zimebaki."
    )

    fun confirmedCount(count: Int, lang: AppLanguage): String = pick(
        lang,
        "Confirmed $count — filed with their suggested categories. Undo on Home if one looks off.",
        "Nimethibitisha $count — zimehifadhiwa na makundi yaliyopendekezwa. Undo ukiwa Home kimoja kikikaa vibaya.",
        "Nimeconfirm $count — zimefilwa na suggested categories. Undo kwa Home kimoja kikikaa off."
    )

    fun nothingToIgnore(lang: AppLanguage): String = pick(
        lang,
        "Nothing matching to ignore.",
        "Hakuna linalolingana la kupuuza.",
        "Hakuna inamatch ya kuignore."
    )

    fun ignoredCount(count: Int, lang: AppLanguage): String = pick(
        lang,
        "Ignored $count — ledger untouched, they're just out of the queue.",
        "Nimezipuuza $count — ledger haijaguswa, zimetoka tu folenini.",
        "Nimeignore $count — ledger haijaguswa, zimetoka tu kwa queue."
    )

    fun dupesClean(lang: AppLanguage): String = pick(
        lang,
        "Queue is already clean — no duplicates found. ✅",
        "Foleni tayari ni safi — hakuna marudio. ✅",
        "Queue iko clean — hakuna duplicates. ✅"
    )

    fun dupesRemoved(count: Int, lang: AppLanguage): String = pick(
        lang,
        "Removed $count duplicate${if (count == 1) "" else "s"} — confirm the rest. ✅",
        "Nimeondoa marudio $count — thibitisha zilizobaki. ✅",
        "Nimetoa duplicates $count — confirm zingine. ✅"
    )

    fun noRowsForName(lang: AppLanguage): String = pick(
        lang,
        "No waiting rows for that name — try 'review' first.",
        "Hakuna safu zinazosubiri kwa jina hilo — jaribu 'review' kwanza.",
        "Hakuna waiting rows za hiyo jina — jaribu 'review' kwanza."
    )

    fun filedUnder(count: Int, category: String, lang: AppLanguage): String = pick(
        lang,
        "Filed $count under $category. ✅",
        "Nimehifadhi $count chini ya $category. ✅",
        "Nimefile $count chini ya $category. ✅"
    )

    fun sayReview(lang: AppLanguage): String = pick(
        lang,
        "Say 'review' to see what's waiting.",
        "Sema 'review' uone zinazosubiri.",
        "Sema 'review' uone zinangoja."
    )

    fun undone(lang: AppLanguage): String = pick(
        lang,
        "Undone — check Home, the row is back where it was. ✅",
        "Imetenduliwa — angalia Home, safu imerudi ilipokuwa. ✅",
        "Imetenduliwa — check Home, row imerudi pale ilikuwa. ✅"
    )

    fun nothingToUndo(lang: AppLanguage): String = pick(
        lang,
        "Nothing to undo right now.",
        "Hakuna la kutendua sasa.",
        "Hakuna cha kuundo saa hii."
    )

    // ---------- review-queue + fallback (command words stay English:
    // the parser matches them; only the prose around them translates) ----------

    fun reviewQueueEmpty(lang: AppLanguage): String = pick(
        lang,
        "Queue is empty.",
        "Foleni iko tupu.",
        "Queue iko empty."
    )

    fun reviewQueueMore(extra: Int, lang: AppLanguage): String = pick(
        lang,
        "\n…+$extra more on Home → Pending.",
        "\n…+$extra zaidi kwenye Home → Pending.",
        "\n…+$extra more kwa Home → Pending."
    )

    fun reviewQueueHint(lang: AppLanguage): String = pick(
        lang,
        "\nSay 'confirm all', 'confirm Nancy', or 'ignore X'.",
        "\nSema 'confirm all', 'confirm Nancy', au 'ignore X'.",
        "\nSema 'confirm all', 'confirm Nancy', ama 'ignore X'."
    )

    fun reviewQueueWaiting(count: Int, body: String, lang: AppLanguage): String = pick(
        lang,
        "$count waiting:\n$body",
        "$count zinasubiri:\n$body",
        "$count zinangoja:\n$body"
    )

    // ---------- small talk: Buddy as a person, not a form ----------

    fun whoAreYou(lang: AppLanguage): String = pick(
        lang,
        "I'm PesaBuddy 🤖 — your campus money buddy. I read your M-Pesa history into budgets, balances and plans, and I never touch money without your tap. Ask me 'is my budget safe?' or tap a suggestion above.",
        "Mimi ni PesaBuddy 🤖 — rafiki yako wa pesa chuoni. Nasoma historia yako ya M-Pesa kuwa bajeti, salio na mipango, wala sigusi pesa bila kugusa kwako. Niulize 'is my budget safe?' au gusa pendekezo hapo juu.",
        "Mimi ni PesaBuddy 🤖 — buddy yako wa doh chuo. Nasoma history yako ya M-Pesa kuwa budgets, balances na plans, na sigusi doh bila tap yako. Niulize 'is my budget safe?' ama tap suggestion hapo juu."
    )

    fun joke(index: Int, lang: AppLanguage): String {
        val i = ((index % 3) + 3) % 3
        return pick(
            lang,
            when (i) {
                0 -> "Why did the student stare at his M-Pesa message? He was watching his money disappear in real time. 😄"
                1 -> "My budget and I have an agreement: it pretends to limit me, I pretend to follow it. 🤝"
                else -> "HELB is like Nairobi rain — everyone debates when it's coming, nobody knows. ☔"
            },
            when (i) {
                0 -> "Kwa nini mwanafunzi aliangalia SMS ya M-Pesa? Alikuwa anaangalia pesa zake zikitoweka live. 😄"
                1 -> "Mimi na bajeti yangu tuna makubaliano: inanidanganya kunizuia, na mimi najifanya naifuata. 🤝"
                else -> "HELB ni kama mvua ya Nairobi — kila mtu anajadili itakuja lini, hakuna anayejua. ☔"
            },
            when (i) {
                0 -> "Mbona msee ali-stare SMS ya M-Pesa? Alikuwa anawatch doh zake zikidisappear live. 😄"
                1 -> "Mimi na budget yangu tuko na deal: inanidanganya kunilimit, na mimi najifanya naifuata. 🤝"
                else -> "HELB ni kama rain ya Nairobi — kila mtu anadebate itakam lini, hakuna anajua. ☔"
            }
        )
    }

    fun sorryReply(lang: AppLanguage): String = pick(
        lang,
        "No worries at all — ledgers forgive, budgets remember. 😄 What next?",
        "Hakuna shida kabisa — ledger husamehe, bajeti hukumbuka. 😄 Nini kinafuata?",
        "Hakuna stress — ledger husamehe, budget hukumbuka. 😄 Nini next?"
    )

    fun howAreYou(lang: AppLanguage): String = pick(
        lang,
        "Doing great — my ledger is balanced and my code compiled on the first try. How is your wallet feeling? 🙂",
        "Niko poa — ledger yangu iko sawa na code yangu ili-compile mara ya kwanza. Mkoba wako unajisikiaje? 🙂",
        "Niko fiti — ledger yangu iko balanced na code yangu ili-compile first try. Wallet yako inafeel aje? 🙂"
    )

    fun goodMorning(lang: AppLanguage): String = pick(
        lang,
        "Good morning ☀️ — fresh ledger, fresh day. Ask me today's spend pace or what's due.",
        "Habari za asubuhi ☀️ — ledger mpya, siku mpya. Niulize mwendo wa matumizi ya leo au kinachodaiwa.",
        "Morning ☀️ — fresh ledger, fresh day. Niulize spend pace ya leo ama inadaiwa nini."
    )

    fun goodNight(lang: AppLanguage): String = pick(
        lang,
        "Good night 🌙 — your money is tucked in and I'll keep watch. See you at the next alert.",
        "Usiku mwema 🌙 — pesa zako zimelala salama nami nitalinda. Tutaonana kwa arifa inayofuata.",
        "Usiku mwema 🌙 — doh zako zimelala salama nami nitachunga. Tutaonana kwa next alert."
    )

    fun loveYou(lang: AppLanguage): String = pick(
        lang,
        "Aww — I keep your money safe, you keep the compliments coming. 💚 Now, about that budget…",
        "Aww — mimi nalinda pesa zako, wewe niletee sifa. 💚 Sasa, kuhusu hiyo bajeti…",
        "Aww — mimi nahold doh zako, wewe niletee compliments. 💚 Sasa, kuhusu hiyo budget…"
    )

    fun genericFallback(lang: AppLanguage): String = pick(
        lang,
        "Hmm, I only answer from your real records. Try: 'leo nime-spend how much?', 'biggest expense?', 'is my budget safe?', 'my stock?', 'what do I lack?', 'replenish what?', or 'what should I buy first?'",
        "Hmm, najibu tu kutokana na rekodi zako halisi. Jaribu: 'leo nimetumia how much?', 'biggest expense?', 'is my budget safe?', 'my stock?', 'what do I lack?', 'replenish what?', au 'what should I buy first?'",
        "Hmm, najibu tu kutoka kwa records zako real. Jaribu: 'leo nimespend how much?', 'biggest expense?', 'is my budget safe?', 'my stock?', 'what do I lack?', 'replenish what?', ama 'what should I buy first?'"
    )

    // ---------- legacy answers: greetings + simple time windows ----------

    fun greeting(nmEx: String, worry: String?, lang: AppLanguage): String = pick(
        lang,
        "Hey$nmEx! I'm PesaBuddy 👋." +
            (if (worry != null) " I remember $worry worries you most — ask 'what should I buy first?' anytime." else "") +
            " Ask me about spending, budgets, savings, stock or balances.",
        "Habari$nmEx! Mimi ni PesaBuddy 👋." +
            (if (worry != null) " Nakumbuka $worry inakusumbua zaidi — uliza 'what should I buy first?' wakati wowote." else "") +
            " Niulize kuhusu matumizi, bajeti, akiba, stock au salio.",
        "Niaje$nmEx! Mimi ni PesaBuddy 👋." +
            (if (worry != null) " Nakumbuka $worry inakusumbua most — uliza 'what should I buy first?' anytime." else "") +
            " Niulize kuhusu spending, budgets, savings, stock ama balances."
    )

    fun helpFallback(lang: AppLanguage): String = pick(
        lang,
        "I answer from your saved records: ask about spending periods, categories, income, budgets, cash, bills, debt, savings, meals, stock or your timetable. Try 'explain the app' for a feature guide, or 'why is flexible money this amount?' for a calculation explanation.",
        "Najibu kutokana na rekodi zako zilizohifadhiwa: uliza kuhusu vipindi vya matumizi, makundi, mapato, bajeti, pesa taslimu, bili, madeni, akiba, milo, stock au ratiba yako. Jaribu 'explain the app' kwa mwongozo wa vipengele, au 'why is flexible money this amount?' kwa maelezo ya hesabu.",
        "Najibu kutoka kwa saved records zako: uliza kuhusu spending periods, categories, income, budgets, cash, bills, debt, savings, meals, stock ama timetable yako. Jaribu 'explain the app' kwa feature guide, ama 'why is flexible money this amount?' kwa calculation explanation."
    )

    fun thanks(lang: AppLanguage): String = pick(
        lang,
        "Karibu sana! 🎉 Keep tracking — small daily records beat big monthly guesses.",
        "Karibu sana! 🎉 Endelea kurekodi — rekodi ndogo za kila siku hushinda makadirio makubwa ya mwezi.",
        "Karibu sana! 🎉 Endelea kutrack — small daily records hushinda big monthly guesses."
    )

    fun todayEmpty(lang: AppLanguage): String = pick(
        lang,
        "No transactions recorded yet — add your first expense and I'll track it here.",
        "Hakuna miamala iliyorekodiwa bado — weka matumizi yako ya kwanza nami nitayafuatilia hapa.",
        "Hakuna transactions zimerecordiwa bado — weka expense yako ya kwanza nami nitaitrack hapa."
    )

    fun todaySpend(today: String, month: String, lang: AppLanguage): String = pick(
        lang,
        "Today ume-spend KSh $today so far. Month total: KSh $month.",
        "Leo umetumia KSh $today hadi sasa. Jumla ya mwezi: KSh $month.",
        "Leo umespend KSh $today so far. Month total: KSh $month."
    )

    fun weekCategory(cat: String, fig: String, lang: AppLanguage): String = pick(
        lang,
        "$cat this week: KSh $fig.",
        "$cat wiki hii: KSh $fig.",
        "$cat hii week: KSh $fig."
    )

    fun yesterdayCategory(cat: String, fig: String, lang: AppLanguage): String = pick(
        lang,
        "Yesterday $cat: KSh $fig.",
        "Jana $cat: KSh $fig.",
        "Yesterday $cat: KSh $fig."
    )

    fun topEmpty(lang: AppLanguage): String = pick(
        lang,
        "No expenses yet — nothing to rank.",
        "Hakuna matumizi bado — hakuna cha kupanga.",
        "Hakuna expenses bado — hakuna cha kurank."
    )

    fun topHits(list: String, lang: AppLanguage): String = pick(
        lang,
        "Biggest hits: $list.",
        "Vibao vikubwa: $list.",
        "Biggest hits: $list."
    )

    // ---------- legacy answers: runway, bills due, week, budget, safe ----------

    fun runOutNoData(lang: AppLanguage): String = pick(
        lang,
        "No data yet — log a week first.",
        "Hakuna data bado — rekodi wiki moja kwanza.",
        "Hakuna data bado — log week moja kwanza."
    )

    fun runOutNoBurn(balance: String, lang: AppLanguage): String = pick(
        lang,
        "KSh $balance left, no burn rate yet — keep logging.",
        "KSh $balance zimebaki, hakuna kiwango cha matumizi bado — endelea kurekodi.",
        "KSh $balance zimebaki, hakuna burn rate bado — endelea kulog."
    )

    fun runOutHeld(balance: String, committed: String, free: String, lang: AppLanguage): String = pick(
        lang,
        "KSh $balance held, but KSh $committed is committed or reserved — flexible: KSh $free.",
        "KSh $balance zimeshikiliwa, lakini KSh $committed zimewekwa akiba au zimeahidiwa — zinazoweza kupangwa: KSh $free.",
        "KSh $balance zimeshikiliwa, lakini KSh $committed ziko committed ama reserved — flexible: KSh $free."
    )

    fun runOutRunway(burn: String, free: String, days: String, committed: Double, lang: AppLanguage): String {
        val tail = if (committed > 0) pick(
            lang,
            " (after KSh ${committed.toInt()} commitments and reservations.)",
            " (baada ya KSh ${committed.toInt()} zilizowekwa akiba na ahadi.)",
            " (after KSh ${committed.toInt()} commitments na reservations.)"
        ) else ""
        return pick(
            lang,
            "At ~KSh $burn/day, free KSh $free lasts ~$days days.$tail",
            "Kwa ~KSh $burn/siku, KSh $free huria zitatosha ~siku $days.$tail",
            "Kwa ~KSh $burn/day, free KSh $free italast ~siku $days.$tail"
        )
    }

    fun billsDueNone(lang: AppLanguage): String = pick(
        lang,
        "Nothing due in the next 7 days. 🎉",
        "Hakuna kinachodaiwa katika siku 7 zijazo. 🎉",
        "Hakuna inadaiwa in the next 7 days. 🎉"
    )

    fun billsDue(list: String, lang: AppLanguage): String = pick(
        lang,
        "Due this week: $list.",
        "Zinazodaiwa wiki hii: $list.",
        "Due hii week: $list."
    )

    fun weekEmpty(lang: AppLanguage): String = pick(
        lang,
        "No transactions recorded yet.",
        "Hakuna miamala iliyorekodiwa bado.",
        "Hakuna transactions zimerecordiwa bado."
    )

    fun weekSpend(week: String, perDay: String, lang: AppLanguage): String = pick(
        lang,
        "This week (Mon–today) ume-spend roughly KSh $week. That's about KSh $perDay per day.",
        "Wiki hii (Jumatatu–leo) umetumia takriban KSh $week. Hiyo ni takriban KSh $perDay kwa siku.",
        "Hii week (Mon–today) umespend roughly KSh $week. Hiyo ni about KSh $perDay per day."
    )

    fun budgetNone(lang: AppLanguage): String = pick(
        lang,
        "You haven't set a monthly budget yet — add one on the Budget tab (use category ALL) and I'll watch it for you.",
        "Hujawa na bajeti ya kila mwezi bado — weka moja kwenye kichupo cha Budget (tumia category ALL) nami nitaifuatilia.",
        "Huna monthly budget bado — weka moja kwa Budget tab (tumia category ALL) nami nitaiwatch."
    )

    fun budgetStatus(budget: String, spent: String, pct: Int, lang: AppLanguage): String {
        val verdict = if (pct >= 100) pick(
            lang,
            "Umekross the line — cut non-essentials for the rest of the month. ⚠️",
            "Umekross mstari — punguza yasiyo ya lazima kwa mwezi uliobaki. ⚠️",
            "Umekross line — cut non-essentials kwa rest ya month. ⚠️"
        ) else if (pct >= 80) pick(
            lang,
            "Careful — you're at $pct%. Slow down on variable spending.",
            "Tahadhari — uko $pct%. Punguza matumizi yanayobadilika.",
            "Careful — uko $pct%. Slow down kwa variable spending."
        ) else pick(
            lang,
            "Budget iko safe so far. 👌",
            "Bajeti iko salama hadi sasa. 👌",
            "Budget iko safe so far. 👌"
        )
        return pick(
            lang,
            "Budget: KSh $budget monthly. Spent KSh $spent ($pct%). $verdict",
            "Bajeti: KSh $budget kwa mwezi. Umetumia KSh $spent ($pct%). $verdict",
            "Budget: KSh $budget monthly. Umespend KSh $spent ($pct%). $verdict"
        )
    }

    fun safeNone(lang: AppLanguage): String = pick(
        lang,
        "Add some income/expenses or set a budget first, then I'll compute your safe daily spend.",
        "Weka mapato/matumizi au bajeti kwanza, kisha nitahesabu matumizi salama ya kila siku.",
        "Weka income/expenses ama set budget kwanza, then nitacompute safe daily spend yako."
    )

    // ---------- legacy answers: biggest, balance, afford, split, transport ----------

    fun biggestNone(lang: AppLanguage): String = pick(
        lang,
        "No expenses recorded yet — I can't rank what doesn't exist. Add a few transactions first.",
        "Hakuna matumizi yaliyorekodiwa bado — siwezi kupanga kisichopo. Weka miamala michache kwanza.",
        "Hakuna expenses zimerecordiwa bado — siwezi kurank kitu haipo. Weka transactions kadhaa kwanza."
    )

    fun biggestTop(cat: String, value: String, month: String, lang: AppLanguage): String = pick(
        lang,
        "Your biggest expense this month is $cat: KSh $value out of KSh $month. That's where saving starts. 💡",
        "Matumizi yako makubwa zaidi mwezi huu ni $cat: KSh $value kati ya KSh $month. Ndipo kuweka akiba kunapoanzia. 💡",
        "Biggest expense yako hii month ni $cat: KSh $value out of KSh $month. Ndipo saving inapoanzia. 💡"
    )

    fun balanceLine(balance: String, income: String, expense: String, mpNote: String, lang: AppLanguage): String = pick(
        lang,
        "Available balance: KSh $balance. This month: KSh $income in, KSh $expense out.$mpNote",
        "Salio linalopatikana: KSh $balance. Mwezi huu: KSh $income ndani, KSh $expense nje.$mpNote",
        "Available balance: KSh $balance. Hii month: KSh $income in, KSh $expense out.$mpNote"
    )

    fun affordPrice(lang: AppLanguage): String = pick(
        lang,
        "Tell me the price — e.g. 'afford 500?' — and I'll check it against your balance and budget.",
        "Niambie bei — mfano 'afford 500?' — nami nitainganisha na salio lako na bajeti.",
        "Niambie price — mfano 'afford 500?' — nami nitaicheck na balance yako na budget."
    )

    fun affordYes(balance: String, free: String, committed: String, buffer: String, num: String, inEnvelope: Boolean, walletNote: String, lang: AppLanguage): String {
        val tail = if (inEnvelope) pick(
            lang,
            " Inside the monthly envelope too. ✅",
            " Ndani ya bahasha ya mwezi pia. ✅",
            " Ndani ya monthly envelope pia. ✅"
        ) else " ✅"
        return pick(
            lang,
            "Your current balance is KSh $balance, and flexible money is KSh $free — KSh $committed is committed and KSh $buffer is your safety buffer. Yes — KSh $num fits.$tail$walletNote",
            "Salio lako la sasa ni KSh $balance, na pesa inayoweza kupangwa ni KSh $free — KSh $committed imewekwa akiba na KSh $buffer ni akiba yako ya dharura. Ndiyo — KSh $num inatosha.$tail$walletNote",
            "Current balance yako ni KSh $balance, na flexible money ni KSh $free — KSh $committed iko committed na KSh $buffer ni safety buffer yako. Yes — KSh $num inatosha.$tail$walletNote"
        )
    }

    fun affordNo(balance: String, free: String, committed: String, buffer: String, num: String, budgetLeft: String?, walletNote: String, lang: AppLanguage): String {
        val tail = (budgetLeft?.let { left ->
            pick(
                lang,
                " Only KSh $left monthly budget left.",
                " KSh $left tu ya bajeti ya mwezi imebaki.",
                " KSh $left tu ya monthly budget imebaki."
            )
        } ?: "") + walletNote + pick(
            lang,
            " Sleep on it? 😴",
            " Lala juu yake? 😴",
            " Lala juu yake? 😴"
        )
        return pick(
            lang,
            "Your current balance is KSh $balance, but flexible money is KSh $free because KSh $committed is committed and KSh $buffer is reserved as your safety buffer. Careful with KSh $num.$tail",
            "Salio lako la sasa ni KSh $balance, lakini pesa inayoweza kupangwa ni KSh $free kwa sababu KSh $committed imewekwa akiba na KSh $buffer imetengwa kama akiba ya dharura. Kuwa mwangalifu na KSh $num.$tail",
            "Current balance yako ni KSh $balance, lakini flexible money ni KSh $free kwa sababu KSh $committed iko committed na KSh $buffer imereserve kama safety buffer yako. Kuwa careful na KSh $num.$tail"
        )
    }

    fun splitHelp(lang: AppLanguage): String = pick(
        lang,
        "Open the Semester tab → rent splitter: set roommates 1–8 and per-person share updates live. Any budget can also be shared from the Budget tab. 🤝",
        "Fungua kichupo cha Semester → mgawanyo wa kodi: weka wapangaji 1–8 na fungu la kila mtu litasasishwa moja kwa moja. Bajeti yoyote pia inaweza kushirikishwa kutoka kichupo cha Budget. 🤝",
        "Fungua Semester tab → rent splitter: set roommates 1–8 na per-person share itaupdate live. Budget yoyote pia inaweza kushareiwa kutoka Budget tab. 🤝"
    )

    fun fareDaily(who: String, fig: String, free: String, lang: AppLanguage): String = pick(
        lang,
        "$who gives KSh $fig daily for fare (you entered) — next lands tomorrow morning. If it doesn't arrive, your flexible KSh $free covers it. 🚌",
        "$who anakupa KSh $fig kila siku kwa nauli (uliingiza) — inayofuata inafika kesho asubuhi. Isipofika, KSh $free zako zinazoweza kupangwa zinatosha. 🚌",
        "$who anakupea KSh $fig daily kwa fare (uliingiza) — next inaland kesho morning. Isipofika, flexible KSh $free yako inatosha. 🚌"
    )

    fun fareWeekly(who: String, fig: String, free: String, lang: AppLanguage): String = pick(
        lang,
        "$who gives KSh $fig weekly for fare (you entered rhythm, ~7 days). Flexible right now: KSh $free. 🚌",
        "$who anakupa KSh $fig kila wiki kwa nauli (uliingiza rhythm, ~siku 7). Zinazoweza kupangwa sasa: KSh $free. 🚌",
        "$who anakupea KSh $fig weekly kwa fare (uliingiza rhythm, ~7 days). Flexible saa hii: KSh $free. 🚌"
    )

    fun transportNone(lang: AppLanguage): String = pick(
        lang,
        "No spending logged yet — I'll estimate transport once your ledger has data.",
        "Hakuna matumizi yaliyorekodiwa bado — nitakadiria nauli ledger yako ikiwa na data.",
        "Hakuna spending imelog bado — nitaestimate transport ledger yako ikiwa na data."
    )

    // ---------- legacy answers: yesterday, timetable, summary, verdict, bills, debts ----------

    fun yesterdaySpend(yesterday: String, today: String, lang: AppLanguage): String = pick(
        lang,
        "Yesterday uli-spend KSh $yesterday. Today so far: KSh $today.",
        "Jana ulitumia KSh $yesterday. Leo hadi sasa: KSh $today.",
        "Yesterday ulispend KSh $yesterday. Today so far: KSh $today."
    )

    fun timetableEmpty(lang: AppLanguage): String = pick(
        lang,
        "Timetable iko empty — set your lecture week under More → Meal Planner, then ask me. 🗓️",
        "Ratiba iko tupu — weka wiki yako ya mihadhara chini ya More → Meal Planner, kisha niulize. 🗓️",
        "Timetable iko empty — set lecture week yako chini ya More → Meal Planner, then niulize. 🗓️"
    )

    fun dayFree(day: String, lang: AppLanguage): String = pick(
        lang,
        "$day uko free — bulk-cook evening, week sorted. 🍱",
        "$day uko huru — jioni ya kupika kwa wingi, wiki iko sawa. 🍱",
        "$day uko free — bulk-cook evening, week sorted. 🍱"
    )

    fun dayBusy(day: String, slots: String, tip: String, lang: AppLanguage): String = pick(
        lang,
        "$day busy: $slots. $tip",
        "$day una shughuli: $slots. $tip",
        "$day busy: $slots. $tip"
    )

    fun eveningsBusy(lang: AppLanguage): String = pick(
        lang,
        "Every evening busy — Sunday ndio bulk-cook day. 🍱",
        "Kila jioni kuna shughuli — Jumapili ndiyo siku ya kupika kwa wingi. 🍱",
        "Kila evening busy — Sunday ndio bulk-cook day. 🍱"
    )

    fun freeEvenings(list: String, lang: AppLanguage): String = pick(
        lang,
        "Free evenings: $list — best bulk-cook nights. 🍱",
        "Jioni huru: $list — nyakati bora za kupika kwa wingi. 🍱",
        "Free evenings: $list — best bulk-cook nights. 🍱"
    )

    fun heaviestDay(day: String, count: String, lang: AppLanguage): String = pick(
        lang,
        "Heaviest: $day ($count slots). Ask 'busy wednesday?' for a day.",
        "Nzito zaidi: $day (vipindi $count). Uliza 'busy wednesday?' kwa siku.",
        "Heaviest: $day ($count slots). Uliza 'busy wednesday?' kwa siku."
    )

    fun summaryEmpty(lang: AppLanguage): String = pick(
        lang,
        "No data yet — add transactions and I'll summarize them here.",
        "Hakuna data bado — weka miamala nami nitayafupisha hapa.",
        "Hakuna data bado — weka transactions nami nitazisummarize hapa."
    )

    fun summaryLine(income: String, expense: String, balance: String, top: String, lang: AppLanguage): String = pick(
        lang,
        "Month so far: KSh $income in, KSh $expense out, balance KSh $balance. Top: $top.",
        "Mwezi hadi sasa: KSh $income ndani, KSh $expense nje, salio KSh $balance. Juu: $top.",
        "Month hadi sasa: KSh $income in, KSh $expense out, balance KSh $balance. Top: $top."
    )

    fun verdictEmpty(lang: AppLanguage): String = pick(
        lang,
        "No data yet — log income and spending for a week, then ask me again.",
        "Hakuna data bado — rekodi mapato na matumizi kwa wiki, kisha niulize tena.",
        "Hakuna data bado — log income na spending kwa week, then niulize tena."
    )

    fun verdictOver(nmEx: String, diff: String, cat: String, lang: AppLanguage): String = pick(
        lang,
        "Honestly$nmEx? You're spending above income by KSh $diff — trim $cat first. 🛑",
        "Ukweli$nmEx? Unatumia zaidi ya mapato kwa KSh $diff — punguza $cat kwanza. 🛑",
        "Honestly$nmEx? Unaspend juu ya income na KSh $diff — trim $cat kwanza. 🛑"
    )

    fun verdictOnTrack(nmEx: String, kept: String, income: String, lang: AppLanguage): String = pick(
        lang,
        "You're on track$nmEx: keeping KSh $kept of KSh $income income. Endelea hivyo! ✅",
        "Uko sawa$nmEx: unabakiza KSh $kept kati ya KSh $income ya mapato. Endelea hivyo! ✅",
        "Uko on track$nmEx: unakeep KSh $kept out of KSh $income income. Endelea hivyo! ✅"
    )

    fun verdictNoIncome(spent: String, lang: AppLanguage): String = pick(
        lang,
        "KSh $spent spent, no income logged — add income (tap + Income) for a real verdict.",
        "KSh $spent zimetumika, hakuna mapato yaliyorekodiwa — weka mapato (gusa + Income) kwa hukumu ya kweli.",
        "KSh $spent zimespendiwa, hakuna income imelog — weka income (tap + Income) kwa real verdict."
    )

    fun categorySpend(cat: String, fig: String, lang: AppLanguage): String = pick(
        lang,
        "Spending on $cat: KSh $fig this month.",
        "Matumizi ya $cat: KSh $fig mwezi huu.",
        "Spending ya $cat: KSh $fig hii month."
    )

    fun billsNone(lang: AppLanguage): String = pick(
        lang,
        "No open bills — nyumba iko sorted. 🎉",
        "Hakuna bili zinazodaiwa — nyumba iko sawa. 🎉",
        "Hakuna open bills — nyumba iko sorted. 🎉"
    )

    fun billWho(paidBy: String, lang: AppLanguage): String = when (paidBy) {
        "ME" -> pick(lang, "you", "wewe", "wewe")
        "PARENTS" -> pick(lang, "parents", "wazazi", "parents")
        "SPONSOR" -> pick(lang, "sponsor", "mfadhili", "sponsor")
        "HELB" -> "HELB"
        else -> pick(lang, "someone else", "mtu mwingine", "mtu mwingine")
    }

    fun billsLine(count: String, mine: String, theirs: String, list: String, lang: AppLanguage): String = pick(
        lang,
        "You have $count open bill(s): KSh $mine marked for you and KSh $theirs marked for someone else to cover. $list.",
        "Una bili $count zinazodaiwa: KSh $mine zimeandikwa kwako na KSh $theirs ziandikwe na mtu mwingine. $list.",
        "Una open bills $count: KSh $mine marked kwako na KSh $theirs marked mtu mwingine alipe. $list."
    )

    fun debtsNone(lang: AppLanguage): String = pick(
        lang,
        "No open debts on record. Clean slate! 🎉",
        "Hakuna madeni kwenye rekodi. Ukurasa safi! 🎉",
        "Hakuna open debts kwa record. Clean slate! 🎉"
    )

    fun debtsLine(count: String, total: String, list: String, lang: AppLanguage): String = pick(
        lang,
        "Open debts: $count totalling KSh $total: $list.",
        "Madeni wazi: $count yenye jumla ya KSh $total: $list.",
        "Open debts: $count totalling KSh $total: $list."
    )

    // ---------- legacy answers: meals, progress, comparisons, goals, income ----------

    fun menuEmpty(lang: AppLanguage): String = pick(
        lang,
        "No foods saved yet — open Meal Planner (More tab), add starch + mboga with prices, then generate a week menu.",
        "Hakuna vyakula vilivyohifadhiwa bado — fungua Meal Planner (kichupo cha More), weka wanga + mboga na bei, kisha tengeneza menyu ya wiki.",
        "Hakuna foods zimesave bado — fungua Meal Planner (More tab), weka starch + mboga na prices, then generate week menu."
    )

    fun menuLine(count: String, foodBudget: String?, perDay: String?, lang: AppLanguage): String {
        val tail = if (foodBudget != null) pick(
            lang,
            " with a KSh $foodBudget monthly food budget (KSh $perDay/day).",
            " na bajeti ya chakula ya KSh $foodBudget kwa mwezi (KSh $perDay/siku).",
            " na monthly food budget ya KSh $foodBudget (KSh $perDay/day)."
        ) else pick(
            lang,
            ". Set a Food budget so menus stay under it.",
            ". Weka bajeti ya Chakula ili menyu zibaki chini yake.",
            ". Weka Food budget ili menus zikae chini yake."
        )
        return pick(
            lang,
            "You have $count foods saved$tail Generate Day/Week/Month/Semester menus from the planner.",
            "Una vyakula $count vilivyohifadhiwa$tail Tengeneza menyu za Siku/Wiki/Mwezi/Muhula kutoka kwa mpangaji.",
            "Una foods $count zimesave$tail Generate Day/Week/Month/Semester menus kutoka kwa planner."
        )
    }

    fun bye(lang: AppLanguage): String = pick(
        lang,
        "Baadaye! 👋 Kumbuka: log it the moment you spend it.",
        "Baadaye! 👋 Kumbuka: rekodi papo hapo unapotumia.",
        "Baadaye! 👋 Kumbuka: log papo hapo unapospend."
    )

    fun cutTipFood(pct: String, save: String, cookAtHome: Boolean, lang: AppLanguage): String = pick(
        lang,
        if (cookAtHome) "Food is $pct% of spending — cooking at home 3x/week could save KSh $save/month"
        else "Food is $pct% of spending — kibanda lunch plates over fast food could save KSh $save/month",
        if (cookAtHome) "Chakula ni $pct% ya matumizi — kupika nyumbani mara 3 kwa wiki kunaweza kuokoa KSh $save/mwezi"
        else "Chakula ni $pct% ya matumizi — sahani za kibanda badala ya fast food zinaweza kuokoa KSh $save/mwezi",
        if (cookAtHome) "Food ni $pct% ya spending — kupika home 3x/week kunaweza kusave KSh $save/month"
        else "Food ni $pct% ya spending — sahani za kibanda badala ya fast food zinaweza kusave KSh $save/month"
    )

    fun cutTipTransport(far: Boolean, lang: AppLanguage): String = pick(
        lang,
        if (far) "Transport is high — off-peak travel or the early bus beats peak fares that run ~2x"
        else "Transport is high — try shared rides or walking short distances",
        if (far) "Nauli iko juu — safiri nje ya msongamano au basi ya mapema hushinda nauli za peak zinazokaribia mara 2"
        else "Nauli iko juu — jaribu usafiri wa pamoja au kutembea umbali mfupi",
        if (far) "Transport iko high — off-peak travel ama early bus hushinda peak fares zinazokimbia ~2x"
        else "Transport iko high — jaribu shared rides ama kuwalk short distances"
    )

    fun cutTipKujibamba(pct: String, lang: AppLanguage): String = pick(
        lang,
        "Kujibamba (grooming) is $pct% — cut back to save",
        "Kujibamba ni $pct% — punguza ili uweke akiba",
        "Kujibamba ni $pct% — punguza ili usave"
    )

    fun cutTipAirtime(fig: String, lang: AppLanguage): String = pick(
        lang,
        "Airtime KSh $fig — switch to WiFi bundles for data",
        "Airtime KSh $fig — hamia kwenye vifurushi vya WiFi kwa data",
        "Airtime KSh $fig — hamia kwa WiFi bundles kwa data"
    )

    fun cutTipBalanced(lang: AppLanguage): String = pick(
        lang,
        "Your spending looks balanced! Try the 24-hour rule on non-essentials.",
        "Matumizi yako yanaonekana sawia! Jaribu kanuni ya saa 24 kwa yasiyo ya lazima.",
        "Spending yako inaonekana balanced! Jaribu 24-hour rule kwa non-essentials."
    )

    fun cutHead(list: String, lang: AppLanguage): String = pick(
        lang,
        "Here's what to cut: $list.",
        "Hivi ndivyo vya kupunguza: $list.",
        "Hivi ndivyo vya kukata: $list."
    )

    fun trackNone(lang: AppLanguage): String = pick(
        lang,
        "No monthly budget set — add one on the Budget tab and I'll track your progress. 📊",
        "Hakuna bajeti ya mwezi — weka moja kwenye kichupo cha Budget nami nitafuatilia maendeleo yako. 📊",
        "Hakuna monthly budget — weka moja kwa Budget tab nami nitatrack progress yako. 📊"
    )

    fun trackOnTrack(spent: String, expected: String, day: String, pct: String, lang: AppLanguage): String = pick(
        lang,
        "On track! You've spent KSh $spent vs KSh $expected expected by day $day. $pct% of budget. ✅",
        "Uko sawa! Umetumia KSh $spent dhidi ya KSh $expected iliyotarajiwa hadi siku $day. $pct% ya bajeti. ✅",
        "Uko on track! Umespend KSh $spent vs KSh $expected expected hadi siku $day. $pct% ya budget. ✅"
    )

    fun trackOver(diff: String, spent: String, expected: String, pct: String, days: String, lang: AppLanguage): String = pick(
        lang,
        "Over by KSh $diff (KSh $spent vs KSh $expected expected). $pct% used with $days days left. ⚠️",
        "Umezidi kwa KSh $diff (KSh $spent dhidi ya KSh $expected iliyotarajiwa). $pct% imetumika na siku $days zimebaki. ⚠️",
        "Umezidi na KSh $diff (KSh $spent vs KSh $expected expected). $pct% used na siku $days zimebaki. ⚠️"
    )

    fun compareThin(lang: AppLanguage): String = pick(
        lang,
        "Not enough current-month expense records to compare; missing records are not evidence of zero spending. Log or scan this month's transactions first. 📊",
        "Hakuna rekodi za kutosha za matumizi ya mwezi huu za kulinganisha; rekodi zinazokosekana si ushahidi wa matumizi sifuri. Rekodi au scan miamala ya mwezi huu kwanza. 📊",
        "Hakuna current-month expense records za kutosha za kucompare; missing records si evidence ya zero spending. Log ama scan transactions za hii month kwanza. 📊"
    )

    fun compareNoLast(lang: AppLanguage): String = pick(
        lang,
        "No data from the matching days last month to compare — keep logging! 📊",
        "Hakuna data ya siku zinazolingana mwezi uliopita za kulinganisha — endelea kurekodi! 📊",
        "Hakuna data ya matching days za last month za kucompare — endelea kulog! 📊"
    )

    fun compareNote(day: String, lang: AppLanguage): String = pick(
        lang,
        "Recorded spending through day $day this month vs the same number of calendar days last month; this reflects ledger entries, not necessarily complete SMS history. ",
        "Matumizi yaliyorekodiwa hadi siku $day mwezi huu dhidi ya idadi sawa ya siku za kalenda mwezi uliopita; hii inaakisi ingizo za ledger, si lazima historia kamili ya SMS. ",
        "Recorded spending hadi siku $day hii month vs same number ya calendar days last month; hii inaakisi ledger entries, si lazima complete SMS history. "
    )

    fun compareLower(month: String, last: String, pct: String, lang: AppLanguage): String = pick(
        lang,
        "This month: KSh $month vs last month KSh $last (${pct}% lower).",
        "Mwezi huu: KSh $month dhidi ya mwezi uliopita KSh $last (chini kwa $pct%).",
        "Hii month: KSh $month vs last month KSh $last (${pct}% lower)."
    )

    fun compareHigher(month: String, last: String, pct: String, cat: String, lang: AppLanguage): String = pick(
        lang,
        "This month: KSh $month vs last month KSh $last ($pct% higher) — watch $cat.",
        "Mwezi huu: KSh $month dhidi ya mwezi uliopita KSh $last (juu kwa $pct%) — angalia $cat.",
        "Hii month: KSh $month vs last month KSh $last ($pct% higher) — watch $cat."
    )

    fun breakdownNone(lang: AppLanguage): String = pick(
        lang,
        "No expenses to break down yet.",
        "Hakuna matumizi ya kuchambua bado.",
        "Hakuna expenses za kubreak down bado."
    )

    fun breakdownLines(list: String, total: String, lang: AppLanguage): String = pick(
        lang,
        "Your categories: $list. Total KSh $total.",
        "Makundi yako: $list. Jumla KSh $total.",
        "Categories zako: $list. Total KSh $total."
    )

    fun savingsNone(lang: AppLanguage): String = pick(
        lang,
        "No savings goals set. Add one on the Savings tab to start tracking. 🎯",
        "Hakuna malengo ya akiba. Weka moja kwenye kichupo cha Savings uanze kufuatilia. 🎯",
        "Hakuna savings goals. Weka moja kwa Savings tab uanze kutrack. 🎯"
    )

    fun savingsLine(saved: String, target: String, pct: String, goals: String, lang: AppLanguage): String = pick(
        lang,
        "You've saved KSh $saved of KSh $target ($pct%) across $goals goals.",
        "Umeweka akiba KSh $saved kati ya KSh $target ($pct%) kwenye malengo $goals.",
        "Umesave KSh $saved out of KSh $target ($pct%) across goals $goals."
    )

    fun foodMonth(fig: String, budgetTail: String?, lang: AppLanguage): String {
        val tail = budgetTail ?: pick(
            lang,
            ". Set a Food budget on the Budget tab.",
            ". Weka bajeti ya Chakula kwenye kichupo cha Budget.",
            ". Weka Food budget kwa Budget tab."
        )
        return pick(
            lang,
            "Food this month: KSh $fig$tail",
            "Chakula mwezi huu: KSh $fig$tail",
            "Food hii month: KSh $fig$tail"
        )
    }

    fun foodBudgetState(budget: String, under: Boolean, lang: AppLanguage): String = pick(
        lang,
        if (under) " Budget KSh $budget — under budget ✅" else " Budget KSh $budget — over budget ⚠️",
        if (under) " Bajeti KSh $budget — chini ya bajeti ✅" else " Bajeti KSh $budget — juu ya bajeti ⚠️",
        if (under) " Budget KSh $budget — under budget ✅" else " Budget KSh $budget — over budget ⚠️"
    )

    fun rentMonth(fig: String, lang: AppLanguage): String = pick(
        lang,
        "Rent this month: KSh $fig.",
        "Kodi mwezi huu: KSh $fig.",
        "Rent hii month: KSh $fig."
    )

    fun goalPrice(lang: AppLanguage): String = pick(
        lang,
        "Tell me the price and deadline — e.g. 'laptop 10000 by December' and I'll check if it's possible.",
        "Niambie bei na tarehe ya mwisho — mfano 'laptop 10000 by December' nami nitaangalia kama inawezekana.",
        "Niambie price na deadline — mfano 'laptop 10000 by December' nami nitacheck kama inawezekana."
    )

    fun goalSaved(saved: String, price: String, lang: AppLanguage): String = pick(
        lang,
        "Your goals record KSh $saved toward this KSh $price target. Check that amount is available to withdraw before buying. ✅",
        "Malengo yako yanaonyesha KSh $saved kuelekea lengo hili la KSh $price. Hakikisha kiasi hicho kinapatikana kutoa kabla ya kununua. ✅",
        "Goals zako zinaonyesha KSh $saved kuelekea hii target ya KSh $price. Check kama hiyo amount iko available kutoa kabla ya kununua. ✅"
    )

    fun goalFits(needed: String, days: String, lang: AppLanguage): String = pick(
        lang,
        "Yes, the target fits the current plan: set aside KSh $needed/day for $days days. This uses flexible cash after commitments and the monthly envelope. 💪",
        "Ndiyo, lengo linaingia kwenye mpango wa sasa: weka kando KSh $needed/siku kwa siku $days. Hii inatumia pesa huria baada ya ahadi na bahasha ya mwezi. 💪",
        "Yes, target inafit kwa current plan: weka kando KSh $needed/day kwa siku $days. Hii inatumia flexible cash after commitments na monthly envelope. 💪"
    )

    fun goalTight(needed: String, cat: String, lang: AppLanguage): String = pick(
        lang,
        "Tight but doable — save KSh $needed/day. You'd need to cut $cat by that much. 🤔",
        "Ni kubana lakini inawezekana — weka KSh $needed/siku. Itakubidi kupunguza $cat kwa kiasi hicho. 🤔",
        "Ni tight lakini doable — save KSh $needed/day. Itakubidi kukata $cat na hiyo amount. 🤔"
    )

    fun goalMiss(price: String, month: String, needed: String, surplus: String, lang: AppLanguage): String = pick(
        lang,
        "KSh $price by $month needs KSh $needed/day. That's more than your surplus of KSh $surplus/day. Consider extending the deadline or cutting costs. ⚠️",
        "KSh $price hadi $month inahitaji KSh $needed/siku. Hiyo ni zaidi ya ziada yako ya KSh $surplus/siku. Fikiria kuongeza muda au kupunguza gharama. ⚠️",
        "KSh $price by $month inahitaji KSh $needed/day. Hiyo ni zaidi ya surplus yako ya KSh $surplus/day. Fikiria kuextend deadline ama kukata costs. ⚠️"
    )

    fun goalsNone2(lang: AppLanguage): String = pick(
        lang,
        "No savings goals set yet — add one on the Savings tab and I'll track your progress. 🎯",
        "Hakuna malengo ya akiba bado — weka moja kwenye kichupo cha Savings nami nitafuatilia maendeleo yako. 🎯",
        "Hakuna savings goals bado — weka moja kwa Savings tab nami nitatrack progress yako. 🎯"
    )

    fun goalsLine(summary: String, lang: AppLanguage): String = pick(
        lang,
        "Your goals: $summary",
        "Malengo yako: $summary",
        "Goals zako: $summary"
    )

    fun incomeLine(earned: String, topTail: String, declTail: String, lang: AppLanguage): String = pick(
        lang,
        "Earned income recorded this month: KSh $earned.$topTail$declTail",
        "Mapato yaliyopatikana mwezi huu: KSh $earned.$topTail$declTail",
        "Earned income imerecordiwa hii month: KSh $earned.$topTail$declTail"
    )

    fun incomeTop(cat: String, fig: String, lang: AppLanguage): String = pick(
        lang,
        " Mostly: $cat KSh $fig.",
        " Kwa sehemu kubwa: $cat KSh $fig.",
        " Mostly: $cat KSh $fig."
    )

    fun incomeTopNone(lang: AppLanguage): String = pick(
        lang,
        " Log income to see where it comes from.",
        " Rekodi mapato uone yanakotoka.",
        " Log income uone zinatoka wapi."
    )

    fun incomeDeclared(fig: String, names: String, lang: AppLanguage): String = pick(
        lang,
        " Declared sources total ~KSh $fig/month ($names); this is an expectation, not cash held or a guaranteed next payment.",
        " Vyanzo vilivyotangazwa jumla ~KSh $fig/mwezi ($names); hili ni matarajio, si pesa mkononi wala malipo yajayo ya uhakika.",
        " Declared sources total ~KSh $fig/month ($names); hii ni expectation, si cash mkononi ama guaranteed next payment."
    )

    fun spendLine(month: String, topTail: String, lang: AppLanguage): String = pick(
        lang,
        "This month's expenses: KSh $month.$topTail",
        "Matumizi ya mwezi huu: KSh $month.$topTail",
        "Expenses za hii month: KSh $month.$topTail"
    )

    fun spendTop(list: String, lang: AppLanguage): String = pick(
        lang,
        " Top: $list.",
        " Juu: $list.",
        " Top: $list."
    )

    fun savedLine(total: String, goals: String, lang: AppLanguage): String = pick(
        lang,
        "Total saved: KSh $total. You have $goals active savings goals.",
        "Jumla iliyowekwa akiba: KSh $total. Una malengo $goals amilifu ya akiba.",
        "Total saved: KSh $total. Una active savings goals $goals."
    )

    fun foodSimple(total: String, cookTip: Boolean, lang: AppLanguage): String {
        val tip = if (cookTip) pick(
            lang,
            "Kibanda plates beat fast food for the same stomach!",
            "Sahani za kibanda hushinda fast food kwa tumbo lile lile!",
            "Sahani za kibanda hushinda fast food kwa tumbo lile lile!"
        ) else pick(
            lang,
            "Consider cooking at home to save money!",
            "Fikiria kupika nyumbani ili uweke akiba!",
            "Fikiria kupika home ili usave!"
        )
        return pick(
            lang,
            "Your food expenses: KSh $total. $tip",
            "Matumizi yako ya chakula: KSh $total. $tip",
            "Food expenses zako: KSh $total. $tip"
        )
    }

    // ---------- legacy answers: stock, kitchen, survival ----------

    fun howMuchRemaining(fig: String, lang: AppLanguage): String = pick(
        lang,
        "You have KSh $fig remaining available balance.",
        "Una KSh $fig salio linalopatikana lililobaki.",
        "Una KSh $fig remaining available balance."
    )

    fun howMuchSaved(total: String, goals: String, lang: AppLanguage): String = pick(
        lang,
        "Total savings: KSh $total. You have $goals active savings goals.",
        "Jumla ya akiba: KSh $total. Una malengo $goals amilifu ya akiba.",
        "Total savings: KSh $total. Una active savings goals $goals."
    )

    fun howMuchBase(avail: String, income: String, expense: String, lang: AppLanguage): String = pick(
        lang,
        "Based on your data: KSh $avail available, KSh $income income, KSh $expense expenses this month.",
        "Kutokana na data yako: KSh $avail zinazopatikana, KSh $income mapato, KSh $expense matumizi mwezi huu.",
        "Kutokana na data yako: KSh $avail available, KSh $income income, KSh $expense expenses hii month."
    )

    fun lackNone(lang: AppLanguage): String = pick(
        lang,
        "Nothing on your need list — add clothes, books and the rest under More → My Things. 🎒",
        "Hakuna chochote kwenye orodha yako ya mahitaji — weka nguo, vitabu na vingine chini ya More → My Things. 🎒",
        "Hakuna kitu kwa need list yako — weka nguo, books na zingine chini ya More → My Things. 🎒"
    )

    fun lackLine(count: String, list: String, total: String, lang: AppLanguage): String = pick(
        lang,
        "You lack $count: $list. Owning it all costs ~KSh $total.",
        "Unakosa $count: $list. Kumiliki vyote kunagharimu ~KSh $total.",
        "Unalack $count: $list. Kumiliki vyote ni ~KSh $total."
    )

    fun haveNone(lang: AppLanguage): String = pick(
        lang,
        "You haven't listed anything yet — More → My Things takes 30 seconds. 🎒",
        "Hujaorodhesha chochote bado — More → My Things inachukua sekunde 30. 🎒",
        "Huja-list chochote bado — More → My Things inachukua seconds 30. 🎒"
    )

    fun haveNeedsOnly(lang: AppLanguage): String = pick(
        lang,
        "Everything listed is still on the need side — no haves yet. 💪",
        "Kila kilichoorodheshwa bado kiko upande wa mahitaji — hakuna ulivyo navyo bado. 💪",
        "Kila kitu listed kiko bado kwa need side — hakuna haves bado. 💪"
    )

    fun haveLine(count: String, list: String, lang: AppLanguage): String = pick(
        lang,
        "You have $count: $list.",
        "Una $count: $list.",
        "Una $count: $list."
    )

    fun stockEmpty(lang: AppLanguage): String = pick(
        lang,
        "Cupboard is empty on record — add unga, oil and friends under More → Kitchen Stock. 🫙",
        "Kabati liko tupu kwenye rekodi — weka unga, mafuta na wenzao chini ya More → Kitchen Stock. 🫙",
        "Cupboard iko empty kwa record — weka unga, oil na wenzao chini ya More → Kitchen Stock. 🫙"
    )

    fun stockLine(list: String, lang: AppLanguage): String = pick(
        lang,
        "Cupboard: $list.",
        "Kabati: $list.",
        "Cupboard: $list."
    )

    fun howLongNone(lang: AppLanguage): String = pick(
        lang,
        "No stock tracked — add your kitchen items first, then ask me. 🫙",
        "Hakuna stock inayofuatiliwa — weka vitu vyako vya jikoni kwanza, kisha niulize. 🫙",
        "Hakuna stock inatrackiwa — weka kitchen items zako kwanza, then niulize. 🫙"
    )

    fun howLongBare(lang: AppLanguage): String = pick(
        lang,
        "No stock tracked.",
        "Hakuna stock inayofuatiliwa.",
        "Hakuna stock inatrackiwa."
    )

    fun howLongFirst(name: String, days: String, cost: String, lang: AppLanguage): String = pick(
        lang,
        "$name runs out first: ~$days day(s) left, refill ≈ KSh $cost.",
        "$name inaisha kwanza: ~siku $days zimebaki, jaza ≈ KSh $cost.",
        "$name inaisha kwanza: ~siku $days zimebaki, refill ≈ KSh $cost."
    )

    fun replenishNone(lang: AppLanguage): String = pick(
        lang,
        "Nothing to replenish on record — More → Kitchen Stock first. 🫙",
        "Hakuna cha kujaza kwenye rekodi — More → Kitchen Stock kwanza. 🫙",
        "Hakuna cha kureplenish kwa record — More → Kitchen Stock kwanza. 🫙"
    )

    fun replenishLine(list: String, total: String, lang: AppLanguage): String = pick(
        lang,
        "Refill plan: $list. All-in: KSh $total.",
        "Mpango wa kujaza: $list. Jumla: KSh $total.",
        "Refill plan: $list. All-in: KSh $total."
    )

    fun buyFirstKitchen(name: String, days: String, lang: AppLanguage): String = pick(
        lang,
        "Kitchen first: $name (~${days}d left). ",
        "Jikoni kwanza: $name (~siku $days zimebaki). ",
        "Kitchen kwanza: $name (~siku $days zimebaki). "
    )

    fun buyFirstBuy(name: String, cost: String, lang: AppLanguage): String = pick(
        lang,
        "Then buy: $name (~KSh $cost).",
        "Kisha nunua: $name (~KSh $cost).",
        "Then nunua: $name (~KSh $cost)."
    )

    fun buyFirstCalm(lang: AppLanguage): String = pick(
        lang,
        "Nothing urgent — cupboard stocked, need-list empty. Enjoy the calm. 🎉",
        "Hakuna cha dharura — kabati limejaa, orodha ya mahitaji iko tupu. Furahia utulivu. 🎉",
        "Hakuna urgent — cupboard stocked, need-list empty. Enjoy the calm. 🎉"
    )

    fun rentHostel(fig: String, lang: AppLanguage): String = pick(
        lang,
        "Rent/hostel spending: KSh $fig on record.",
        "Matumizi ya kodi/hosteli: KSh $fig kwenye rekodi.",
        "Rent/hostel spending: KSh $fig kwa record."
    )

    fun airtimeSpend(fig: String, lang: AppLanguage): String = pick(
        lang,
        "Airtime + data spending: KSh $fig on record.",
        "Matumizi ya airtime + data: KSh $fig kwenye rekodi.",
        "Airtime + data spending: KSh $fig kwa record."
    )

    fun surviveEmpty(lang: AppLanguage): String = pick(
        lang,
        "I can't plan survival on an empty cupboard — add your unga & co under More → Kitchen Stock first. 🫙",
        "Siwezi kupanga kuishi kwa kabati tupu — weka unga wako & co chini ya More → Kitchen Stock kwanza. 🫙",
        "Siwezi kuplan survival kwa empty cupboard — weka unga wako & co chini ya More → Kitchen Stock kwanza. 🫙"
    )

    fun surviveGap(days: String, have: String, lang: AppLanguage): String = pick(
        lang,
        "Stock covers $days of $have days, and no Cook staples saved for the gap — add cheap ones in Meal Planner first. 🍳",
        "Stock inatosha siku $days kati ya $have, na hakuna vyakula vya Cook vilivyohifadhiwa kwa pengo — weka vya bei rahisi kwenye Meal Planner kwanza. 🍳",
        "Stock inacover siku $days out of $have, na hakuna Cook staples zimesave kwa gap — weka cheap ones kwa Meal Planner kwanza. 🍳"
    )

    fun surviveStockOnly(days: String, lang: AppLanguage): String = pick(
        lang,
        "Yes — your stock alone carries all $days days. Spend nothing. 💪",
        "Ndiyo — stock yako yenyewe inabeba siku zote $days. Usitumie chochote. 💪",
        "Yes — stock yako pekee inacarry siku zote $days. Spend nothing. 💪"
    )

    fun survivePossible(stockDays: String, cost: String, shopping: String, balance: String, lang: AppLanguage): String = pick(
        lang,
        "Yes — stock covers $stockDays days, top-up KSh $cost ($shopping), inside your KSh $balance.",
        "Ndiyo — stock inatosha siku $stockDays, ongeza KSh $cost ($shopping), ndani ya KSh $balance yako.",
        "Yes — stock inacover siku $stockDays, top-up KSh $cost ($shopping), ndani ya KSh $balance yako."
    )

    fun surviveTight(cost: String, balance: String, short: String, lang: AppLanguage): String = pick(
        lang,
        "Tight — top-up needs KSh $cost but you hold KSh $balance (short KSh $short).",
        "Kubana — kuongeza kunahitaji KSh $cost lakini unashikilia KSh $balance (upungufu KSh $short).",
        "Tight — top-up inahitaji KSh $cost lakini unashikilia KSh $balance (short KSh $short)."
    )

    fun cookedOne(name: String, left: String, unit: String, daysTail: String, lang: AppLanguage): String = pick(
        lang,
        "$name: burned a cooking day — ~$left $unit left$daysTail. Bar moved, enjoy. 🍳",
        "$name: siku moja ya kupika imeungua — ~$left $unit zimebaki$daysTail. Kipimo kimesonga, furahia. 🍳",
        "$name: siku moja ya kupika imeburn — ~$left $unit zimebaki$daysTail. Bar imemove, enjoy. 🍳"
    )

    fun cookedDays(days: String, lang: AppLanguage): String = pick(
        lang,
        " (~${days}d)",
        " (~siku $days)",
        " (~siku $days)"
    )

    fun cookedEmpty(lang: AppLanguage): String = pick(
        lang,
        "Cupboard is empty on record — add your stock under More → Kitchen Stock first. 🫙",
        "Kabati liko tupu kwenye rekodi — weka stock yako chini ya More → Kitchen Stock kwanza. 🫙",
        "Cupboard iko empty kwa record — weka stock yako chini ya More → Kitchen Stock kwanza. 🫙"
    )

    fun cookedAll(list: String, lang: AppLanguage): String = pick(
        lang,
        "Burned a cooking day off everything: $list. Bars moved, zero typing. 🍳",
        "Siku moja ya kupika imeungua kwa vyote: $list. Vipimo vimesonga, bila kuandika. 🍳",
        "Siku moja ya kupika imeburn kwa vyote: $list. Bars zimemove, zero typing. 🍳"
    )

    fun transportSimple(total: String, far: Boolean, lang: AppLanguage): String {
        val tip = if (far) pick(
            lang,
            "Long route — off-peak or the early bus dodges ~2x peak fares.",
            "Njia ndefu — nje ya msongamano au basi ya mapema huepuka nauli za peak zinazokaribia mara 2.",
            "Route ndefu — off-peak ama early bus huepuka ~2x peak fares."
        ) else pick(
            lang,
            "Consider using matatus or shared rides to reduce costs.",
            "Fikiria kutumia matatu au usafiri wa pamoja ili kupunguza gharama.",
            "Fikiria kutumia matatu ama shared rides ili kupunguza costs."
        )
        return pick(
            lang,
            "Your transport expenses: KSh $total. $tip",
            "Matumizi yako ya nauli: KSh $total. $tip",
            "Transport expenses zako: KSh $total. $tip"
        )
    }

    fun transportRoute(home: String, mode: String, stages: String?, lang: AppLanguage): String {
        val via = stages?.let {
            pick(lang, " via $it", " kupitia $it", " via $it")
        } ?: ""
        return pick(
            lang,
            "Your usual route: $home → campus by $mode$via. Log a fare and I'll track the real cost.",
            "Njia yako ya kawaida: $home → chuo kwa $mode$via. Rekodi nauli nami nitafuatilia gharama halisi.",
            "Route yako ya kawaida: $home → campus na $mode$via. Log fare nami nitatrack real cost."
        )
    }

    fun safeGuide(daily: String, days: String, committed: Double, lang: AppLanguage): String {
        val tail = if (committed > 0) pick(
            lang,
            " (KSh ${committed.toInt()} is committed or reserved.)",
            " (KSh ${committed.toInt()} imewekwa akiba au imeahidiwa.)",
            " (KSh ${committed.toInt()} iko committed ama reserved.)"
        ) else ""
        return pick(
            lang,
            "A cautious guide is KSh $daily per day for the remaining $days days. It is capped by today's safe-to-spend estimate, monthly budget remaining and flexible cash after obligations.$tail Hii ni estimate, not a guarantee.",
            "Mwongozo wa tahadhari ni KSh $daily kwa siku kwa siku $days zilizobaki. Umezuiwa na kadirio la salama la leo, salio la bajeti ya mwezi na pesa inayoweza kupangwa baada ya majukumu.$tail Hii ni kadirio, si hakikisho.",
            "Cautious guide ni KSh $daily per day kwa remaining $days days. Imecap na today's safe-to-spend estimate, monthly budget remaining na flexible cash after obligations.$tail Hii ni estimate, si guarantee."
        )
    }

    // ---------- simulations ----------

    fun simTag(lang: AppLanguage): String = pick(
        lang,
        "SIMULATED — your ledger is untouched.",
        "SIMULATED — ledger haijaguswa.",
        "SIMULATED — ledger haijaguswa."
    )

    fun simPurchase(spent: String, verdict: String, runway: String, lang: AppLanguage): String {
        val head = pick(
            lang,
            "If you spend $spent:",
            "Kama ukitumia $spent:",
            "Kama ukispend $spent:"
        )
        return "$head\n$verdict\n$runway\n${simTag(lang)}"
    }

    fun simPurchaseFlex(now: String, after: String, lang: AppLanguage): String = pick(
        lang,
        "Flexible money $now → $after.",
        "Pesa inayoweza kupangwa $now → $after.",
        "Flexible doh $now → $after."
    )

    fun simPurchaseOver(shortfall: String, lang: AppLanguage): String = pick(
        lang,
        "That exceeds flexible money by $shortfall — recorded commitments would be at risk.",
        "Hiyo inazidi pesa inayoweza kupangwa kwa $shortfall — ahadi zilizorekodiwa ziko hatarini.",
        "Hiyo inapita flexible doh na $shortfall — commitments zimerecordiwa ziko kwa risk."
    )

    fun simRunway(days: String, burn: String, lang: AppLanguage): String = pick(
        lang,
        "Runway cost ≈ $days days at $burn/day burn.",
        "Gharama ya muda ≈ siku $days kwa matumizi ya $burn/siku.",
        "Runway cost ≈ siku $days kwa burn ya $burn/day."
    )

    fun simRunwayUnknown(lang: AppLanguage): String = pick(
        lang,
        "Runway impact unknown — log a week of spending first.",
        "Athari kwa muda haijulikani — kwanza rekodi matumizi ya wiki moja.",
        "Runway impact haijulikani — kwanza log spend ya wiki moja."
    )

    fun simLate(days: Int, held: String, burn: String, left: String, missing: String, lang: AppLanguage): String {
        val head = pick(
            lang,
            "If income is $days days late:",
            "Mapato yakichelewa siku $days:",
            "Income ikichelewa siku $days:"
        )
        val math = pick(
            lang,
            "$held held now − $burn/day × $days = $left left.",
            "$held zilizopo − $burn/siku × $days = $left zilizobaki.",
            "$held kwa sasa − $burn/day × $days = $left inabaki."
        )
        val basis = pick(
            lang,
            "Assumes your current burn holds.",
            "Inadhania matumizi yako ya sasa yataendelea.",
            "Inaassume burn yako ya sasa itahold."
        )
        val miss = if (missing.isBlank()) "" else pick(
            lang, "\nMissing: $missing.", "\nInakosekana: $missing.", "\nMissing: $missing."
        )
        return "$head\n$math\n$basis$miss\n${simTag(lang)}"
    }

    fun simRent(old: String, target: String, effect: String, lang: AppLanguage): String = pick(
        lang,
        "If rent moves $old → $target:\nThat $effect, other spending unchanged.\n${simTag(lang)}",
        "Kodi ikihama $old → $target:\nHiyo $effect, matumizi mengine yakiwa vile vile.\n${simTag(lang)}",
        "Rent ikihama $old → $target:\nHiyo $effect, spend ingine ikiwa vile vile.\n${simTag(lang)}"
    )

    fun simRentFrees(delta: String, lang: AppLanguage): String = pick(
        lang, "frees $delta/mo", "inaachia $delta/mwezi", "inafree $delta/mo"
    )

    fun simRentCosts(delta: String, lang: AppLanguage): String = pick(
        lang, "costs $delta/mo extra", "inagharimu $delta/mwezi zaidi", "inacost $delta/mo extra"
    )

    fun askAmountForSpend(lang: AppLanguage): String = pick(
        lang,
        "How much would you spend? E.g. 'what if I spend 2000?'",
        "Ungetumia kiasi gani? Mf. 'what if I spend 2000?'",
        "Ungespend ngapi? Mf. 'what if I spend 2000?'"
    )

    fun askDaysLate(lang: AppLanguage): String = pick(
        lang,
        "How many days late? E.g. 'what if my allowance is 7 days late?'",
        "Zimechelewa siku ngapi? Mf. 'allowance ikichelewa siku 7?'",
        "Late na siku ngapi? Mf. 'allowance ikichelewa siku 7?'"
    )

    fun askNewRent(lang: AppLanguage): String = pick(
        lang,
        "What would the rent be? E.g. 'what if rent was 7000?'",
        "Kodi ingekuwa kiasi gani? Mf. 'rent ikiwa 7000?'",
        "Rent ingekuwa ngapi? Mf. 'rent ikiwa 7000?'"
    )

    fun askCurrentRent(lang: AppLanguage): String = pick(
        lang,
        "What rent do you pay now? I don't have one recorded.",
        "Unalipa kodi kiasi gani sasa? Sina iliyorekodiwa.",
        "Unalipa rent ngapi saa hii? Sina imerecordiwa."
    )

    // ---------- plans ----------

    fun planHead(days: Int, flexible: String, bills: String, spendable: String, lang: AppLanguage): String = pick(
        lang,
        "$days-day plan (projection, not a promise):\nFlexible $flexible − upcoming bills $bills = $spendable spendable.",
        "Mpango wa siku $days (makadirio, si ahadi):\nPesa inayoweza kupangwa $flexible − bili zijazo $bills = $spendable inayoweza kutumika.",
        "Plan ya siku $days (projection, si promise):\nFlexible $flexible − bills zijazo $bills = $spendable spendable."
    )

    fun planBillsFirst(gap: String, lang: AppLanguage): String = pick(
        lang,
        "Bills need protecting first — no safe daily spend until the $gap gap closes. Highest pressure: upcoming bills. I changed nothing — say the word and I'll help trim.",
        "Bili zinahitaji kulindwa kwanza — hakuna matumizi salama ya kila siku hadi pengo la $gap lifungwe. Shinikizo kubwa: bili zijazo. Sijabadilisha chochote — niambie nikusaidie kupunguza.",
        "Bills zinahitaji kuprotectiwa kwanza — hakuna safe daily spend hadi gap ya $gap ifungwe. Pressure kubwa: bills zijazo. Sijachange chochote — niambie nikusaidie kukata."
    )

    fun planDaily(daily: String, pace: String, lang: AppLanguage): String = pick(
        lang,
        "Safe daily spend ≈ $daily.\n$pace\nI changed nothing — applying a plan comes later, with your tap.",
        "Matumizi salama ya kila siku ≈ $daily.\n$pace\nSijabadilisha chochote — kutekeleza mpango kutakuja baadaye, kwa kubonyeza kwako.",
        "Safe daily spend ≈ $daily.\n$pace\nSijachange chochote — kuapply plan itakuja later, na tap yako."
    )

    fun planPaceFits(burn: String, lang: AppLanguage): String = pick(
        lang,
        "Your $burn/day burn fits inside it — hold the line.",
        "Matumizi yako ya $burn/siku yanatoshea humo — endelea hivyo.",
        "Burn yako ya $burn/day inatoshea humo — hold the line."
    )

    fun planPaceOver(burn: String, over: String, lang: AppLanguage): String = pick(
        lang,
        "Your $burn/day burn is over pace by $over/day — trim food or transport first.",
        "Matumizi yako ya $burn/siku yanazidi kwa $over/siku — punguza chakula au nauli kwanza.",
        "Burn yako ya $burn/day iko over pace na $over/day — kata food ama transport kwanza."
    )

    fun planBurnUnknown(lang: AppLanguage): String = pick(
        lang,
        "Burn rate unknown — log a week first and I'll sharpen this.",
        "Kiwango cha matumizi hakijulikani — kwanza rekodi wiki moja nami nitaboresha haya.",
        "Burn rate haijulikani — kwanza log wiki moja nami nitasharpen hii."
    )

    fun askPlanFirst(lang: AppLanguage): String = pick(
        lang,
        "No plan on the table yet — ask me to survive some days first, e.g. 'help me survive 14 days'.",
        "Hakuna mpango mezani bado — niombe ukokoe siku kadhaa kwanza, mf. 'nisaidie kustahimili siku 14'.",
        "Hakuna plan kwa meza bado — niulize kusurvive siku kadhaa kwanza, mf. 'nisaidie kusurvive siku 14'."
    )

    fun applyPlanSummary(days: Int, total: String, daily: String, lang: AppLanguage): String = pick(
        lang,
        "Apply $days-day plan?\n• Monthly envelope $total (bills + spendable)\n• Daily spend-check reminder $daily/day",
        "Kutekeleza mpango wa siku $days?\n• Bahasha ya mwezi $total (bili + inayoweza kutumika)\n• Kikumbusho cha kila siku $daily/siku",
        "Kuapply plan ya siku $days?\n• Monthly envelope $total (bills + spendable)\n• Daily reminder $daily/day"
    )

    fun semesterNoDates(lang: AppLanguage): String = pick(
        lang,
        "Set your semester dates under University first — I need the term window, and I won't invent one.",
        "Weka tarehe za muhula kwenye University kwanza — nahitaji dirisha la muhula, wala sitalitunga.",
        "Weka semester dates kwa University kwanza — nahitaji term window, na sitainvent moja."
    )

    fun semesterUpcoming(untilStart: Int, total: Int, outlook: String, lang: AppLanguage): String = pick(
        lang,
        "Semester starts in $untilStart days ($total days total). Opening outlook: $outlook after commitments.",
        "Muhula unaanza baada ya siku $untilStart (siku $total kwa jumla). Mtazamo wa mwanzo: $outlook baada ya ahadi.",
        "Semester inaanza in siku $untilStart (siku $total total). Opening outlook: $outlook after commitments."
    )

    fun semesterEnded(spent: String, available: String, lang: AppLanguage): String = pick(
        lang,
        "That semester ended — spent $spent against $available available.",
        "Muhula huo uliisha — ulitumia $spent dhidi ya $available zilizopatikana.",
        "Hiyo semester iliisha — ulispend $spent dhidi ya $available zilizokuwapo."
    )

    fun semesterOnTrack(end: String, lang: AppLanguage): String = pick(
        lang,
        "On track: projected to end at $end.",
        "Uko sawa: inakadiriwa kuisha ukiwa na $end.",
        "Uko on track: projected kuisha na $end."
    )

    fun semesterTight(day: Int, lang: AppLanguage): String = pick(
        lang,
        "Tight: money looks likely to run out around semester day $day at the current pace.",
        "Ni kubana: pesa inaonekana kuisha karibu siku ya $day ya muhula kwa mwendo huu.",
        "Ni tight: doh inaonekana kuisha karibu siku ya $day ya semester kwa hii pace."
    )

    fun semesterHead(elapsed: Int, total: Int, allowance: String, spent: String, verdict: String, lang: AppLanguage): String = pick(
        lang,
        "Semester day $elapsed of $total:\n• Weekly allowance $allowance · spent $spent so far.\n• $verdict\nProjection from recorded history — not a promise.",
        "Siku ya $elapsed ya muhula kati ya $total:\n• Posho ya wiki $allowance · umetumia $spent hadi sasa.\n• $verdict\nMakadirio kutoka historia iliyorekodiwa — si ahadi.",
        "Siku ya $elapsed ya semester kati ya $total:\n• Weekly allowance $allowance · umespend $spent hadi saa hii.\n• $verdict\nProjection kutoka recorded history — si promise."
    )

    // ---------- briefing / compare ----------

    fun briefingHead(lang: AppLanguage): String = pick(
        lang,
        "Morning briefing (recorded + calculated):",
        "Ripoti ya asubuhi (iliyorekodiwa + imehesabiwa):",
        "Morning briefing (imerecordiwa + imecalculateiwa):"
    )

    fun briefingState(flexible: String, safeToday: String, lang: AppLanguage): String = pick(
        lang,
        "• Flexible $flexible · safe today $safeToday.",
        "• Inayoweza kupangwa $flexible · salama leo $safeToday.",
        "• Flexible $flexible · safe leo $safeToday."
    )

    fun briefingQueue(count: Int, lang: AppLanguage): String =
        if (count > 0) pick(
            lang,
            "• $count transactions waiting for review.",
            "• Miamala $count inasubiri ukaguzi.",
            "• Transactions $count zinasubiri review."
        ) else pick(
            lang, "• Review queue clear.", "• Foleni ya ukaguzi iko wazi.", "• Review queue iko clear."
        )

    fun briefingPressure(count: Int, total: String, lang: AppLanguage): String =
        if (count > 0) {
            val n = "$count bill${if (count == 1) "" else "s"}"
            pick(
                lang,
                "• Pressure: $n due within 7 days ($total).",
                "• Shinikizo: $n zinadaiwa ndani ya siku 7 ($total).",
                "• Pressure: $n due within siku 7 ($total)."
            )
        } else pick(
            lang,
            "• No bills due in the next 7 days.",
            "• Hakuna bili zinazodaiwa ndani ya siku 7.",
            "• Hakuna bills due kwa siku 7 zijazo."
        )

    fun briefingActionProtect(total: String, lang: AppLanguage): String = pick(
        lang,
        "One action: protect $total for the coming bills.",
        "Hatua moja: linda $total kwa bili zijazo.",
        "Action moja: protect $total kwa bills zijazo."
    )

    fun briefingActionReview(lang: AppLanguage): String = pick(
        lang,
        "One action: say 'review' and clear the queue.",
        "Hatua moja: sema 'pitia' ukafute foleni.",
        "Action moja: sema 'review' uclear queue."
    )

    fun briefingActionTrim(lang: AppLanguage): String = pick(
        lang,
        "One action: spending has overtaken the plan — trim food or transport first.",
        "Hatua moja: matumizi yamezidi mpango — punguza chakula au nauli kwanza.",
        "Action moja: spend imepita plan — kata food ama transport kwanza."
    )

    fun briefingActionHold(lang: AppLanguage): String = pick(
        lang,
        "One action: nothing urgent — hold the line.",
        "Hatua moja: hakuna cha dharura — endelea hivyo.",
        "Action moja: hakuna urgent — hold the line."
    )

    fun changedHead(thisMonth: String, lastMonth: String, pct: String, lang: AppLanguage): String = pick(
        lang,
        "What changed (recorded):\n• Spending: $thisMonth this month vs $lastMonth last month ($pct).",
        "Kilichobadilika (kimerekodiwa):\n• Matumizi: $thisMonth mwezi huu dhidi ya $lastMonth mwezi uliopita ($pct).",
        "What changed (imerecordiwa):\n• Spend: $thisMonth mwezi huu vs $lastMonth last month ($pct)."
    )

    fun changedTail(pending: Int, bills: Int, lang: AppLanguage): String = pick(
        lang,
        "• $pending waiting for review · $bills open bills.",
        "• $pending zinasubiri ukaguzi · bili $bills wazi.",
        "• $pending zinasubiri review · bills $bills open."
    )

    fun compareHead(thisMonth: String, lastMonth: String, pct: String, lang: AppLanguage): String = pick(
        lang,
        "This month vs last month (recorded):\n• $thisMonth vs $lastMonth ($pct).",
        "Mwezi huu dhidi ya uliopita (kimerekodiwa):\n• $thisMonth dhidi ya $lastMonth ($pct).",
        "Mwezi huu vs last month (imerecordiwa):\n• $thisMonth vs $lastMonth ($pct)."
    )

    fun compareTops(thisTop: String, lastTop: String, lang: AppLanguage): String = when {
        thisTop.isNotBlank() && lastTop.isNotBlank() -> pick(
            lang,
            "Top category: $thisTop now vs $lastTop last month.",
            "Kipengele kikuu: $thisTop sasa dhidi ya $lastTop mwezi uliopita.",
            "Top category: $thisTop saa hii vs $lastTop last month."
        )
        thisTop.isNotBlank() -> pick(
            lang,
            "Top category now: $thisTop.",
            "Kipengele kikuu sasa: $thisTop.",
            "Top category saa hii: $thisTop."
        )
        else -> pick(
            lang,
            "No categorized spending to compare yet.",
            "Hakuna matumizi yaliyopangwa kulinganisha bado.",
            "Hakuna categorized spend ya kucompare bado."
        )
    }

    // ---------- income ----------

    fun incomeEarned(fig: String, lang: AppLanguage): String = pick(
        lang,
        "Earned $fig this month (recorded).",
        "Umelipwa $fig mwezi huu (kimerekodiwa).",
        "Umeearn $fig mwezi huu (imerecordiwa)."
    )

    fun incomeNoExpected(lang: AppLanguage): String = pick(
        lang,
        "No expected paydays declared — say 'salary 45000 lands on the 25th' to track one.",
        "Hakuna malipo yanayotarajiwa yaliyotangazwa — sema 'mshahara 45000 unafika tarehe 25' kufuatilia moja.",
        "Hakuna expected paydays zimedeclareiwa — sema 'salary 45000 inaland tarehe 25' kutrack moja."
    )

    fun incomeExpectedHead(lang: AppLanguage): String = pick(
        lang,
        "Expected in the next 30 days (estimates, not guarantees):",
        "Yanayotarajiwa ndani ya siku 30 zijazo (makadirio, si uhakika):",
        "Expected kwa siku 30 zijazo (estimates, si guarantee):"
    )

    fun askIncomeAmount(lang: AppLanguage): String = pick(
        lang,
        "How much lands each time? E.g. 'salary 45000 lands on the 25th'.",
        "Kiasi gani kinafika kila mara? Mf. 'mshahara 45000 unafika tarehe 25'.",
        "Inaland ngapi kila time? Mf. 'salary 45000 inaland tarehe 25'."
    )

    fun askIncomeLabel(amount: String, lang: AppLanguage): String = pick(
        lang,
        "What do we call the $amount income — salary, hustle, HELB?",
        "Mapato haya ya $amount tunayaita nini — mshahara, hustle, HELB?",
        "Hii income ya $amount tunaiita nini — salary, hustle, HELB?"
    )

    // ---------- reminders ----------

    fun noReminders(lang: AppLanguage): String = pick(
        lang,
        "No Buddy reminders set — say 'remind me about rent every 1st' to create one.",
        "Hakuna vikumbusho vya Buddy — sema 'nikumbushe kuhusu kodi kila tarehe 1' kuunda kimoja.",
        "Hakuna reminders za Buddy — sema 'nikumbushe kuhusu rent kila tarehe 1' kucreate moja."
    )

    fun remindersHead(lang: AppLanguage): String = pick(
        lang, "Your reminders:", "Vikumbusho vyako:", "Reminders zako:"
    )

    fun askReminderTitle(lang: AppLanguage): String = pick(
        lang,
        "Remind you about what? E.g. 'remind me about rent tomorrow'.",
        "Nikukumbushe kuhusu nini? Mf. 'nikumbushe kuhusu kodi kesho'.",
        "Nikukumbushe kuhusu nini? Mf. 'nikumbushe kuhusu rent kesho'."
    )

    fun askReminderWhen(lang: AppLanguage): String = pick(
        lang,
        "When — tomorrow, every day, every Monday, or every 1st?",
        "Lini — kesho, kila siku, kila Jumatatu, au kila tarehe 1?",
        "Lini — kesho, kila siku, kila Monday, ama kila tarehe 1?"
    )

    fun conditionalDecline(lang: AppLanguage): String = pick(
        lang,
        "I can create calendar-style reminders, but the current app does not support condition-based reminders — only fixed times. Try 'remind me about rent every 1st'.",
        "Naweza kuunda vikumbusho vya kalenda, lakini programu ya sasa haiauni vikumbusho vya masharti — nyakati maalum tu. Jaribu 'nikumbushe kuhusu kodi kila tarehe 1'.",
        "Naeza create reminders za calendar, lakini app ya saa hii haikubali conditional reminders — fixed times tu. Jaribu 'nikumbushe kuhusu rent kila tarehe 1'."
    )

    fun nothingToCancel(lang: AppLanguage): String = pick(
        lang,
        "Nothing to cancel — no Buddy reminders set.",
        "Hakuna cha kufuta — hakuna vikumbusho vya Buddy.",
        "Hakuna cha kucancel — hakuna reminders za Buddy."
    )

    // ---------- creation asks ----------

    fun askBudgetAmount(lang: AppLanguage): String = pick(
        lang,
        "How much should the budget be? E.g. 'set Food budget 6000'.",
        "Bajeti iwe kiasi gani? Mf. 'weka bajeti ya Chakula 6000'.",
        "Budget iwe ngapi? Mf. 'weka budget ya Food 6000'."
    )

    fun askBudgetCategory(amount: String, lang: AppLanguage): String = pick(
        lang,
        "Which category gets $amount? E.g. 'set Food budget'.",
        "Kipengele gani kipate $amount? Mf. 'weka bajeti ya Chakula'.",
        "Category gani ipate $amount? Mf. 'weka budget ya Food'."
    )

    fun askBillAmount(lang: AppLanguage): String = pick(
        lang,
        "How much is the bill? E.g. 'add bill rent 8000'.",
        "Bili ni kiasi gani? Mf. 'ongeza bili ya kodi 8000'.",
        "Bill ni ngapi? Mf. 'ongeza bill ya rent 8000'."
    )

    fun askBillName(amount: String, lang: AppLanguage): String = pick(
        lang,
        "Which bill is $amount for?",
        "Bili hiyo ya $amount ni ya nini?",
        "Hiyo bill ya $amount ni ya nini?"
    )

    fun askDebtAmount(lang: AppLanguage): String = pick(
        lang,
        "How much? E.g. 'I owe Brian 2000'.",
        "Kiasi gani? Mf. 'namdai Brian 2000'.",
        "Ngapi? Mf. 'namdai Brian 2000'."
    )

    fun askDebtPerson(amount: String, lang: AppLanguage): String = pick(
        lang,
        "Who is the $amount with?",
        "Hiyo $amount ni ya nani?",
        "Hiyo $amount ni ya nani?"
    )

    fun askGoalAmount(lang: AppLanguage): String = pick(
        lang,
        "How much is the goal? E.g. 'save for laptop 80000'.",
        "Lengo ni kiasi gani? Mf. 'weka akiba ya laptop 80000'.",
        "Goal ni ngapi? Mf. 'save for laptop 80000'."
    )

    fun askGoalTitle(amount: String, lang: AppLanguage): String = pick(
        lang,
        "What is the $amount goal for?",
        "Lengo hilo la $amount ni la nini?",
        "Hiyo goal ya $amount ni ya nini?"
    )

    fun askTxnAmount(lang: AppLanguage): String = pick(
        lang,
        "How much should I add? E.g. 'add KSh 500 transport'.",
        "Niongeze kiasi gani? Mf. 'ongeza KSh 500 nauli'.",
        "Niadd ngapi? Mf. 'ongeza KSh 500 transport'."
    )

    // ---------- bill / debt resolution asks ----------

    fun noBillMatch(lang: AppLanguage): String = pick(
        lang,
        "No open bill matches that — open Bills to see what's due.",
        "Hakuna bili wazi inayolingana — fungua Bills uone zinazodaiwa.",
        "Hakuna open bill inamatch — fungua Bills uone zinadaiwa."
    )

    fun askWhichBill(label: String, lang: AppLanguage): String = pick(
        lang,
        "Which one — reply with the amount:\n$label",
        "Ni ipi — jibu kwa kiasi:\n$label",
        "Ni gani — reply na amount:\n$label"
    )

    fun askBillAmountFor(lang: AppLanguage): String = pick(
        lang,
        "To how much? E.g. 'change the rent bill to 6000'.",
        "Iwe kiasi gani? Mf. 'badilisha bili ya kodi hadi 6000'.",
        "Iwe ngapi? Mf. 'change bill ya rent hadi 6000'."
    )

    fun askWhichBillByName(amount: String, names: String, lang: AppLanguage): String = pick(
        lang,
        "Which bill should become $amount? Reply with its exact name:\n$names",
        "Bili ipi iwe $amount? Jibu kwa jina lake kamili:\n$names",
        "Bill gani iwe $amount? Reply na jina lake exact:\n$names"
    )

    fun askDebtPerson(lang: AppLanguage): String = pick(
        lang,
        "Who — which debt should the payment go to?",
        "Nani — deni lipi lipokee malipo?",
        "Nani — deni gani ipokee payment?"
    )

    fun askDebtAmountFor(person: String, lang: AppLanguage): String = pick(
        lang,
        "How much are you recording on $person's debt?",
        "Unarekodi kiasi gani kwenye deni la $person?",
        "Unarecord ngapi kwa deni ya $person?"
    )

    fun noDebtMatch(person: String, lang: AppLanguage): String = pick(
        lang,
        "No open debt with $person — check Debt Tracking.",
        "Hakuna deni wazi na $person — angalia Debt Tracking.",
        "Hakuna open debt na $person — check Debt Tracking."
    )

    fun noBillDateMatch(lang: AppLanguage): String = noBillMatch(lang)

    fun askBillDate(lang: AppLanguage): String = pick(
        lang,
        "To which date? E.g. 'move rent to the 5th'.",
        "Ihamie tarehe gani? Mf. 'hamisha kodi hadi tarehe 5'.",
        "Ihamie tarehe gani? Mf. 'hamisha rent hadi tarehe 5'."
    )

    fun askChoicesExpired(lang: AppLanguage): String = pick(
        lang,
        "Those choices expired — say it again and I'll list them fresh.",
        "Chaguzi zile zimepitwa na wakati — sema tena nitaziorodhesha upya.",
        "Choices zile zimeexpire — sema tena nitazilist fresh."
    )

    fun nothingWaitingReview(lang: AppLanguage): String = pick(
        lang,
        "No recorded transactions match that — try 'review' to see what's waiting.",
        "Hakuna miamala inayolingana — jaribu 'pitia' kuona zinazosubiri.",
        "Hakuna transactions zinamatch — jaribu 'review' kuona zinazosubiri."
    )

    // ---------- confirm-card summaries ----------

    fun summarizeAdd(kind: String, fig: String, category: String, who: String, lang: AppLanguage): String {
        val head = pick(lang, "Add $kind?", "Kuongeza $kind?", "Kuadd $kind?")
        return "$head\n$fig · $category$who · ${pick(lang, "today", "leo", "leo")}"
    }

    fun summarizePref(text: String, lang: AppLanguage): String = text

    fun summarizeBudget(category: String, fig: String, lang: AppLanguage): String = pick(
        lang,
        "Set budget?\n$category · $fig/month",
        "Kuweka bajeti?\n$category · $fig/mwezi",
        "Kuset budget?\n$category · $fig/month"
    )

    fun summarizeBill(name: String, fig: String, dueDays: Int, lang: AppLanguage): String = pick(
        lang,
        "Add bill?\n$name · $fig · due in $dueDays days",
        "Kuongeza bili?\n$name · $fig · inadaiwa baada ya siku $dueDays",
        "Kuadd bill?\n$name · $fig · due in siku $dueDays"
    )

    fun summarizeDebt(dirYouOwe: Boolean, person: String, fig: String, lang: AppLanguage): String {
        val dir = if (dirYouOwe) pick(lang, "you owe", "unadaiwa", "unadaiwa")
        else pick(lang, "owes you", "anakudai", "anakudai")
        return pick(
            lang,
            "Record debt?\n$dir $person · $fig",
            "Kurekodi deni?\n$dir $person · $fig",
            "Kurecord deni?\n$dir $person · $fig"
        )
    }

    fun summarizeGoal(title: String, fig: String, lang: AppLanguage): String = pick(
        lang,
        "Create goal?\n$title · $fig · 90 days",
        "Kuanzisha lengo?\n$title · $fig · siku 90",
        "Kucreate goal?\n$title · $fig · siku 90"
    )

    fun summarizeReminder(title: String, label: String, lang: AppLanguage): String = pick(
        lang,
        "Set reminder?\n$title · $label",
        "Kuweka kikumbusho?\n$title · $label",
        "Kuset reminder?\n$title · $label"
    )

    fun summarizeCancelReminders(lang: AppLanguage): String = pick(
        lang,
        "Cancel all Buddy reminders?\nThey will stop firing on this device.",
        "Kufuta vikumbusho vyote vya Buddy?\nVitakoma kuwaka kwenye kifaa hiki.",
        "Kucancel reminders zote za Buddy?\nZitastop kuwaka kwa device hii."
    )

    fun summarizeApply(days: Int, total: String, daily: String, lang: AppLanguage): String = pick(
        lang,
        "Apply $days-day plan?\n• Monthly envelope $total (bills + spendable)\n• Daily spend-check reminder $daily/day",
        "Kutekeleza mpango wa siku $days?\n• Bahasha ya mwezi $total (bili + inayoweza kutumika)\n• Kikumbusho cha kila siku $daily/siku",
        "Kuapply plan ya siku $days?\n• Monthly envelope $total (bills + spendable)\n• Daily reminder $daily/day"
    )

    fun summarizeReclassify(count: Int, category: String, lang: AppLanguage): String =
        if (count == 1) pick(
            lang,
            "File under $category?",
            "Kuhifadhi chini ya $category?",
            "Kufile chini ya $category?"
        ) else pick(
            lang,
            "Reclassify $count transactions to $category?",
            "Kupanga upya miamala $count chini ya $category?",
            "Kureclassify transactions $count kwenda $category?"
        )

    fun summarizeDelete(count: Int, lang: AppLanguage): String =
        if (count == 1) pick(
            lang,
            "Delete this transaction?\nThis will change your financial history.",
            "Kufuta muamala huu?\nHii itabadilisha historia yako ya kifedha.",
            "Kudelete hii transaction?\nHii itachange history yako ya financial."
        ) else pick(
            lang,
            "Delete $count transactions?\nThis will change your financial history.",
            "Kufuta miamala $count?\nHii itabadilisha historia yako ya kifedha.",
            "Kudelete transactions $count?\nHii itachange history yako ya financial."
        )

    fun summarizeBillDate(lang: AppLanguage): String = pick(
        lang,
        "Move the due date?\nEverything else on the bill stays the same.",
        "Kuhamisha tarehe ya malipo?\nMengineyo yote kwenye bili yanabaki vile vile.",
        "Kuhamisha due date?\nZingine zote kwa bill zinabaki vile vile."
    )

    fun summarizeIncome(label: String, fig: String, whenText: String, lang: AppLanguage): String = pick(
        lang,
        "Track income?\n$label · $fig · $whenText",
        "Kufuatilia mapato?\n$label · $fig · $whenText",
        "Kutrack income?\n$label · $fig · $whenText"
    )

    fun incomeWhenDaily(lang: AppLanguage): String = pick(lang, "daily", "kila siku", "daily")
    fun incomeWhenWeekly(lang: AppLanguage): String = pick(lang, "weekly", "kila wiki", "weekly")
    fun incomeWhenOnce(lang: AppLanguage): String = pick(lang, "one-off", "mara moja", "one-off")
    fun incomeWhenMonthly(day: Int, lang: AppLanguage): String =
        if (day in 1..31) pick(
            lang, "every $day", "kila tarehe $day", "kila tarehe $day"
        ) else pick(lang, "monthly", "kila mwezi", "monthly")

    // ---------- executor results ----------

    fun prefDone(key: String, lang: AppLanguage): String = pick(
        lang, "Done — $key.", "Imekamilika — $key.", "Done — $key."
    )

    fun themeOn(mode: String, lang: AppLanguage): String = pick(
        lang,
        "Done — ${mode.lowercase()} theme on.",
        "Imekamilika — mandhari ya ${mode.lowercase()} umewashwa.",
        "Done — ${mode.lowercase()} theme on."
    )

    fun balancesHidden(hidden: Boolean, lang: AppLanguage): String =
        if (hidden) pick(
            lang,
            "Done — balances are hidden.",
            "Imekamilika — salio zimefichwa.",
            "Done — balances zimefichwa."
        ) else pick(
            lang,
            "Done — balances are visible.",
            "Imekamilika — salio zinaonekana.",
            "Done — balances zinaonekana."
        )

    fun languageSet(value: String, lang: AppLanguage): String = pick(
        lang,
        "Done — language is ${value.lowercase()}.",
        "Imekamilika — lugha ni ${value.lowercase()}.",
        "Done — language ni ${value.lowercase()}."
    )

    fun txnAdded(fig: String, kind: String, who: String, category: String, lang: AppLanguage): String = pick(
        lang,
        "Added $fig $kind$who under $category.",
        "Nimeongeza $fig $kind$who chini ya $category.",
        "Nimeadd $fig $kind$who chini ya $category."
    )

    fun reclassified(count: Int, category: String, lang: AppLanguage): String =
        if (count == 1) pick(
            lang,
            "Filed under $category. Undo on Home if I got it wrong. ✅",
            "Imehifadhiwa chini ya $category. Tendua ukiwa Home nikikosea. ✅",
            "Imefilwa chini ya $category. Undo kwa Home nikikosea. ✅"
        ) else pick(
            lang,
            "Reclassified $count transactions to $category. Undo them one by one on Home. ✅",
            "Nimepanga upya miamala $count chini ya $category. Zitendue moja moja ukiwa Home. ✅",
            "Nimereclassify transactions $count kwenda $category. Undo moja moja kwa Home. ✅"
        )

    fun deleted(count: Int, lang: AppLanguage): String =
        if (count == 1) pick(
            lang,
            "Deleted. Undo lives on Home if that was wrong.",
            "Imefutwa. Tendua ukiwa Home kama haikustahili.",
            "Imedeletewa. Undo iko Home kama haikustahili."
        ) else pick(
            lang,
            "Deleted $count transactions. Undo them one by one on Home.",
            "Nimefuta miamala $count. Zitendue moja moja ukiwa Home.",
            "Nimedelete transactions $count. Undo moja moja kwa Home."
        )

    fun budgetSet(category: String, fig: String, lang: AppLanguage): String = pick(
        lang,
        "Set $category monthly budget $fig. Edit anytime in Budgets. ✅",
        "Nimeweka bajeti ya mwezi ya $category $fig. Hariri wakati wowote kwenye Budgets. ✅",
        "Nimeset monthly budget ya $category $fig. Edit anytime kwa Budgets. ✅"
    )

    fun billAdded(name: String, fig: String, dueDays: Int, lang: AppLanguage): String = pick(
        lang,
        "Bill '$name' $fig added, due in $dueDays days — adjust the date in Bills. ✅",
        "Bili '$name' $fig imeongezwa, inadaiwa baada ya siku $dueDays — rekebisha tarehe kwenye Bills. ✅",
        "Bill '$name' $fig imeaddiwa, due in siku $dueDays — adjust date kwa Bills. ✅"
    )

    fun debtRecorded(dirYouOwe: Boolean, person: String, fig: String, lang: AppLanguage): String {
        val dir = if (dirYouOwe) pick(lang, "you owe", "unadaiwa", "unadaiwa")
        else pick(lang, "owes you", "anakudai", "anakudai")
        return pick(
            lang,
            "Recorded: $dir $person $fig. See it in Debt Tracking. ✅",
            "Imerekodiwa: $dir $person $fig. Ione kwenye Debt Tracking. ✅",
            "Imerecordiwa: $dir $person $fig. Ione kwa Debt Tracking. ✅"
        )
    }

    fun goalCreated(title: String, fig: String, lang: AppLanguage): String = pick(
        lang,
        "Goal '$title' $fig created (90 days) under Savings. ✅",
        "Lengo '$title' $fig limeanzishwa (siku 90) kwenye Savings. ✅",
        "Goal '$title' $fig imecreateiwa (siku 90) kwa Savings. ✅"
    )

    fun reminderSet(title: String, label: String, permNote: Boolean, lang: AppLanguage): String {
        val base = pick(
            lang,
            "Reminder set: $title — $label.",
            "Kikumbusho kimewekwa: $title — $label.",
            "Reminder imesetwa: $title — $label."
        )
        if (!permNote) return base
        return base + pick(
            lang,
            " Notifications are off for PesaFlow, so enable them in system Settings to hear it.",
            " Arifa zimezimwa kwa PesaFlow, kwa hiyo ziwashe kwenye Mipangilio ya kifaa kuzisikia.",
            " Notifications ziko off kwa PesaFlow, kwa hiyo ziwashe kwa system Settings kuziskia."
        )
    }

    fun remindersCancelled(count: Int, lang: AppLanguage): String =
        if (count == 0) nothingToCancel(lang)
        else pick(
            lang,
            "Cancelled $count reminder${if (count == 1) "" else "s"}. ✅",
            "Nimefuta vikumbusho $count. ✅",
            "Nimecancel reminders $count. ✅"
        )

    fun planApplied(total: String, lang: AppLanguage): String = pick(
        lang,
        "Plan applied: monthly envelope $total plus a daily 20:00 spend check. Adjust anytime in Budgets. ✅",
        "Mpango umetekelezwa: bahasha ya mwezi $total pamoja na ukaguzi wa kila siku saa 20:00. Rekebisha wakati wowote kwenye Budgets. ✅",
        "Plan imeapplyiwa: monthly envelope $total plus daily spend check saa 20:00. Adjust anytime kwa Budgets. ✅"
    )

    fun billPaid(name: String, fig: String, lang: AppLanguage): String = pick(
        lang,
        "Paid '$name' $fig — filed as an expense. ✅",
        "Nimelipa '$name' $fig — imehifadhiwa kama matumizi. ✅",
        "Nimelipa '$name' $fig — imefilwa kama expense. ✅"
    )

    fun billEdited(name: String, fig: String, lang: AppLanguage): String = pick(
        lang,
        "Bill '$name' is now $fig. ✅",
        "Bili '$name' sasa ni $fig. ✅",
        "Bill '$name' saa hii ni $fig. ✅"
    )

    fun billMoved(name: String, day: String, lang: AppLanguage): String = pick(
        lang,
        "Bill '$name' now due $day. ✅",
        "Bili '$name' sasa inadaiwa $day. ✅",
        "Bill '$name' saa hii due $day. ✅"
    )

    fun debtPaid(person: String, fig: String, lang: AppLanguage): String = pick(
        lang,
        "Recorded $fig on $person's debt. ✅",
        "Nimerekodi $fig kwenye deni la $person. ✅",
        "Nimerecord $fig kwa deni ya $person. ✅"
    )

    fun incomeTracked(label: String, fig: String, lang: AppLanguage): String = pick(
        lang,
        "Tracking $label $fig — it now feeds planning and the runway. ✅",
        "Ninafuatilia $label $fig — sasa inahesabika kwenye mipango na muda. ✅",
        "Ninatrack $label $fig — saa hii inafeed planning na runway. ✅"
    )

    fun failed(reason: String, lang: AppLanguage): String = pick(
        lang,
        "It didn't go through: $reason.",
        "Haikufanikiwa: $reason.",
        "Haikuenda through: $reason."
    )

    // ---------- bridge ----------

    fun tapToRun(lang: AppLanguage): String = pick(
        lang,
        " — tap below to run it. Nothing happens until you do.",
        " — bonyeza hapa chini kuitekeleza. Hakuna kitakachotokea hadi ufanye hivyo.",
        " — tap hapa chini kuirun. Hakuna kitafanyika hadi ufanye hivyo."
    )

    fun partialNote(lang: AppLanguage): String = pick(
        lang,
        "I didn't catch the rest — say it on its own.",
        "Sikupata kilichobaki — kiseme kivyake.",
        "Sikupata kilichobaki — kiseme kivyake."
    )

    // ---------- capability discovery ----------

    fun describe(introTitles: List<String>, lang: AppLanguage): String = pick(
        lang,
        "I can currently help with: ${introTitles.joinToString("; ")}. Financial changes and planning arrive in later phases — I'll say so instead of pretending.",
        "Kwa sasa naweza kusaidia na: ${introTitles.joinToString("; ")}. Mabadiliko ya kifedha na mipango yatakuja katika awamu zijazo — nitasema hivyo badala ya kujifanya.",
        "Saa hii naeza kusaidia na: ${introTitles.joinToString("; ")}. Changes za financial na planning zitakuja kwa phases zijazo — nitasema hivyo badala ya kupretend."
    )

    fun simBurnUnknown(lang: AppLanguage): String = pick(
        lang,
        "Log a week of spending first — I need your burn rate to compare plans.",
        "Kwanza rekodi matumizi ya wiki moja — nahitaji kiwango chako cha matumizi kulinganisha mipango.",
        "Kwanza log spend ya wiki moja — nahitaji burn rate yako kucompare plans."
    )

    fun comparePlansHead(days: Int, flexible: String, lang: AppLanguage): String = pick(
        lang,
        "Three $days-day plans from $flexible flexible (assumes burn holds):",
        "Mipango mitatu ya siku $days kutoka $flexible inayoweza kupangwa (ikidhania matumizi yataendelea):",
        "Plans tatu za siku $days kutoka $flexible flexible (ikiassume burn itahold):"
    )

    fun comparePlansRow(label: String, burn: String, end: String, lasts: String, lang: AppLanguage): String = pick(
        lang,
        "• $label: $burn/day → ends $end, lasts $lasts",
        "• $label: $burn/siku → inaisha $end, inadumu $lasts",
        "• $label: $burn/day → inaisha $end, inadumu $lasts"
    )

    fun comparePlansLabels(lang: AppLanguage): List<String> = when (lang) {
        AppLanguage.KISWAHILI -> listOf("Mwangalifu", "Wa kawaida", "Mkubwa")
        else -> listOf("Conservative", "Normal", "Aggressive")
    }

    fun lastsIndefinitely(lang: AppLanguage): String = pick(
        lang, "indefinitely", "milele", "indefinitely"
    )

    fun lastsDays(days: String, lang: AppLanguage): String = pick(
        lang,
        "≈ $days days",
        "≈ siku $days",
        "≈ siku $days"
    )
}
