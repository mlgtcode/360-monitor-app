package app.monitor360.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.monitor360.data.ApiClient
import app.monitor360.data.Prefs
import app.monitor360.data.Repository
import app.monitor360.ui.AppViewModel
import app.monitor360.ui.components.Field
import app.monitor360.ui.userMessage
import app.monitor360.work.Scheduler
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(vm.settings.current.baseUrl) }
    var advanced by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("360 Monitor", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(
            "Check the status of your servers and websites, inspect metrics and manage monitors on the go.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Field(key, { key = it.trim(); error = null }, "API key", password = true, isError = error != null, supporting = error)
        Text(
            "Create a key in the 360 Monitoring web app: profile menu → API Keys. API access requires a Business or Enterprise plan.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (advanced) Field(url, { url = it.trim() }, "API endpoint")
        Button(
            onClick = {
                busy = true
                error = null
                val endpoint = url.ifBlank { Prefs.DEFAULT_BASE_URL }
                scope.launch {
                    try {
                        Repository(ApiClient({ Prefs(apiKey = key, baseUrl = endpoint) })).ping()
                        vm.settings.update { it.copy(apiKey = key, baseUrl = endpoint) }
                        Scheduler.apply(ctx, vm.settings.current)
                        vm.refresh()
                    } catch (e: Exception) {
                        error = e.userMessage()
                    }
                    busy = false
                }
            },
            enabled = key.length >= 8 && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Checking…" else "Connect") }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton({ advanced = !advanced }) { Text(if (advanced) "Hide advanced" else "Advanced") }
            TextButton({
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://app.360monitoring.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Open 360 Monitoring") }
        }
        Text(
            "Unofficial app. It is not affiliated with, endorsed by or sponsored by 360 Monitoring, WebPros or Plesk. " +
                "Your API key stays on this device and is only sent to the 360 Monitoring API.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray.copy(alpha = 0.8f),
        )
    }
}
