/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.backup

import okio.Buffer
import com.squareup.moshi.JsonReader

/**
 * A minimal JSON tree used only by the backup migration pipeline.
 *
 * Moshi's typed adapters are used for the current schema. Migrations need to modify an older
 * document before the current adapter sees it, so they operate on this tree instead. Numbers keep
 * their original literal text: a migration that does not touch a weight, timestamp or measurement
 * cannot introduce a floating point rounding error into it.
 */
sealed class JsonNode {
    data class Obj(val values: LinkedHashMap<String, JsonNode> = LinkedHashMap()) : JsonNode() {
        operator fun get(key: String): JsonNode? = values[key]
        operator fun set(key: String, value: JsonNode) {
            values[key] = value
        }

        fun remove(key: String): JsonNode? = values.remove(key)
    }

    data class Arr(val values: MutableList<JsonNode> = mutableListOf()) : JsonNode() {
        operator fun get(index: Int): JsonNode? = values.getOrNull(index)
    }

    data class Str(val value: String) : JsonNode()

    data class Num(val literal: String) : JsonNode() {
        fun toDouble(): Double = literal.toDouble()
        fun toLong(): Long = literal.toLong()
        fun toInt(): Int = literal.toInt()
    }

    data class Bool(val value: Boolean) : JsonNode()

    data object Null : JsonNode()

    fun render(): String = buildString { appendJson(this@JsonNode) }

    companion object {
        private const val MAX_DEPTH = 64

        fun parse(raw: ByteArray): JsonNode {
            val buffer = Buffer().write(raw)
            JsonReader.of(buffer).use { reader ->
                val node = readNode(reader, 0)
                if (reader.peek() != JsonReader.Token.END_DOCUMENT) {
                    throw BackupException(
                        "The backup contains more than one JSON document in one section.",
                        BackupErrorCode.MALFORMED_ARCHIVE
                    )
                }
                return node
            }
        }

        private fun readNode(reader: JsonReader, depth: Int): JsonNode {
            if (depth > MAX_DEPTH) {
                throw BackupException(
                    "The backup JSON is nested too deeply.",
                    BackupErrorCode.MALFORMED_ARCHIVE
                )
            }
            return when (reader.peek()) {
                JsonReader.Token.BEGIN_OBJECT -> {
                    reader.beginObject()
                    val obj = Obj()
                    while (reader.hasNext()) {
                        val name = reader.nextName()
                        if (obj.values.containsKey(name)) {
                            throw BackupException(
                                "The backup contains a duplicate JSON field.",
                                BackupErrorCode.MALFORMED_ARCHIVE
                            )
                        }
                        obj.values[name] = readNode(reader, depth + 1)
                    }
                    reader.endObject()
                    obj
                }

                JsonReader.Token.BEGIN_ARRAY -> {
                    reader.beginArray()
                    val arr = Arr()
                    while (reader.hasNext()) {
                        arr.values += readNode(reader, depth + 1)
                    }
                    reader.endArray()
                    arr
                }

                JsonReader.Token.STRING -> Str(reader.nextString())
                JsonReader.Token.NUMBER -> Num(reader.nextString())
                JsonReader.Token.BOOLEAN -> Bool(reader.nextBoolean())
                JsonReader.Token.NULL -> {
                    reader.nextNull<Any>()
                    Null
                }

                else -> throw BackupException(
                    "The backup contains an unsupported JSON value.",
                    BackupErrorCode.MALFORMED_ARCHIVE
                )
            }
        }

        private fun StringBuilder.appendJson(node: JsonNode) {
            when (node) {
                is Obj -> {
                    append('{')
                    var first = true
                    for ((key, value) in node.values) {
                        if (!first) append(',')
                        first = false
                        appendQuoted(key)
                        append(':')
                        appendJson(value)
                    }
                    append('}')
                }

                is Arr -> {
                    append('[')
                    node.values.forEachIndexed { index, value ->
                        if (index > 0) append(',')
                        appendJson(value)
                    }
                    append(']')
                }

                is Str -> appendQuoted(node.value)
                is Num -> append(node.literal)
                is Bool -> append(if (node.value) "true" else "false")
                JsonNode.Null -> append("null")
            }
        }

        private fun StringBuilder.appendQuoted(value: String) {
            append('"')
            for (char in value) {
                when (char) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (char < ' ') {
                            append("\\u")
                            append(char.code.toString(16).padStart(4, '0'))
                        } else {
                            append(char)
                        }
                    }
                }
            }
            append('"')
        }
    }
}
