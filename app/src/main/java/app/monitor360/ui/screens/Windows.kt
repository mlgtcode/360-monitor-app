@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.monitor360.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.monitor360.data.MaintenanceWindow
import app.monitor360.data.WindowInput
import app.monitor360.data.WindowOwner
import app.monitor360.ui.AppViewModel
import app.monitor360.ui.components.ChoiceChips
import app.monitor360.ui.components.ConfirmDialog
import app.monitor360.ui.components.EmptyState
import app.monitor360.ui.components.Field
import app.monitor360.ui.components.KeyValue
import app.monitor360.ui.components.LoadContent
import app.monitor360.ui.components.LocalSnackbar
import app.monitor360.ui.components.SectionCard
import app.monitor360.ui.components.rememberLoad
import app.monitor360.ui.userMessage
import kotlinx.coroutines.launch

private val DAYS = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
private val TIME = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

@Composable
fun WindowsTab(owner: WindowOwner, id: String, vm: AppViewModel) {
    val handle = rememberLoad(owner, id) { vm.repo.windows(owner, id) }
    var editing by remember { mutableStateOf<MaintenanceWindow?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<MaintenanceWindow?>(null) }
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "During a maintenance window no alerts are sent for this ${if (owner == WindowOwner.SERVER) "server" else "website"}.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton({ creating = true }) {
            Icon(Icons.Default.Add, null)
            Text("  New maintenance window")
        }
        LoadContent(handle) { list ->
            if (list.isEmpty()) EmptyState("No maintenance windows scheduled.")
            list.forEach { w ->
                SectionCard(title = w.name.ifBlank { "Maintenance" }, trailing = {
                    IconButton({ editing = w }) { Icon(Icons.Default.Edit, "Edit") }
                    IconButton({ deleting = w }) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                }) {
                    KeyValue("Repeats", w.interval)
                    KeyValue("Impact", w.impact)
                    KeyValue("Time", listOfNotNull(w.startTime, w.endTime).joinToString(" – "))
                    if (w.interval == "once") KeyValue("Date", listOfNotNull(w.startDate, w.endDate).joinToString(" → "))
                    KeyValue("Days", w.days)
                    KeyValue("Day of month", w.dayOfMonth)
                }
            }
        }
    }

    if (creating || editing != null) {
        WindowDialog(
            initial = editing,
            onDismiss = { creating = false; editing = null },
        ) { input ->
            scope.launch {
                try {
                    val e = editing
                    if (e == null) vm.repo.createWindow(owner, id, input) else vm.repo.updateWindow(owner, id, e.id, input)
                    snack.showSnackbar(if (e == null) "Maintenance window created" else "Maintenance window updated")
                    creating = false; editing = null
                    handle.reload()
                } catch (ex: Exception) {
                    snack.showSnackbar(ex.userMessage())
                }
            }
        }
    }
    deleting?.let { w ->
        ConfirmDialog("Delete maintenance window?", "\"${w.name}\" will be removed.", onConfirm = {
            deleting = null
            scope.launch {
                try {
                    vm.repo.deleteWindow(owner, id, w.id)
                    snack.showSnackbar("Maintenance window deleted")
                    handle.reload()
                } catch (ex: Exception) {
                    snack.showSnackbar(ex.userMessage())
                }
            }
        }, onDismiss = { deleting = null })
    }
}

@Composable
private fun WindowDialog(initial: MaintenanceWindow?, onDismiss: () -> Unit, onSave: (WindowInput) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var interval by remember { mutableStateOf(initial?.interval?.takeIf { it in listOf("once", "daily", "monthly") } ?: "once") }
    var impact by remember { mutableStateOf(initial?.impact?.takeIf { it in listOf("minor", "major") } ?: "minor") }
    var startTime by remember { mutableStateOf(initial?.startTime ?: "00:00") }
    var endTime by remember { mutableStateOf(initial?.endTime ?: "01:00") }
    var startDate by remember { mutableStateOf(initial?.startDate ?: "") }
    var endDate by remember { mutableStateOf(initial?.endDate ?: "") }
    var days by remember { mutableStateOf(initial?.days?.split(',')?.map { it.trim().lowercase() }?.filter { it in DAYS }?.toSet() ?: setOf("mon")) }
    var dom by remember { mutableStateOf(initial?.dayOfMonth ?: "1") }

    val valid = name.isNotBlank() && TIME.matches(startTime) && TIME.matches(endTime) &&
        when (interval) {
            "once" -> DATE.matches(startDate) && DATE.matches(endDate)
            "daily" -> days.isNotEmpty()
            else -> dom.toIntOrNull() in 1..31
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New maintenance window" else "Edit maintenance window") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(name, { name = it }, "Name")
                Text("Repeats", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(listOf("once", "daily", "monthly"), interval, { it.replaceFirstChar { c -> c.uppercase() } }, { interval = it })
                Text("Impact", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(listOf("minor", "major"), impact, { it.replaceFirstChar { c -> c.uppercase() } }, { impact = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(startTime, { startTime = it }, "Start time", Modifier.weight(1f), placeholder = "HH:mm", isError = !TIME.matches(startTime))
                    Field(endTime, { endTime = it }, "End time", Modifier.weight(1f), placeholder = "HH:mm", isError = !TIME.matches(endTime))
                }
                when (interval) {
                    "once" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field(startDate, { startDate = it }, "Start date", Modifier.weight(1f), placeholder = "YYYY-MM-DD", isError = startDate.isNotEmpty() && !DATE.matches(startDate))
                        Field(endDate, { endDate = it }, "End date", Modifier.weight(1f), placeholder = "YYYY-MM-DD", isError = endDate.isNotEmpty() && !DATE.matches(endDate))
                    }
                    "daily" -> {
                        Text("Days", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DAYS.forEach { d ->
                                FilterChip(d in days, { days = if (d in days) days - d else days + d }, { Text(d.replaceFirstChar { it.uppercase() }) })
                            }
                        }
                    }
                    else -> Field(dom, { dom = it.filter(Char::isDigit).take(2) }, "Day of month", keyboard = KeyboardType.Number)
                }
            }
        },
        confirmButton = {
            TextButton(
                {
                    onSave(
                        WindowInput(
                            name = name.trim(), interval = interval, impact = impact,
                            startDate = startDate, endDate = endDate, startTime = startTime, endTime = endTime,
                            days = DAYS.filter { it in days }.joinToString(","), dayOfMonth = dom,
                        ),
                    )
                },
                enabled = valid,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
