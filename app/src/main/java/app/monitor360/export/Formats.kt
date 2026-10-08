package app.monitor360.export

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val stampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm").withZone(ZoneOffset.UTC)
private val prettyJson = Json { prettyPrint = true }

object Exporter {
    fun stamp(epochSeconds: Long): String = stampFormat.format(Instant.ofEpochSecond(epochSeconds))

    fun csv(table: Table): ExportFile {
        val out = StringBuilder("\uFEFF")
        out.append(table.columns.joinToString(",") { quote(it.name) }).append("\r\n")
        table.rows.forEach { row -> out.append(row.joinToString(",") { quote(text(it)) }).append("\r\n") }
        return ExportFile("${table.dataset.file}.csv", ExportFormat.CSV.mime, out.toString().toByteArray())
    }

    fun json(bundle: ExportBundle): ExportFile {
        val root = linkedMapOf<String, kotlinx.serialization.json.JsonElement>(
            "generated_at" to JsonPrimitive(Instant.ofEpochSecond(bundle.generatedAt).toString()),
        )
        if (bundle.notes.isNotEmpty()) root["notes"] = JsonArray(bundle.notes.map { JsonPrimitive(it) })
        bundle.tables.forEach { t ->
            root[t.dataset.file.replace('-', '_')] = JsonArray(
                t.rows.map { row ->
                    JsonObject(t.columns.mapIndexed { i, c -> c.name to value(row.getOrNull(i)) }.toMap())
                },
            )
        }
        return ExportFile("export.json", ExportFormat.JSON.mime, prettyJson.encodeToString(JsonObject.serializer(), JsonObject(root)).toByteArray())
    }

    fun markdown(bundle: ExportBundle): ExportFile {
        val out = StringBuilder()
        out.append("# 360 Monitor export\n\n")
        out.append("Generated ").append(Instant.ofEpochSecond(bundle.generatedAt)).append("\n\n")
        bundle.notes.forEach { out.append("> ").append(it).append("\n") }
        if (bundle.notes.isNotEmpty()) out.append("\n")
        bundle.tables.forEach { t ->
            out.append("## ").append(t.dataset.label).append(" (").append(t.rows.size).append(")\n\n")
            if (t.rows.isEmpty()) {
                out.append("_No entries._\n\n")
                return@forEach
            }
            val idx = t.pdfColumns.mapNotNull { name -> t.columns.indexOfFirst { it.name == name }.takeIf { it >= 0 } }
            out.append("| ").append(idx.joinToString(" | ") { t.columns[it].name }).append(" |\n")
            out.append("|").append(idx.joinToString("|") { "---" }).append("|\n")
            t.rows.take(MARKDOWN_ROWS).forEach { row ->
                out.append("| ").append(idx.joinToString(" | ") { text(row.getOrNull(it)).replace("|", "\\|").replace("\n", " ") }).append(" |\n")
            }
            if (t.rows.size > MARKDOWN_ROWS) out.append("\n_${t.rows.size - MARKDOWN_ROWS} more rows omitted. Use CSV or JSON for the full data._\n")
            out.append("\n")
        }
        return ExportFile("report.md", ExportFormat.MARKDOWN.mime, out.toString().toByteArray())
    }

    fun files(bundle: ExportBundle, format: ExportFormat): List<ExportFile> = when (format) {
        ExportFormat.CSV -> bundle.tables.map { csv(it) }
        ExportFormat.JSON -> listOf(json(bundle))
        ExportFormat.MARKDOWN -> listOf(markdown(bundle))
        ExportFormat.PDF -> listOf(PdfReport.render(bundle))
    }

    fun single(bundle: ExportBundle, format: ExportFormat): ExportFile {
        val files = files(bundle, format)
        val stamp = stamp(bundle.generatedAt)
        if (files.size == 1) {
            val f = files[0]
            return ExportFile("360-monitor-$stamp.${f.name.substringAfterLast('.')}", f.mime, f.bytes)
        }
        return ExportFile("360-monitor-$stamp.zip", "application/zip", zip(files))
    }

    fun archive(bundle: ExportBundle, formats: Set<ExportFormat>): ExportFile {
        val entries = formats.sortedBy { it.ordinal }.flatMap { format ->
            files(bundle, format).map { f -> if (format == ExportFormat.CSV) ExportFile("csv/${f.name}", f.mime, f.bytes) else f }
        }
        return ExportFile("360monitor-archive-${stamp(bundle.generatedAt)}.zip", "application/zip", zip(entries))
    }

    fun zip(files: List<ExportFile>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            files.forEach { f ->
                zip.putNextEntry(ZipEntry(f.name))
                zip.write(f.bytes)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    fun text(v: Any?): String = when (v) {
        null -> ""
        is Double -> if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
        else -> v.toString()
    }

    private fun quote(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    private fun value(v: Any?): kotlinx.serialization.json.JsonElement = when (v) {
        null -> JsonNull
        is Number -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        else -> JsonPrimitive(v.toString())
    }

    private const val MARKDOWN_ROWS = 200
}
