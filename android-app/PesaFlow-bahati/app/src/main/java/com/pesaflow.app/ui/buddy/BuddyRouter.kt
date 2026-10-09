package com.pesaflow.app.ui.buddy

import com.pesaflow.app.ui.dashboard.BuddyBrain

// Deterministic intent router: cheapest reliable mechanism first (exact
// navigation verbs, keyword rules, BuddyBrain's scored classifier). Close
// ties and low confidence fall through to Unknown so the legacy layer can
// ask back or answer — the router never guesses. Pure, unit-tested.
data class RoutedCommand(
    val intent: BuddyIntent,
    val entities: BuddyEntities,
    val confidence: Float,
    val normalized: String
)

object BuddyIntentRouter {

    private val SAFE_KEYS = listOf("safe", "per day", "kila siku", "can i spend", "spend today", "spend this week", "salama")
    private val FLEX_KEYS = listOf("flexible", "free money", "money left", "available to plan", "ngapi", "baki")
    private fun hasAnyWord(normalized: String, keys: List<String>): Boolean =
        keys.any { k ->
            if (k.contains(" ")) normalized.contains(k) else BuddyTextNorm.hasWord(normalized, k)
        }

    // Phase 2/3 verbs, matched order-free on word boundaries ("500
    // transport add" counts the same as "add 500 transport").
    private val ADD_VERBS = listOf("add", "ongeza", "weka", "record", "log")
    private fun hasVerb(normalized: String, vararg verbs: String): Boolean =
        verbs.any { BuddyTextNorm.hasWord(normalized, it) }
    private val LEGACY_MONEY_WORDS = listOf("bill", "debt", "goal", "budget", "saving", "save ")
    private val INCOME_MARKERS = listOf("salary", "income", "mshahara", "received", "allowance", "helb", "posho")
    private fun hasMarkers(normalized: String, markers: List<String>): Boolean =
        markers.any { m -> if (m.contains(" ")) normalized.contains(m) else BuddyTextNorm.hasWord(normalized, m) }
    private val ADD_STOPWORDS = listOf(
        "add", "ongeza", "weka", "record", "log", "expense", "ksh", "kes", "bob",
        "shillings", "for", "my", "today", "yesterday", "please", "tafadhali"
    )

    // Every query runs collapsed (punctuation-proof) and typo-corrected
    // before any verb matching, so order and spelling stop mattering.
    // Known merchants ride along as protected words — "naivas" is never a typo.
    fun route(raw: String, knownMerchants: List<String> = emptyList()): RoutedCommand {
        val protected = knownMerchants.map { it.lowercase() }.toSet()
        val normalized = BuddyTextNorm.normalize(raw, protected)
        val entities = BuddyEntityExtractor.extract(raw, knownMerchants)
        // Held disambiguation choices resolve first ("2", "all"); anything
        // else clears them and routes normally — stale choices never fire.
        BuddyTxnResolver.resolveReply(normalized)?.let {
            return RoutedCommand(it, entities, 0.9f, normalized)
        }
        BuddyTxnResolver.clearPending()
        // Navigation wins on explicit movement verbs, before any finance intent.
        entities.screen?.let {
            return RoutedCommand(BuddyIntent.OpenScreen(it), entities, 0.9f, normalized)
        }
        // Phase 2: display preferences execute immediately (low-risk control).
        routePreference(normalized)?.let { return it }
        // Creation drafts: budget/bill/debt/goal phrases the new pipeline
        // handles end-to-end (typed command + confirm card + audit). Anything
        // it can't complete stays Unknown for the legacy cards.
        routeCreateBudget(normalized, entities)?.let { return it }
        routeAddBill(normalized, entities)?.let { return it }
        routeAddDebt(normalized, entities)?.let { return it }
        routeCreateGoal(normalized, entities)?.let { return it }
        // Phase 3: add-transaction drafts a typed command; a missing amount
        // asks instead of acting.
        if (hasVerb(normalized, *ADD_VERBS.toTypedArray()) &&
            LEGACY_MONEY_WORDS.any { BuddyTextNorm.hasWord(normalized, it.trim()) }
        ) {
            return RoutedCommand(BuddyIntent.Unknown, entities, 0f, normalized)
        }
        routeAddTransaction(normalized, entities)?.let { return it }
        // Income: expected paydays draft sources; plain "record salary" stays
        // a transaction. Queries ("how much do I earn") answer from the ledger.
        routeIncome(normalized, entities)?.let { return it }
        // Bill/debt servicing and plan-apply before generic creation: "paid
        // Brian" is a settlement, not a new debt; "apply plan" closes the loop.
        routeApplyPlan(normalized)?.let { return it }
        routeBillOps(normalized, entities)?.let { return it }
        routeDebtPay(normalized, entities)?.let { return it }
        // Reminders: list and cancel-all first (they contain "remind" too),
        // then creation drafts. Conditional triggers decline honestly.
        routeReminders(normalized)?.let { return it }
        // Command-center orchestration: briefing, change digest, comparison.
        routeBriefing(normalized)?.let { return it }
        // Export/backup/restore live on the Export screen (which confirms
        // destructive steps itself) — take the user there, never act inline.
        if (listOf("export", "backup", "restore").any(normalized::contains)) {
            return RoutedCommand(
                BuddyIntent.OpenScreen(com.pesaflow.app.ui.NavRoutes.EXPORT),
                entities, 0.85f, normalized
            )
        }
        // Survival planning: read-only projection first, apply only on tap.
        routePlan(normalized, entities)?.let { return it }
        // What-if simulations: read-only hypotheticals over engine math.
        routeSimulate(normalized, entities)?.let { return it }
        // Transaction control: find first (read-only), then reclassify and
        // delete drafts. Pending-queue rows stay on the legacy cards via the
        // bridge guard; this layer only resolves against the ledger.
        routeFind(normalized, entities, knownMerchants)?.let { return it }
        routeReclassify(normalized, entities, knownMerchants)?.let { return it }
        routeDelete(normalized, entities, knownMerchants)?.let { return it }
        // Close tie between two intents: don't guess, let the legacy layer ask.
        if (BuddyBrain.disambiguate(normalized) != null) {
            return RoutedCommand(BuddyIntent.Unknown, entities, 0f, normalized)
        }
        if (hasAnyWord(normalized, SAFE_KEYS)) {
            return RoutedCommand(BuddyIntent.QuerySafeSpend, entities, 0.7f, normalized)
        }
        val top = BuddyBrain.classify(normalized).firstOrNull()
        if (top != null && top.name == "balance") {
            return RoutedCommand(BuddyIntent.QueryBalance, entities, top.conf, normalized)
        }
        if (hasAnyWord(normalized, FLEX_KEYS)) {
            return RoutedCommand(BuddyIntent.QueryFlexible, entities, 0.6f, normalized)
        }
        if (top == null || top.conf < 0.5f) {
            return RoutedCommand(BuddyIntent.Unknown, entities, top?.conf ?: 0f, normalized)
        }
        val intent = when (top.name) {
            "bills" -> BuddyIntent.QueryBills
            "debts" -> BuddyIntent.QueryDebts
            "goals", "savings" -> BuddyIntent.QueryGoals
            "help" -> BuddyIntent.Help
            else -> BuddyIntent.Unknown
        }
        return RoutedCommand(intent, entities, top.conf, normalized)
    }

    private fun routePreference(normalized: String): RoutedCommand? {
        val entities = BuddyEntities()
        // Plural = the display setting ("balances"); singular "balance" is
        // the figure and stays a balance query.
        if (listOf("hide balances", "hide my balances", "hide money", "hide my money", "hide amounts")
                .any(normalized::contains)
        ) {
            return RoutedCommand(BuddyIntent.UpdatePreference(PrefKey.HIDE_BALANCES, "true"), entities, 0.8f, normalized)
        }
        if (listOf("show balances", "show my balances", "show money", "show my money", "unhide")
                .any(normalized::contains)
        ) {
            return RoutedCommand(BuddyIntent.UpdatePreference(PrefKey.HIDE_BALANCES, "false"), entities, 0.8f, normalized)
        }
        if (normalized.contains("dark mode") || normalized.contains("dark theme")) {
            return RoutedCommand(BuddyIntent.UpdatePreference(PrefKey.THEME, "DARK"), entities, 0.8f, normalized)
        }
        if (normalized.contains("light mode") || normalized.contains("light theme")) {
            return RoutedCommand(BuddyIntent.UpdatePreference(PrefKey.THEME, "LIGHT"), entities, 0.8f, normalized)
        }
        if (normalized.contains("system theme")) {
            return RoutedCommand(BuddyIntent.UpdatePreference(PrefKey.THEME, "SYSTEM"), entities, 0.8f, normalized)
        }
        val language = when {
            normalized.contains("kiswahili") || normalized.contains("swahili") -> "KISWAHILI"
            normalized.contains("sheng") -> "SHENG"
            normalized.contains("english") -> "ENGLISH"
            else -> null
        }
        if (language != null && normalized.contains("language") || normalized.contains("lugha")) {
            return RoutedCommand(BuddyIntent.UpdatePreference(PrefKey.LANGUAGE, language ?: "MIXED"), entities, 0.7f, normalized)
        }
        return null
    }

    private fun routeAddTransaction(normalized: String, entities: BuddyEntities): RoutedCommand? {
        if (!hasVerb(normalized, *ADD_VERBS.toTypedArray())) return null
        if (LEGACY_MONEY_WORDS.any { BuddyTextNorm.hasWord(normalized, it.trim()) }) return null
        val income = hasMarkers(normalized, INCOME_MARKERS)
        val amount = entities.amount
        val category = entities.category ?: "Other"
        val merchant = merchantRemainder(normalized, amount, entities.category)
        return RoutedCommand(
            BuddyIntent.AddTransaction(amount, category, merchant, income),
            entities, if (amount != null) 0.75f else 0.5f, normalized
        )
    }

    private val PLAN_WORDS = listOf("survive", "surviv", "stretch", "make it", "prepare", "plan")
    private val PLAN_CONTEXT = listOf(
        "surviv", "stretch", "make it", "prepare", "next", "week",
        "month", "days", "horizon", "rent", "morning briefing", "briefing"
    )
    private val PLAN_EXCLUDE = listOf("meal", "food plan", "data plan", "floor plan")

    private fun routePlan(normalized: String, entities: BuddyEntities): RoutedCommand? {
        if (!hasAnyWord(normalized, PLAN_WORDS)) return null
        if (PLAN_EXCLUDE.any(normalized::contains)) return null
        if (!hasAnyWord(normalized, PLAN_CONTEXT)) return null
        val horizon = entities.dayCount
            ?: if (normalized.contains("week")) 7
            else if (normalized.contains("month")) 30
            else if (normalized.contains("fortnight")) 14
            else 14
        return RoutedCommand(BuddyIntent.BuildPlan(horizon), entities, 0.75f, normalized)
    }

    private val INCOME_WORDS = listOf("income", "salary", "mshahara", "paycheck", "payday", "allowance", "helb", "hustle", "earnings")
    private val LANDING_WORDS = listOf(
        "lands", "landing", "every", "monthly", "weekly", "daily", "per month",
        "payday", "allowance", "recurring"
    )

    private fun routeIncome(normalized: String, entities: BuddyEntities): RoutedCommand? {
        // Hypotheticals belong to the simulator, never to source drafting.
        if (SIM_VERBS.any(normalized::contains)) return null
        val earnGate = Regex("(?<![a-z])earn(?![a-z])").containsMatchIn(normalized)
        if (!hasAnyWord(normalized, INCOME_WORDS) && !earnGate) return null
        val landing = hasAnyWord(normalized, LANDING_WORDS)
        val creating = hasVerb(normalized, "add", "new", "record", "set", "weka", "ongeza")
        val earnWord = Regex("(?<![a-z])earn(?![a-z])").containsMatchIn(normalized)
        if (!landing && !creating) {
            // "how much do I earn", "my salary", "my income" → ledger query.
            if (normalized.contains("how much") || earnWord ||
                normalized.contains("my salary") || normalized.contains("my income") ||
                normalized.contains("get paid")
            ) {
                return RoutedCommand(BuddyIntent.QueryIncome, entities, 0.8f, normalized)
            }
            return null
        }
        if (!landing) return null // plain "record salary" → AddTransaction owns it
        val kind = when {
            normalized.contains("helb") -> "HELB_MPESA"
            normalized.contains("parent") || normalized.contains("mum") || normalized.contains("dad") ||
                normalized.contains("sponsor") || normalized.contains("mzazi") -> "PARENT"
            normalized.contains("guardian") -> "GUARDIAN"
            normalized.contains("hustle") -> "HUSTLE"
            normalized.contains("scholarship") -> "SCHOLARSHIP"
            else -> "OTHER"
        }
        val kindLabel = mapOf(
            "HELB_MPESA" to "HELB", "PARENT" to "Parents", "GUARDIAN" to "Guardian",
            "HUSTLE" to "Hustle", "SCHOLARSHIP" to "Scholarship"
        )[kind]
        val label = remainder(
            normalized,
            listOf(
                "add", "new", "record", "set", "weka", "ongeza", "my", "income", "salary",
                "mshahara", "paycheck", "payday", "allowance", "helb", "hustle", "earnings",
                "lands", "landing", "every", "each", "monthly", "weekly", "daily", "per", "month",
                "week", "day", "ksh", "kes", "of", "on", "the", "a", "please", "tafadhali", "is", "at"
            )
        ) ?: if (normalized.contains("salary")) "Salary" else kindLabel
        val frequency = when {
            normalized.contains("daily") -> "DAILY"
            normalized.contains("weekly") -> "WEEKLY"
            normalized.contains("once") || normalized.contains("one-time") || normalized.contains("one time") -> "ONCE"
            else -> "MONTHLY"
        }
        val dayOfMonth = Regex("(\\d{1,2})(st|nd|rd|th)\\b").find(normalized)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("on the (\\d{1,2})\\b").find(normalized)?.groupValues?.get(1)?.toIntOrNull()
            ?: 0
        return RoutedCommand(
            BuddyIntent.AddIncomeSource(label, kind, entities.amount, frequency, dayOfMonth.coerceIn(0, 31)),
            entities, 0.8f, normalized
        )
    }

    private fun routeApplyPlan(normalized: String): RoutedCommand? {
        val t = normalized.trim()
        val wantsApply = t.contains("apply plan") || t.contains("apply it") ||
            (com.pesaflow.app.ui.dashboard.BuddyMemory.lastPlan != null &&
                (t == "do it" || t == "yes" || t == "go ahead" || t == "sawa" || t == "apply"))
        if (!wantsApply) return null
        return RoutedCommand(BuddyIntent.ApplyPlan, BuddyEntities(), 0.9f, normalized)
    }

    private val WEEKDAY_NUM = mapOf(
        "sunday" to 1, "jumapili" to 1, "monday" to 2, "jumatatu" to 2,
        "tuesday" to 3, "jumanne" to 3, "wednesday" to 4, "jumatano" to 4,
        "thursday" to 5, "alhamisi" to 5, "friday" to 6, "ijumaa" to 6,
        "saturday" to 7, "jumamosi" to 7
    )

    // "the 5th", "Friday", "tomorrow" → next occurrence at noon, or null.
    fun parseBillDate(normalized: String): Long? {
        val now = java.util.Calendar.getInstance()
        fun atNoon(cal: java.util.Calendar): Long {
            cal.set(java.util.Calendar.HOUR_OF_DAY, 12)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
        if (normalized.contains("tomorrow") || normalized.contains("kesho")) {
            val c = (now.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
            return atNoon(c)
        }
        Regex("(\\d{1,2})(st|nd|rd|th)\\b").find(normalized)?.groupValues?.get(1)?.toIntOrNull()?.let { dom ->
            if (dom !in 1..31) return@let
            val c = (now.clone() as java.util.Calendar).apply {
                set(java.util.Calendar.DAY_OF_MONTH, dom.coerceAtMost(getActualMaximum(java.util.Calendar.DAY_OF_MONTH)))
                if (!after(now)) add(java.util.Calendar.MONTH, 1)
            }
            return atNoon(c)
        }
        WEEKDAY_NUM.entries.firstOrNull { (w, _) ->
            Regex("(?<![a-z])$w(?![a-z])").containsMatchIn(normalized)
        }?.value?.let { wd ->
            val c = (now.clone() as java.util.Calendar)
            do {
                c.add(java.util.Calendar.DAY_OF_YEAR, 1)
            } while (c.get(java.util.Calendar.DAY_OF_WEEK) != wd)
            return atNoon(c)
        }
        return null
    }

    private fun routeBillOps(normalized: String, entities: BuddyEntities): RoutedCommand? {
        // Lookups ("show my unpaid bills") stay queries — never drafts.
        if (hasVerb(normalized, "show", "find", "list", "search")) return null
        // Due-date moves win over amount edits when a date is named: "move
        // rent to the 5th" reschedules, "change rent to 6000" reprices.
        val dateMove = listOf("move", "shift", "postpone", "push", "reschedule").any(normalized::contains) ||
            ((normalized.contains("bill") || normalized.contains("rent")) &&
                parseBillDate(normalized) != null && entities.amount == null)
        if (dateMove && !looksLikeQuestion(normalized)) {
            val name = remainder(
                normalized, listOf(
                    "bill", "bills", "move", "shift", "postpone", "push", "reschedule",
                    "change", "set", "to", "the", "my", "due", "date", "on", "ksh", "kes",
                    "st", "nd", "rd", "th", "tomorrow", "kesho", "next"
                ) + WEEKDAY_NUM.keys.toList()
            )
            return RoutedCommand(
                BuddyIntent.EditBillDate(name, parseBillDate(normalized)), entities, 0.8f, normalized
            )
        }
        val paidBill = (BuddyTextNorm.hasWord(normalized, "mark") && BuddyTextNorm.hasWord(normalized, "paid")) ||
            (BuddyTextNorm.hasWord(normalized, "bill") && BuddyTextNorm.hasWord(normalized, "paid"))
        if (paidBill) {
            val name = remainder(
                normalized, listOf("mark", "bill", "bills", "paid", "the", "my", "as") + BILL_VERBS
            )
            return RoutedCommand(BuddyIntent.MarkBillPaid(name, entities.amount), entities, 0.8f, normalized)
        }
        val editBill = BuddyTextNorm.hasWord(normalized, "bill") && entities.amount != null &&
            hasVerb(normalized, "change", "move", "update", "set", "edit")
        if (editBill && !looksLikeQuestion(normalized)) {
            val name = remainder(
                normalized, listOf("bill", "bills", "change", "move", "update", "set", "edit", "to", "the", "my", "ksh", "kes") + BUDGET_VERBS
            ) ?: entities.category
            return RoutedCommand(BuddyIntent.EditBill(name, entities.amount), entities, 0.75f, normalized)
        }
        return null
    }

    private fun routeDebtPay(normalized: String, entities: BuddyEntities): RoutedCommand? {
        val amount = entities.amount
        val paidMarkers = listOf("paid", "repaid", "settled", "lipia", "gives", "gave")
        if (amount == null || !hasAnyWord(normalized, paidMarkers)) return null
        if (BuddyTextNorm.hasWord(normalized, "bill")) return null
        val person = remainder(
            normalized, paidMarkers + listOf(
                "i", "me", "to", "my", "the", "ksh", "kes", "debt", "deni",
                "record", "please", "tafadhali", "have", "has", "was"
            )
        )
        return RoutedCommand(BuddyIntent.RecordDebtPayment(person, amount), entities, 0.75f, normalized)
    }

    private val REMIND_VERBS = listOf("remind", "reminder", "kumbusha", "alert me")
    private val WEEKDAYS = mapOf(
        "sunday" to 1, "jumapili" to 1, "monday" to 2, "jumatatu" to 2,
        "tuesday" to 3, "jumanne" to 3, "wednesday" to 4, "jumatano" to 4,
        "thursday" to 5, "alhamisi" to 5, "friday" to 6, "ijumaa" to 6,
        "saturday" to 7, "jumamosi" to 7
    )

    private fun routeReminders(normalized: String): RoutedCommand? {
        if (normalized.contains("cancel all reminders") || normalized.contains("clear reminders") ||
            normalized.contains("delete all reminders")
        ) {
            return RoutedCommand(BuddyIntent.CancelReminders, BuddyEntities(), 0.85f, normalized)
        }
        if (listOf("my reminders", "what reminders", "list reminders", "show reminders", "reminders do i have")
                .any(normalized::contains)
        ) {
            return RoutedCommand(BuddyIntent.ListReminders, BuddyEntities(), 0.85f, normalized)
        }
        if (REMINDBase(normalized)) return null
        val conditional = listOf("when ", "if ", "gets tight", "runs low", "is tight", "goes below")
            .any(normalized::contains) && !normalized.contains("what if")
        val title = remainder(
            normalized,
            REMIND_VERBS + listOf(
                "me", "about", "that", "to", "every", "each", "day", "daily", "month",
                "monthly", "week", "tomorrow", "kesho", "please", "tafadhali", "on", "at",
                "1st", "first", "st", "nd", "rd", "th"
            )
        )
        val dayCount = BuddyEntityExtractor.extractDayCount(normalized)
        val kind: ReminderKind?
        val label: String?
        val weekday = WEEKDAYS.entries.firstOrNull { (w, _) ->
            Regex("(?<![a-z])$w(?![a-z])").containsMatchIn(normalized)
        }?.value
        val atHour = Regex("\\bat\\s?(\\d{1,2})\\s?(am|pm)?\\b").find(normalized)?.let {
            val h = it.groupValues[1].toIntOrNull() ?: 9
            if (it.groupValues[2] == "pm" && h < 12) h + 12 else h
        } ?: 9
        when {
            normalized.contains("tomorrow") || normalized.contains("kesho") -> {
                kind = ReminderKind.Once(msToTomorrow9()); label = "tomorrow 9:00"
            }
            dayCount != null && normalized.contains(" in ") -> {
                kind = ReminderKind.Once(dayCount * 24L * 60 * 60 * 1000); label = "in $dayCount days"
            }
            weekday != null && normalized.contains("every") -> {
                kind = ReminderKind.Weekly(weekday, atHour, 0); label = "every ${WEEKDAYS.entries.first { it.value == weekday }.key}"
            }
            normalized.contains("every day") || normalized.contains("daily") -> {
                kind = ReminderKind.Daily(atHour, 0); label = "daily $atHour:00"
            }
            normalized.contains("every month") || normalized.contains("monthly") ||
                normalized.contains("1st") || normalized.contains("every 1") -> {
                kind = ReminderKind.MonthlyDay(1); label = "every 1st"
            }
            Regex("every\\s?(\\d{1,2})(st|nd|rd|th)").find(normalized) != null -> {
                val dom = Regex("every\\s?(\\d{1,2})(st|nd|rd|th)").find(normalized)!!.groupValues[1].toInt()
                kind = ReminderKind.MonthlyDay(dom.coerceIn(1, 28)); label = "every ${dom}th"
            }
            else -> {
                kind = null; label = null
            }
        }
        return RoutedCommand(
            BuddyIntent.CreateReminder(title, kind, label, conditional),
            BuddyEntityExtractor.extract(normalized), 0.8f, normalized
        )
    }

    private fun REMINDBase(normalized: String): Boolean = !hasAnyWord(normalized, REMIND_VERBS)

    private fun msToTomorrow9(): Long {
        val now = java.util.Calendar.getInstance()
        val next = (now.clone() as java.util.Calendar).apply {
            add(java.util.Calendar.DAY_OF_YEAR, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 9)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return (next.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
    }

    private fun routeBriefing(normalized: String): RoutedCommand? {
        if (normalized.contains("briefing") || normalized.contains("morning report")) {
            return RoutedCommand(BuddyIntent.MorningBriefing, BuddyEntities(), 0.85f, normalized)
        }
        if (BuddyTextNorm.hasWord(normalized, "semester") &&
            hasAnyWord(
                normalized,
                listOf("survive", "surviv", "make it", "last", "runway", "will", "outlook")
            )
        ) {
            return RoutedCommand(BuddyIntent.SemesterOutlook, BuddyEntities(), 0.8f, normalized)
        }
        if (normalized.contains("runway") || normalized.contains("will my money last")) {
            return RoutedCommand(BuddyIntent.SemesterOutlook, BuddyEntities(), 0.75f, normalized)
        }
        if ((normalized.contains("compare") || normalized.contains("conservative") ||
                normalized.contains("aggressive")) && normalized.contains("plan")
        ) {
            val entities = BuddyEntityExtractor.extract(normalized)
            // "three plans" is a count of scenarios, not a horizon — only an
            // explicit day/week window sets the horizon.
            val horizon = entities.dayCount?.takeIf { normalized.contains("day") }?.coerceIn(1, 365) ?: 30
            return RoutedCommand(
                BuddyIntent.CompareSpendingPlans(horizon),
                entities, 0.8f, normalized
            )
        }
        if (listOf("what changed", "what's changed", "whats changed", "what is new", "what's new", "anything new")
                .any(normalized::contains)
        ) {
            return RoutedCommand(BuddyIntent.WhatChanged, BuddyEntities(), 0.8f, normalized)
        }
        if (listOf("compare", "vs ", "versus", "difference", "last month", "this month vs")
                .any(normalized::contains)
        ) {
            return RoutedCommand(BuddyIntent.ComparePeriods, BuddyEntities(), 0.75f, normalized)
        }
        return null
    }

    private val SIM_VERBS = listOf("what if", "what happens if", "simulate", "suppose", "imagine")
    private val LATE_WORDS = listOf("late", "delayed", "delays", "delay")

    private fun routeSimulate(normalized: String, entities: BuddyEntities): RoutedCommand? {
        if (SIM_VERBS.none(normalized::contains)) return null
        if (hasAnyWord(normalized, LATE_WORDS)) {
            return RoutedCommand(
                BuddyIntent.SimulateIncomeLate(entities.dayCount), entities, 0.8f, normalized
            )
        }
        if (normalized.contains("rent")) {
            return RoutedCommand(
                BuddyIntent.SimulateRentChange(entities.amount, normalized.contains(" by ")),
                entities, 0.8f, normalized
            )
        }
        return RoutedCommand(BuddyIntent.SimulatePurchase(entities.amount), entities, 0.8f, normalized)
    }

    private val BUDGET_VERBS = listOf("set budget", "new budget", "weka budget", "create budget")
    private val BILL_VERBS = listOf("add bill", "new bill", "ongeza bill")
    private val DEBT_VERBS = listOf("add debt", "new debt", "ongeza deni", "i owe", "kopa")
    private val GOAL_VERBS = listOf("save for", "new goal", "add goal", "lengo")

    private fun looksLikeQuestion(normalized: String): Boolean =
        normalized.contains("?") || listOf(
            "is my", "is the", "are my", "how ", "why ", "what ", "left",
            "do i have", "should i", "safe"
        ).any(normalized::contains)

    private fun remainder(normalized: String, drop: List<String>): String? {
        var s = " $normalized "
        s = s.replace(Regex("\\d[\\d,]*"), " ")
        drop.forEach { w ->
            s = s.replace(Regex("(?<![a-z])${Regex.escape(w.trim())}(?![a-z])"), " ")
        }
        return s.replace(Regex("[^a-z ]"), " ").split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ").trim().takeIf { it.isNotBlank() }
            ?.replaceFirstChar { it.uppercase() }?.take(40)
    }

    private fun routeCreateBudget(normalized: String, entities: BuddyEntities): RoutedCommand? {
        if (looksLikeQuestion(normalized)) return null
        val explicit = BUDGET_VERBS.any(normalized::contains) ||
            (BuddyTextNorm.hasWord(normalized, "set") && BuddyTextNorm.hasWord(normalized, "budget")) ||
            (BuddyTextNorm.hasWord(normalized, "budget") &&
                hasVerb(normalized, "new", "create", "weka", "ongeza"))
        val implied = normalized.contains("budget") && entities.amount != null && entities.amount > 0
        if (!explicit && !implied) return null
        return RoutedCommand(
            BuddyIntent.CreateBudget(entities.category, entities.amount),
            entities, 0.8f, normalized
        )
    }

    private fun routeAddBill(normalized: String, entities: BuddyEntities): RoutedCommand? {
        val combo = (BuddyTextNorm.hasWord(normalized, "bill") || BuddyTextNorm.hasWord(normalized, "bills")) &&
            hasVerb(normalized, "add", "new", "ongeza", "create")
        if (BILL_VERBS.none(normalized::contains) && !combo) return null
        val name = remainder(
            normalized,
            BILL_VERBS + listOf(
                "bill", "bills", "ksh", "kes", "every", "first", "1st", "monthly", "month",
                "tomorrow", "kesho", "add", "new", "create", "ongeza", "my"
            )
        ) ?: entities.category
        val monthly = normalized.contains("every first") || normalized.contains("1st") ||
            normalized.contains("monthly") || normalized.contains("every month")
        val dueDays = when {
            monthly -> daysToNextFirst()
            normalized.contains("tomorrow") || normalized.contains("kesho") -> 1
            else -> 7
        }
        return RoutedCommand(
            BuddyIntent.AddBill(name, entities.amount, dueDays, if (monthly) "MONTHLY" else "ONE_TIME"),
            entities, 0.8f, normalized
        )
    }

    private fun routeAddDebt(normalized: String, entities: BuddyEntities): RoutedCommand? {
        val explicit = DEBT_VERBS.any(normalized::contains) ||
            ((BuddyTextNorm.hasWord(normalized, "debt") || BuddyTextNorm.hasWord(normalized, "deni")) &&
                hasVerb(normalized, "add", "new", "create", "owe"))
        val implied = normalized.contains("deni") && entities.amount != null && !looksLikeQuestion(normalized)
        if (!explicit && !implied) return null
        val person = remainder(normalized, DEBT_VERBS + listOf("deni", "debt", "ksh", "kes", "owe", "owed"))
        val iOwe = normalized.contains("i owe") || normalized.contains("kopa") ||
            (!normalized.contains("owed to me") && !normalized.contains("nadaiwa"))
        return RoutedCommand(
            BuddyIntent.AddDebt(person, entities.amount, iOwe), entities, 0.8f, normalized
        )
    }

    private fun routeCreateGoal(normalized: String, entities: BuddyEntities): RoutedCommand? {
        val explicit = GOAL_VERBS.any(normalized::contains) ||
            (BuddyTextNorm.hasWord(normalized, "goal") &&
                hasVerb(normalized, "add", "new", "create", "save"))
        val implied = normalized.contains("goal") && entities.amount != null && !looksLikeQuestion(normalized)
        if (!explicit && !implied) return null
        val title = remainder(
            normalized, GOAL_VERBS + listOf("goal", "ksh", "kes", "save", "for")
        )
        return RoutedCommand(
            BuddyIntent.CreateGoal(title, entities.amount), entities, 0.8f, normalized
        )
    }

    private fun daysToNextFirst(): Int {
        val c = java.util.Calendar.getInstance()
        val today = c.get(java.util.Calendar.DAY_OF_MONTH)
        val dim = c.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        return if (today == 1) dim else dim - today + 1
    }

    private val FIND_VERBS = listOf("show", "find", "list", "search")
    private val TXN_NOUNS = listOf("transaction", "spending", "payment")
    private val DIRECTION_OUT = listOf("sent", "paid", "spent", "payments to", "paid to")
    private val DIRECTION_IN = listOf("received", "income")
    private val RECLASSIFY_VERBS = listOf("change", "mark", "move", "categorize", "categorise", "recategorize", "file")
    private val DELETE_VERBS = listOf("delete", "remove", "erase")
    private val DELETE_GUARDS = listOf("all data", "everything", "wipe", "reset", "clear all")
    private val MERCHANT_STOPWORDS = setOf(
        "show", "find", "list", "search", "my", "the", "a", "me", "all", "with",
        "transaction", "transactions", "spending", "spend", "payment", "payments",
        "change", "mark", "move", "to", "as", "under", "that", "this", "it",
        "ksh", "kes", "bob", "please", "tafadhali", "sent", "paid", "received",
        "how", "much", "did", "have", "has", "from", "for", "on", "in", "was"
    )

    private fun routeFind(
        normalized: String,
        entities: BuddyEntities,
        knownMerchants: List<String>
    ): RoutedCommand? {
        val withMatch = Regex("transactions? with (.+)").find(normalized)
        val outNow = DIRECTION_OUT.any { BuddyTextNorm.hasWord(normalized, it) }
        val merchant = if (withMatch != null) {
            withMatch.groupValues[1].trim().takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
        } else {
            if (FIND_VERBS.none { BuddyTextNorm.hasWord(normalized, it) } && !outNow) return null
            if (TXN_NOUNS.none { BuddyTextNorm.hasWord(normalized, it) } && !outNow) return null
            resolveLedgerMerchant(normalized, entities.category, knownMerchants)
        }
        val filter = TxnFilter(
            merchant = merchant,
            category = entities.category,
            amount = entities.amount,
            expenseOnly = outNow,
            incomeOnly = DIRECTION_IN.any { BuddyTextNorm.hasWord(normalized, it) } && !outNow
        )
        if (filter.isEmpty()) return null
        return RoutedCommand(BuddyIntent.FindTransactions(filter), entities, 0.75f, normalized)
    }

    private fun routeReclassify(
        normalized: String,
        entities: BuddyEntities,
        knownMerchants: List<String>
    ): RoutedCommand? {
        if (!hasVerb(normalized, *RECLASSIFY_VERBS.toTypedArray())) return null
        if (!hasVerb(normalized, "to", "as", "under")) return null
        val category = entities.category
        if (category == null || category == "Other") return null
        val merchant = resolveLedgerMerchant(normalized, category, knownMerchants)
        return RoutedCommand(
            BuddyIntent.Reclassify(category, merchant, entities.amount),
            entities, 0.75f, normalized
        )
    }

    private fun routeDelete(
        normalized: String,
        entities: BuddyEntities,
        knownMerchants: List<String>
    ): RoutedCommand? {
        if (!hasVerb(normalized, *DELETE_VERBS.toTypedArray())) return null
        if (DELETE_GUARDS.any(normalized::contains)) return null
        if (!BuddyTextNorm.hasWord(normalized, "transaction") &&
            !BuddyTextNorm.hasWord(normalized, "that") &&
            !BuddyTextNorm.hasWord(normalized, "it") && entities.amount == null &&
            resolveLedgerMerchant(normalized, null, knownMerchants) == null
        ) return null
        val merchant = resolveLedgerMerchant(normalized, null, knownMerchants)
        return RoutedCommand(BuddyIntent.Delete(merchant, entities.amount), entities, 0.7f, normalized)
    }

    private fun resolveLedgerMerchant(
        normalized: String,
        category: String?,
        knownMerchants: List<String>
    ): String? {
        if (knownMerchants.isEmpty()) return null
        val drop = MERCHANT_STOPWORDS + BuddyBrain.BUDDY_CATEGORIES.map { it.lowercase() } +
            listOfNotNull(category?.lowercase())
        val tokens = normalized.lowercase().split(Regex("[^a-z0-9]+"))
            .filter { it.length > 2 && it !in drop && it.toDoubleOrNull() == null }
        if (tokens.isEmpty()) return null
        return knownMerchants.map { m ->
            val low = m.lowercase()
            m to tokens.count { low.contains(it) }
        }.filter { it.second > 0 }.maxByOrNull { it.second }?.first
    }

    private fun merchantRemainder(normalized: String, amount: Double?, category: String?): String? {
        var s = " $normalized "
        s = s.replace(Regex("\\d[\\d,]*"), " ")
        (ADD_VERBS + ADD_STOPWORDS + listOfNotNull(category?.lowercase())).forEach { w ->
            s = s.replace(Regex("(?<![a-z])${Regex.escape(w.trim())}(?![a-z])"), " ")
        }
        return s.replace(Regex("[^a-z ]"), " ").split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ").trim().takeIf { it.isNotBlank() }
            ?.replaceFirstChar { it.uppercase() }?.take(40)
    }
}
