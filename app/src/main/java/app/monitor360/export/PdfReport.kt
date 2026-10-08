package app.monitor360.export

import java.io.ByteArrayOutputStream
import java.time.Instant

private class PdfPage {
    val content = StringBuilder()

    fun text(x: Float, yFromTop: Float, size: Float, bold: Boolean, text: String, gray: Float = 0f) {
        val y = PdfReport.PAGE_H - yFromTop
        content.append("BT ").append(if (bold) "/F2 " else "/F1 ").append(size).append(" Tf ")
            .append(gray).append(" g ").append(x).append(' ').append(y).append(" Td (")
            .append(escape(text)).append(") Tj ET\n")
    }

    private fun escape(s: String): String {
        val out = StringBuilder()
        for (c in s) {
            when {
                c == '\\' || c == '(' || c == ')' -> out.append('\\').append(c)
                c.code in 32..126 || c.code in 160..255 -> out.append(c)
                else -> out.append('?')
            }
        }
        return out.toString()
    }
}

private class PdfDocumentWriter {
    private val pages = mutableListOf<PdfPage>()

    fun newPage(): PdfPage = PdfPage().also { pages += it }

    fun toBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()
        fun write(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))
        fun obj(body: String) {
            offsets += out.size()
            write("${offsets.size} 0 obj\n$body\nendobj\n")
        }

        write("%PDF-1.4\n")
        val firstPage = 5
        obj("<< /Type /Catalog /Pages 2 0 R >>")
        obj("<< /Type /Pages /Kids [${pages.indices.joinToString(" ") { "${firstPage + it * 2} 0 R" }}] /Count ${pages.size} >>")
        obj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
        obj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>")
        pages.forEachIndexed { i, page ->
            val contentId = firstPage + i * 2 + 1
            obj(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${PdfReport.PAGE_W} ${PdfReport.PAGE_H}] " +
                    "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents $contentId 0 R >>",
            )
            val bytes = page.content.toString().toByteArray(Charsets.ISO_8859_1)
            obj("<< /Length ${bytes.size} >>\nstream\n${String(bytes, Charsets.ISO_8859_1)}endstream")
        }
        val xref = out.size()
        write("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { write(String.format("%010d 00000 n \n", it)) }
        write("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toByteArray()
    }
}

object PdfReport {
    const val PAGE_W = 595
    const val PAGE_H = 842
    private const val MARGIN = 36f
    private const val ROW_H = 15f
    private const val MAX_ROWS = 400
    private const val FONT = 9f
    private const val CHAR_WIDTH = 0.5f

    private fun fit(text: String, width: Float): String {
        val capacity = (width / (FONT * CHAR_WIDTH)).toInt().coerceAtLeast(1)
        return if (text.length <= capacity) text else text.take((capacity - 1).coerceAtLeast(0)) + "~"
    }

    fun render(bundle: ExportBundle): ExportFile {
        val doc = PdfDocumentWriter()
        var number = 0
        var page = doc.newPage().also { number = 1 }
        var y = MARGIN + 10f

        fun footer() = page.text(PAGE_W - MARGIN - 40f, PAGE_H - 20f, FONT, false, "Page $number", 0.4f)

        fun nextPage() {
            footer()
            page = doc.newPage()
            number++
            y = MARGIN + 10f
        }

        fun room(height: Float) {
            if (y + height > PAGE_H - 40f) nextPage()
        }

        page.text(MARGIN, y + 8f, 20f, true, "360 Monitor report")
        y += 30f
        page.text(MARGIN, y, FONT, false, "Generated ${Instant.ofEpochSecond(bundle.generatedAt)}", 0.4f)
        y += 16f
        bundle.notes.forEach {
            page.text(MARGIN, y, FONT, false, it, 0.4f)
            y += 13f
        }
        y += 6f
        bundle.tables.forEach {
            page.text(MARGIN, y, FONT, false, "${it.dataset.label}: ${it.rows.size}")
            y += 13f
        }
        y += 14f

        bundle.tables.forEach { table ->
            room(60f)
            page.text(MARGIN, y, 13f, true, table.dataset.label)
            y += 18f
            if (table.rows.isEmpty()) {
                page.text(MARGIN, y, FONT, false, "No entries.", 0.4f)
                y += 20f
                return@forEach
            }
            val idx = table.pdfColumns.mapNotNull { name -> table.columns.indexOfFirst { it.name == name }.takeIf { it >= 0 } }
            val colWidth = (PAGE_W - 2 * MARGIN) / idx.size

            fun row(cells: List<String>, bold: Boolean) {
                cells.forEachIndexed { i, cell -> page.text(MARGIN + i * colWidth, y, FONT, bold, fit(cell, colWidth - 6f)) }
                y += ROW_H
            }

            room(ROW_H * 2)
            row(idx.map { table.columns[it].name }, true)
            table.rows.take(MAX_ROWS).forEach { values ->
                if (y + ROW_H > PAGE_H - 40f) {
                    nextPage()
                    row(idx.map { table.columns[it].name }, true)
                }
                row(idx.map { Exporter.text(values.getOrNull(it)) }, false)
            }
            if (table.rows.size > MAX_ROWS) {
                room(ROW_H)
                page.text(MARGIN, y, FONT, false, "${table.rows.size - MAX_ROWS} more rows omitted, see the CSV or JSON export.", 0.4f)
                y += ROW_H
            }
            y += 14f
        }
        footer()
        return ExportFile("report.pdf", ExportFormat.PDF.mime, doc.toBytes())
    }
}
