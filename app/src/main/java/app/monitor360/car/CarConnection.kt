package app.monitor360.car

import android.content.Context

data class CarConnectionRecord(val time: Long, val hostPackage: String, val apiLevel: Int)

object CarConnection {
    private const val FILE = "car_connection"

    fun record(context: Context, hostPackage: String?, apiLevel: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong("time", System.currentTimeMillis())
            .putString("host", hostPackage ?: "unknown")
            .putInt("api", apiLevel)
            .apply()
    }

    fun recordMedia(context: Context, hostPackage: String?) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong("media_time", System.currentTimeMillis())
            .putString("media_host", hostPackage ?: "unknown")
            .apply()
    }

    fun lastMedia(context: Context): CarConnectionRecord? {
        val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val time = sp.getLong("media_time", 0)
        if (time == 0L) return null
        return CarConnectionRecord(time, sp.getString("media_host", "unknown") ?: "unknown", 0)
    }

    fun last(context: Context): CarConnectionRecord? {
        val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val time = sp.getLong("time", 0)
        if (time == 0L) return null
        return CarConnectionRecord(time, sp.getString("host", "unknown") ?: "unknown", sp.getInt("api", 0))
    }
}
