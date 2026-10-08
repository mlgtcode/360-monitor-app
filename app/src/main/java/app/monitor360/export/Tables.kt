package app.monitor360.export

enum class Dataset(val label: String, val file: String) {
    SERVERS("Servers", "servers"),
    WEBSITES("Websites", "websites"),
    CONTACTS("Contacts", "contacts"),
    WINDOWS("Maintenance windows", "maintenance-windows"),
    ALERTS("Alert history", "alerts"),
    UPTIME("Uptime report", "uptime"),
}

enum class ExportFormat(val label: String, val ext: String, val mime: String) {
    CSV("CSV", "csv", "text/csv"),
    JSON("JSON", "json", "application/json"),
    MARKDOWN("Markdown", "md", "text/markdown"),
    PDF("PDF report", "pdf", "application/pdf"),
}

class Column(val name: String, val numeric: Boolean = false)

class Table(
    val dataset: Dataset,
    val columns: List<Column>,
    val rows: List<List<Any?>>,
    val pdfColumns: List<String> = columns.take(6).map { it.name },
)

class ExportBundle(
    val generatedAt: Long,
    val tables: List<Table>,
    val notes: List<String> = emptyList(),
)

class ExportFile(val name: String, val mime: String, val bytes: ByteArray)
