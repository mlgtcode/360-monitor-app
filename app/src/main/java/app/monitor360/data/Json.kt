package app.monitor360.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

val AppJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun JsonElement?.asObj(): JsonObject? = this as? JsonObject
fun JsonElement?.asArr(): JsonArray? = this as? JsonArray

fun JsonElement?.asStr(): String? =
    (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

fun JsonObject.str(vararg keys: String): String? {
    for (k in keys) this[k].asStr()?.let { if (it.isNotBlank()) return it }
    return null
}

fun JsonObject.dbl(vararg keys: String): Double? {
    for (k in keys) {
        val p = this[k] as? JsonPrimitive ?: continue
        p.doubleOrNull?.let { return it }
        p.contentOrNull?.toDoubleOrNull()?.let { return it }
    }
    return null
}

fun JsonObject.lng(vararg keys: String): Long? {
    for (k in keys) {
        val p = this[k] as? JsonPrimitive ?: continue
        p.longOrNull?.let { return it }
        p.doubleOrNull?.let { return it.toLong() }
        p.contentOrNull?.toLongOrNull()?.let { return it }
    }
    return null
}

fun JsonObject.int(vararg keys: String): Int? = lng(*keys)?.toInt()

fun JsonObject.bool(vararg keys: String): Boolean? {
    for (k in keys) (this[k] as? JsonPrimitive)?.booleanOrNull?.let { return it }
    return null
}

fun JsonObject.obj(key: String): JsonObject? = this[key].asObj()
fun JsonObject.arr(key: String): JsonArray? = this[key].asArr()

fun JsonObject.strList(key: String): List<String> =
    arr(key)?.mapNotNull { it.asStr() } ?: emptyList()

fun JsonElement.listUnder(key: String): List<JsonObject> {
    val arr = when (this) {
        is JsonArray -> this
        is JsonObject -> this[key].asArr() ?: JsonArray(emptyList())
        else -> JsonArray(emptyList())
    }
    return arr.mapNotNull { it as? JsonObject }
}

fun jsonObjectOf(vararg pairs: Pair<String, JsonElement?>): JsonObject =
    JsonObject(pairs.mapNotNull { (k, v) -> v?.let { k to it } }.toMap())

fun String?.toJson(): JsonElement? = this?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it.trim()) }
fun Number?.toJson(): JsonElement? = this?.let { JsonPrimitive(it) }
fun List<String>.toJsonArray(): JsonArray = JsonArray(map { JsonPrimitive(it) })
