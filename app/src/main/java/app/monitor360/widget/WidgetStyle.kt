package app.monitor360.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import app.monitor360.data.Accent
import app.monitor360.data.AppJson
import app.monitor360.data.int
import app.monitor360.data.jsonObjectOf
import app.monitor360.data.str
import app.monitor360.data.toJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

enum class WidgetTheme(val label: String) {
    AUTO("Follow system"),
    LIGHT("Light"),
    DARK("Dark"),
    BLACK("Black"),
    ACCENT("Accent color"),
}

enum class Corners(val label: String, val dp: Int) {
    SQUARE("Square", 4),
    SMALL("Small", 12),
    MEDIUM("Medium", 22),
    LARGE("Large", 32),
}

enum class TextSize(val label: String, val scale: Float) {
    SMALL("Small", 0.88f),
    NORMAL("Normal", 1f),
    LARGE("Large", 1.18f),
}

enum class ListFilter(val label: String) {
    ALL("Everything"),
    PROBLEMS("Only problems"),
    FAVORITES("Only favorites"),
}

data class WidgetStyle(
    val theme: WidgetTheme = WidgetTheme.AUTO,
    val opacity: Int = 100,
    val corners: Corners = Corners.MEDIUM,
    val textSize: TextSize = TextSize.NORMAL,
    val accent: Accent? = null,
    val showControls: Boolean = true,
    val filter: ListFilter = ListFilter.ALL,
) {
    fun sp(size: Int) = (size * textSize.scale).sp
}

class WidgetColors(
    val surface: Color,
    val surfaceAlt: Color,
    val text: Color,
    val muted: Color,
    val up: Color,
    val down: Color,
    val unknown: Color,
    val accent: Color,
)

private fun lightColors(accent: Color) = WidgetColors(
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFF1F5F9),
    text = Color(0xFF0F1B26),
    muted = Color(0xFF5B6B7B),
    up = Color(0xFF1B9E5A),
    down = Color(0xFFD7263D),
    unknown = Color(0xFF8B98A5),
    accent = accent,
)

private fun darkColors(accent: Color, black: Boolean) = WidgetColors(
    surface = if (black) Color(0xFF000000) else Color(0xFF151F2A),
    surfaceAlt = if (black) Color(0xFF14181D) else Color(0xFF1E2B38),
    text = Color(0xFFE8EEF4),
    muted = Color(0xFF9DB0C2),
    up = Color(0xFF3DD68C),
    down = Color(0xFFFF6B7D),
    unknown = Color(0xFF7B8794),
    accent = accent,
)

private fun accentColors(accent: Color) = WidgetColors(
    surface = accent,
    surfaceAlt = Color.White.copy(alpha = 0.18f),
    text = Color.White,
    muted = Color.White.copy(alpha = 0.8f),
    up = Color(0xFFB6F7D2),
    down = Color(0xFFFFC2CA),
    unknown = Color.White.copy(alpha = 0.7f),
    accent = Color.White,
)

fun widgetColors(theme: WidgetTheme, accent: Color, systemDark: Boolean = false): WidgetColors = when (theme) {
    WidgetTheme.AUTO -> if (systemDark) darkColors(accent, false) else lightColors(accent)
    WidgetTheme.LIGHT -> lightColors(accent)
    WidgetTheme.DARK -> darkColors(accent, false)
    WidgetTheme.BLACK -> darkColors(accent, true)
    WidgetTheme.ACCENT -> accentColors(accent)
}

class WidgetPalette(
    val surface: ColorProvider,
    val surfaceAlt: ColorProvider,
    val text: ColorProvider,
    val muted: ColorProvider,
    val up: ColorProvider,
    val down: ColorProvider,
    val unknown: ColorProvider,
    val accent: ColorProvider,
)

fun WidgetStyle.palette(appAccent: Accent): WidgetPalette {
    val accentColor = Color((accent ?: appAccent).seed)
    val alpha = opacity.coerceIn(10, 100) / 100f
    val light = widgetColors(if (theme == WidgetTheme.AUTO) WidgetTheme.LIGHT else theme, accentColor)
    val dark = widgetColors(if (theme == WidgetTheme.AUTO) WidgetTheme.DARK else theme, accentColor)
    fun pick(transparent: Boolean = false, f: (WidgetColors) -> Color): ColorProvider {
        val tint = { c: Color -> if (transparent) c.copy(alpha = c.alpha * alpha) else c }
        return if (theme == WidgetTheme.AUTO) ColorProvider(day = tint(f(light)), night = tint(f(dark))) else ColorProvider(tint(f(light)))
    }
    return WidgetPalette(
        surface = pick(true) { it.surface },
        surfaceAlt = pick(true) { it.surfaceAlt },
        text = pick { it.text },
        muted = pick { it.muted },
        up = pick { it.up },
        down = pick { it.down },
        unknown = pick { it.unknown },
        accent = pick { it.accent },
    )
}

class WidgetStyleStore(context: Context) {
    private val prefs = context.getSharedPreferences("widget_styles", Context.MODE_PRIVATE)

    fun get(id: Int, default: WidgetStyle = WidgetStyle()): WidgetStyle {
        val raw = prefs.getString(key(id), null) ?: return default
        return runCatching { decode(AppJson.parseToJsonElement(raw) as JsonObject, default) }.getOrDefault(default)
    }

    fun has(id: Int) = prefs.contains(key(id))

    fun set(id: Int, style: WidgetStyle) {
        prefs.edit().putString(key(id), encode(style).toString()).apply()
    }

    fun remove(ids: IntArray) {
        prefs.edit().apply { ids.forEach { remove(key(it)) } }.apply()
    }

    private fun key(id: Int) = "style_$id"

    private fun encode(s: WidgetStyle) = jsonObjectOf(
        "theme" to s.theme.name.toJson(),
        "opacity" to JsonPrimitive(s.opacity),
        "corners" to s.corners.name.toJson(),
        "text" to s.textSize.name.toJson(),
        "accent" to s.accent?.name.toJson(),
        "controls" to JsonPrimitive(s.showControls),
        "filter" to s.filter.name.toJson(),
    )

    private inline fun <reified E : Enum<E>> JsonObject.enum(key: String, default: E): E =
        str(key)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private fun decode(o: JsonObject, d: WidgetStyle) = WidgetStyle(
        theme = o.enum("theme", d.theme),
        opacity = o.int("opacity") ?: d.opacity,
        corners = o.enum("corners", d.corners),
        textSize = o.enum("text", d.textSize),
        accent = o.str("accent")?.let { name -> Accent.entries.firstOrNull { it.name == name } },
        showControls = (o["controls"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: d.showControls,
        filter = o.enum("filter", d.filter),
    )
}
