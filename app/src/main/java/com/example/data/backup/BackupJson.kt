/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.backup

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import okio.Buffer

/** Central Moshi instance and the strict byte-to-DTO helpers used by the archive codec. */
object BackupJson {

    val moshi: Moshi = Moshi.Builder().build()

    fun <T> adapter(type: Class<T>): JsonAdapter<T> = moshi.adapter(type)

    fun <T> parse(raw: ByteArray, type: Class<T>): T {
        val reader = JsonReader.of(Buffer().write(raw))
        reader.use {
            val value = adapter(type).fromJson(reader)
                ?: throw BackupException(
                    "A required JSON document in the backup is empty.",
                    BackupErrorCode.INVALID_DATA
                )
            if (reader.peek() != JsonReader.Token.END_DOCUMENT) {
                throw BackupException(
                    "The backup contains trailing data after a JSON document.",
                    BackupErrorCode.MALFORMED_ARCHIVE
                )
            }
            return value
        }
    }

    fun <T> toBytes(value: T, type: Class<T>): ByteArray =
        adapter(type).toJson(value).toByteArray(Charsets.UTF_8)
}
