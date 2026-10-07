package com.pesaflow.app.data.parsers

import android.content.Context
import com.pesaflow.app.data.models.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern


object MpesaParser {

    // User category rules cache — populated by ViewModel on rule changes.
    // Avoids threading DAO through every parseMessage call site.
    @Volatile
    private var cachedRules: List<com.pesaflow.app.data.models.CategoryRule> = emptyList()

    fun setCachedRules(rules: List<com.pesaflow.app.data.models.CategoryRule>) {
        cachedRules = rules.filter { it.enabled }
    }

    /**
     * Match user-defined category rule against SMS text + merchant.
     * Returns the rule's category, or null. Case-insensitive substring.
     */
    fun matchCategoryRule(rawText: String, merchant: String): String? {
        val text = rawText.lowercase()
        val merch = merchant.lowercase()
        return cachedRules.firstOrNull { rule ->
            val kw = rule.keyword.lowercase()
            text.contains(kw) || merch.contains(kw)
        }?.category
    }


    private val p2pRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.\\s*You\\s+have\\s+sent\\s+KSh\\s*([0-9,.]+)\\s+to\\s+([^.]+?)\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)"
    )
    private val merchantRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.\\s*KSh\\s*([0-9,.]+)\\s+paid\\s+to\\s+([^.]+?)\\.\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)"
    )
    private val receiveRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.\\s*You\\s+have\\s+received\\s+KSh\\s*([0-9,.]+)\\s+from\\s+([^.]+?)\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)"
    )
    private val airtimeRegex = Pattern.compile(
        "(?i)(?:([A-Z0-9]{8,12})\\s+)?Confirmed\\.\\s*You\\s+bought\\s+KSh\\s*([0-9,.]+)\\s+of\\s+Airtime\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)"
    )
    private val withdrawRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.\\s*Withdraw\\s+KSh\\s*([0-9,.]+)\\s+from\\s+([^.]+?)\\.\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)"
    )
    private val paybillRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.\\s*KSh\\s*([0-9,.]+)\\s+sent\\s+to\\s+([^.]+?)(?:\\s+for\\s+account\\s+([0-9A-Za-z-]+))?\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)"
    )
    private val tillRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.\\s*KSh\\s*([0-9,.]+)\\s+paid\\s+to\\s+(?:Till\\s+)?([0-9]{5,9})\\s*-?\\s*([^.]*?)\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)"
    )
    // Pochi la Biashara: "sent KSh X to NAME ... pochi ..." and
    // "KSh X paid to NAME ... pochi" shapes. The (Pochi) tag keeps pochi
    // payments grouped apart from same-name P2P sends; the name still
    // drives the category (mama mboga pochi → Food).
    private val pochiSentRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.[^.]{0,80}?KSh\\s*([0-9,.]+)\\s+sent\\s+to\\s+([^.]*?pochi[^.]*?)\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)"
    )
    private val pochiPaidRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.\\s*(?:KSh|KES|Ksh)\\s*([0-9,.]+)\\s+paid\\s+to\\s+([^.]*?pochi[^.]*?)\\.\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)"
    )
    private val giftedAirtimeRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.\\s*You\\s+have\\s+sent\\s+KSh\\s*([0-9,.]+)\\s+worth\\s+of\\s+airtime\\s+to\\s+([0-9+]+)\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)"
    )
    private val bundleRegex = Pattern.compile(
        "(?i)Confirmed\\.[^.]{0,80}?(?:bought|purchased)[^.]{0,80}?bundles?[^.]{0,80}?KSh\\s*([0-9,.]+)"
    )
    private val mshwariRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.[^.]{0,80}?transferred\\s+KSh\\s*([0-9,.]+)\\s+(?:from\\s+M-?PESA\\s+)?to\\s+M-?SHWARI"
    )
    private val bankInRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.[^.]{0,80}?transferred\\s+KSh\\s*([0-9,.]+)\\s+from\\s+([A-Za-z\\- ]+?)\\s+to\\s+M-?PESA"
    )
    private val bankOutRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.[^.]{0,80}?transferred\\s+KSh\\s*([0-9,.]+)\\s+(?:from\\s+M-?PESA\\s+)?to\\s+(KCB|EQUITY|CO-?OP|STANBIC|ABSA|FAMILY|DTB|NCBA)"
    )
    private val fulizaRegex = Pattern.compile(
        "([A-Z0-9]{8,12})\\s*Confirmed\\.[^.]{0,80}?borrowed\\s+KSh\\s*([0-9,.]+)",
        Pattern.CASE_INSENSITIVE
    )
    // Fuliza limit/balance notices ("your Fuliza balance is KSh 200") — money you
    // could touch, still a loan. Parsed so the user gets asked, never auto-spent.
    private val fulizaBalanceRegex = Pattern.compile(
        "(?i)fuliza[^.]{0,80}?(?:limit|balance|available|loan)[^.]{0,60}?(?:KSh|KES|Ksh)\\s*([0-9,.]+)"
    )
    // Bank SMS (sender is the bank, not M-Pesa): require an explicit bank name
    // plus a credit/debit verb plus an amount — never amount-alone.
    private val bankCreditRegex = Pattern.compile(
        "(?i)(KCB|EQUITY|CO-?OP(?:ERATIVE)?|ABSA|STANBIC|FAMILY|DTB|NCBA|I&M|STANCHART|BANK).{0,120}?(credited|deposited|paid in|payment received|received).{0,60}?(?:KSh|KES|Ksh)\\s*([0-9,.]+)"
    )
    private val bankDebitRegex = Pattern.compile(
        "(?i)(KCB|EQUITY|CO-?OP(?:ERATIVE)?|ABSA|STANBIC|FAMILY|DTB|NCBA|I&M|STANCHART|BANK).{0,120}?(debited|withdrawn|paid out|charged|deducted|purchase).{0,60}?(?:KSh|KES|Ksh)\\s*([0-9,.]+)"
    )
    // HELB upkeep/disbursement mentions outside the standard M-Pesa receive shape.
    private val helbRegex = Pattern.compile(
        "(?i)HELB[^.]{0,80}?(?:KSh|KES|Ksh)\\s*([0-9,.]+)"
    )
    // Okoa Jahazi / emergency airtime advances (debt-like, tracked as Airtime).
    private val okoaRegex = Pattern.compile(
        "(?i)(?:okoa(?: jahazi)?|emergency airtime)[^.]{0,60}?(?:KSh|KES|Ksh)\\s*([0-9,.]+)"
    )

    // Digital lenders: Tala/Branch/Zenka/Hustler Fund disburse by SMS without
    // M-Pesa codes — parse conservatively (lender + disbursal verb + amount).
    private val loanRegex = Pattern.compile(
        "(?i)(tala|branch|zenka|hustler fund|mshwari loan|kcb m-pesa).{0,60}?(?:borrowed|disbursed|approved|advanced|loan of|sent you).{0,60}?(?:KSh|KES|Ksh)\\s*([0-9,.]+)"
    )

    // Verb-first order too: "borrowed KSh 1,000 from Hustler Fund".
    private val loanRevRegex = Pattern.compile(
        "(?i)(?:borrowed|disbursed|approved|advanced)\\s+(?:KSh|KES|Ksh)\\s*([0-9,.]+)\\s+from\\s+(tala|branch|zenka|hustler fund|mshwari loan|kcb m-pesa)"
    )

    private fun loanName(raw: String): String {
        val l = raw.lowercase()
        return when {
            l.contains("hustler") -> "Hustler Fund"
            l.contains("branch") -> "Branch"
            l.contains("zenka") -> "Zenka"
            l.contains("kcb") -> "KCB M-Pesa"
            l.contains("mshwari") -> "M-Shwari Loan"
            else -> "Tala"
        }
    }

    fun parseLoan(smsBody: String): PendingTransaction? {
        val clean = smsBody.replace("\n", " ").trim()
        val m = loanRegex.matcher(clean)
        val mr = loanRevRegex.matcher(clean)
        val (amountStr, lender) = when {
            m.find() -> m.group(2) to loanName(m.group(1) ?: "")
            mr.find() -> mr.group(1) to loanName(mr.group(2) ?: "")
            else -> return null
        }
        return buildPending(
            code = null,
            amountStr = amountStr,
            party = lender,
            dateStr = null,
            timeStr = null,
            type = TransactionType.INCOME,
            raw = smsBody.replace("\n", " ").trim(),
            confidence = 0.75f
        )?.copy(category = "Debt", confidenceScore = 0.75f)
    }
    // Agent deposit: cash → M-Pesa (money entering the tracked wallet).
    private val depositRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})\\s*Confirmed\\.[^.]{0,60}?deposited\\s+(?:KSh|KES|Ksh)\\s*([0-9,.]+)\\s+to\\s+([^.]+?)\\s+on\\s+([0-9/\\-]{6,12})\\s+at\\s+([0-9:.\\sAPM]+)"
    )
    // Other telcos (Airtel/Telkom/Equitel): no Confirmed header, "successfully" shape.
    private val telcoSentRegex = Pattern.compile(
        "(?i)(?:successfully\\s+)?sent\\s+(?:KSh|KES|Ksh)\\s*([0-9,.]+)\\s+to\\s+([0-9+\\s]{7,15}|[^.,]+?)(?:\\s+on\\s+|\\s*,|\\s*\\.|\$)"
    )
    private val telcoReceivedRegex = Pattern.compile(
        "(?i)(?:successfully\\s+)?(?:received|credited)[^.]{0,40}?(?:KSh|KES|Ksh)\\s*([0-9,.]+)\\s+from\\s+([0-9+\\s]{7,15}|[^.,]+?)(?:\\s+on\\s+|\\s+at\\s+|\\s*,|\\s*\\.|\$)"
    )
    // "purchased KSh20 airtime" alongside the classic "bought KSh X of Airtime".
    private val airtimeBuyRegex = Pattern.compile(
        "(?i)(?:bought|purchased)\\s+(?:KSh|KES|Ksh)\\s*([0-9,.]+)[^.]{0,30}?(?:airtime|bundles|data)"
    )
    // Bank bodies that omit the bank name (the sender carries it — passed in).
    private val bankCreditBareRegex = Pattern.compile(
        "(?i)(credited|deposited|paid in|payment received).{0,60}?(?:KSh|KES|Ksh)\\s*([0-9,.]+)"
    )
    private val bankDebitBareRegex = Pattern.compile(
        "(?i)(debited|withdrawn|withdrawal|paid out|charged|deducted|purchase of).{0,60}?(?:KSh|KES|Ksh)\\s*([0-9,.]+)"
    )
    private val BANK_SENDERS = listOf("KCB", "EQUITY", "CO-OP", "COOP", "ABSA", "STANBIC", "FAMILY", "DTB", "NCBA", "I&M", "STANCHART", "HELB", "TALA", "BRANCH", "HUSTLER", "KWFT", "SIDIAN", "ECOBANK")

    // Senders worth opening even without a "Confirmed" header.
    fun isTransactionalSender(sender: String): Boolean {
        val s = sender.lowercase()
        return s.contains("mpesa") || s.contains("safaricom") || s.contains("airtel") ||
            s.contains("telkom") || s.contains("equitel") ||
            BANK_SENDERS.any { s.contains(it.lowercase().replace("-", "")) || s.contains(it.lowercase()) }
    }

    fun bankNameOfSender(sender: String): String? {
        val s = sender.uppercase()
        return BANK_SENDERS.firstOrNull { s.contains(it) }
    }
    private val fallbackRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})[^.]{0,60}?Confirmed[^.]{0,80}?KSh\\s*([0-9,.]+)"
    )
    private val bareReversalRegex = Pattern.compile(
        "(?i)([A-Z0-9]{8,12})[^.]{0,60}?(reversed|reversal)[^.]{0,80}?KSh\\s*([0-9,.]+)"
    )


    // smsTimestampMs is the inbox/receipt clock (Telephony "date" column,
    // SmsMessage timestamp, notification post time). Dateless texts —
    // bundles, bank, Fuliza, M-Shwari — have no "on 12/9/26" inside, so
    // without this they all land at scan time: a 4-month-old bundle buy
    // would pollute "today". 0 = unknown (manual paste/share/test).
    fun parseMessage(smsBody: String, sender: String = "", smsTimestampMs: Long = 0L): PendingTransaction? {
        val sanitized = smsBody.replace("\n", " ").trim()
        val senderBank = bankNameOfSender(sender)

        // Pochi la Biashara first: the keyword shapes beat generic P2P so
        // pochi payments are tagged, never swallowed as plain sends.
        var matcher = pochiSentRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = (matcher.group(3) ?: "").trim() + " (Pochi)",
                dateStr = matcher.group(4),
                timeStr = matcher.group(5),
                type = TransactionType.EXPENSE,
                raw = sanitized,
                confidence = 0.9f
            )
        }
        matcher = pochiPaidRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = (matcher.group(3) ?: "").trim() + " (Pochi)",
                dateStr = matcher.group(4),
                timeStr = matcher.group(5),
                type = TransactionType.EXPENSE,
                raw = sanitized,
                confidence = 0.9f
            )
        }

        // Match standard Sent Money
        matcher = p2pRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = matcher.group(3),
                dateStr = matcher.group(4),
                timeStr = matcher.group(5),
                type = TransactionType.EXPENSE,
                raw = sanitized
            )
        }


        // Match Lipa Na M-Pesa Buy Goods/Paybill
        matcher = merchantRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = matcher.group(3),
                dateStr = matcher.group(4),
                timeStr = matcher.group(5),
                type = TransactionType.EXPENSE,
                raw = sanitized
            )
        }


        // Match Received Money
        matcher = receiveRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = matcher.group(3),
                dateStr = matcher.group(4),
                timeStr = matcher.group(5),
                type = TransactionType.INCOME,
                raw = sanitized
            )
        }


        // Match Airtime purchase (no payee merchant)
        matcher = airtimeRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = "Safaricom Airtime",
                dateStr = matcher.group(3),
                timeStr = matcher.group(4),
                type = TransactionType.EXPENSE,
                raw = sanitized
            )
        }


        // Match Agent withdrawal
        matcher = withdrawRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = matcher.group(3),
                dateStr = matcher.group(4),
                timeStr = matcher.group(5),
                type = TransactionType.EXPENSE,
                raw = sanitized
            )
        }


        // Match Paybill with optional account number
        matcher = paybillRegex.matcher(sanitized)
        if (matcher.find()) {
            val business = (matcher.group(3) ?: "Paybill").trim()
            val account = matcher.group(4)?.trim().orEmpty()
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = if (account.isNotEmpty()) "$business • $account" else business,
                dateStr = matcher.group(5),
                timeStr = matcher.group(6),
                type = TransactionType.EXPENSE,
                raw = sanitized
            )
        }


        // Match Till payment (shop name optional — Shopping fallback when unknown)
        matcher = tillRegex.matcher(sanitized)
        if (matcher.find()) {
            val till = matcher.group(3) ?: ""
            val name = (matcher.group(4) ?: "").trim()
            val merchant = name.ifEmpty { "Till $till" }
            val pending = buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = merchant,
                dateStr = matcher.group(5),
                timeStr = matcher.group(6),
                type = TransactionType.EXPENSE,
                raw = sanitized
            )
            return pending?.copy(
                category = if (pending.category == "Other") "Shopping" else pending.category,
                confidenceScore = 0.85f
            )
        }


        // Match gifted airtime ("worth of airtime to 0712...")
        matcher = giftedAirtimeRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = "Airtime to ${matcher.group(3)}",
                dateStr = matcher.group(4),
                timeStr = matcher.group(5),
                type = TransactionType.EXPENSE,
                raw = sanitized
            )
        }


        // Match data-bundle purchase (dateless Safaricom format — inbox
        // clock when the scan has it, else now)
        matcher = bundleRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(1),
                party = "Safaricom Bundles",
                dateStr = null,
                timeStr = null,
                smsTimestampMs = smsTimestampMs,
                type = TransactionType.EXPENSE,
                raw = sanitized,
                confidence = 0.8f
            )
        }


        // Match M-Shwari deposit — real savings, excluded from spending insights
        matcher = mshwariRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = "M-Shwari",
                dateStr = null,
                timeStr = null,
                smsTimestampMs = smsTimestampMs,
                type = TransactionType.SAVING,
                raw = sanitized,
                confidence = 0.85f
            )
        }


        // Match bank → M-Pesa (money entering the tracked wallet)
        matcher = bankInRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = "${matcher.group(3)?.trim()} transfer",
                dateStr = null,
                timeStr = null,
                smsTimestampMs = smsTimestampMs,
                type = TransactionType.INCOME,
                raw = sanitized,
                confidence = 0.8f
            )
        }


        // Match M-Pesa → bank (money leaving the tracked wallet, kept as saving)
        matcher = bankOutRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = "${matcher.group(3)?.trim()} transfer",
                dateStr = null,
                timeStr = null,
                smsTimestampMs = smsTimestampMs,
                type = TransactionType.SAVING,
                raw = sanitized,
                confidence = 0.8f
            )
        }


        // Match Fuliza overdraft (borrowed, spendable now — user sorts the debt)
        matcher = fulizaRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = "Fuliza",
                dateStr = null,
                timeStr = null,
                smsTimestampMs = smsTimestampMs,
                type = TransactionType.INCOME,
                raw = sanitized,
                confidence = 0.8f
            )
        }


        // Match HELB mentions with an amount (bank-side or upkeep texts that
        // miss the standard M-Pesa receive shape — M-Pesa HELB already matched above).
        matcher = helbRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(1),
                party = "HELB",
                dateStr = null,
                timeStr = null,
                smsTimestampMs = smsTimestampMs,
                type = TransactionType.INCOME,
                raw = sanitized,
                confidence = 0.85f
            )
        }


        // Match bank credit SMS (money entering a bank account).
        matcher = bankCreditRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(3),
                party = (matcher.group(1) ?: "Bank").trim(),
                dateStr = null,
                timeStr = null,
                smsTimestampMs = smsTimestampMs,
                type = TransactionType.INCOME,
                raw = sanitized,
                confidence = 0.7f,
                method = PaymentMethod.BANK_TRANSFER
            )
        }


        // Match bank debit SMS (money leaving a bank account).
        matcher = bankDebitRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(3),
                party = (matcher.group(1) ?: "Bank").trim(),
                dateStr = null,
                timeStr = null,
                smsTimestampMs = smsTimestampMs,
                type = TransactionType.EXPENSE,
                raw = sanitized,
                confidence = 0.7f,
                method = PaymentMethod.BANK_TRANSFER
            )
        }


        // Match Okoa Jahazi / emergency airtime advances.
        matcher = okoaRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(1),
                party = "Okoa Jahazi",
                dateStr = null,
                timeStr = null,
                smsTimestampMs = smsTimestampMs,
                type = TransactionType.EXPENSE,
                raw = sanitized,
                confidence = 0.7f
            )
        }


        // Match Fuliza limit/balance notices — ask, never auto-spend.
        // "Outstanding" is what you OWE, not money in: never income.
        matcher = fulizaBalanceRegex.matcher(sanitized)
        if (matcher.find() && !sanitized.lowercase().contains("outstanding")) {
            return buildPending(
                code = null,
                amountStr = matcher.group(1),
                party = "Fuliza",
                dateStr = null,
                timeStr = null,
                smsTimestampMs = smsTimestampMs,
                type = TransactionType.INCOME,
                raw = sanitized,
                confidence = 0.7f
            )
        }


        // Match digital-lender disbursements (Tala/Branch/Zenka/Hustler Fund).
        parseLoan(sanitized)?.let { return it }


        // Match agent deposit (cash → M-Pesa wallet).
        matcher = depositRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = (matcher.group(3) ?: "M-Pesa agent").trim(),
                dateStr = matcher.group(4),
                timeStr = matcher.group(5),
                type = TransactionType.INCOME,
                raw = sanitized,
                confidence = 0.9f
            )
        }


        // Match other-telco sends (Airtel/Telkom/Equitel shape).
        matcher = telcoSentRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(1),
                party = (matcher.group(2) ?: "Telco transfer").trim(),
                dateStr = null,
                timeStr = null,
                smsTimestampMs = smsTimestampMs,
                type = TransactionType.EXPENSE,
                raw = sanitized,
                confidence = 0.7f
            )
        }


        // Match other-telco receipts.
        matcher = telcoReceivedRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(1),
                party = (matcher.group(2) ?: "Telco transfer").trim(),
                dateStr = null,
                timeStr = null,
                smsTimestampMs = smsTimestampMs,
                type = TransactionType.INCOME,
                raw = sanitized,
                confidence = 0.7f
            )
        }


        // Match "purchased KSh20 airtime" variant.
        matcher = airtimeBuyRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = null,
                amountStr = matcher.group(1),
                party = "Safaricom Airtime",
                dateStr = null,
                timeStr = null,
                smsTimestampMs = smsTimestampMs,
                type = TransactionType.EXPENSE,
                raw = sanitized,
                confidence = 0.75f
            )
        }


        // Match bank SMS whose body omits the bank name (sender carries it).
        if (senderBank != null) {
            matcher = bankCreditBareRegex.matcher(sanitized)
            if (matcher.find()) {
                return buildPending(
                    code = null,
                    amountStr = matcher.group(2),
                    party = senderBank,
                    dateStr = null,
                    timeStr = null,
                    smsTimestampMs = smsTimestampMs,
                    type = TransactionType.INCOME,
                    raw = sanitized,
                    confidence = 0.7f,
                    method = PaymentMethod.BANK_TRANSFER
                )
            }
            matcher = bankDebitBareRegex.matcher(sanitized)
            if (matcher.find()) {
                return buildPending(
                    code = null,
                    amountStr = matcher.group(2),
                    party = senderBank,
                    dateStr = null,
                    timeStr = null,
                    smsTimestampMs = smsTimestampMs,
                    type = TransactionType.EXPENSE,
                    raw = sanitized,
                    confidence = 0.7f,
                    method = PaymentMethod.BANK_TRANSFER
                )
            }
        }


        // Match reversal notices without the standard "Confirmed." header
        matcher = bareReversalRegex.matcher(sanitized)
        if (matcher.find()) {
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(3),
                party = "M-Pesa Reversal",
                dateStr = null,
                timeStr = null,
                smsTimestampMs = smsTimestampMs,
                type = TransactionType.INCOME,
                raw = sanitized,
                confidence = 0.8f
            )
        }


        // Generic fallback: confirmed code + amount, lower confidence.
        // Reversals return money, everything else unknown leaves as expense.
        matcher = fallbackRegex.matcher(sanitized)
        if (matcher.find()) {
            val reversal = sanitized.contains("revers", ignoreCase = true)
            return buildPending(
                code = matcher.group(1),
                amountStr = matcher.group(2),
                party = if (reversal) "M-Pesa Reversal" else "M-Pesa",
                dateStr = null,
                timeStr = null,
                smsTimestampMs = smsTimestampMs,
                type = if (reversal) TransactionType.INCOME else TransactionType.EXPENSE,
                raw = sanitized
            )
        }


        // Universal reader — READ vs UNDERSTAND split. Anything carrying a
        // money verb + a real amount becomes a low-confidence pending row so
        // no transaction is ever silently lost; the user (or a rhythm pass)
        // supplies the understanding later. Balance-only notices stay null.
        return universalRead(sanitized, senderBank, smsTimestampMs)
    }

    private val amountRegex = Pattern.compile("(?i)(?:KSh|KES|Kshs?)\\s*([0-9,.]+)")
    private val codeRegex = Pattern.compile("\\b[A-Z0-9]{8,12}\\b")
    private val amountBanCtx = listOf("balance", "cost", "fee", "charge", "limit", "outstanding", "owed", "overdue", "arrears", "deni", "due")
    private val moneyVerbs = listOf(
        "sent", "send", "received", "receive", "paid", "pay", "withdraw",
        "withdrawn", "deposit", "transfer", "purchased", "purchase", "buy",
        "bought", "spent", "spend", "charged", "debited", "debit",
        "credited", "credit", "revers", "refund", "cashback", "borrow",
        "loan", "lent", "airtime", "bundle", "sav", "ziidi", "shwari",
        "locked", "fuliza", "okoa", "lipa", "payment", "payments", "topup",
        "top up", "recharge", "deduct", "deducted", "redeem", "redeemed",
        "bill", "billed", "kununua", "kutuma", "kutoka", "kuweka",
        "checkout", "check out"
    )
    // ALL-CAPS status words that match the code regex but are not codes.
    // Using one as a fingerprint would collapse unrelated texts into a
    // single "duplicate" and silently eat money.
    private val noiseCodes = setOf(
        "CONFIRMED", "REVERSED", "REVERSAL", "RECEIVED", "SUCCESSFUL",
        "COMPLETED", "APPROVED"
    )

    private fun universalRead(sanitized: String, senderBank: String?, smsTimestampMs: Long = 0L): PendingTransaction? {
        return try {
            val low = sanitized.lowercase()
            // Promos and windfalls are not movements, however KSh-shaped.
            if (listOf("dial *", "t&c", "terms and conditions", "promo", "congratulations", "you have won", "subscribe", " win ").any { low.contains(it) }) return null
            val hasVerb = moneyVerbs.any { low.contains(it) }
            // M-Pesa-shaped but in unlearned words: a real (non-noise)
            // reference code plus a confirmation marker. "CONFIRMED" alone
            // is not a code — otherwise every bare confirmation would pass.
            val confirmedShape = low.contains("confirm") &&
                codeRegex.matcher(sanitized).let { m ->
                    var ok = false
                    while (m.find()) {
                        if (m.group(0) !in noiseCodes) { ok = true; break }
                    }
                    ok
                }
            if (!hasVerb && !confirmedShape) return null
            // Informational texts carry no movement: balances, limits.
            if (low.contains("balance") && !listOf("sent", "received", "paid", "withdraw", "deposit", "transfer", "revers").any { low.contains(it) }) return null
            // First REAL amount: skip balance/cost/fee tails, never the movement.
            val amt = amountRegex.matcher(sanitized)
            var amountStr: String? = null
            while (amt.find()) {
                val start = amt.start(1)
                val ctx = sanitized.substring(maxOf(0, start - 18), start).lowercase()
                if (amountBanCtx.none { ctx.contains(it) }) {
                    amountStr = amt.group(1)
                    break
                }
            }
            amountStr ?: return null
            val code = codeRegex.matcher(sanitized).let { m ->
                var found: String? = null
                while (m.find()) {
                    val cand = m.group(0)
                    if (cand !in noiseCodes) { found = cand; break }
                }
                found
            }
            val type = when {
                hasVerb && (low.contains("shwari") || low.contains("ziidi") || (low.contains("sav") && low.contains("deposit"))) -> TransactionType.SAVING
                hasVerb && (low.contains("receiv") || low.contains("deposit") || low.contains("credit") ||
                    low.contains("revers") || low.contains("refund") || low.contains("cashback") ||
                    low.contains("borrow") || low.contains("loan")) -> TransactionType.INCOME
                else -> TransactionType.EXPENSE
            }
            val row = buildPending(
                code = code,
                amountStr = amountStr,
                party = senderBank ?: "M-Pesa",
                dateStr = null,
                timeStr = null,
                smsTimestampMs = smsTimestampMs,
                type = type,
                raw = sanitized,
                confidence = if (hasVerb) 0.5f else 0.35f
            ) ?: return null
            // Verb-less captures carry no direction evidence: tag Unknown so
            // Home asks "what was this?" instead of filing it as Other.
            if (!hasVerb) row.copy(category = "Unknown", confidenceScore = 0.35f) else row
        } catch (e: Exception) {
            null
        }
    }


    private fun buildPending(
        code: String?, amountStr: String?, party: String?,
        dateStr: String?, timeStr: String?, type: TransactionType, raw: String,
        confidence: Float = 0.95f,
        method: PaymentMethod = PaymentMethod.MPESA,
        smsTimestampMs: Long = 0L
    ): PendingTransaction? {
        return try {
            // Trailing dots are sentence punctuation, not decimals: "KSh99.00."
            // must parse as 99.00 — otherwise every end-of-sentence amount throws.
            val amount = amountStr?.replace(",", "")?.trimEnd('.', ',')?.trim()?.toDouble() ?: return null
            // Strip paybill account suffixes ("... for account 12345") and balance tails
            val merchant = (party?.trim() ?: "Unknown Party")
                .split(" for account")[0]
                .split("  ")[0]
                .trim()
                .ifEmpty { "Unknown Party" }
            val timestamp = parseDateTime(dateStr, timeStr, smsTimestampMs)
            // Codeless texts (bundles, bank-bare, HELB) still dedupe: a
            // fingerprint of merchant + amount + day keys them in both tables,
            // so rescans never double-insert what a code would have caught.
            val day = timestamp / (24L * 60 * 60 * 1000)
            val fingerprint = code?.takeIf { it.isNotBlank() }
                ?: "FP|${merchant.lowercase()}|${amount}|$day"

            // Priority: built-in rules → user category rules → ML tail.
            // Boundary: this only enriches the PENDING suggestion row — the
            // user still taps Confirm before anything reaches the ledger.
            // ML never overrides a rule hit, never throws (parsing must not).
            val ruleCategory = inferCategory(merchant, type)
            var finalCategory = ruleCategory
            var mlProvenance = ""
            if (ruleCategory == "Other" || ruleCategory == "Unknown") {
                // User-defined rules win over ML — explicit intent beats inference.
                val userRule = runCatching {
                    matchCategoryRule(raw, merchant)
                }.getOrNull()
                if (userRule != null) {
                    finalCategory = userRule
                    mlProvenance = "RULE"
                } else if (merchant.isNotBlank()) {
                    runCatching {
                        val sug = com.pesaflow.app.data.ml.MlEngine.suggestCategory(merchant, raw)
                        if (sug.confidence >= 0.80 && sug.category != "Other" && sug.category.isNotBlank()) {
                            finalCategory = sug.category
                            mlProvenance = "ML ${sug.source} ${(sug.confidence * 100).toInt()}%"
                        }
                    }
                }
            }

            PendingTransaction(
                amount = amount,
                type = type,
                category = finalCategory,
                merchant = merchant,
                dateTimestamp = timestamp,
                paymentMethod = method,
                source = TransactionSource.MPESA_SMS,
                sourceTransactionId = fingerprint,
                rawText = raw,
                confidenceScore = confidence,
                displayCategory = mlProvenance
            )
        } catch (e: Exception) {
            null
        }
    }


    private fun parseDateTime(dateStr: String?, timeStr: String?, smsTimestampMs: Long = 0L): Long {
        val now = System.currentTimeMillis()
        if (dateStr != null && timeStr != null) {
            return try {
                val cleanTime = timeStr.trim().replace("PM", " PM").replace("AM", " AM").replace("\\s+".toRegex(), " ")
                // 4-digit years first (a "12/09/2026" forced through dd/MM/yy lands
                // in 2020 — the "confirm yesterday" ghost). Strict, newest wins.
                val tries = listOf("dd/MM/yyyy h:mm a", "dd/MM/yyyy H:mm", "dd/MM/yy h:mm a", "dd/MM/yy H:mm")
                for (pattern in tries) {
                    try {
                        val format = SimpleDateFormat(pattern, Locale.US)
                        format.isLenient = false
                        val t = format.parse("$dateStr $cleanTime")?.time ?: continue
                        // Sanity: SMS dates are days old at most, never years off.
                        if (kotlin.math.abs(t - now) < 370L * 24 * 60 * 60 * 1000) return t
                    } catch (e: Exception) {
                        // Try the next pattern.
                    }
                }
                now
            } catch (e: Exception) {
                now
            }
        }
        // Dateless text (bundles, bank, Fuliza, M-Shwari — no "on 12/9/26"
        // inside): the inbox clock is the truth when we have it. Stamping
        // scan-time used to land 4-month-old texts in "today".
        if (smsTimestampMs > 0 && smsTimestampMs <= now + 24L * 60 * 60 * 1000) return smsTimestampMs
        // Time only, no inbox stamp (manual paste/share): resolve the clock
        // against now, stepping back 24h while it lands in the future — an
        // event can never be tomorrow.
        if (timeStr != null) resolveTimeOnly(timeStr, now)?.let { return it }
        return now
    }

    // "10:30 AM" with no date: today at 10:30 if that is past, else the same
    // clock yesterday (then the day before, capped). Null when unparseable.
    internal fun resolveTimeOnly(timeStr: String, nowMs: Long): Long? {
        return try {
            val clean = timeStr.trim().replace("PM", " PM").replace("AM", " AM").replace("\\s+".toRegex(), " ")
            var hm: Pair<Int, Int>? = null
            for (pattern in listOf("h:mm a", "H:mm", "h:mm:ss a", "H:mm:ss")) {
                try {
                    val d = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(clean) ?: continue
                    val c = Calendar.getInstance().apply { time = d }
                    hm = c.get(Calendar.HOUR_OF_DAY) to c.get(Calendar.MINUTE)
                    break
                } catch (e: Exception) {
                    // Try the next pattern.
                }
            }
            val (h, m) = hm ?: return null
            val day = Calendar.getInstance().apply {
                timeInMillis = nowMs
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, m)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            var guard = 0
            while (day.timeInMillis > nowMs && guard++ < 3) day.add(Calendar.DAY_OF_YEAR, -1)
            day.timeInMillis
        } catch (e: Exception) {
            null
        }
    }


    fun inferCategory(merchant: String, type: TransactionType): String {
        val lower = merchant.lowercase()
        // Transfers and loans first — they beat the INCOME default below.
        if (lower.contains("fuliza")) return "Other"
        if (lower.contains("m-shwari") || lower.contains("mshwari")) return "Savings"
        if (lower.contains("transfer") || lower.contains("agent") || lower.contains("kcb") || lower.contains("equity") || lower.contains("absa") || lower.contains("stanbic") || lower.contains("co-op") || lower.contains("coop") || lower.contains("family") || lower.contains("dtb") || lower.contains("ncba") || lower.contains("bank")) return "Transfer"
        // Main-format income kinds: name the money by where it came from.
        if (type == TransactionType.INCOME) {
            return when {
                lower.contains("helb") -> "HELB"
                lower.contains("scholarship") || lower.contains("bursary") -> "Scholarship"
                lower.contains("allowance") || lower.contains("upkeep") || lower.contains("parent") ||
                    lower.contains("mum") || lower.contains("dad") || lower.contains("mother") || lower.contains("father") -> "Parent"
                lower.contains("gift") -> "Gift"
                lower.contains("pochi") -> "Business"
                else -> "Salary"
            }
        }
        return when {
            lower.contains("safaricom") || lower.contains("airtime") || lower.contains("bundle") || lower.contains("data") || lower.contains("okoa") || lower.contains("saf") -> "Airtime"
            lower.contains("kplc") || lower.contains("token") || lower.contains("electric") -> "Electricity"
            lower.contains("supermarket") || lower.contains("naivas") || lower.contains("quickmart") || lower.contains("carrefour") || lower.contains("chandarana") -> "Shopping"
            lower.contains("cyber") || lower.contains("print") || lower.contains("book") || lower.contains("stationery") || lower.contains("photocopy") -> "Printing"
            lower.contains("java") || lower.contains("hotel") || lower.contains("cafe") || lower.contains("kiosk") || lower.contains("kibanda") || lower.contains("vibanda") || lower.contains("lunch") || lower.contains("supper") || lower.contains("breakfast") || lower.contains("dinner") || lower.contains("chapo") || lower.contains("chips") || lower.contains("smokie") || lower.contains("mutura") || lower.contains("ndengu") || lower.contains("ugali") || lower.contains("sukuma") || lower.contains("pilau") || lower.contains("chapati") || lower.contains("nyama") || lower.contains("kuku") || lower.contains("mama") || lower.contains("ng") || lower.contains("rest") || lower.contains("food") || lower.contains("eat") -> "Food"
            lower.contains("matatu") || lower.contains("uber") || lower.contains("bolt") || lower.contains("lavender") || lower.contains("stage") || lower.contains("fare") || lower.contains("boda") || lower.contains("motorbike") || lower.contains("grability") || lower.contains("ride") || lower.contains("car") || lower.contains("parking") -> "Transport"
            lower.contains("hostel") || lower.contains("rent") || lower.contains("house") || lower.contains("apartment") -> "Rent"
            lower.contains("water") || lower.contains("nairobi water") -> "Water"
            lower.contains("school") || lower.contains("fees") || lower.contains("university") || lower.contains("tuition") || lower.contains("exam") -> "School"
            lower.contains("reversal") || lower.contains("revers") -> "Other"
            lower.contains("salon") || lower.contains("barber") || lower.contains("hair") || lower.contains("nails") -> "Kujibamba"
            lower.contains("shirt") || lower.contains("trouser") || lower.contains("shoe") || lower.contains("clothe") || lower.contains("dress") || lower.contains("jacket") || lower.contains("jeans") || lower.contains("wear") -> "Clothes"
            lower.contains("hospital") || lower.contains("clinic") || lower.contains("pharmacy") || lower.contains("medicine") || lower.contains("drug") -> "Health"
            lower.contains("wifi") || lower.contains("internet") || lower.contains("modem") -> "Data"
            // Bare pochi names with no food/shopping keywords: small-business
            // buys land in Shopping; the name still wins when it matches above.
            lower.contains("pochi") -> "Shopping"
            else -> "Other"
        }
    }
}


// M-Pesa wallet balance: every transaction SMS carries a balance tail
// ("New M-PESA balance is KSh X"). Read it, store it, show it — display-only,
 // the ledger stays the source of truth for math.
 private val balanceRegex = Pattern.compile(
     "(?i)M-?PESA balance (?:was|is)\\s+(?:KSh|KES|Ksh)\\s*([0-9,.]+)"
 )
 // Loose fallback for odd phrasings ("Your balance is KSh X") — NEVER from
 // loan/debt/bill contexts (Fuliza outstanding ≠ wallet).
 private val balanceLooseRegex = Pattern.compile(
     "(?i)\\bbalance\\s+(?:was|is)\\s+(?:KSh|KES|Ksh)\\s*([0-9,.]+)"
 )
private val balanceBlocklist = listOf("fuliza", "loan", "deni", "madeni", "bill", "fees", "overdue", "owed", "m-shwari", "mshwari")

fun parseBalance(smsBody: String): Double? {
    return try {
        val clean = smsBody.replace("\n", " ")
        val m = balanceRegex.matcher(clean)
        if (m.find()) return m.group(1)?.replace(",", "")?.trimEnd('.')?.toDoubleOrNull()
        val low = clean.lowercase()
        if (balanceBlocklist.any { low.contains(it) }) return null
        val m2 = balanceLooseRegex.matcher(clean)
        if (m2.find()) m2.group(1)?.replace(",", "")?.trimEnd('.')?.toDoubleOrNull() else null
    } catch (e: Exception) {
        null
    }
}

// Money I have = M-Pesa wallet stock + Ziidi holding. The ledger is NOT
// added: confirmed M-Pesa flows already live inside the wallet number,
// and Ziidi top-ups leave the wallet when they move. Log Ziidi moves as
// Savings (merchant "Ziidi") and both stay honest.
fun moneyIHave(wallet: Double?, ziidi: Double): Double = (wallet ?: 0.0) + ziidi

fun saveMpesaBalance(context: Context, amount: Double) {
    context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE).edit()
        .putString("mpesa_balance", amount.toString())
        .putLong("mpesa_balance_at", System.currentTimeMillis())
        .apply()
    appendBalanceHistory(context, amount)
}

// Balance HISTORY, not just the latest value: every transaction SMS carries a
// balance tail, so each harvest appends a (timestamp, amount) point. A ring
// buffer in prefs (no schema migration) capped at 300 readings — enough for a
// trend line and a "ledger vs M-Pesa" reconciliation check.
private const val BALANCE_HISTORY_KEY = "mpesa_balance_history"
private const val BALANCE_HISTORY_MAX = 300

private fun appendBalanceHistory(context: Context, amount: Double) {
    try {
        val p = context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE)
        val ring = p.getString(BALANCE_HISTORY_KEY, "").orEmpty()
        val entries = if (ring.isEmpty()) mutableListOf() else ring.split(";").toMutableList()
        val now = System.currentTimeMillis()
        // Same reading twice in a row (e.g. two SMS about one transaction)
        // adds no information — skip it.
        val lastTs = entries.lastOrNull()?.substringBefore(":")?.toLongOrNull() ?: 0L
        val lastAmt = entries.lastOrNull()?.substringAfter(":")?.toDoubleOrNull()
        if (lastAmt == amount && now - lastTs < 6L * 60 * 60 * 1000) return
        entries.add("$now:$amount")
        while (entries.size > BALANCE_HISTORY_MAX) entries.removeAt(0)
        p.edit().putString(BALANCE_HISTORY_KEY, entries.joinToString(";")).apply()
    } catch (e: Exception) {
        // History is a nice-to-have; never break a balance harvest.
    }
}

// Newest-last list of (timestamp, amount) SMS balance readings.
fun readMpesaBalanceHistory(context: Context): List<Pair<Long, Double>> {
    return try {
        val ring = context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE)
            .getString(BALANCE_HISTORY_KEY, "").orEmpty()
        if (ring.isEmpty()) return emptyList()
        ring.split(";").mapNotNull { entry ->
            val ts = entry.substringBefore(":").toLongOrNull() ?: return@mapNotNull null
            val amt = entry.substringAfter(":").toDoubleOrNull() ?: return@mapNotNull null
            ts to amt
        }
    } catch (e: Exception) {
        emptyList()
    }
}

// Returns (amount, timestamp) or null when no SMS has ever carried a balance.
fun readMpesaBalance(context: Context): Pair<Double, Long>? {
    val p = context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE)
    val amt = p.getString("mpesa_balance", null)?.toDoubleOrNull() ?: return null
    val at = p.getLong("mpesa_balance_at", 0L)
    if (at <= 0L) return null
    return amt to at
}

fun balanceAgeText(at: Long, now: Long): String {
    val mins = ((now - at) / 60000).coerceAtLeast(0)
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "$mins min ago"
        mins < 60 * 24 -> "${mins / 60}h ago"
        else -> "${mins / (60 * 24)}d ago"
    }
}


// Transaction costs ride as tails ("Transaction cost, KSh22.00") or access
// fees — tracked separately so fee bleed is visible, never mixed into spending.
private val feeRegex = Pattern.compile(
    "(?i)(?:transaction cost|access fee|service fee|service charge|withdrawal charge|transfer charge|fee charged|charges)[^.]{0,30}?(?:KSh|KES|Ksh)\\s*([0-9,.]+)"
)

fun parseFee(smsBody: String): Double? {
    return try {
        val m = feeRegex.matcher(smsBody.replace("\n", " "))
        if (m.find()) m.group(1)?.replace(",", "")?.trimEnd('.')?.toDoubleOrNull() else null
    } catch (e: Exception) {
        null
    }
}

// Small-amount fee rule: M-Pesa transaction costs live under KSh 10 —
// x.xx where x is 0-9 (0.50, 5.00, 7.50...). When a text mentions a cost
// word but skips the formal "Transaction cost, KShX" tail, a KSh amount in
// 0.01..9.99 is the fee, never the movement. Pure + tested.
fun isLikelyFeeAmount(amount: Double): Boolean = amount in 0.01..9.99

private val feeWordRegex = Pattern.compile(
    "(?i)\\b(cost|charge|charges|fee|fees|deducted|levy)\\b"
)
private val kshAmountRegex = Pattern.compile(
    "(?i)(?:KSh|KES|Ksh)\\s*([0-9,.]+)"
)

fun extractSmallFeeAmount(smsBody: String): Double? {
    return try {
        val clean = smsBody.replace("\n", " ")
        if (!feeWordRegex.matcher(clean).find()) return null
        val m = kshAmountRegex.matcher(clean)
        var best: Double? = null
        while (m.find()) {
            val v = m.group(1)?.replace(",", "")?.trimEnd('.')?.toDoubleOrNull() ?: continue
            if (isLikelyFeeAmount(v)) {
                if (best == null || v < best) best = v
            }
        }
        best
    } catch (e: Exception) {
        null
    }
}

// Strict tail first, small-amount fallback second. Callers (pipeline,
// scanner) use this instead of parseFee so odd tails still land in the
// monthly fee pot instead of polluting spending.
fun parseFeeWithFallback(smsBody: String): Double? =
    parseFee(smsBody) ?: extractSmallFeeAmount(smsBody)

// Month-keyed fee pot: rolls over automatically on the 1st.
fun saveFee(context: Context, amount: Double) {
    val p = context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE)
    val month = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
    val total = if (p.getString("mpesa_fee_month", "") == month) {
        (p.getString("mpesa_fee_total", "0")?.toDoubleOrNull() ?: 0.0) + amount
    } else amount
    p.edit().putString("mpesa_fee_month", month).putString("mpesa_fee_total", total.toString()).apply()
}

fun readMonthFees(context: Context): Double {
    val p = context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE)
    val month = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
    if (p.getString("mpesa_fee_month", "") != month) return 0.0
    return p.getString("mpesa_fee_total", "0")?.toDoubleOrNull() ?: 0.0
}


// Coverage meter: last full-scan stats, so "89%" is a shown number, not a vibe.
fun saveScanStats(context: Context, found: Int, parsed: Int, unreadable: Int) {
    context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE).edit()
        .putInt("scan_found", found)
        .putInt("scan_parsed", parsed)
        .putInt("scan_unreadable", unreadable)
        .putLong("scan_at", System.currentTimeMillis())
        .apply()
}

fun readScanStats(context: Context): String? {
    val p = context.getSharedPreferences("pesaflow_prefs", Context.MODE_PRIVATE)
    val found = p.getInt("scan_found", 0)
    if (found <= 0) return null
    val parsed = p.getInt("scan_parsed", 0)
    val unreadable = p.getInt("scan_unreadable", 0)
    val at = p.getLong("scan_at", 0L)
    val pct = (parsed * 100 / found).coerceIn(0, 100)
    val whenStr = balanceAgeText(at, System.currentTimeMillis())
    return "$found texts · $pct% read · $unreadable need eyes ($whenStr)"
}

