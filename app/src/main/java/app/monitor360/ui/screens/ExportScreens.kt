package app.monitor360.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.monitor360.export.Dataset
import app.monitor360.export.ExportBundle
import app.monitor360.export.ExportCollector
import app.monitor360.export.ExportFile
import app.monitor360.export.ExportFormat
import app.monitor360.export.ExportIo
import app.monitor360.export.ExportOptions
import app.monitor360.export.Exporter
import app.monitor360.ui.AppViewModel
import app.monitor360.ui.Range
import app.monitor360.ui.components.ChoiceChips
import app.monitor360.ui.components.EmptyState
import app.monitor360.ui.components.LoadContent
import app.monitor360.ui.components.LocalSnackbar
import app.monitor360.ui.components.ScreenScaffold
import app.monitor360.ui.components.SectionCard
import app.monitor360.ui.components.rememberLoad
import app.monitor360.ui.fmtDuration
import app.monitor360.ui.theme.StatusColors
import app.monitor360.ui.userMessage
import kotlinx.coroutines.launch

private fun mimeFor(datasets: Set<Dataset>, format: ExportFormat): String =
    if (format == ExportFormat.CSV && datasets.size > 1) "application/zip" else format.mime

private fun fileNameFor(datasets: Set<Dataset>, format: ExportFormat): String {
    val stamp = Exporter.stamp(System.currentTimeMillis() / 1000)
    val ext = if (format == ExportFormat.CSV && datasets.size > 1) "zip" else format.ext
    return "360-monitor-$stamp.$ext"
}

@Composable
fun ExportScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var datasets by rememberSaveable { mutableStateOf(setOf(Dataset.SERVERS, Dataset.WEBSITES)) }
    var format by rememberSaveable { mutableStateOf(ExportFormat.CSV) }
    var alertRange by rememberSaveable { mutableStateOf(Range.D30) }
    var uptimeRange by rememberSaveable { mutableStateOf(Range.D30) }
    var busy by remember { mutableStateOf(false) }
    var last by remember { mutableStateOf<String?>(null) }

    suspend fun build(): ExportFile {
        val options = ExportOptions(datasets, alertDays = (alertRange.seconds / 86400).toInt(), uptimeDays = (uptimeRange.seconds / 86400).toInt())
        val bundle: ExportBundle = ExportCollector(vm.repo).collect(options)
        return Exporter.single(bundle, format)
    }

    fun run(work: suspend (ExportFile) -> String) {
        busy = true
        scope.launch {
            try {
                last = work(build())
            } catch (e: Exception) {
                snack.showSnackbar(e.userMessage())
            }
            busy = false
        }
    }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeFor(datasets, format))) { uri: Uri? ->
        if (uri != null) run { file ->
            ExportIo.save(ctx, uri, file.bytes)
            "Saved ${file.name} (${ExportIo.sizeLabel(file.bytes.size)})"
        }
    }

    ScreenScaffold("Export data", onBack) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionCard(title = "What to export") {
                Dataset.entries.forEach { d ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(d in datasets, { on -> datasets = if (on) datasets + d else datasets - d })
                        Text(d.label)
                    }
                }
                if (Dataset.CONTACTS in datasets) {
                    Text(
                        "Contacts include e-mail addresses, phone numbers and webhook URLs. Store the file somewhere safe.",
                        style = MaterialTheme.typography.bodySmall, color = StatusColors.warn(),
                    )
                }
                if (Dataset.ALERTS in datasets) {
                    Text("Alert history range", style = MaterialTheme.typography.labelLarge)
                    ChoiceChips(listOf(Range.D7, Range.D30), alertRange, { it.label }, { alertRange = it })
                }
                if (Dataset.UPTIME in datasets) {
                    Text("Uptime range", style = MaterialTheme.typography.labelLarge)
                    ChoiceChips(listOf(Range.D7, Range.D30), uptimeRange, { it.label }, { uptimeRange = it })
                }
                Text(
                    "Alerts, maintenance windows and uptime need one API request per server or website.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(title = "Format") {
                ChoiceChips(ExportFormat.entries, format, { it.label }, { format = it })
                Text(
                    when (format) {
                        ExportFormat.CSV -> "One CSV file per dataset (UTF-8, opens in Excel). Several datasets are packed into a ZIP."
                        ExportFormat.JSON -> "A single JSON file with every selected dataset."
                        ExportFormat.MARKDOWN -> "A readable report, first 200 rows per dataset."
                        ExportFormat.PDF -> "A printable report, first 400 rows per dataset."
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    { run { file -> ExportIo.share(ctx, file); "Prepared ${file.name} (${ExportIo.sizeLabel(file.bytes.size)})" } },
                    enabled = datasets.isNotEmpty() && !busy,
                ) { Text("Share") }
                OutlinedButton(
                    { saver.launch(fileNameFor(datasets, format)) },
                    enabled = datasets.isNotEmpty() && !busy,
                ) { Text("Save to…") }
            }
            last?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(
                "To export automatically on a schedule, use More → Archive.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun UptimeReportScreen(vm: AppViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var range by rememberSaveable { mutableStateOf(Range.D30) }
    val handle = rememberLoad(range) {
        val days = (range.seconds / 86400).toInt()
        ExportCollector(vm.repo).collect(ExportOptions(setOf(Dataset.UPTIME), uptimeDays = days))
    }

    ScreenScaffold("Uptime report", onBack) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceChips(listOf(Range.D7, Range.D30), range, { it.label }, { range = it })
            LoadContent(handle) { bundle ->
                val table = bundle.tables.single()
                val rows = table.rows
                if (rows.isEmpty()) {
                    EmptyState("No websites to report on.")
                    return@LoadContent
                }
                val avg = rows.mapNotNull { it[5] as? Double }.average()
                val downtime = rows.sumOf { (it[6] as? Number)?.toLong() ?: 0L }
                SectionCard(title = "Summary") {
                    Text("Average uptime %.3f%%".format(avg), style = MaterialTheme.typography.titleMedium)
                    Text("Total downtime ${fmtDuration(downtime)} across ${rows.size} website${if (rows.size == 1) "" else "s"}", style = MaterialTheme.typography.bodyMedium)
                    bundle.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = StatusColors.warn()) }
                }
                rows.forEach { row ->
                    val pct = row[5] as? Double
                    SectionCard(modifier = Modifier.fillMaxWidth(), title = row[1].toString(), trailing = {
                        Text(
                            pct?.let { "%.3f%%".format(it) } ?: "–",
                            style = MaterialTheme.typography.titleMedium,
                            color = if ((pct ?: 100.0) < vm.settings.current.uptimeWarn) StatusColors.warn() else StatusColors.up(),
                        )
                    }) {
                        Text(
                            "${fmtDuration((row[6] as? Number)?.toLong())} down · ${row[7]} event${if (row[7] == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(ExportFormat.CSV, ExportFormat.PDF).forEach { f ->
                        OutlinedButton({
                            scope.launch {
                                try {
                                    ExportIo.share(ctx, Exporter.single(bundle, f), "Uptime report")
                                } catch (e: Exception) {
                                    snack.showSnackbar(e.userMessage())
                                }
                            }
                        }) { Text("Share ${f.label}") }
                    }
                }
            }
        }
    }
}
