package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Copying a picked file into the app's own storage.
 *
 * The system photo picker hands back a `content://` uri that belongs to the picker, not to us. That
 * grant is deliberately short lived: it is revoked once the picker is out of the way, which is why
 * every later read of the stored uri failed with
 * `SecurityException: does not have permission to access picker uri`.
 *
 * The bytes are therefore copied into the app's own files directory at the moment they are handed
 * over, and what gets persisted is a path the app owns and can read forever - no grant, no expiry,
 * and it survives restarts and updates. The copy has to happen while the picker's result is still
 * warm, so this is deliberately synchronous and takes the uri directly.
 *
 * Shared by the exercise media and the profile picture, which need exactly the same thing: bytes
 * the app owns, at a path it controls, that outlive the picker's grant.
 */
internal object MediaStore {

    private const val TAG = "MediaStore"

    /** Hard ceiling on what one pick may be. Guard against a user selecting a 2 GB home video. */
    const val MAX_BYTES = 64L * 1024L * 1024L

    /**
     * Copies [source] into [target] and returns the path to keep.
     *
     * The file is written to a sibling first: a half copied file would otherwise replace a working
     * one and leave the caller with a permanently broken media slot.
     *
     * @param source the uri returned by the picker.
     * @param target the file to write. Its parent directory is created if needed.
     * @param mimeType the resolver's type for [source], used to pick a file extension.
     * @return the absolute path, or null when the bytes could not be copied.
     */
    fun copy(context: Context, target: File, source: Uri, mimeType: String?): String? {
        target.parentFile?.mkdirs()
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
                            Log.w(TAG, "picked media exceeded ${MAX_BYTES} bytes")
                            return null
                        }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: run {
                staging.delete()
                Log.w(TAG, "picked media had no readable stream")
                return null
            }

            if (staging.length() == 0L) {
                staging.delete()
                Log.w(TAG, "picked media came back empty")
                return null
            }

            target.delete()
            if (!staging.renameTo(target)) {
                staging.delete()
                Log.w(TAG, "could not move picked media into place")
                return null
            }
            target.absolutePath
        } catch (e: IOException) {
            staging.delete()
            Log.w(TAG, "picked media could not be copied", e)
            null
        } catch (e: SecurityException) {
            staging.delete()
            Log.w(TAG, "picked media was no longer readable", e)
            null
        }
    }

    /**
     * A file extension for a picker mime type.
     *
     * The video branch is the one that matters for exercise media: the extension is the only hint
     * the player is given when the model is a plain path, and `.mp4` is what every phone records.
     */
    fun extensionFor(mimeType: String?): String = when {
        mimeType == null -> "bin"
        mimeType.startsWith("video/") -> "mp4"
        mimeType.startsWith("image/gif") -> "gif"
        mimeType.startsWith("image/png") -> "png"
        mimeType.startsWith("image/webp") -> "webp"
        else -> "jpg"
    }
}
