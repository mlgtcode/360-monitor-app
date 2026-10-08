package app.monitor360.ui.screens

import android.content.Intent
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.monitor360.app
import app.monitor360.archive.ArchiveConfig
import app.monitor360.archive.ArchiveFrequency
import app.monitor360.archive.ArchiveResult
import app.monitor360.archive.ArchiveRunner
import app.monitor360.archive.ArchiveScheduler
import app.monitor360.archive.archiveSink
import app.monitor360.export.Dataset
import app.monitor360.export.ExportFormat
import app.monitor360.ui.AppViewModel
import app.monitor360.ui.Range
import app.monitor360.ui.components.ChoiceChips
import app.monitor360.ui.components.LocalSnackbar
import app.monitor360.ui.components.NumberField
import app.monitor360.ui.components.ScreenScaffold
import app.monitor360.ui.components.SectionCard
import app.monitor360.ui.theme.StatusColors
import app.monitor360.ui.userMessage
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun ArchiveScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = ctx.app.archive
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(store.load()) }
    var last by remember { mutableStateOf(store.lastResult()) }
    var busy by remember { mutableStateOf(false) }

    fun update(change: (ArchiveConfig) -> ArchiveConfig) {
        config = change(config)
        store.save(config)
        ArchiveScheduler.apply(ctx, config)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            update { it.copy(folderUri = uri.toString()) }
        }
    }

    ScreenScaffold("Automatic archive", onBack) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionCard(title = "Schedule") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Archive automatically", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(config.enabled, { on -> update { it.copy(enabled = on) } }, enabled = config.targetReady || config.enabled)
                }
                if (!config.targetReady) {
                    Text("Choose a destination below first.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ChoiceChips(ArchiveFrequency.entries, config.frequency, { it.label }, { f -> update { it.copy(frequency = f) } })
                Text(
                    "Runs in the background when the device is online. Android decides the exact time, so it may be a little later.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(title = "Contents") {
                Dataset.entries.forEach { d ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(d in config.datasets, { on -> update { it.copy(datasets = if (on) it.datasets + d else it.datasets - d) } })
                        Text(d.label)
                    }
                }
                if (Dataset.ALERTS in config.datasets) {
                    Text("Alert history range", style = MaterialTheme.typography.labelLarge)
                    ChoiceChips(
                        listOf(Range.D7, Range.D30), if (config.alertDays <= 7) Range.D7 else Range.D30, { it.label },
                        { r -> update { it.copy(alertDays = (r.seconds / 86400).toInt()) } },
                    )
                }
                Text("Formats inside the ZIP", style = MaterialTheme.typography.labelLarge)
                ExportFormat.entries.forEach { f ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(f in config.formats, { on -> update { it.copy(formats = if (on) it.formats + f else it.formats - f) } })
                        Text(f.label)
                    }
                }
                NumberField(
                    config.keep, { v -> update { it.copy(keep = v ?: 0) } }, "Archives to keep",
                    supporting = "Older archives are deleted after each run. 0 keeps everything.",
                )
            }

            SectionCard(title = "Destination") {
                Text(
                    if (config.folderUri.isBlank()) "No folder chosen" else Uri.decode(config.folderUri).substringAfterLast(':'),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton({ picker.launch(null) }) { Text(if (config.folderUri.isBlank()) "Choose folder" else "Change folder") }
                Text(
                    "Any folder works, including folders that a cloud app such as OneDrive, Google Drive or Nextcloud keeps in sync.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button({
                busy = true
                scope.launch {
                    val result: ArchiveResult = try {
                        ArchiveRunner(vm.repo).run(config, ctx.archiveSink(config))
                    } catch (e: Exception) {
                        ArchiveResult(System.currentTimeMillis(), false, e.userMessage())
                    }
                    store.saveResult(result)
                    last = result
                    busy = false
                }
            }, enabled = config.targetReady && !busy) { Text("Archive now") }

            last?.let { r ->
                SectionCard(title = "Last run") {
                    Text(
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(r.at)),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        (if (r.ok) "Success: " else "Failed: ") + r.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (r.ok) StatusColors.up() else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
