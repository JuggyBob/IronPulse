package com.example.util

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

/**
 * Where the athlete's profile picture is kept.
 *
 * The picture is copied into the app's own storage the moment it is chosen, and it is cropped
 * square on the way in, because the avatar is shown in a circle. Keeping a copy rather than a
 * reference is the point: the picker's `content://` grant is revoked the moment the picker closes,
 * and a copy keeps working even after the athlete deletes the original from their gallery.
 *
 * There is only ever one picture, so the file has a fixed name.
 */
object ProfileMediaStore {

    private const val DIR_NAME = "profile_media"
    private const val FILE_STEM = "avatar"

    private fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /**
     * Writes the cropped picture into the app's storage and returns the path to keep.
     *
     * A new picture replaces the previous one, so only one file is ever kept.
     *
     * @return an absolute path, or null when the picture could not be written.
     */
    fun write(context: Context, bitmap: Bitmap): String? {
        val target = File(dir(context), "$FILE_STEM.png")
        // Written to a sibling first: a half written file would leave the avatar broken.
        val staging = File(target.parentFile, "${target.name}.part")
        return try {
            FileOutputStream(staging).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            if (staging.length() == 0L) {
                staging.delete()
                return null
            }
            target.delete()
            if (!staging.renameTo(target)) {
                staging.delete()
                return null
            }
            target.absolutePath
        } catch (e: Exception) {
            staging.delete()
            null
        }
    }

    /** Removes the profile picture. */
    fun delete(context: Context) {
        dir(context).listFiles { file -> file.isFile && file.name.startsWith("$FILE_STEM.") }
            ?.forEach { it.delete() }
    }
}
