package com.pesaflow.app.data.exports

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.models.isFeeRow
import com.pesaflow.app.data.models.isFulizaBorrowing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


// PesaSense-style M-Pesa transaction report: green header (period, count,
// generated-at), INCOME / EXPENSES / NET / EXCLUDED strip, spending-by-
// category bars, then the full DATE · TIME · CODE · TYPE · AMOUNT · FEES ·
// CATEGORY · RECIPIENT · SIM · EXCL table across pages.
//
// Money rules (documented, matching the app everywhere else):
// - income = ALL inflows (borrowed/internal legs included — wallet truth);
// - expenses = EXPENSE rows EXCLUDING carrier-charge rows;
// - fees ride the separate FEES column (fee rows) and never inflate expenses;
// - NET = income − expenses (fees tracked apart, like the reference);
// - samples and future rows are excluded from every figure.
data class ReportRow(
    val date: String,
    val time: String,
    val code: String,
    val type: String,
    val amount: Double,
    val isIncome: Boolean,
    val fee: Double,
    val category: String,
    val recipient: String,
    val sim: String
)

data class CategorySlice(
    val name: String,
    val total: Double,
    val pct: Int,
    val count: Int
)

data class StatementReport(
    val periodLabel: String,
    val count: Int,
    val generatedLabel: String,
    val income: Double,
    val expenses: Double,
    val fees: Double,
    val net: Double,
    val excludedCount: Int,
    val byCategory: List<CategorySlice>,
    val rows: List<ReportRow>
)

internal fun reportTypeLabel(t: Transaction): String = when {
    t.isFeeRow() -> "Transaction Cost"
    t.isFulizaBorrowing() -> "Fuliza Borrow"
    t.subcategory.equals("Fuliza repayment", ignoreCase = true) -> "Fuliza Repayment"
    t.type == TransactionType.INCOME -> "Money Received"
    t.type == TransactionType.EXPENSE -> "Money Sent"
    t.type == TransactionType.SAVING -> "Savings Move"
    t.type == TransactionType.INVESTMENT -> "Investment"
    else -> "Money Transfer"
}

fun statementReportModel(
    txs: List<Transaction>,
    nowMs: Long = System.currentTimeMillis(),
    // Export windows: default is the whole ledger (samples never export —
    // they are demo data, counted as EXCLUDED). Callers pass a month/30-day
    // window for duration-scoped exports; the period label always states
    // exactly what the figures cover.
    startMs: Long = Long.MIN_VALUE,
    endMs: Long = Long.MAX_VALUE
): StatementReport {
    val rows = txs.filter { !it.isSample && it.dateTimestamp in startMs..endMs }
    val tz = com.pesaflow.app.data.time.KenyaTime.timeZone
    val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = tz }
    val timeFmt = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = tz }
    val stampFmt = SimpleDateFormat("dd/MM/yyyy HH.mm", Locale.US).apply { timeZone = tz }
    val income = rows.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val expenses = rows.filter { it.type == TransactionType.EXPENSE && !it.isFeeRow() }.sumOf { it.amount }
    val fees = rows.filter { it.isFeeRow() }.sumOf { it.amount }
    val byCat = rows.filter { it.type == TransactionType.EXPENSE && !it.isFeeRow() }
        .groupBy { it.category.ifBlank { "Other" } }
        .mapValues { e -> e.value.sumOf { it.amount } to e.value.size }
        .toList().sortedByDescending { it.second.first }
    val expTotal = byCat.sumOf { it.second.first }.coerceAtLeast(1.0)
    val ordered = rows.sortedByDescending { it.dateTimestamp }
    return StatementReport(
        periodLabel = if (ordered.isEmpty()) "—" else
            dateFmt.format(Date(ordered.last().dateTimestamp)) + " – " + dateFmt.format(Date(ordered.first().dateTimestamp)),
        count = ordered.size,
        generatedLabel = stampFmt.format(Date(nowMs)),
        income = income,
        expenses = expenses,
        fees = fees,
        net = income - expenses,
        excludedCount = txs.count { it.isSample },
        byCategory = byCat.take(8).map { (name, pair) ->
            CategorySlice(name, pair.first, (pair.first / expTotal * 100).toInt(), pair.second)
        },
        rows = ordered.map { t ->
            ReportRow(
                date = dateFmt.format(Date(t.dateTimestamp)),
                time = timeFmt.format(Date(t.dateTimestamp)),
                code = t.sourceTransactionId?.takeIf { it.isNotBlank() } ?: "–",
                type = reportTypeLabel(t),
                amount = t.amount,
                isIncome = t.type == TransactionType.INCOME,
                fee = if (t.isFeeRow()) t.amount else 0.0,
                category = t.category.ifBlank { "Other" },
                recipient = t.merchant.ifBlank { "–" },
                sim = if (t.simSlot >= 0) "SIM " + (t.simSlot + 1) else "–"
            )
        }
    )
}

// ---------------------------------------------------------------- renderer
// Thin Android drawing over the tested model above: A4 portrait, green
// header, totals strip, category bars, full table, page footers. No math
// lives here — every figure arrives computed.

private const val PAGE_W = 595
private const val PAGE_H = 842
private const val MARGIN = 36
private const val CONTENT_W = PAGE_W - MARGIN * 2

private val GREEN = Color.rgb(30, 138, 77)
private val GREEN_DARK = Color.rgb(20, 100, 55)
private val GREEN_PALE = Color.rgb(226, 242, 233)
private val INK = Color.rgb(33, 33, 33)
private val GREY = Color.rgb(120, 120, 120)
private val RED = Color.rgb(200, 50, 50)
private val POS = Color.rgb(0, 140, 70)

private fun paint(size: Float, bold: Boolean, color: Int): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        this.color = color
    }

private fun money(v: Double): String = "KES " + kotlin.math.abs(v).toInt()

private fun trunc(paint: Paint, text: String, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) return text
    var end = text.length
    while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
    return text.substring(0, end) + "…"
}

fun renderStatementReportPdf(report: StatementReport): PdfDocument {
    val doc = PdfDocument()
    val subP = paint(9f, false, Color.WHITE)
    val headLabelP = paint(8f, false, GREY)
    val headValGreenP = paint(11f, true, POS)
    val headValRedP = paint(11f, true, RED)
    val headValInkP = paint(11f, true, INK)
    val sectionP = paint(10f, true, GREEN_DARK)
    val bodyP = paint(8f, false, INK)
    val smallP = paint(7.5f, false, GREY)
    val tableHeadP = paint(7.5f, true, GREEN_DARK)
    val cellP = paint(7.5f, false, INK)
    val cellRedP = paint(7.5f, true, RED)
    val cellGreenP = paint(7.5f, true, POS)
    val footP = paint(7.5f, false, GREY)

    // Column x-offsets inside the content area (sum < CONTENT_W).
    val colX = intArrayOf(0, 52, 86, 148, 208, 256, 292, 354, 444, 476)
    fun drawRow(canvas: android.graphics.Canvas, y: Float, cells: List<String>, p: Paint, amountPaint: Paint? = null, amountIdx: Int = 4) {
        cells.forEachIndexed { i, text ->
            val w = (if (i + 1 < colX.size) colX[i + 1] else CONTENT_W) - colX[i] - 4f
            canvas.drawText(
                trunc(if (i == amountIdx && amountPaint != null) amountPaint else p, text, w),
                (MARGIN + colX[i]).toFloat(), y,
                if (i == amountIdx && amountPaint != null) amountPaint else p
            )
        }
    }

    var pageNo = 0
    var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, ++pageNo).create())
    var canvas = page.canvas
    var y = 0f

    // Footers are drawn at page creation (the bottom strip sits outside
    // the content guard, so rows never overwrite them). Page totals would
    // need a wasteful second render pass — the header already states the
    // row count, so "Page N" here is exact and honest.
    fun footer() {
        canvas.drawText(
            "PesaPlanner — M-Pesa Transaction Report · Page $pageNo",
            MARGIN.toFloat(), (PAGE_H - 20).toFloat(), footP
        )
    }
    footer()

    fun newPage() {
        doc.finishPage(page)
        page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, ++pageNo).create())
        canvas = page.canvas
        footer()
        y = MARGIN.toFloat()
    }

    fun need(h: Float) {
        if (y + h > PAGE_H - MARGIN) newPage()
    }

    // Header band.
    y = MARGIN.toFloat()
    canvas.drawRect(
        0f, 0f, PAGE_W.toFloat(), 86f,
        Paint().apply { color = GREEN }
    )
    canvas.drawText("PesaPlanner", MARGIN.toFloat(), 32f, paint(19f, true, Color.WHITE))
    canvas.drawText("M-Pesa Transaction Report", MARGIN.toFloat(), 50f, subP)
    canvas.drawText(report.periodLabel, (PAGE_W - MARGIN - 170).toFloat(), 30f, subP)
    canvas.drawText(report.count.toString() + " transactions", (PAGE_W - MARGIN - 170).toFloat(), 46f, subP)
    canvas.drawText("Report: " + report.generatedLabel, (PAGE_W - MARGIN - 170).toFloat(), 62f, subP)
    y = 108f

    // Totals strip.
    val totals = listOf(
        Triple("INCOME", money(report.income), headValGreenP),
        Triple("EXPENSES", money(report.expenses), headValRedP),
        Triple("NET", (if (report.net < 0) "-" else "") + money(report.net), if (report.net < 0) headValRedP else headValGreenP),
        Triple("EXCLUDED", if (report.excludedCount == 0) "None" else report.excludedCount.toString(), headValInkP)
    )
    totals.forEachIndexed { i, (label, value, p) ->
        val x = MARGIN + i * (CONTENT_W / 4f)
        canvas.drawText(label, x, y, headLabelP)
        canvas.drawText(value, x, y + 15f, p)
    }
    y += 30f
    canvas.drawLine(MARGIN.toFloat(), y, (PAGE_W - MARGIN).toFloat(), y, Paint().apply { color = Color.LTGRAY })
    y += 16f

    // Spending by category.
    canvas.drawText("SPENDING BY CATEGORY", MARGIN.toFloat(), y, sectionP)
    y += 14f
    val maxCat = report.byCategory.firstOrNull()?.total ?: 1.0
    report.byCategory.forEach { slice ->
        need(14f)
        canvas.drawText(trunc(bodyP, slice.name, 120f), MARGIN.toFloat(), y, bodyP)
        val barX = MARGIN + 128f
        val barW = 200f * (slice.total / maxCat).toFloat().coerceIn(0f, 1f)
        canvas.drawRect(barX, y - 8f, barX + 200f, y - 1f, Paint().apply { color = Color.rgb(235, 235, 235) })
        canvas.drawRect(barX, y - 8f, barX + barW, y - 1f, Paint().apply { color = GREEN })
        canvas.drawText(money(slice.total), barX + 206f, y, cellP)
        canvas.drawText(slice.pct.toString() + "%", barX + 262f, y, smallP)
        canvas.drawText(slice.count.toString() + " txns", barX + 292f, y, smallP)
        y += 14f
    }
    if (report.fees > 0) {
        need(14f)
        canvas.drawText(
            "Transaction costs (separate from spending): " + money(report.fees),
            MARGIN.toFloat(), y, bodyP
        )
        y += 14f
    }
    y += 6f

    // Table header.
    need(30f)
    canvas.drawRect(
        MARGIN.toFloat(), y - 11f, (PAGE_W - MARGIN).toFloat(), y + 5f,
        Paint().apply { color = GREEN_PALE }
    )
    drawRow(
        canvas, y,
        listOf("DATE", "TIME", "CODE", "TYPE", "AMOUNT", "FEES", "CATEGORY", "RECIPIENT", "SIM", "EXCL"),
        tableHeadP
    )
    y += 16f

    // Rows.
    report.rows.forEach { r ->
        need(13f)
        drawRow(
            canvas, y,
            listOf(
                r.date, r.time, r.code, r.type,
                money(r.amount),
                if (r.fee > 0) money(r.fee) else "KES 0",
                r.category, r.recipient, r.sim, "–"
            ),
            cellP,
            amountPaint = if (r.isIncome) cellGreenP else cellRedP
        )
        y += 12f
    }

    doc.finishPage(page)
    return doc
}
