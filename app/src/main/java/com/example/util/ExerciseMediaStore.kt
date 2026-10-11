package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Where media the athlete attaches to one of their own exercises is kept.
 *
 * The picker hands back a `content://` uri that belongs to the system photo picker, not to us.
 * That grant is deliberately short lived: it is revoked once the picker is out of the way, which
 * is why every later read of the stored uri failed with
 * `SecurityException: does not have permission to access picker uri`.
 *
 * The bytes are therefore copied into the app's own files directory at the moment they are handed
 * over, and what gets persisted is a path the app owns and can read forever — no grant, no expiry,
 * and it survives restarts and updates. The copy has to happen while the picker's result is still
 * warm, so [import] is deliberately synchronous and takes the uri directly.
 */
object ExerciseMediaStore {

    private const val TAG = "ExerciseMediaStore"
    private const val DIR_NAME = "exercise_media"

    /** Hard ceiling on what one pick may be. Guard against a user selecting a 2 GB home video. */
    private const val MAX_BYTES = 64L * 1024L * 1024L

    private fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    private fun fileFor(context: Context, exerciseId: Long, extension: String): File =
        File(dir(context), "exercise_$exerciseId.$extension")

    /**
     * Copies [source] into the app's storage and returns the path to keep on the exercise.
     *
     * The file is named after the exercise, so attaching a new pick silently replaces the previous
     * one instead of leaving an orphan behind, and [delete] can find it again on removal.
     *
     * @param source the uri returned by the picker.
     * @param exerciseId the exercise that already exists in the database and will own this file.
     * @param mimeType the resolver's type for [source], used to pick a file extension.
     * @return an absolute path, or null when the bytes could not be copied.
     */
    fun import(context: Context, exerciseId: Long, source: Uri, mimeType: String?): String? {
        val extension = extensionFor(mimeType)
        val target = fileFor(context, exerciseId, extension)
        // Written to a sibling first: a half copied file would otherwise replace a working one and
        // leave the exercise with a permanently broken media slot.
        val staging = File(target.parentFile, "${target.name}.part")

        return try {
            context.contentResolver.openInputStream(source)?.use { input ->
                FileOutputStream(staging).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        written += read
                        if (written > MAX_BYTES) {
                            staging.delete()
                            Log.w(TAG, "picker media for $exerciseId exceeded ${MAX_BYTES} bytes")
                            return null
                        }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: run {
                staging.delete()
                Log.w(TAG, "picker media for $exerciseId had no readable stream")
                return null
            }

            if (staging.length() == 0L) {
                staging.delete()
                Log.w(TAG, "picker media for $exerciseId came back empty")
                return null
            }

            target.delete()
            if (!staging.renameTo(target)) {
                staging.delete()
                Log.w(TAG, "could not move picker media for $exerciseId into place")
                return null
            }
            target.absolutePath
        } catch (e: IOException) {
            staging.delete()
            Log.w(TAG, "picker media for $exerciseId could not be copied", e)
            null
        } catch (e: SecurityException) {
            staging.delete()
            Log.w(TAG, "picker media for $exerciseId was no longer readable", e)
            null
        }
    }

    /**
     * Removes the media belonging to [exerciseId], whatever its extension.
     *
     * Globs rather than naming a single file because the extension changes with the mime type of
     * whatever was picked last.
     */
    fun delete(context: Context, exerciseId: Long) {
        dir(context).listFiles { file ->
            file.isFile && (file.name == "exercise_$exerciseId.part" ||
                file.name.startsWith("exercise_${exerciseId}."))
        }?.forEach { it.delete() }
    }

    /**
     * A file extension for a picker mime type.
     *
     * The video branch is the one that matters: the extension is the only hint the player is given
     * when the model is a plain path, and `.mp4` is what every phone records.
     */
    private fun extensionFor(mimeType: String?): String = when {
        mimeType == null -> "bin"
        mimeType.startsWith("video/") -> "mp4"
        mimeType.startsWith("image/gif") -> "gif"
        mimeType.startsWith("image/png") -> "png"
        mimeType.startsWith("image/webp") -> "webp"
        else -> "jpg"
    }
}
