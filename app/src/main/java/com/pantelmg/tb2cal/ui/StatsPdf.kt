package com.pantelmg.tb2cal.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.pantelmg.tb2cal.model.Fmt
import com.pantelmg.tb2cal.model.GoogleColors
import com.pantelmg.tb2cal.model.HistoryEntry
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Writes the Statistics graph of one period as an A4 PDF: Title, period, total, the stacked-column graph with
 * its axes, then a table of every TimeBlock name (count, total, share). Long tables continue on more pages.
 */
internal object StatsPdf {
    private const val W = 595 // A4 in points
    private const val H = 842
    private const val M = 40f
    private const val DEFAULT_COLOR = 0xFF1565C0.toInt()

    /**
     * "TB2Cal " + only the period shown: Daily "11 Nov 2026", weekly "09 to 15 Nov 2026" or "27 Oct to 02 Nov 2026"
     * (and "29 Dec 2025 to 04 Jan 2026" across years), monthly "Oct 2026", yearly "2026".
     */
    fun fileName(period: StatsPeriod, start: LocalDate, end: LocalDate): String {
        fun f(d: LocalDate, pattern: String) = d.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
        val last = end.minusDays(1)
        val name = when (period) {
            StatsPeriod.DAILY -> f(start, "dd MMM yyyy")
            StatsPeriod.WEEKLY -> when {
                start.year != last.year -> "${f(start, "dd MMM yyyy")} to ${f(last, "dd MMM yyyy")}"
                start.month != last.month -> "${f(start, "dd MMM")} to ${f(last, "dd MMM yyyy")}"
                else -> "${f(start, "dd")} to ${f(last, "dd MMM yyyy")}"
            }
            StatsPeriod.MONTHLY -> f(start, "MMM yyyy")
            StatsPeriod.YEARLY -> "${start.year}"
        }
        return "TB2Cal $name.pdf"
    }

    fun write(
        ctx: Context,
        uri: Uri,
        history: List<HistoryEntry>,
        rows: List<StatRow>,
        period: StatsPeriod,
        periodTitle: String,
        start: LocalDate,
        end: LocalDate,
    ) {
        val locale = Locale.getDefault()
        val doc = PdfDocument()
        try {
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF202124.toInt(); textSize = 11f }
            val bold = Paint(text).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
            val title = Paint(bold).apply { textSize = 20f }
            val gray = Paint(text).apply { color = 0xFF5F6368.toInt(); textSize = 9f }
            val grayRight = Paint(gray).apply { textAlign = Paint.Align.RIGHT }
            val grayCenter = Paint(gray).apply { textAlign = Paint.Align.CENTER }
            val textRight = Paint(text).apply { textAlign = Paint.Align.RIGHT }
            val boldRight = Paint(bold).apply { textAlign = Paint.Align.RIGHT }
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDADCE0.toInt(); strokeWidth = 0.7f }
            val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9AA0A6.toInt(); strokeWidth = 1f }
            val fill = Paint(Paint.ANTI_ALIAS_FLAG)

            var pageNo = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            var c: Canvas = page.canvas

            // ---- Header ----
            val total = rows.sumOf { it.totalMs }
            val count = rows.sumOf { it.count }
            c.drawText("TB2Cal · Statistics", M, 60f, title)
            c.drawText("${period.label} · $periodTitle", M, 82f, bold)
            c.drawText("Total: ${Fmt.duration(total)} · $count TimeBlock(s)", M, 100f, text)
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", locale))
            c.drawText("Exported $now", M, 116f, gray)

            // ---- Graph ----
            val left = M + 40f
            val right = W - M
            val top = 150f
            val bottom = 420f
            val n = statsBucketCount(period, start, end)
            val buckets = statsBuckets(history, period, start, end)
            val maxMs = (buckets.maxOfOrNull { it.values.sum() } ?: 0L).coerceAtLeast(60_000L)
            val colors = rows.associate { it.key to (GoogleColors.byKey(it.colorKey)?.argb?.toInt() ?: DEFAULT_COLOR) }

            for (f in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                val y = bottom - (bottom - top) * f
                c.drawLine(left, y, right, y, if (f == 0f) axis else line)
                c.drawText(if (f == 0f) "0" else Fmt.duration((maxMs * f).toLong()), left - 6f, y + 3f, grayRight)
            }
            val gap = if (n > 12) 2f else 6f
            val barW = (right - left - gap * (n - 1)) / n
            buckets.forEachIndexed { i, b ->
                val x = left + i * (barW + gap)
                var y = bottom
                rows.forEach { r ->
                    val v = b[r.key] ?: return@forEach
                    val h = (bottom - top) * v / maxMs
                    fill.color = colors[r.key] ?: DEFAULT_COLOR
                    c.drawRect(x, y - h, x + barW, y, fill)
                    y -= h
                }
                val label = xLabel(period, start, i, locale)
                if (label.isNotEmpty()) c.drawText(label, x + barW / 2, bottom + 14f, grayCenter)
            }
            val axisName = when (period) {
                StatsPeriod.DAILY -> "Hour of the day"
                StatsPeriod.WEEKLY -> "Day of the week"
                StatsPeriod.MONTHLY -> "Day of the month"
                StatsPeriod.YEARLY -> "Month"
            }
            c.drawText(axisName, (left + right) / 2, bottom + 30f, grayCenter)

            // ---- Table ----
            val colCount = 380f
            val colTotal = 460f
            val colShare = right
            fun header(y: Float) {
                c.drawText("TimeBlock", M + 18f, y, bold)
                c.drawText("Count", colCount, y, boldRight)
                c.drawText("Total", colTotal, y, boldRight)
                c.drawText("Share", colShare, y, boldRight)
                c.drawLine(M, y + 6f, right, y + 6f, axis)
            }
            var y = bottom + 66f
            header(y)
            y += 24f
            rows.forEach { r ->
                if (y > H - M) {
                    doc.finishPage(page)
                    pageNo++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
                    c = page.canvas
                    y = M + 20f
                    header(y)
                    y += 24f
                }
                fill.color = colors[r.key] ?: DEFAULT_COLOR
                c.drawRect(M, y - 9f, M + 10f, y + 1f, fill)
                c.drawText(ellipsize(r.name, text, colCount - 50f - (M + 18f)), M + 18f, y, text)
                c.drawText("${r.count}", colCount, y, textRight)
                c.drawText(Fmt.duration(r.totalMs), colTotal, y, textRight)
                val share = if (total > 0) r.totalMs * 100.0 / total else 0.0
                c.drawText(String.format(locale, "%.1f%%", share), colShare, y, textRight)
                c.drawLine(M, y + 6f, right, y + 6f, line)
                y += 20f
            }
            doc.finishPage(page)

            val out = ctx.contentResolver.openOutputStream(uri) ?: throw IOException("Can't open the file.")
            out.use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    /** Daily: Every 3rd hour. Weekly: Short day names. Monthly: 1, 5, 10, 15… Yearly: Short month names. */
    private fun xLabel(p: StatsPeriod, start: LocalDate, i: Int, locale: Locale): String = when (p) {
        StatsPeriod.DAILY -> if (i % 3 == 0) "$i:00" else ""
        StatsPeriod.WEEKLY -> start.plusDays(i.toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        StatsPeriod.MONTHLY -> if (i == 0 || (i + 1) % 5 == 0) "${i + 1}" else ""
        StatsPeriod.YEARLY -> Month.of(i + 1).getDisplayName(TextStyle.SHORT, locale)
    }

    private fun ellipsize(s: String, p: Paint, maxW: Float): String {
        if (p.measureText(s) <= maxW) return s
        var t = s
        while (t.isNotEmpty() && p.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }
}
