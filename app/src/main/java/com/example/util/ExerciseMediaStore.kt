package com.example.util

import android.content.Context
import android.net.Uri
import java.io.File

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
 *
 * The copying itself lives in [MediaStore], shared with the profile picture.
 */
object ExerciseMediaStore {

    private const val TAG = "ExerciseMediaStore"
    private const val DIR_NAME = "exercise_media"

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
        val extension = MediaStore.extensionFor(mimeType)
        val target = fileFor(context, exerciseId, extension)
        return MediaStore.copy(context, target, source, mimeType)
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
}
