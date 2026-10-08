package app.monitor360.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { SYSTEM("System default"), LIGHT("Light"), DARK("Dark") }

enum class Accent(val label: String, val seed: Long) {
    TEAL("Teal", 0xFF0F9D8A),
    BLUE("Blue", 0xFF2F6FED),
    INDIGO("Indigo", 0xFF5B5BD6),
    PURPLE("Purple", 0xFF9B51E0),
    PINK("Pink", 0xFFE0457B),
    ORANGE("Orange", 0xFFE8731A),
    GREEN("Green", 0xFF2E9E4F),
    SLATE("Slate", 0xFF52667A),
}

data class Prefs(
    val apiKey: String = "",
    val baseUrl: String = DEFAULT_BASE_URL,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoled: Boolean = false,
    val dynamicColor: Boolean = false,
    val accent: Accent = Accent.TEAL,
    val backgroundRefreshMinutes: Int = 30,
    val notificationsEnabled: Boolean = true,
    val notifyOnDown: Boolean = true,
    val notifyOnRecovery: Boolean = true,
    val autoRefreshSeconds: Int = 60,
    val defaultRangeHours: Int = 24,
    val cpuWarn: Int = 80,
    val memWarn: Int = 80,
    val diskWarn: Int = 85,
    val uptimeWarn: Double = 99.0,
    val compactLists: Boolean = false,
    val showIds: Boolean = false,
    val statusPageId: String = "",
    val favorites: Set<String> = emptySet(),
) {
    val isConfigured get() = apiKey.isNotBlank()

    companion object {
        const val DEFAULT_BASE_URL = "https://api.monitoring360.io/v1"
    }
}

class SettingsStore(context: Context) {
    private val plain: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val secure: SharedPreferences = try {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "secure_settings", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        context.getSharedPreferences("secure_settings_fallback", Context.MODE_PRIVATE)
    }

    private val _prefs = MutableStateFlow(load())
    val prefs: StateFlow<Prefs> = _prefs.asStateFlow()
    val current: Prefs get() = _prefs.value

    private inline fun <reified E : Enum<E>> SharedPreferences.enum(key: String, default: E): E =
        getString(key, null)?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

    private fun load(): Prefs {
        val d = Prefs()
        return Prefs(
            apiKey = secure.getString("api_key", "") ?: "",
            baseUrl = plain.getString("base_url", d.baseUrl) ?: d.baseUrl,
            themeMode = plain.enum("theme_mode", d.themeMode),
            amoled = plain.getBoolean("amoled", d.amoled),
            dynamicColor = plain.getBoolean("dynamic_color", d.dynamicColor),
            accent = plain.enum("accent", d.accent),
            backgroundRefreshMinutes = plain.getInt("bg_refresh_min", d.backgroundRefreshMinutes),
            notificationsEnabled = plain.getBoolean("notifications", d.notificationsEnabled),
            notifyOnDown = plain.getBoolean("notify_down", d.notifyOnDown),
            notifyOnRecovery = plain.getBoolean("notify_recovery", d.notifyOnRecovery),
            autoRefreshSeconds = plain.getInt("auto_refresh_sec", d.autoRefreshSeconds),
            defaultRangeHours = plain.getInt("range_hours", d.defaultRangeHours),
            cpuWarn = plain.getInt("cpu_warn", d.cpuWarn),
            memWarn = plain.getInt("mem_warn", d.memWarn),
            diskWarn = plain.getInt("disk_warn", d.diskWarn),
            uptimeWarn = plain.getFloat("uptime_warn", d.uptimeWarn.toFloat()).toDouble(),
            compactLists = plain.getBoolean("compact", d.compactLists),
            showIds = plain.getBoolean("show_ids", d.showIds),
            statusPageId = plain.getString("status_page_id", "") ?: "",
            favorites = plain.getStringSet("favorites", emptySet())?.toSet() ?: emptySet(),
        )
    }

    fun update(transform: (Prefs) -> Prefs) {
        val old = _prefs.value
        val new = transform(old)
        secure.edit().putString("api_key", new.apiKey).apply()
        plain.edit()
            .putString("base_url", new.baseUrl)
            .putString("theme_mode", new.themeMode.name)
            .putBoolean("amoled", new.amoled)
            .putBoolean("dynamic_color", new.dynamicColor)
            .putString("accent", new.accent.name)
            .putInt("bg_refresh_min", new.backgroundRefreshMinutes)
            .putBoolean("notifications", new.notificationsEnabled)
            .putBoolean("notify_down", new.notifyOnDown)
            .putBoolean("notify_recovery", new.notifyOnRecovery)
            .putInt("auto_refresh_sec", new.autoRefreshSeconds)
            .putInt("range_hours", new.defaultRangeHours)
            .putInt("cpu_warn", new.cpuWarn)
            .putInt("mem_warn", new.memWarn)
            .putInt("disk_warn", new.diskWarn)
            .putFloat("uptime_warn", new.uptimeWarn.toFloat())
            .putBoolean("compact", new.compactLists)
            .putBoolean("show_ids", new.showIds)
            .putString("status_page_id", new.statusPageId)
            .putStringSet("favorites", new.favorites)
            .apply()
        _prefs.value = new
    }

    fun signOut() {
        secure.edit().clear().apply()
        plain.edit().clear().apply()
        _prefs.value = Prefs()
    }
}
