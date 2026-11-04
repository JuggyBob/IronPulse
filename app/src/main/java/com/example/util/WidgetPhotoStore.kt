package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Where the athlete's Home widget photo is kept.
 *
 * The picked image is copied into the app's own storage the moment it is chosen, exactly like the
 * profile picture: the picker's `content://` grant ends when the picker closes, so a path to the
 * original would stop working as soon as the athlete deleted the picture from their gallery. The
 * copy is cropped to the card's 16:9 shape on the way in, which keeps the file small and means the
 * Home card never has to decide which part of a portrait photo to show.
 *
 * This is deliberately a different directory from [ProfileMediaStore]. Restoring the default Home
 * widgets may delete every widget photo, and it must never be able to take the profile picture with
 * it.
 */
object WidgetPhotoStore {

    private const val TAG = "WidgetPhotoStore"
    private const val DIR_NAME = "home_widget_media"
    private const val FILE_PREFIX = "widget_"
    private const val JPEG_QUALITY = 90

    /** The aspect ratio of the photo card on Home. */
    private const val TARGET_ASPECT = 16f / 9f

    private fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /**
     * Decodes a picked picture, crops it to the card shape and stores the copy.
     *
     * @return the app-private path, or null when the image cannot be read or written.
     */
    fun import(context: Context, uri: Uri): String? {
        val decoded = ImageCropper.decode(context, uri) ?: return null
        val cropped = cropToAspect(decoded, TARGET_ASPECT)
        val path = write(context, cropped)
        if (cropped !== decoded) cropped.recycle()
        decoded.recycle()
        return path
    }

    /**
     * Writes [bitmap] as a JPEG through a staging file.
     *
     * The rename is the commit point: a failure while encoding leaves the previous photo untouched
     * and a `.tmp` file that [deleteUnreferenced] can remove on the next cleanup.
     */
    fun write(context: Context, bitmap: Bitmap): String? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val folder = dir(context)
        val target = File(
            folder,
            "$FILE_PREFIX${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
        )
        val staging = File(folder, "${target.name}.tmp")
        return try {
            FileOutputStream(staging).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    staging.delete()
                    return null
                }
            }
            if (staging.length() == 0L) {
                staging.delete()
                return null
            }
            if (!staging.renameTo(target)) {
                staging.delete()
                return null
            }
            target.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "could not write the widget photo", e)
            staging.delete()
            null
        }
    }

    /**
     * Writes already encoded image bytes into the widget photo directory.
     *
     * Used by the backup importer, which must restore the exact JPEG/PNG bytes from the archive
     * rather than decode and re-encode them (re-encoding would change the athlete's picture).
     * The file goes through a staging name and is renamed only after a complete write, so a failure
     * cannot leave a partially restored image that the Home card would try to display.
     */
    fun writeEncoded(context: Context, bytes: ByteArray, extension: String): String? {
        if (bytes.isEmpty()) return null
        val safeExtension = when (extension.lowercase()) {
            "jpg", "jpeg" -> "jpg"
            "png" -> "png"
            "webp" -> "webp"
            else -> return null
        }
        val folder = dir(context)
        val target = File(
            folder,
            "$FILE_PREFIX${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$safeExtension"
        )
        val staging = File(folder, "${target.name}.tmp")
        return try {
            FileOutputStream(staging).use { out -> out.write(bytes) }
            if (staging.length() != bytes.size.toLong()) {
                staging.delete()
                return null
            }
            if (!staging.renameTo(target)) {
                staging.delete()
                return null
            }
            target.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "could not restore the widget photo", e)
            staging.delete()
            null
        }
    }

    /** True when [path] points at a non-empty file that can be handed to the image loader. */
    fun isUsable(path: String): Boolean {
        if (path.isBlank()) return false
        val file = File(path)
        return file.isFile && file.length() > 0L
    }

    /** Deletes [path], but only when it is a file inside the widget photo directory. */
    fun delete(context: Context, path: String) {
        if (path.isBlank()) return
        val folder = runCatching { dir(context).canonicalFile }.getOrNull() ?: return
        val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return
        if (file.parentFile == folder && file.isFile) {
            file.delete()
        }
    }

    /**
     * Deletes every widget photo that is not in [referencedPaths].
     *
     * Called after a replacement, a removal and a reset. Passing an empty set is the reset case:
     * nothing references a widget photo any more, so all of them go. A stale `.tmp` from a failed
     * write is never referenced and is removed by the same pass.
     */
    fun deleteUnreferenced(context: Context, referencedPaths: Set<String>) {
        val folder = dir(context)
        val keep = referencedPaths
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { File(it).canonicalPath }.getOrNull() }
            .toSet()
        folder.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val canonical = runCatching { file.canonicalPath }.getOrNull() ?: return@forEach
            if (canonical !in keep) {
                file.delete()
            }
        }
    }

    /** Removes the widget photo directory and everything in it. */
    fun deleteAll(context: Context) {
        val folder = dir(context)
        folder.listFiles()?.forEach { it.delete() }
        folder.delete()
    }

    /**
     * Centre-crops [source] to [targetAspect].
     *
     * The photo card uses `ContentScale.Crop`, so the same framing could be achieved at draw time,
     * but storing the cropped copy means the file is smaller and the card has one obvious shape on
     * every screen size.
     */
    internal fun cropToAspect(source: Bitmap, targetAspect: Float): Bitmap {
        if (source.width <= 0 || source.height <= 0 || targetAspect <= 0f) return source
        val sourceAspect = source.width.toFloat() / source.height.toFloat()
        val width: Int
        val height: Int
        if (sourceAspect > targetAspect) {
            height = source.height
            width = (height * targetAspect).toInt().coerceIn(1, source.width)
        } else {
            width = source.width
            height = (width / targetAspect).toInt().coerceIn(1, source.height)
        }
        if (width == source.width && height == source.height) return source
        val left = (source.width - width) / 2
        val top = (source.height - height) / 2
        return try {
            Bitmap.createBitmap(source, left, top, width, height)
        } catch (e: Exception) {
            Log.w(TAG, "could not crop the widget photo", e)
            source
        }
    }
}
