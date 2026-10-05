package com.zer0state.zzthat.grammar

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** contracts/vectors.json at the pin, read in place so there is one copy. */
object Vectors {
    val root: JsonObject by lazy { read("zz.vectors").jsonObject }

    fun rows(key: String): List<JsonObject> = root[key]?.jsonArray?.map { it.jsonObject }.orEmpty()

    fun section(key: String): JsonObject = root[key]?.jsonObject ?: JsonObject(emptyMap())

    /** The fixture-7 list from the check_word section. */
    val fixture7: WordList by lazy { WordList("fixture-7", section("check_word").strings("wordlist")) }

    fun read(property: String): JsonElement {
        val path = System.getProperty(property) ?: error("system property $property is not set")
        return Json.parseToJsonElement(File(path).readText(Charsets.UTF_8))
    }

    /** One parameterized case: the row's input as the label, and the row. */
    fun cases(rows: List<JsonObject>, label: (JsonObject) -> String = { it.string("input") ?: "" }): List<Array<Any>> =
        rows.mapIndexed { index, row ->
            val text = label(row)
            val shown = if (text.length > 40) text.take(40) + "..." else text
            arrayOf("#$index ${escape(shown)}", row)
        }

    private fun escape(text: String): String = buildString {
        for (c in text) {
            if (c.code in 0x20..0x7E) append(c) else append("\\u%04x".format(c.code))
        }
    }
}

fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.strings(key: String): List<String> = (this[key] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
