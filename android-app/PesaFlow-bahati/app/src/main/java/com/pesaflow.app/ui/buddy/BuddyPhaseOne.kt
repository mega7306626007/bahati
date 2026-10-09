package com.pesaflow.app.ui.buddy

// Thin Phase-1 composition, not a manager: route → capability → validate →
// compose. Returns Unhandled for anything outside Phase 1 so the legacy
// answer chain keeps working unchanged. Each stage stays independently
// testable; this object only orders them.
object BuddyPhaseOne {

    private val BARE_WHY = listOf("why", "why?", "explain", "how", "what", "huh")

    // Clause splitting: " and ", " then ", " plus ", ";" — but never
    // between digits ("between 100 and 200" stays whole). Max 3 clauses.
    private val CLAUSE_SPLIT = Regex("(?<!\\d)\\s+(?:and|then|plus)\\s+(?!\\d)|;|&&")
    private const val MAX_CLAUSES = 3

    fun splitClauses(raw: String): List<String> =
        raw.split(CLAUSE_SPLIT).map { it.trim() }.filter { it.isNotBlank() }.take(MAX_CLAUSES)

    fun handle(
        raw: String,
        state: BuddyReadState,
        txs: List<com.pesaflow.app.data.models.Transaction> = emptyList(),
        knownMerchants: List<String> = emptyList(),
        screenContext: String? = null,
        budgetExplanation: String? = null,
        bills: List<com.pesaflow.app.data.models.Bill> = emptyList(),
        debts: List<com.pesaflow.app.data.models.Debt> = emptyList()
    ): BuddyOutcome {
        val clauses = splitClauses(raw)
        if (clauses.size > 1) {
            val parts = clauses.map {
                resolveOne(BuddyIntentRouter.route(it, knownMerchants), it, state, txs, screenContext, budgetExplanation, bills, debts)
            }
            val handled = parts.filter { it !is BuddyOutcome.Unhandled }
            if (handled.isEmpty()) return BuddyOutcome.Unhandled
            return BuddyOutcome.Compound(handled, partial = handled.size < parts.size)
        }
        val routed = BuddyIntentRouter.route(raw, knownMerchants)
        return resolveOne(routed, raw, state, txs, screenContext, budgetExplanation, bills, debts)
    }

    private fun resolveOne(
        routed: RoutedCommand,
        raw: String,
        state: BuddyReadState,
        txs: List<com.pesaflow.app.data.models.Transaction>,
        screenContext: String?,
        budgetExplanation: String?,
        bills: List<com.pesaflow.app.data.models.Bill>,
        debts: List<com.pesaflow.app.data.models.Debt>
    ): BuddyOutcome {
        // Screen-aware bare questions: summoned from Budgets, "why?" means
        // the budget progress on that screen — answered from its own math.
        if (routed.intent is BuddyIntent.Unknown &&
            screenContext == com.pesaflow.app.ui.NavRoutes.BUDGETS &&
            BARE_WHY.any { raw.trim().lowercase() == it } &&
            budgetExplanation != null
        ) {
            return BuddyOutcome.Answer(budgetExplanation)
        }
        BuddyCapabilities.forIntent(routed.intent) ?: return BuddyOutcome.Unhandled
        // Financial mutations never execute from prose: valid drafts become
        // confirm cards, incomplete ones ask for the missing entity.
        if (routed.intent is BuddyIntent.QueryIncome ||
            routed.intent is BuddyIntent.AddIncomeSource ||
            routed.intent is BuddyIntent.AddTransaction ||
            routed.intent is BuddyIntent.CreateBudget ||
            routed.intent is BuddyIntent.AddBill ||
            routed.intent is BuddyIntent.AddDebt ||
            routed.intent is BuddyIntent.CreateGoal ||
            routed.intent is BuddyIntent.CreateReminder ||
            routed.intent is BuddyIntent.ListReminders ||
            routed.intent is BuddyIntent.CancelReminders ||
            routed.intent is BuddyIntent.ApplyPlan
        ) {
            return BuddyResponseComposer.compose(routed.intent, state)
        }
        val lang = state.lang
        when (val intent = routed.intent) {
            is BuddyIntent.MarkBillPaid -> return resolveBill(intent.billName, intent.amount, bills, lang)
            is BuddyIntent.EditBill -> return resolveBillEdit(intent.billName, intent.amount, bills, lang)
            is BuddyIntent.EditBillDate -> return resolveBillDate(intent.billName, intent.dueTimestamp, bills, lang)
            is BuddyIntent.RecordDebtPayment -> return resolveDebtPay(intent.person, intent.amount, debts, lang)
            is BuddyIntent.ResolvedDebtPay -> {
                val command = BuddyCommand.RecordDebtPayment(intent.debtId, intent.amount)
                return BuddyOutcome.ProposeCommand(command, debtPaySummary(intent.debtId, intent.amount, debts, lang))
            }
            else -> Unit
        }
        when (val intent = routed.intent) {
            is BuddyIntent.FindTransactions -> {
                val result = BuddyTxnQuery.summarize(BuddyTxnQuery.filter(txs, intent.filter))
                return BuddyResponseComposer.answerTxnQuery(intent.filter, result, state.lang)
            }
            is BuddyIntent.Reclassify ->
                return resolveMutation(
                    txs, intent.merchant, intent.amount,
                    com.pesaflow.app.ui.dashboard.PendingCandidateAction.Reclassify(intent.category),
                    "reclassify to ${intent.category}",
                    { ids -> BuddyCommand.ReclassifyTransaction(ids, intent.category) },
                    lang
                )
            is BuddyIntent.Delete ->
                return resolveMutation(
                    txs, intent.merchant, intent.amount,
                    com.pesaflow.app.ui.dashboard.PendingCandidateAction.Delete,
                    "delete",
                    { ids -> BuddyCommand.DeleteTransaction(ids) },
                    lang
                )
            is BuddyIntent.ResolvedReclassify ->
                if (intent.txIds.isEmpty()) {
                    return BuddyOutcome.Ask(BuddyStrings.askChoicesExpired(state.lang))
                } else {
                    return BuddyOutcome.ProposeCommand(
                        BuddyCommand.ReclassifyTransaction(intent.txIds, intent.category),
                        BuddyResponseComposer.summarize(
                            BuddyCommand.ReclassifyTransaction(intent.txIds, intent.category),
                            state.lang
                        )
                    )
                }
            is BuddyIntent.ResolvedDelete ->
                if (intent.txIds.isEmpty()) {
                    return BuddyResponseComposer.bulkDeleteUnsupported(state.lang)
                } else {
                    return BuddyOutcome.ProposeCommand(
                        BuddyCommand.DeleteTransaction(intent.txIds),
                        BuddyResponseComposer.summarize(
                            BuddyCommand.DeleteTransaction(intent.txIds), state.lang
                        )
                    )
                }
            else -> Unit
        }
        return when (val validation = BuddyValidator.validate(routed.intent)) {
            BuddyValidation.Allowed -> BuddyResponseComposer.compose(routed.intent, state)
            is BuddyValidation.NeedsConfirmation ->
                BuddyOutcome.Ask(BuddyStrings.needsConfirmation(validation.prompt, state.lang))
            is BuddyValidation.Unsupported ->
                BuddyOutcome.Unhandled
        }
    }

    private fun openBills(bills: List<com.pesaflow.app.data.models.Bill>) =
        bills.filter { it.status != "PAID" }

    private fun resolveBill(
        name: String?,
        amount: Double?,
        bills: List<com.pesaflow.app.data.models.Bill>,
        lang: com.pesaflow.app.data.models.AppLanguage
    ): BuddyOutcome {
        val matches = openBills(bills).filter {
            name == null || it.name.contains(name, ignoreCase = true)
        }.let { list ->
            if (amount != null) list.filter { kotlin.math.abs(it.amount - amount) < 0.01 } else list
        }
        return when {
            matches.isEmpty() -> BuddyOutcome.Ask(BuddyStrings.noBillMatch(lang))
            matches.size == 1 -> {
                val b = matches.first()
                val command = BuddyCommand.MarkBillPaid(b.id)
                BuddyOutcome.ProposeCommand(
                    command,
                    BuddyStrings.billPaySummary(
                        b.name,
                        com.pesaflow.app.data.finance.MoneyFormatter.compact(com.pesaflow.app.data.finance.Money.of(b.amount)),
                        lang
                    )
                )
            }
            else -> BuddyOutcome.Ask(
                BuddyStrings.askWhichBill(
                    matches.take(5).joinToString("\n") {
                        "• ${it.name} · ${com.pesaflow.app.data.finance.MoneyFormatter.compact(com.pesaflow.app.data.finance.Money.of(it.amount))}"
                    },
                    lang
                )
            )
        }
    }

    private fun fmtDay(ts: Long): String =
        java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault()).format(java.util.Date(ts))

    private fun resolveBillDate(
        name: String?,
        dueTimestamp: Long?,
        bills: List<com.pesaflow.app.data.models.Bill>,
        lang: com.pesaflow.app.data.models.AppLanguage
    ): BuddyOutcome {
        if (dueTimestamp == null || dueTimestamp <= 0) {
            return BuddyOutcome.Ask(BuddyStrings.askBillDate(lang))
        }
        val matches = openBills(bills).filter { name == null || it.name.contains(name, ignoreCase = true) }
        return when {
            matches.isEmpty() -> BuddyOutcome.Ask(BuddyStrings.noBillMatch(lang))
            matches.size == 1 -> {
                val b = matches.first()
                val command = BuddyCommand.EditBillDate(b.id, dueTimestamp)
                BuddyOutcome.ProposeCommand(
                    command, BuddyStrings.billMoveSummary(b.name, fmtDay(dueTimestamp), lang)
                )
            }
            else -> BuddyOutcome.Ask(
                BuddyStrings.billMoveWhich(
                    fmtDay(dueTimestamp),
                    matches.take(5).joinToString("\n") { "• ${it.name}" },
                    lang
                )
            )
        }
    }

    private fun resolveBillEdit(
        name: String?,
        amount: Double?,
        bills: List<com.pesaflow.app.data.models.Bill>,
        lang: com.pesaflow.app.data.models.AppLanguage
    ): BuddyOutcome {
        if (amount == null || amount <= 0) {
            return BuddyOutcome.Ask(BuddyStrings.askBillAmountFor(lang))
        }
        val matches = openBills(bills).filter { name == null || it.name.contains(name, ignoreCase = true) }
        return when {
            matches.isEmpty() -> BuddyOutcome.Ask(BuddyStrings.noBillMatch(lang))
            matches.size == 1 -> {
                val b = matches.first()
                val command = BuddyCommand.EditBill(b.id, amount)
                BuddyOutcome.ProposeCommand(
                    command,
                    BuddyStrings.billEditSummary(
                        b.name,
                        com.pesaflow.app.data.finance.MoneyFormatter.compact(com.pesaflow.app.data.finance.Money.of(amount)),
                        lang
                    )
                )
            }
            else -> BuddyOutcome.Ask(
                BuddyStrings.billEditWhich(
                    com.pesaflow.app.data.finance.MoneyFormatter.compact(com.pesaflow.app.data.finance.Money.of(amount)),
                    matches.take(5).joinToString("\n") { "• ${it.name}" },
                    lang
                )
            )
        }
    }

    private fun resolveDebtPay(
        person: String?,
        amount: Double?,
        debts: List<com.pesaflow.app.data.models.Debt>,
        lang: com.pesaflow.app.data.models.AppLanguage
    ): BuddyOutcome {
        if (person.isNullOrBlank()) {
            return BuddyOutcome.Ask(BuddyStrings.askDebtPerson(lang))
        }
        if (amount == null || amount <= 0) {
            return BuddyOutcome.Ask(BuddyStrings.askDebtAmountFor(person, lang))
        }
        val matches = debts.filter { it.status != "PAID" && it.person.contains(person, ignoreCase = true) }
        return when {
            matches.isEmpty() -> BuddyOutcome.Ask(BuddyStrings.noDebtMatch(person, lang))
            matches.size == 1 -> {
                val command = BuddyCommand.RecordDebtPayment(matches.first().id, amount)
                BuddyOutcome.ProposeCommand(command, debtPaySummary(matches.first().id, amount, debts, lang))
            }
            else -> {
                val cands = matches.take(5).map {
                    com.pesaflow.app.ui.dashboard.PendingCandidate(
                        it.id,
                        "${it.person} · ${com.pesaflow.app.data.finance.MoneyFormatter.compact(com.pesaflow.app.data.finance.Money.of(it.amount))} owed",
                        com.pesaflow.app.ui.dashboard.PendingCandidateAction.DebtPay(it.id, amount)
                    )
                }
                com.pesaflow.app.ui.dashboard.BuddyMemory.pendingCandidates = cands
                BuddyResponseComposer.askWhich(
                    cands,
                    "record the ${com.pesaflow.app.data.finance.MoneyFormatter.compact(com.pesaflow.app.data.finance.Money.of(amount))} payment on",
                    lang = lang
                )
            }
        }
    }

    private fun debtPaySummary(
        debtId: String,
        amount: Double,
        debts: List<com.pesaflow.app.data.models.Debt>,
        lang: com.pesaflow.app.data.models.AppLanguage
    ): String {
        val d = debts.firstOrNull { it.id == debtId }
        val who = d?.let { "${it.person} (${com.pesaflow.app.data.finance.MoneyFormatter.compact(com.pesaflow.app.data.finance.Money.of(it.amount))} owed)" } ?: "that debt"
        return BuddyStrings.debtPaySummary(
            who,
            com.pesaflow.app.data.finance.MoneyFormatter.compact(com.pesaflow.app.data.finance.Money.of(amount)),
            lang
        )
    }

    private fun resolveMutation(
        txs: List<com.pesaflow.app.data.models.Transaction>,
        merchant: String?,
        amount: Double?,
        action: com.pesaflow.app.ui.dashboard.PendingCandidateAction,
        verb: String,
        toCommand: (List<String>) -> BuddyCommand,
        lang: com.pesaflow.app.data.models.AppLanguage
    ): BuddyOutcome {
        // "This/that one" with no name: offer the 5 most recent ledger rows.
        val hintMerchant = merchant
        return when (val r = BuddyTxnResolver.resolve(txs, hintMerchant, amount, action)) {
            TxnResolution.None -> if (hintMerchant == null) {
                // "This/that one" with no name: offer the 5 most recent rows.
                val cands = txs.filter { !it.isSample }
                    .sortedByDescending { it.dateTimestamp }.take(5)
                    .map {
                        com.pesaflow.app.ui.dashboard.PendingCandidate(
                            it.id, BuddyTxnResolver.label(it), action
                        )
                    }
                if (cands.isEmpty()) return BuddyOutcome.Answer(BuddyStrings.noTransactionsYet(lang))
                com.pesaflow.app.ui.dashboard.BuddyMemory.pendingCandidates = cands
                BuddyResponseComposer.askWhich(cands, verb, scope = "5 most recent", lang = lang)
            } else {
                BuddyOutcome.Ask(BuddyStrings.nothingWaitingReview(lang))
            }
            is TxnResolution.Single -> {
                val command = toCommand(r.txIds)
                BuddyOutcome.ProposeCommand(command, BuddyResponseComposer.summarize(command, lang))
            }
            is TxnResolution.Multiple ->
                BuddyResponseComposer.askWhich(r.candidates, verb, lang = lang)
        }
    }
}
