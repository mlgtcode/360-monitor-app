@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.monitor360.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.monitor360.data.Contact
import app.monitor360.data.Monitor
import app.monitor360.data.MonitorInput
import app.monitor360.data.Node
import app.monitor360.data.int
import app.monitor360.data.str
import app.monitor360.data.strList
import app.monitor360.ui.AppViewModel
import app.monitor360.ui.components.ChoiceChips
import app.monitor360.ui.components.Dropdown
import app.monitor360.ui.components.ErrorState
import app.monitor360.ui.components.Field
import app.monitor360.ui.components.Load
import app.monitor360.ui.components.LoadingBox
import app.monitor360.ui.components.LocalSnackbar
import app.monitor360.ui.components.NumberField
import app.monitor360.ui.components.ScreenScaffold
import app.monitor360.ui.components.SectionCard
import app.monitor360.ui.components.rememberLoad
import app.monitor360.ui.userMessage
import kotlinx.coroutines.launch

private val INTERVALS = listOf(30, 60, 120, 300, 600, 900, 1800, 3600)
private val AUTH_METHODS = listOf(
    0 to "None", 1 to "Basic", 2 to "Digest", 3 to "Digest IE", 4 to "Negotiate",
    5 to "NTLM", 6 to "NTLM Windbind", 7 to "Any (auto)",
)

private fun intervalLabel(s: Int) = if (s < 60) "$s seconds" else if (s < 3600) "${s / 60} min" else "${s / 3600} h"

@Composable
fun MonitorFormScreen(vm: AppViewModel, id: String?, onBack: () -> Unit) {
    val existing = if (id != null) rememberLoad(id) { vm.repo.monitor(id) } else null
    ScreenScaffold(title = if (id == null) "Add website" else "Edit website", onBack = onBack) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            when (val v = existing?.value) {
                null -> MonitorFormContent(vm, null, onBack)
                is Load.Loading -> LoadingBox()
                is Load.Err -> ErrorState(v.message, existing.reload)
                is Load.Ok -> MonitorFormContent(vm, v.data, onBack)
            }
        }
    }
}

private fun stripScheme(u: String) = u.removePrefix("https://").removePrefix("http://")

private fun hostOf(u: String) = stripScheme(u).substringBefore('/').substringBefore('?').removePrefix("www.")

@Composable
private fun MonitorFormContent(vm: AppViewModel, initial: Monitor?, onDone: () -> Unit) {
    val raw = initial?.raw
    val snack = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val contactsState by vm.contacts.collectAsStateWithLifecycle()
    val nodesState by vm.nodes.collectAsStateWithLifecycle()
    val monitorsState by vm.monitors.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadContacts(); vm.loadNodes() }

    val shared = remember { if (initial == null) vm.sharedUrl.value else null }
    LaunchedEffect(shared) { if (shared != null) vm.sharedUrl.value = null }

    var name by remember { mutableStateOf(initial?.name ?: shared?.let(::hostOf) ?: "") }
    var protocol by remember {
        mutableStateOf(
            initial?.protocol?.lowercase()?.takeIf { it in listOf("http", "https", "tcp", "icmp") }
                ?: if (shared?.startsWith("http://") == true) "http" else "https",
        )
    }
    var url by remember { mutableStateOf(initial?.url ?: shared?.let(::stripScheme) ?: "") }
    var port by remember { mutableStateOf(raw?.int("port")) }
    var interval by remember { mutableStateOf(raw?.int("interval") ?: 60) }
    var timeout by remember { mutableStateOf<Int?>(raw?.int("timeout") ?: 30) }
    var ctimeout by remember { mutableStateOf(raw?.int("ctimeout")) }
    var redirects by remember { mutableStateOf<Int?>(raw?.int("redirects") ?: 3) }
    var downFrom by remember { mutableStateOf<Int?>(raw?.int("down_from_min") ?: 3) }
    var triggerAfter by remember { mutableStateOf<Int?>(raw?.int("trigger_after_checks") ?: 1) }
    var nodeId by remember { mutableStateOf(initial?.locationId) }
    var method by remember { mutableStateOf(raw?.str("method")?.uppercase() ?: "HEAD") }
    var keyword by remember { mutableStateOf(raw?.str("keyword") ?: "") }
    var alertWhenFound by remember { mutableStateOf(raw?.str("match_type") != "no") }
    var auth by remember { mutableStateOf(raw?.int("auth") ?: 0) }
    var httpUser by remember { mutableStateOf(raw?.str("http_username") ?: "") }
    var httpPass by remember { mutableStateOf("") }
    var userAgent by remember { mutableStateOf(raw?.str("useragent") ?: "") }
    var headers by remember { mutableStateOf(raw?.str("headers") ?: "") }
    var postData by remember { mutableStateOf(raw?.str("postdata") ?: "") }
    var resolve by remember { mutableStateOf(raw?.str("resolve") ?: "") }
    var ipVersion by remember { mutableStateOf(raw?.str("ipv4_ipv6") ?: "whatever") }
    var dep by remember { mutableStateOf(raw?.str("dep")) }
    var contactSel by remember { mutableStateOf((raw?.strList("contact_list") ?: emptyList()).toSet()) }
    var advanced by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val webCheck = protocol == "http" || protocol == "https"
    val valid = name.isNotBlank() && url.isNotBlank() && (protocol != "tcp" || (port ?: 0) > 0)

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard(title = "Basics") {
            Field(name, { name = it }, "Name", supporting = "Shown in dashboards and alerts")
            Text("Check type", style = MaterialTheme.typography.labelLarge)
            ChoiceChips(listOf("https", "http", "tcp", "icmp"), protocol, { it.uppercase() }, { protocol = it; if (it == "tcp" && port == null) port = 80 })
            Field(
                url, { url = it }, if (protocol == "icmp") "IP address or hostname" else if (webCheck) "URL" else "Host",
                placeholder = if (webCheck) "example.com/page" else "example.com",
                keyboard = KeyboardType.Uri, supporting = if (webCheck) "The scheme is added from the check type" else null,
            )
            if (protocol == "tcp" || webCheck) NumberField(port, { port = it }, if (protocol == "tcp") "Port" else "Port (optional)")
        }

        SectionCard(title = "Checks") {
            Dropdown("Check interval", (INTERVALS + interval).distinct().sorted(), interval, ::intervalLabel, { interval = it })
            NumberField(timeout, { timeout = it }, "Timeout (seconds)", supporting = "Total time a check may take")
            NumberField(ctimeout, { ctimeout = it }, "Connection timeout (seconds)", supporting = "Optional, time allowed to connect")
            if (webCheck) NumberField(redirects, { redirects = it }, "Max. redirects", supporting = "0 disables redirects")
            NumberField(downFrom, { downFrom = it }, "Re-check from N probes when down")
            NumberField(triggerAfter, { triggerAfter = it }, "Alert after N failed checks")
            val nodes = (nodesState as? Load.Ok)?.data.orEmpty()
            Dropdown<Node?>(
                "Monitoring location", listOf<Node?>(null) + nodes, nodes.firstOrNull { it.id == nodeId },
                { it?.name ?: "Random location" }, { nodeId = it?.id },
            )
        }

        SectionCard(title = "Alert contacts") {
            when (val c = contactsState) {
                is Load.Loading -> Text("Loading contacts…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                is Load.Err -> Text(c.message, color = MaterialTheme.colorScheme.error)
                is Load.Ok -> if (c.data.isEmpty()) Text("No contacts yet. Create contacts under More → Contacts.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else c.data.forEach { contact -> ContactChannels(contact, contactSel) { contactSel = it } }
            }
        }

        SectionCard(
            title = "Advanced options",
            trailing = { TextButton({ advanced = !advanced }) { Text(if (advanced) "Hide" else "Show") } },
        ) {
            if (advanced) {
                if (webCheck) {
                    Text("HTTP method", style = MaterialTheme.typography.labelLarge)
                    ChoiceChips(listOf("HEAD", "GET", "POST", "PUT"), method, { it }, { method = it })
                    Field(keyword, { keyword = it }, "Keyword", supporting = "Optional text to look for in the page HTML")
                    if (keyword.isNotBlank()) ChoiceChips(listOf(true, false), alertWhenFound, { if (it) "Alert when found" else "Alert when missing" }, { alertWhenFound = it })
                    Dropdown("HTTP authentication", AUTH_METHODS, AUTH_METHODS.firstOrNull { it.first == auth }, { it.second }, { auth = it.first })
                    if (auth != 0) {
                        Field(httpUser, { httpUser = it }, "Username")
                        Field(httpPass, { httpPass = it }, "Password", password = true, supporting = if (initial != null) "Leave empty to keep the current password" else null)
                    }
                    Field(userAgent, { userAgent = it }, "User agent")
                    Field(headers, { headers = it }, "Custom headers", placeholder = "api-key:abc", singleLine = false)
                    if (method == "POST" || method == "PUT") Field(postData, { postData = it }, "Post data", placeholder = "key=value&key2=value2", singleLine = false)
                    Field(resolve, { resolve = it }, "Resolve (bypass DNS)", placeholder = "example.com:443:12.12.12.12")
                }
                Text("IP version", style = MaterialTheme.typography.labelLarge)
                ChoiceChips(listOf("whatever", "ipv4", "ipv6"), ipVersion, { if (it == "whatever") "Any" else it.uppercase().replace("IPV", "IPv") }, { ipVersion = it })
                val others = (monitorsState as? Load.Ok)?.data.orEmpty().filter { it.id != initial?.id }
                if (others.isNotEmpty()) Dropdown<Monitor?>(
                    "Depends on (suppress alerts while down)", listOf<Monitor?>(null) + others, others.firstOrNull { it.id == dep },
                    { it?.title ?: "None" }, { dep = it?.id },
                )
            } else {
                Text("Method, keyword match, authentication, headers, DNS override, IP version and dependencies.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }

        Button(
            onClick = {
                saving = true
                val input = MonitorInput(
                    name = name, url = url, protocol = protocol,
                    port = port, interval = interval, timeout = timeout, connectTimeout = ctimeout,
                    redirects = if (webCheck) redirects else null,
                    method = if (webCheck) method else null,
                    keyword = if (webCheck) keyword else null, keywordAlertWhenFound = alertWhenFound,
                    userAgent = userAgent, headers = headers, postData = postData, resolve = resolve,
                    ipVersion = ipVersion, downFromMin = downFrom, triggerAfterChecks = triggerAfter,
                    auth = if (auth != 0) auth else null,
                    httpUsername = httpUser.takeIf { auth != 0 }, httpPassword = httpPass.takeIf { auth != 0 },
                    nodeId = nodeId, dependencyId = dep, contactList = contactSel.toList(),
                )
                scope.launch {
                    try {
                        if (initial == null) vm.repo.createMonitor(input) else vm.repo.updateMonitor(initial.id, input)
                        vm.refresh()
                        snack.showSnackbar(if (initial == null) "Website added" else "Website updated")
                        onDone()
                    } catch (e: Exception) {
                        saving = false
                        snack.showSnackbar(e.userMessage())
                    }
                }
            },
            enabled = valid && !saving,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (saving) "Saving…" else if (initial == null) "Start monitoring" else "Save changes") }
    }
}

@Composable
private fun ContactChannels(contact: Contact, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    val channels = contact.channels
    if (channels.isEmpty()) return
    Column {
        Text(contact.name, style = MaterialTheme.typography.labelLarge)
        channels.forEach { ch ->
            val key = "${contact.id}-$ch"
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(key in selected, { on -> onChange(if (on) selected + key else selected - key) })
                Text(ch.replaceFirstChar { it.uppercase() })
            }
        }
    }
}
