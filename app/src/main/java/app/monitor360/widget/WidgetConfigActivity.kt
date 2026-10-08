package app.monitor360.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.monitor360.app
import app.monitor360.data.Accent
import app.monitor360.ui.theme.Monitor360Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class WidgetKind(
    val receiver: String,
    val label: String,
    val default: WidgetStyle = WidgetStyle(),
    val usesTheme: Boolean = true,
    val isList: Boolean = false,
) {
    CARDS("StatusWidgetReceiver", "Status · Cards"),
    DARK("StatusDarkWidgetReceiver", "Status · Dark", WidgetStyle(theme = WidgetTheme.DARK)),
    COMPACT("StatusCompactWidgetReceiver", "Status · Compact"),
    BANNER("StatusBannerWidgetReceiver", "Status · Banner", usesTheme = false),
    DOTS("StatusDotsWidgetReceiver", "Status · Dots"),
    COUNTER("StatusCounterWidgetReceiver", "Status · Counter"),
    WEBSITES("WebsitesWidgetReceiver", "Websites", isList = true),
    SERVERS("ServersWidgetReceiver", "Servers", isList = true);

    companion object {
        fun of(providerClassName: String?): WidgetKind =
            entries.firstOrNull { providerClassName?.endsWith(it.receiver) == true } ?: CARDS
    }
}

class WidgetConfigActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider?.className
        val kind = WidgetKind.of(provider)
        val store = app.widgetStyles
        val initial = store.get(widgetId, kind.default)

        setContent {
            val prefs by app.settings.prefs.collectAsStateWithLifecycle()
            Monitor360Theme(prefs) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    ConfigScreen(kind, initial, prefs.accent, onCancel = { finish() }) { style ->
                        store.set(widgetId, style)
                        val context = applicationContext
                        CoroutineScope(Dispatchers.Default).launch { WidgetUpdater.updateAll(context) }
                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                        finish()
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigScreen(kind: WidgetKind, initial: WidgetStyle, appAccent: Accent, onCancel: () -> Unit, onSave: (WidgetStyle) -> Unit) {
    var style by remember { mutableStateOf(initial) }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Customize widget", style = MaterialTheme.typography.headlineSmall)
        Text(kind.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

        Preview(style, appAccent)

        if (kind.usesTheme) {
            Section("Theme") {
                Chips(WidgetTheme.entries, style.theme, { it.label }) { style = style.copy(theme = it) }
            }
        }
        if (kind.usesTheme) {
            Section("Accent color") {
                AccentPicker(style.accent, appAccent) { style = style.copy(accent = it) }
            }
        }
        Section("Background opacity: ${style.opacity}%") {
            Slider(style.opacity.toFloat(), { style = style.copy(opacity = it.toInt()) }, valueRange = 20f..100f)
        }
        Section("Corners") {
            Chips(Corners.entries, style.corners, { it.label }) { style = style.copy(corners = it) }
        }
        Section("Text size") {
            Chips(TextSize.entries, style.textSize, { it.label }) { style = style.copy(textSize = it) }
        }
        if (kind.isList) {
            Section("Show") {
                Chips(ListFilter.entries, style.filter, { it.label }) { style = style.copy(filter = it) }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Refresh and settings buttons", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Hide them for a cleaner look. You can still reconfigure the widget by long-pressing it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(style.showControls, { style = style.copy(showControls = it) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button({ onSave(style) }) { Text("Save") }
            OutlinedButton({ style = kind.default }) { Text("Reset") }
            OutlinedButton(onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> FilterChip(o == selected, { onSelect(o) }, { Text(label(o)) }) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(selected: Accent?, appAccent: Accent, onSelect: (Accent?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterChip(selected == null, { onSelect(null) }, { Text("App color (${appAccent.label})") })
        Accent.entries.forEach { a ->
            val chosen = a == selected
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color(a.seed))
                    .then(if (chosen) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .clickable { onSelect(a) },
                contentAlignment = Alignment.Center,
            ) { if (chosen) Icon(Icons.Default.Check, a.label, tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun Preview(style: WidgetStyle, appAccent: Accent) {
    val colors = widgetColors(style.theme, Color((style.accent ?: appAccent).seed), isSystemInDarkTheme())
    val alpha = style.opacity / 100f
    val scale = style.textSize.scale
    Box(
        Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF4F6D7A), Color(0xFFE0B04F), Color(0xFF8E5572))))
            .padding(16.dp),
    ) {
        Column(
            Modifier.fillMaxSize().clip(RoundedCornerShape(style.corners.dp.dp))
                .background(colors.surface.copy(alpha = colors.surface.alpha * alpha)).padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 4.dp, height = 14.dp).background(colors.accent))
                Spacer(Modifier.width(8.dp))
                Text("360 Monitor", color = colors.text, fontWeight = FontWeight.Bold, fontSize = (13 * scale).sp, modifier = Modifier.weight(1f))
                if (style.showControls) Text("⟳  ⚙", color = colors.accent, fontSize = (14 * scale).sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewCard("Servers", colors, alpha, scale, Modifier.weight(1f))
                PreviewCard("Websites", colors, alpha, scale, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PreviewCard(label: String, colors: WidgetColors, alpha: Float, scale: Float, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(colors.surfaceAlt.copy(alpha = colors.surfaceAlt.alpha * alpha)).padding(8.dp),
    ) {
        Text(label, color = colors.muted, fontSize = (10 * scale).sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("2", color = colors.up, fontWeight = FontWeight.Bold, fontSize = (22 * scale).sp)
            Text(" up", color = colors.muted, fontSize = (10 * scale).sp)
        }
        Text("1 down", color = colors.down, fontWeight = FontWeight.Bold, fontSize = (11 * scale).sp)
    }
}
