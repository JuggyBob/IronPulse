package com.example.util

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Where the athlete's profile picture is kept.
 *
 * The same reasoning as the exercise media: the picker's `content://` grant is short lived, so the
 * bytes are copied into the app's own files directory the moment they are picked. The picture then
 * keeps working even after the athlete deletes it from their gallery, which is the whole point of
 * keeping a copy rather than a reference.
 *
 * There is only ever one picture, so the file has a fixed name. The extension follows the mime
 * type of whatever was picked last, and [delete] globs over the extensions rather than tracking
 * one, so an old file can never be left behind.
 */
object ProfileMediaStore {

    private const val DIR_NAME = "profile_media"
    private const val FILE_STEM = "avatar"

    private fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /**
     * Copies [source] into the app's storage and returns the path to keep on the profile.
     *
     * A new pick replaces the previous picture instead of leaving an orphan behind.
     *
     * @return an absolute path, or null when the bytes could not be copied.
     */
    fun import(context: Context, source: Uri, mimeType: String?): String? {
        val extension = MediaStore.extensionFor(mimeType)
        val target = File(dir(context), "$FILE_STEM.$extension")
        val copied = MediaStore.copy(context, target, source, mimeType)
        if (copied != null) {
            // Drop the picture the previous pick left behind, so only one file is ever kept.
            deleteOtherExtensions(context, keepExtension = extension)
        }
        return copied
    }

    /** Removes the profile picture. */
    fun delete(context: Context) {
        dir(context).listFiles { file -> file.isFile && file.name.startsWith("$FILE_STEM.") }
            ?.forEach { it.delete() }
    }

    /** Removes every stored picture except the one with [keepExtension]. */
    private fun deleteOtherExtensions(context: Context, keepExtension: String) {
        dir(context).listFiles { file ->
            file.isFile && file.name.startsWith("$FILE_STEM.") && file.name != "$FILE_STEM.$keepExtension"
        }?.forEach { it.delete() }
    }
}
