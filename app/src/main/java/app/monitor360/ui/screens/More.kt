@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.monitor360.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.monitor360.data.Contact
import app.monitor360.data.ContactInput
import app.monitor360.data.Incident
import app.monitor360.data.IncidentInput
import app.monitor360.data.IncidentUpdate
import app.monitor360.ui.AppViewModel
import app.monitor360.ui.components.AppIcons
import app.monitor360.ui.components.ChipsRow
import app.monitor360.ui.components.ClickableCard
import app.monitor360.ui.components.ConfirmDialog
import app.monitor360.ui.components.EmptyState
import app.monitor360.ui.components.ErrorState
import app.monitor360.ui.components.Field
import app.monitor360.ui.components.KeyValue
import app.monitor360.ui.components.Load
import app.monitor360.ui.components.LoadContent
import app.monitor360.ui.components.LoadingBox
import app.monitor360.ui.components.LocalSnackbar
import app.monitor360.ui.components.ScreenScaffold
import app.monitor360.ui.components.SectionCard
import app.monitor360.ui.components.rememberLoad
import app.monitor360.ui.fmtDateTime
import app.monitor360.ui.userMessage
import kotlinx.coroutines.launch

@Composable
fun MoreScreen(onNavigate: (String) -> Unit) {
    ScreenScaffold("More") { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MoreItem(Icons.Default.Notifications, "Alert history", "Recent alerts across all servers and websites") { onNavigate("alerts") }
            MoreItem(Icons.Default.Build, "Maintenance windows", "Scheduled maintenance for every server and website") { onNavigate("maintenance") }
            MoreItem(AppIcons.Label, "Tags", "Servers grouped by tag, tap to filter the server list") { onNavigate("tags") }
            MoreItem(Icons.Default.Star, "Favorites", "The servers and websites you starred") { onNavigate("favorites") }
            MoreItem(AppIcons.Copy, "Export data", "Servers, websites, alerts and more as CSV, JSON, Markdown or PDF") { onNavigate("export") }
            MoreItem(Icons.Default.Refresh, "Automatic archive", "Scheduled ZIP backups to a folder of your choice") { onNavigate("archive") }
            MoreItem(Icons.Default.Info, "Uptime report", "Uptime and downtime of every website for the last 7 or 30 days") { onNavigate("uptime-report") }
            MoreItem(Icons.Default.Person, "Contacts", "Who gets notified by e-mail, SMS, Slack, webhook …") { onNavigate("contacts") }
            MoreItem(Icons.Default.Warning, "Status page incidents", "Publish and update incidents on your status pages") { onNavigate("incidents") }
            MoreItem(AppIcons.Key, "API user tokens", "Tokens for sharing access to a set of tagged servers") { onNavigate("tokens") }
            MoreItem(AppIcons.Language, "Monitoring locations", "Probe locations used for website checks") { onNavigate("nodes") }
            MoreItem(Icons.Default.Info, "Diagnostics", "Request log, API quota, Android Auto status and a support report") { onNavigate("diagnostics") }
        }
    }
}

@Composable
private fun MoreItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ClickableCard(onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ContactsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.contacts.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Contact?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Contact?>(null) }
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { vm.loadContacts() }

    ScreenScaffold(
        "Contacts", onBack,
        actions = { IconButton(vm::loadContacts) { Icon(Icons.Default.Refresh, "Refresh") } },
        floatingActionButton = { FloatingActionButton({ creating = true }) { Icon(Icons.Default.Add, "Add contact") } },
    ) { pad ->
        when (val st = state) {
            is Load.Loading -> LoadingBox(Modifier.padding(pad))
            is Load.Err -> ErrorState(st.message, vm::loadContacts, Modifier.padding(pad))
            is Load.Ok -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (st.data.isEmpty()) item { EmptyState("No contacts yet. Tap + to add one.") }
                items(st.data, key = { it.id }) { c ->
                    SectionCard(title = c.name, trailing = {
                        IconButton({ editing = c }) { Icon(Icons.Default.Edit, "Edit") }
                        IconButton({ deleting = c }) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                    }) {
                        ChipsRow(c.channels)
                        KeyValue("E-mail", c.email)
                        KeyValue("SMS", c.sms)
                        KeyValue("Slack channel", c.slackChannel)
                        KeyValue("Webhook", c.webhook)
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        ContactDialog(editing, onDismiss = { creating = false; editing = null }) { input ->
            scope.launch {
                try {
                    val e = editing
                    if (e == null) vm.repo.createContact(input) else vm.repo.updateContact(e.id, input)
                    snack.showSnackbar(if (e == null) "Contact created" else "Contact updated")
                    creating = false; editing = null
                    vm.loadContacts()
                } catch (ex: Exception) {
                    snack.showSnackbar(ex.userMessage())
                }
            }
        }
    }
    deleting?.let { c ->
        ConfirmDialog("Delete contact?", "\"${c.name}\" will no longer receive alerts.", onConfirm = {
            deleting = null
            scope.launch {
                try {
                    vm.repo.deleteContact(c.id)
                    snack.showSnackbar("Contact deleted")
                    vm.loadContacts()
                } catch (ex: Exception) {
                    snack.showSnackbar(ex.userMessage())
                }
            }
        }, onDismiss = { deleting = null })
    }
}

@Composable
private fun ContactDialog(initial: Contact?, onDismiss: () -> Unit, onSave: (ContactInput) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var email by remember { mutableStateOf(initial?.email ?: "") }
    var sms by remember { mutableStateOf(initial?.sms ?: "") }
    var pushbullet by remember { mutableStateOf(initial?.pushbullet ?: "") }
    var pushover by remember { mutableStateOf(initial?.pushover ?: "") }
    var slackChannel by remember { mutableStateOf(initial?.slackChannel ?: "") }
    var slackWebhook by remember { mutableStateOf(initial?.slackWebhook ?: "") }
    var webhook by remember { mutableStateOf(initial?.webhook ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New contact" else "Edit contact") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(name, { name = it }, "Name")
                Field(email, { email = it }, "E-mail", keyboard = KeyboardType.Email)
                Field(sms, { sms = it }, "SMS number", placeholder = "+31612345678", keyboard = KeyboardType.Phone)
                Field(slackChannel, { slackChannel = it }, "Slack channel", placeholder = "#alerts")
                Field(slackWebhook, { slackWebhook = it }, "Slack webhook URL", keyboard = KeyboardType.Uri)
                Field(webhook, { webhook = it }, "Webhook URL", keyboard = KeyboardType.Uri)
                Field(pushbullet, { pushbullet = it }, "Pushbullet key")
                Field(pushover, { pushover = it }, "Pushover key")
            }
        },
        confirmButton = {
            TextButton({ onSave(ContactInput(name.trim(), email, sms, pushbullet, pushover, slackChannel, slackWebhook, webhook)) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun IncidentsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    var pageInput by remember { mutableStateOf(prefs.statusPageId) }
    val pageId = prefs.statusPageId
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val handle = rememberLoad(pageId) { if (pageId.isBlank()) emptyList() else vm.repo.incidents(pageId) }
    var creating by remember { mutableStateOf(false) }
    var updateFor by remember { mutableStateOf<Incident?>(null) }
    var editUpdate by remember { mutableStateOf<Pair<Incident, IncidentUpdate>?>(null) }
    var deleteUpdate by remember { mutableStateOf<Pair<Incident, IncidentUpdate>?>(null) }

    fun run(ok: String, work: suspend () -> Unit) = scope.launch {
        try { work(); snack.showSnackbar(ok); handle.reload() } catch (e: Exception) { snack.showSnackbar(e.userMessage()) }
    }

    ScreenScaffold(
        "Incidents", onBack,
        actions = { if (pageId.isNotBlank()) IconButton(handle.reload) { Icon(Icons.Default.Refresh, "Refresh") } },
        floatingActionButton = { if (pageId.isNotBlank()) FloatingActionButton({ creating = true }) { Icon(Icons.Default.Add, "New incident") } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard(title = "Status page") {
                Text("Incidents belong to a status page. Enter the ID of the page (visible in its URL in the 360 Monitoring web app).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Field(pageInput, { pageInput = it.trim() }, "Page ID", placeholder = "5515610465689a321a8a4227")
                Button({ vm.settings.update { it.copy(statusPageId = pageInput) } }, enabled = pageInput != pageId) { Text("Load incidents") }
            }
            if (pageId.isNotBlank()) LoadContent(handle) { list ->
                if (list.isEmpty()) EmptyState("No incidents on this page.")
                list.forEach { inc ->
                    SectionCard(title = inc.name, trailing = { Text(inc.status?.let { "Status $it" } ?: "", style = MaterialTheme.typography.labelMedium) }) {
                        inc.body?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        Text(fmtDateTime(inc.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        inc.updates.forEach { u ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(u.body ?: u.name ?: "(update)", style = MaterialTheme.typography.bodySmall)
                                    Text(listOfNotNull(u.status?.let { "Status $it" }, u.timestamp?.let { fmtDateTime(it) }).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton({ editUpdate = inc to u }) { Icon(Icons.Default.Edit, "Edit update") }
                                IconButton({ deleteUpdate = inc to u }) { Icon(Icons.Default.Delete, "Delete update", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                        OutlinedButton({ updateFor = inc }) { Text("Post update") }
                    }
                }
            }
        }
    }

    if (creating) IncidentDialog("New incident", null, onDismiss = { creating = false }) { input ->
        creating = false
        run("Incident created") { vm.repo.createIncident(pageId, input) }
    }
    updateFor?.let { inc ->
        IncidentDialog("Update: ${inc.name}", null, defaultName = inc.name, onDismiss = { updateFor = null }) { input ->
            updateFor = null
            run("Update posted") { vm.repo.createIncidentUpdate(pageId, inc.id, input) }
        }
    }
    editUpdate?.let { (inc, u) ->
        IncidentDialog("Edit update", u, defaultName = inc.name, onDismiss = { editUpdate = null }) { input ->
            editUpdate = null
            run("Update saved") { vm.repo.editIncidentUpdate(pageId, inc.id, u.id, input) }
        }
    }
    deleteUpdate?.let { (inc, u) ->
        ConfirmDialog("Delete incident update?", "This update will be removed from \"${inc.name}\".", onConfirm = {
            deleteUpdate = null
            run("Update deleted") { vm.repo.deleteIncidentUpdate(pageId, inc.id, u.id) }
        }, onDismiss = { deleteUpdate = null })
    }
}

@Composable
private fun IncidentDialog(title: String, initial: IncidentUpdate?, defaultName: String = "", onDismiss: () -> Unit, onSave: (IncidentInput) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: defaultName) }
    var body by remember { mutableStateOf(initial?.body ?: "") }
    var status by remember { mutableStateOf(initial?.status?.toString() ?: "1") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(name, { name = it }, "Title")
                Field(body, { body = it }, "Message", singleLine = false)
                Field(status, { status = it.filter(Char::isDigit).take(2) }, "Status code", keyboard = androidx.compose.ui.text.input.KeyboardType.Number, supporting = "Numeric status as defined by your status page (e.g. 1–4)")
            }
        },
        confirmButton = { TextButton({ onSave(IncidentInput(name.trim(), body.trim(), status.toIntOrNull() ?: 1)) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun TokensScreen(vm: AppViewModel, onBack: () -> Unit) {
    val handle = rememberLoad { vm.repo.userTokens() }
    var creating by remember { mutableStateOf(false) }
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    ScreenScaffold(
        "API user tokens", onBack,
        actions = { IconButton(handle.reload) { Icon(Icons.Default.Refresh, "Refresh") } },
        floatingActionButton = { FloatingActionButton({ creating = true }) { Icon(Icons.Default.Add, "New token") } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LoadContent(handle) { list ->
                if (list.isEmpty()) EmptyState("No user tokens yet. Tap + to create one.")
                list.forEach { t ->
                    SectionCard(title = t.name ?: "(unnamed)", trailing = {
                        IconButton({
                            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("token", t.token))
                            scope.launch { snack.showSnackbar("Token copied") }
                        }) { Icon(AppIcons.Copy, "Copy token") }
                    }) {
                        Text(t.token, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        ChipsRow(t.tags)
                    }
                }
            }
        }
    }
    if (creating) {
        var name by remember { mutableStateOf("") }
        var tags by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New user token") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(name, { name = it }, "Name")
                    Field(tags, { tags = it }, "Tags", supporting = "Comma separated; limits the token to servers with these tags")
                }
            },
            confirmButton = {
                TextButton({
                    val tagList = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    creating = false
                    scope.launch {
                        try { vm.repo.createUserToken(name.trim(), tagList); snack.showSnackbar("Token created"); handle.reload() }
                        catch (e: Exception) { snack.showSnackbar(e.userMessage()) }
                    }
                }, enabled = name.isNotBlank()) { Text("Create") }
            },
            dismissButton = { TextButton({ creating = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun NodesScreen(vm: AppViewModel, onBack: () -> Unit) {
    val handle = rememberLoad { vm.repo.nodes() }
    ScreenScaffold("Monitoring locations", onBack, actions = { IconButton(handle.reload) { Icon(Icons.Default.Refresh, "Refresh") } }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LoadContent(handle) { nodes ->
                if (nodes.isEmpty()) EmptyState("No locations available.")
                nodes.sortedBy { it.name }.forEach { n ->
                    SectionCard {
                        Text(n.name, style = MaterialTheme.typography.titleMedium)
                        KeyValue("IP address", n.ip)
                    }
                }
            }
        }
    }
}
