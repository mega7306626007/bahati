package com.pesaflow.app.data.parsers

import com.pesaflow.app.data.ledger.ContactMemory
import com.pesaflow.app.data.ledger.applyContactMemory
import com.pesaflow.app.data.models.PaymentMethod
import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.TransactionSource
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.models.isEarnedIncome
import com.pesaflow.app.data.models.isFeeRow
import java.io.InputStream

// M-Pesa statement import: CSV exports and password-protected statement PDFs
// become PendingTransactions — the exact same rows an SMS scan would queue.
// That is the whole point: Receipt No. rides as sourceTransactionId (so the
// pending + ledger dedup sees statement rows and SMS rows as one event),
// MpesaParser.inferCategory maps every merchant, applyContactMemory stamps
// remembered faces, and approvePending teaches CategoryMemory on confirm.
// Nothing here writes to the ledger; callers queue via tryQueuePending and
// the dashboard review/approve flow takes it from there.
data class StatementParseResult(
    val rows: List<PendingTransaction>,
    // Rows seen but deliberately dropped (failed status, zero amounts).
    val skipped: Int = 0,
    // Newest wallet balance tail seen (statement Balance column), if any.
    val latestBalance: Double? = null,
    val fileKind: String = "MPESA_CSV",
    // Lines carrying a receipt-like code, whether or not they parsed. When
    // rows is empty but candidates > 0, the layout is unknown (not an empty
    // file) — the message says so instead of blaming scanned images.
    val candidates: Int = 0,
    // Raw characters the file yielded (PDF text length / CSV length).
    val charsRead: Int = 0,
    // First-page SUMMARY totals (PDF statements only): what the file claims
    // overall, shown next to what was queued as a completeness check.
    val summary: StatementSummary = StatementSummary(),
    // Wallet flow straight from the money columns (completed rows only):
    // reconcile target proving the parse lost nothing — rows in/out must
    // match the file's own Paid In / Paid Out, whatever the categories say.
    val walletIn: Double = 0.0,
    val walletOut: Double = 0.0,
    // First 80 chars of the sniffed header line: when nothing parses, the
    // message names what was actually found instead of blaming the user.
    val headerEcho: String = ""
)

sealed interface StatementPdfOpen {
    data class Ok(val text: String) : StatementPdfOpen
    // Encrypted and no (or wrong) password supplied — ask the user, retry.
    data object NeedsPassword : StatementPdfOpen
    data class Error(val message: String) : StatementPdfOpen
}

// First-page SUMMARY table: per-type Paid In / Paid Out plus the TOTAL row.
// Used to tell the user what the statement claims overall next to what was
// queued — a completeness check no row-level parse can give.
data class StatementSummary(
    val byType: Map<String, Pair<Double, Double>> = emptyMap(),
    val totalIn: Double = 0.0,
    val totalOut: Double = 0.0
)

object StatementImporter {

    private const val CONFIDENCE = 0.85f

    // ------------------------------------------------------------------ CSV

    /** Entry point for .csv / .txt statement exports. */
    fun parseStatementCsv(
        csvText: String,
        memories: Map<String, ContactMemory> = emptyMap()
    ): StatementParseResult {
        // Email exports arrive with BOMs and preamble title/customer lines
        // before the real header — both used to sink the whole file into
        // "no readable transactions". Strip the BOM, then let the line with
        // the most delimiters win as header (title lines carry none).
        val clean = csvText.replace("\uFEFF", "")
        val lines = clean.lineSequence().map { it.trimEnd('\r') }.toList()
        val candidates = lines.filter { it.isNotBlank() }.take(10)
        if (candidates.isEmpty()) return StatementParseResult(emptyList())
        fun delimCount(line: String) =
            maxOf(line.count { it == ',' }, line.count { it == ';' }, line.count { it == '\t' }, line.count { it == '|' })
        val header = candidates.maxByOrNull { delimCount(it) } ?: candidates.first()
        if (delimCount(header) == 0) {
            // Single-column file: nothing tabular to sniff — report honestly.
            return StatementParseResult(emptyList(), charsRead = clean.length, headerEcho = header.take(80))
        }
        val delimiter = CsvImporter.sniffDelimiter(header)
        val body = lines.drop(lines.indexOf(header))
        return if (isMpesaStatementHeader(header, delimiter)) {
            parseMpesaStatementCsv(body, delimiter, memories)
        } else {
            parseGenericCsv(body, delimiter, memories)
        }
    }

    private fun isMpesaStatementHeader(header: String, delimiter: String): Boolean {
        val cols = splitCsvLine(header, delimiter).map { it.lowercase() }
        fun has(vararg keys: String) = cols.any { c -> keys.any { it in c } }
        // Receipt + completion/details + a paid/withdrawn pair (or balance).
        return has("receipt") && has("completion", "date", "time") &&
            has("detail", "description", "narration", "particular") &&
            (has("paid in", "paidin", "credit") || has("withdrawn", "debit") || has("balance"))
    }

    private fun parseMpesaStatementCsv(
        lines: List<String>,
        delimiter: String,
        memories: Map<String, ContactMemory>
    ): StatementParseResult {
        val headerCols = splitCsvLine(lines.first { it.isNotBlank() }, delimiter)
        fun col(vararg keys: String): Int {
            val idx = headerCols.indexOfFirst { c ->
                val low = c.lowercase()
                keys.any { it in low }
            }
            return idx
        }
        val receiptIdx = col("receipt")
        val timeIdx = col("completion", "date", "time", "posted")
        val detailsIdx = col("detail", "description", "narration", "particular")
        val statusIdx = col("status")
        val paidIdx = col("paid in", "paidin", "credit", "money in", "deposit")
        val outIdx = col("withdrawn", "withdraw", "debit", "money out", "payment")
        val balIdx = col("balance")
        // Paid/withdrawn columns are the statement's spine — without at least
        // one of them this is not a statement-shaped file; fall back instead
        // of misreading columns as amounts.
        if (paidIdx < 0 && outIdx < 0) return parseGenericCsv(lines, delimiter, memories)

        val rows = mutableListOf<PendingTransaction>()
        var skipped = 0
        var latestTs = Long.MIN_VALUE
        var latestBal: Double? = null
        var walletIn = 0.0
        var walletOut = 0.0
        lines.drop(1).forEach { line ->
            if (line.isBlank()) return@forEach
            val cols = splitCsvLine(line, delimiter)
            fun at(i: Int) = cols.getOrNull(i).orEmpty().trim().trim('"')
            // Only settled money enters review — failed/cancelled rows are
            // real life too, but never ledger facts.
            if (statusIdx >= 0 && !at(statusIdx).equals("completed", ignoreCase = true)) {
                skipped++
                return@forEach
            }
            // Statements print Withdrawn as negatives (-500.00); columns —
            // not signs — decide money direction, so compare absolutes.
            val paid = at(paidIdx).toStatementAmount()?.let { kotlin.math.abs(it) }
            val out = at(outIdx).toStatementAmount()?.let { kotlin.math.abs(it) }
            if (paid == null && out == null) {
                skipped++
                return@forEach
            }
            val details = at(detailsIdx).ifBlank { at(2) }
            val ts = parseStatementDateTime(at(timeIdx))
            at(balIdx).toStatementAmount()?.takeIf { it > 0 }?.let { bal ->
                if (ts >= latestTs) {
                    latestTs = ts
                    latestBal = bal
                }
            }
            buildStatementRow(
                receipt = at(receiptIdx).uppercase().ifBlank { null },
                details = details,
                paidIn = paid ?: 0.0,
                withdrawn = out ?: 0.0,
                timestamp = ts,
                rawText = line.trim()
            )?.let {
                rows.add(it)
                walletIn += paid ?: 0.0
                walletOut += out ?: 0.0
            } ?: run { skipped++ }
        }
        return StatementParseResult(
            applyContactMemory(rows, memories), skipped, latestBal,
            candidates = rows.size + skipped, charsRead = lines.sumOf { it.length },
            walletIn = walletIn, walletOut = walletOut,
            headerEcho = lines.firstOrNull { it.isNotBlank() }?.take(80).orEmpty()
        )
    }

    // Generic (non-M-Pesa) CSVs: same column sniffing as Settings import, but
    // every row becomes a PENDING row so mapping, memory and review apply —
    // never a silent direct-to-ledger write.
    private fun parseGenericCsv(
        lines: List<String>,
        delimiter: String,
        memories: Map<String, ContactMemory>
    ): StatementParseResult {
        val header = lines.first { it.isNotBlank() }
        val idx = CsvImporter.sniffColumns(header, delimiter)
        val rows = mutableListOf<PendingTransaction>()
        var skipped = 0
        var walletIn = 0.0
        var walletOut = 0.0
        lines.drop(1).forEach { line ->
            if (line.isBlank()) return@forEach
            val cols = splitCsvLine(line, delimiter)
            if (cols.size <= maxOf(idx[0], idx[1], idx[2])) {
                skipped++
                return@forEach
            }
            fun at(i: Int) = cols.getOrNull(i).orEmpty().trim().trim('"')
            val signed = at(idx[2]).toStatementAmount()
            if (signed == null || signed == 0.0 || !signed.isFinite()) {
                skipped++
                return@forEach
            }
            if (signed > 0) walletIn += signed else walletOut += -signed
            val merchant = cleanStatementMerchant(at(idx[1]))
            val type = if (signed < 0) TransactionType.EXPENSE else TransactionType.INCOME
            val explicitCat = cols.getOrNull(idx[3]).orEmpty().trim().trim('"')
            rows.add(
                PendingTransaction(
                    amount = kotlin.math.abs(signed),
                    type = type,
                    category = explicitCat.ifBlank { MpesaParser.inferCategory(merchant, type, line) },
                    merchant = merchant,
                    dateTimestamp = parseStatementDateTime(at(idx[0])),
                    paymentMethod = PaymentMethod.MPESA,
                    source = TransactionSource.CSV_IMPORT,
                    sourceTransactionId = null,
                    rawText = "CSV: " + line.trim().take(220),
                    confidenceScore = CONFIDENCE
                )
            )
        }
        return StatementParseResult(
            applyContactMemory(rows, memories), skipped, fileKind = "GENERIC_CSV",
            candidates = rows.size + skipped, charsRead = lines.sumOf { it.length },
            walletIn = walletIn, walletOut = walletOut,
            headerEcho = lines.firstOrNull { it.isNotBlank() }?.take(80).orEmpty()
        )
    }

    // ------------------------------------------------------------- PDF text

    // SUMMARY section: "SEND MONEY: 0.00 63,637.00" / "TOTAL: 225,803.01
    // 225,562.97". The all-caps label requirement keeps table rows (whose
    // only colons sit inside timestamps) and footers from ever matching.
    internal fun parseStatementSummary(pdfText: String): StatementSummary {
        val line = Regex(
            """^([A-Z][A-Z0-9 /()\-&]{2,}?):\s+([\d,]+\.\d{2})\s+([\d,]+\.\d{2})\s*$""",
            setOf(RegexOption.MULTILINE)
        )
        val byType = mutableMapOf<String, Pair<Double, Double>>()
        var totalIn = 0.0
        var totalOut = 0.0
        line.findAll(pdfText).forEach { m ->
            val label = m.groupValues[1].trim().replace(Regex("\\s+"), " ")
            val a = m.groupValues[2].toStatementAmount() ?: return@forEach
            val b = m.groupValues[3].toStatementAmount() ?: return@forEach
            if (label.equals("TOTAL", ignoreCase = true)) {
                totalIn = a
                totalOut = b
            } else {
                byType[label] = a to b
            }
        }
        return StatementSummary(byType, totalIn, totalOut)
    }

    // Fee bleed the SMS path harvests from "Transaction cost, KSh X" tails
    // has no tail in statements — the charge is its own row. Same pot, same
    // rule: an EXPENSE row whose Details name a carrier charge feeds the
    // monthly fee insight. The row itself still queues for review, exactly
    // like a standalone SMS fee notice (row + pot on both paths).
    // "charge" matches on a word boundary so "recharge"/airtime never trips
    // it; school-fees merchants carry no charge marker so they stay out.
    private val statementFeeMarkers = listOf(
        "transaction cost", "service fee", "service charge", "access fee",
        "fee charged", "transfer charge", "withdrawal charge", "paybill charge",
        "pay bill charge", "levy", "deducted"
    )
    private val chargeWord = Regex("(?i)\\bcharges?\\b")

    internal fun isStatementFeeRow(pending: PendingTransaction): Boolean {
        if (pending.type != TransactionType.EXPENSE) return false
        val text = (pending.merchant + " " + pending.rawText).lowercase()
        return chargeWord.containsMatchIn(text) || statementFeeMarkers.any { it in text }
    }

    // Same figures either way: statement rows folded into the exact report
    // the inbox scan produces — same income rule (isEarnedIncome), same
    // EXPENSE-only expense/cats, same byMonth/topSenders shape. Fee/principal
    // pairs are two real money movements, so unlike SMS companion notices
    // they are NOT collapsed; and readRate counts parse failures honestly.
    fun statementScanResult(
        rows: List<PendingTransaction>,
        skipped: Int = 0,
        nowMs: Long = System.currentTimeMillis()
    ): SmsScanResult {
        val dayMs = com.pesaflow.app.data.academic.DAY_MS
        val daysBack = if (rows.isEmpty()) 1 else
            ((nowMs - rows.minOf { it.dateTimestamp }) / dayMs + 1).toInt().coerceAtLeast(1)
        var income = 0.0
        var expense = 0.0
        var feeTotal = 0.0
        val cats = mutableMapOf<String, Double>()
        rows.forEach { p ->
            if (p.isEarnedIncome()) income += p.amount
            else if (p.type == TransactionType.EXPENSE && !p.isFeeRow()) expense += p.amount
            if (p.type == TransactionType.EXPENSE && !p.isFeeRow()) cats[p.category] = (cats[p.category] ?: 0.0) + p.amount
            if (p.isFeeRow()) feeTotal += p.amount
        }
        val monthStart = com.pesaflow.app.data.time.monthRange(nowMs).startInclusive
        val mtdRows = rows.filter { it.dateTimestamp >= monthStart }
        return SmsScanResult(
            found = rows.size + skipped,
            parsed = rows,
            unreadable = skipped,
            incomeTotal = income,
            expenseTotal = expense,
            byCategory = cats,
            daysBack = daysBack,
            capped = false,
            feeTotal = feeTotal,
            mtdIncome = mtdRows.filter { it.isEarnedIncome() }.sumOf { it.amount },
            mtdExpense = mtdRows.filter { it.type == TransactionType.EXPENSE && !it.isFeeRow() }.sumOf { it.amount },
            mtdDays = ((nowMs - monthStart) / (24L * 60 * 60 * 1000) + 1).toInt().coerceAtLeast(1),
            byMonth = rows.groupBy { monthKey(it.dateTimestamp) }.mapValues { it.value.size },
            topSenders = rows.groupBy { it.displayMerchant.ifBlank { it.merchant.ifBlank { "Unknown" } } }
                .mapValues { it.value.size }.toList().sortedByDescending { it.second }.take(5),
            label = "rows"
        )
    }

    // Table text extraction breaks long Details across lines. A statement
    // row always STARTS with its receipt code, so fold every continuation
    // line back into its row; header/footer text before the first row is
    // dropped, trailing footer stays harmless (the row regex has no end
    // anchor and needs receipt + date + status + amounts to match at all).
    internal fun joinWrappedRows(pdfText: String): List<String> {
        // Receipt + date on the opening line: real codes run 9–12 chars,
        // and the date requirement keeps a wrapped "Completed …" line from
        // ever opening its own buffer and orphaning its row's amounts.
        val rowStart = Regex("""^\s*[A-Z0-9]{9,12}\s+(\d{4}-\d{2}-\d{2}|\d{1,2}[/-])""")
        val out = mutableListOf<String>()
        var current: StringBuilder? = null
        pdfText.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            if (rowStart.containsMatchIn(line)) {
                current?.let { if (it.isNotBlank()) out.add(it.toString()) }
                current = StringBuilder(line)
            } else {
                current?.append(' ')?.append(line)
            }
        }
        current?.let { if (it.isNotBlank()) out.add(it.toString()) }
        return out
    }

    /** Entry point for text already extracted from a statement PDF. */
    fun parseStatementPdfText(
        pdfText: String,
        memories: Map<String, ContactMemory> = emptyMap()
    ): StatementParseResult {
        // Real statements wrap long Details across lines and print Withdrawn
        // as negatives (-500.00): first fold continuation lines into their
        // row (a row always starts with its receipt code), then match one row
        // per buffer. No end anchor — footer text trailing the last row must
        // not sink it. Columns (not signs) decide money direction.
        val buffers = joinWrappedRows(pdfText)
        val candidates = buffers.size
        val rowRegex = Regex(
            """([A-Z0-9]{9,12})\s+(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}(?::\d{2})?|\d{1,2}/\d{1,2}/\d{2,4}[,\s]+\d{1,2}:\d{2}(?::\d{2})?\s*(?:AM|PM)?|\d{1,2}-[A-Za-z]{3}-\d{2,4}\s+\d{1,2}:\d{2}(?::\d{2})?\s*(?:AM|PM)?)\s+(.+?)\s+(COMPLETED|FAILED|CANCELLED|SUCCESSFUL)\s+(.+)""",
            setOf(RegexOption.IGNORE_CASE)
        )
        val rows = mutableListOf<PendingTransaction>()
        var skipped = 0
        var latestTs = Long.MIN_VALUE
        var latestBal: Double? = null
        var walletIn = 0.0
        var walletOut = 0.0
        buffers.forEach { buffer ->
            val m = rowRegex.find(buffer) ?: run { skipped++; return@forEach }
            if (!m.groupValues[4].equals("completed", ignoreCase = true)) {
                skipped++
                return@forEach
            }
            val (paid, out, bal) = splitStatementAmounts(m.groupValues[5])
            if (paid <= 0 && out <= 0) {
                skipped++
                return@forEach
            }
            val ts = parseStatementDateTime(m.groupValues[2])
            if (bal != null && bal > 0 && ts >= latestTs) {
                latestTs = ts
                latestBal = bal
            }
            buildStatementRow(
                receipt = m.groupValues[1].uppercase(),
                details = m.groupValues[3],
                paidIn = paid,
                withdrawn = out,
                timestamp = ts,
                rawText = ("Statement: " + m.groupValues[1].uppercase() + " · " +
                    m.groupValues[3].trim().take(140))
            )?.let {
                rows.add(it)
                walletIn += paid
                walletOut += out
            } ?: run { skipped++ }
        }
        return StatementParseResult(
            applyContactMemory(rows, memories), skipped, latestBal,
            fileKind = "MPESA_PDF", candidates = candidates, charsRead = pdfText.length,
            summary = parseStatementSummary(pdfText),
            walletIn = walletIn, walletOut = walletOut
        )
    }

    /**
     * Opens a statement PDF (plain or password-protected) and returns its
     * text. M-Pesa statements ship encrypted — the password is usually the
     * ID / document number used at download. Wrong password surfaces as
     * [StatementPdfOpen.NeedsPassword] so the UI can ask again, never crash.
     *
     * Note: [PDDocument.isEncrypted] is NOT consulted on purpose — it reports
     * whether the file *has* an encryption dictionary, which stays true even
     * after a correct password unlocks it. The only real signal is the load
     * itself: wrong password throws InvalidPasswordException, success means
     * the text is readable.
     */
    // Paged twin of extractPdfText for progress bars: same open semantics
    // (password trim, NeedsPassword, never-crash), but text comes out in
    // page chunks so the UI can show a true 0→1 bar on long statements.
    // onProgress runs on the caller's thread — post to Main yourself.
    fun extractPdfTextPaged(
        input: InputStream,
        password: String?,
        onProgress: (donePages: Int, totalPages: Int) -> Unit = { _, _ -> }
    ): StatementPdfOpen {
        val cleanPw = password?.trim().orEmpty()
        return try {
            val doc = if (cleanPw.isEmpty()) {
                com.tom_roush.pdfbox.pdmodel.PDDocument.load(input)
            } else {
                com.tom_roush.pdfbox.pdmodel.PDDocument.load(input, cleanPw)
            }
            try {
                val total = doc.numberOfPages.coerceAtLeast(1)
                val sb = StringBuilder()
                var start = 1
                while (start <= total) {
                    val end = minOf(start + 4, total)
                    val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                    stripper.startPage = start
                    stripper.endPage = end
                    sb.append(stripper.getText(doc)).append('\n')
                    onProgress(end, total)
                    start = end + 1
                }
                StatementPdfOpen.Ok(sb.toString())
            } finally {
                try {
                    doc.close()
                } catch (_: Exception) {
                }
            }
        } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            StatementPdfOpen.NeedsPassword
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            StatementPdfOpen.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    fun extractPdfText(input: InputStream, password: String?): StatementPdfOpen {
        // Keyboards/autofill love trailing spaces; statement passwords never
        // contain them — trim so a pasted ID number just works.
        val cleanPw = password?.trim().orEmpty()
        return try {
            val doc = if (cleanPw.isEmpty()) {
                com.tom_roush.pdfbox.pdmodel.PDDocument.load(input)
            } else {
                com.tom_roush.pdfbox.pdmodel.PDDocument.load(input, cleanPw)
            }
            try {
                val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                StatementPdfOpen.Ok(stripper.getText(doc))
            } finally {
                try {
                    doc.close()
                } catch (_: Exception) {
                }
            }
        } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            StatementPdfOpen.NeedsPassword
        } catch (e: Throwable) {
            // Errors too (NoClassDefFoundError from over-eager shrinking,
            // OOM on huge scans): a failed open must surface as a message,
            // never as a dead app. Cancellation still propagates.
            if (e is kotlinx.coroutines.CancellationException) throw e
            StatementPdfOpen.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    // ------------------------------------------------------------------ row

    // One statement row → one pending row, typed and categorized exactly like
    // an SMS parse: Receipt No. is the dedup code, inferCategory maps the
    // merchant, Fuliza/Ziidi subcategories ride along, confidence 0.85 keeps
    // coded rows auto-approvable under the same PendingPolicy as SMS.
    private fun buildStatementRow(
        receipt: String?,
        details: String,
        paidIn: Double,
        withdrawn: Double,
        timestamp: Long,
        rawText: String
    ): PendingTransaction? {
        if (paidIn <= 0 && withdrawn <= 0) return null
        if (!paidIn.isFinite() || !withdrawn.isFinite()) return null
        val merchant = cleanStatementMerchant(details)
        val lowM = merchant.lowercase()
        val lowD = details.lowercase()
        fun has(vararg words: String) = words.any { it in lowM || it in lowD }
        // "ziidi" only counts with product phrasing (M-Pesa Ziidi, MMF,
        // invest/deposit/…) — sends to a person/business named Ziidi fall
        // through as ordinary transfers below, never savings.
        val ziidiProduct = has("ziidi") &&
            com.pesaflow.app.data.parsers.MpesaParser.isZiidiProduct("$lowM $lowD")
        val savingsMove = has("m-shwari", "mshwari", "sacco") || ziidiProduct
        val internalMove = has(
            "pochi", "kcb", "equity", "absa", "stanbic", "co-op", "coop",
            "family bank", "dtb", "ncba", "bank transfer", "agent float",
            // Cash out of the wallet is a move like the SMS path treats it
            // (TRANSFER, never spending) — checked AFTER chargeRow below so
            // "Withdrawal Charge" fee lines never land here.
            "withdraw"
        )
        // Carrier charges are flagged before anything else: they stay EXPENSE
        // (real outflows) but carry "Transaction Cost" so spending totals,
        // budgets and paces exclude them exactly like SMS fee rows.
        val chargeRow = withdrawn > 0 && paidIn <= 0 &&
            (chargeWord.containsMatchIn("$lowM $lowD") || statementFeeMarkers.any { it in lowM || it in lowD })
        val type = when {
            paidIn > 0 && withdrawn > 0 -> if (paidIn >= withdrawn) TransactionType.INCOME else TransactionType.EXPENSE
            // Returned money is restored, never earned — same rule as the
            // SMS refund branch. Either direction rides as a move, ahead of
            // the paid-in default so money-back never counts as income.
            // ("reversal" does NOT contain "reverse" — match the stem.)
            has("revers") -> TransactionType.TRANSFER
            paidIn > 0 -> TransactionType.INCOME
            savingsMove -> TransactionType.SAVING
            chargeRow -> TransactionType.EXPENSE
            internalMove -> TransactionType.TRANSFER
            else -> TransactionType.EXPENSE
        }
        val amount = if (paidIn > 0 && withdrawn > 0) kotlin.math.abs(paidIn - withdrawn) else maxOf(paidIn, withdrawn)
        if (amount <= 0 || !amount.isFinite()) return null
        val ziidi = ziidiProduct
        return PendingTransaction(
            amount = amount,
            type = type,
            category = when {
                ziidi -> "Savings"
                type == TransactionType.TRANSFER -> "Transfers"
                // Fuliza-funded flows keep their Debt identity even when the
                // merchant name was stripped to a recipient ("Benard...").
                has("fuliza") || has("overdraft") || has("overdraw") -> "Debt"
                // Paybills whose merchant lost its context in cleaning
                // ("OKAVA KENYA LTD") still read as bills. Bank-routed ones
                // never reach here — internalMove types them TRANSFER above.
                type == TransactionType.EXPENSE && (has("paybill") || has("pay bill")) -> "Bills"
                else -> MpesaParser.inferCategory(merchant, type, details)
            },
            subcategory = when {
                chargeRow -> "Transaction Cost"
                // Research-backed Fuliza/OD markers: "OD Loan Repayment to"
                // and "OverDraft of Credit Party" are the statement's words
                // (open-source M-Pesa parsers confirm both). Repayments lower
                // the deni; Fuliza-funded sends ("Customer Transfer Fuliza
                // MPesa") are new borrowing out and must not.
                MpesaParser.isFulizaText("$lowM $lowD") && type == TransactionType.INCOME -> "Borrowed funds"
                MpesaParser.isFulizaRepaymentText("$lowM $lowD") && type == TransactionType.EXPENSE -> "Fuliza repayment"
                MpesaParser.isFulizaFeeText("$lowM $lowD") && type == TransactionType.EXPENSE -> "Fuliza charges"
                MpesaParser.isFulizaText("$lowM $lowD") && type == TransactionType.EXPENSE -> "Borrowed spend"
                ziidi -> "Ziidi transfer"
                else -> ""
            },
            merchant = if (ziidi) "M-Pesa Ziidi" else merchant,
            dateTimestamp = timestamp,
            paymentMethod = PaymentMethod.MPESA,
            source = TransactionSource.CSV_IMPORT,
            sourceTransactionId = receipt?.takeIf { it.isNotBlank() },
            rawText = rawText.take(280),
            confidenceScore = CONFIDENCE
        )
    }

    // "Customer Transfer to 0712 345 678 - JANE WAMBUI" → "JANE WAMBUI".
    // "Merchant Payment to 123456 - NAIVAS" → "NAIVAS". The name rides after
    // the last dash; residual till/phone/account digits are stripped. SMS
    // parses keep party casing, so this does too — ContactMemory normalizes
    // case at match time, never here.
    internal fun cleanStatementMerchant(details: String): String {
        val d = details.trim()
        if (d.isEmpty()) return "Unknown Party"
        if (d.equals("airtime purchase", ignoreCase = true) ||
            d.startsWith("airtime purchase of", ignoreCase = true)
        ) return "Safaricom"
        // Data bundles bought (for self or others) are one product, not one
        // person per phone number — file them together like SMS airtime.
        if (d.contains("data bundles", ignoreCase = true)) return "Safaricom Data"
        var candidate = d
        val dash = d.lastIndexOf(" - ")
        if (dash >= 0) {
            val tail = d.substring(dash + 3).trim()
            if (tail.any { it.isLetter() }) candidate = tail
        } else {
            // No dash to split on ("Small Business Payment ... from
            // 254725***355 NANCY KAMUYU"): drop everything through the first
            // masked/long number so the person survives, not the digits.
            val masked = Regex("\\b[\\d*Xx]{7,15}\\b").find(candidate)
            if (masked != null) {
                val rest = candidate.substring(masked.range.last + 1).trim()
                if (rest.any { it.isLetter() }) candidate = rest
            }
        }
        candidate = candidate
            .replace(Regex("(?i)^(customer transfer to|merchant payment to|pay bill to|funds received from|customer withdrawal at agent|agent withdrawal|deposit of funds at agent|till payment to|payment to|transfer to|sent to|paid to)\\s+"), "")
            .replace(Regex("\\b(?:0|254)\\d{9}\\b"), "")
            // Masked numbers as statements print them (0707***786, 2547XX***112).
            .replace(Regex("\\b[\\d*Xx]{7,15}\\b"), "")
            .replace(Regex("(?i)\\btill\\s*(no\\.?|number)?\\s*\\d{5,9}"), "")
            .replace(Regex("(?i)\\b(paybill|account|till)\\s*\\d{4,9}"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
            .trim('-', ',', '.', ' ')
            // Dangling fragments ("KWA NEHEMA GENERAL STORES - 2").
            .replace(Regex("\\s+-\\s+\\S{1,4}$"), "")
            .trim()
        return candidate.ifBlank { "Unknown Party" }
    }

    // Quote-aware split: statement Details fields carry commas inside quotes.
    internal fun splitCsvLine(line: String, delimiter: String): List<String> {
        if (delimiter.length != 1) return line.split(delimiter)
        val d = delimiter[0]
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        cur.append('"')
                        i++
                    } else inQuotes = !inQuotes
                }
                c == d && !inQuotes -> {
                    out.add(cur.toString())
                    cur.clear()
                }
                else -> cur.append(c)
            }
            i++
        }
        out.add(cur.toString())
        return out.map { it.trim() }
    }

    private fun String.toStatementAmount(): Double? {
        val s = this.replace(",", "").replace(Regex("(?i)\\b(ksh|kes)\\b"), "").trim()
        if (s.isEmpty() || s == "-" || s.equals("nil", ignoreCase = true)) return null
        return s.toDoubleOrNull()
    }

    // Amount tail after the status word: up to three columns (Paid In,
    // Withdrawn, Balance), any of which may be blank. Columns — not signs —
    // decide direction, but signs disambiguate blanks: "-50.00 240.04" is
    // withdrawn 50 + balance 240.04, while "0.00 500.00 12000.00" is paid 0
    // + withdrawn 500 + balance 12000. Only the leading dense amount run
    // counts, so joined footer text ("Page 2 of 67") can never poison it.
    // Returns (paidIn, withdrawn, balance?) — all non-negative.
    internal fun splitStatementAmounts(tail: String): Triple<Double, Double, Double?> {
        val dense = Regex("""^\s*((?:-?[\d,]+(?:\.\d{1,2})?\s*){1,3})""")
            .find(tail)?.groupValues?.get(1).orEmpty()
        val nums = Regex("-?[\\d,]+(?:\\.\\d{1,2})?").findAll(dense)
            .mapNotNull { it.value.toStatementAmount() }.toList()
        if (nums.isEmpty()) return Triple(0.0, 0.0, null)
        val bal = nums.last()
        val rest = nums.dropLast(1)
        val neg = rest.filter { it < 0.0 }
        return when {
            neg.isNotEmpty() -> Triple(
                rest.filter { it > 0.0 }.sumOf { kotlin.math.abs(it) },
                neg.sumOf { kotlin.math.abs(it) },
                bal
            )
            rest.size >= 2 -> Triple(
                kotlin.math.abs(rest[0]), kotlin.math.abs(rest[1]), bal
            )
            rest.size == 1 && rest[0] > 0.0 -> Triple(rest[0], 0.0, bal)
            else -> Triple(0.0, 0.0, bal)
        }
    }

    private val STATEMENT_DATETIMES = listOf(
        "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm",
        "dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy HH:mm",
        "dd-MM-yyyy HH:mm:ss", "dd-MM-yyyy HH:mm",
        "MM/dd/yyyy HH:mm:ss", "MM/dd/yyyy HH:mm",
        "dd MMM yyyy HH:mm:ss", "dd MMM yyyy HH:mm",
        "dd MMM yyyy, hh:mm a", "dd/MM/yyyy, hh:mm a"
    )

    internal fun parseStatementDateTime(raw: String): Long {
        val s = raw.trim().trim('"')
        if (s.isEmpty()) return System.currentTimeMillis()
        for (fmt in STATEMENT_DATETIMES) {
            try {
                val f = java.text.SimpleDateFormat(fmt, java.util.Locale.US)
                f.isLenient = false
                val t = f.parse(s)?.time ?: continue
                if (t > 946684800000L && t < System.currentTimeMillis() + 24L * 60 * 60 * 1000) return t
            } catch (_: Exception) {
            }
        }
        return CsvImporter.parseFlexibleDate(s)
    }
}
