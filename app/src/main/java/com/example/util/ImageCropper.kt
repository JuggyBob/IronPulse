package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log

/**
 * Turning a picture the athlete picked into the square that becomes their profile picture.
 *
 * Two jobs: reading the picture properly (a photo from a phone camera is stored rotated and
 * carries its rotation in EXIF, so it has to be turned upright or the crop is taken from the wrong
 * part of it), and cutting the square out of it.
 */
object ImageCropper {

    private const val TAG = "ImageCropper"

    /**
     * The largest edge a picture is decoded at.
     *
     * A modern phone camera produces 4000 pixel images and several of them in a row will not fit
     * in memory once decoded. The avatar is displayed at 56 dp, so decoding anything larger than
     * this is wasted memory.
     */
    private const val MAX_DECODED_EDGE = 2048

    /**
     * Reads the picture the athlete picked, upright and no larger than it needs to be.
     *
     * @return the decoded picture, or null when the file could not be read.
     */
    fun decode(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
        } catch (e: Exception) {
            Log.w(TAG, "could not read the bounds of the picked picture", e)
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = try {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } catch (e: Exception) {
            Log.w(TAG, "could not decode the picked picture", e)
            null
        } ?: return null

        return applyExifRotation(context, uri, decoded)
    }

    /** The power of two that brings the picture down to roughly [MAX_DECODED_EDGE]. */
    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= MAX_DECODED_EDGE) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    /**
     * Turns the picture the way the camera held it.
     *
     * Without this a portrait photo is stored sideways and the crop is taken from the sky.
     */
    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            Log.w(TAG, "could not read the orientation of the picked picture", e)
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "not enough memory to turn the picked picture", e)
            bitmap
        }
    }

    /**
     * Cuts the square the athlete framed out of the picture.
     *
     * The picture is drawn so that it covers a square of [viewportSize] pixels, scaled by [zoom] and
     * moved by [offset]. This turns that framing back into a rectangle in the picture's own pixels
     * and cuts it out.
     *
     * @param zoom how far past "fills the square" the athlete zoomed in.
     * @param offset how far the picture was dragged, in pixels, from where it was centred.
     */
    fun crop(
        source: Bitmap,
        viewportSize: Int,
        zoom: Float,
        offsetX: Float,
        offsetY: Float
    ): Bitmap {
        if (viewportSize <= 0) return source

        val width = source.width
        val height = source.height
        val size = viewportSize.toFloat()

        // The scale at which the picture just fits inside the square, and the scale at which it
        // fills it. The picture is drawn at the second one, so there are never gaps in the crop.
        val fitScale = minOf(size / width, size / height)
        val coverScale = maxOf(size / width, size / height)
        val effective = coverScale * zoom

        // The size the picture is drawn at, and where its top left corner ended up.
        val drawnWidth = width * effective
        val drawnHeight = height * effective
        val left = (size - drawnWidth) / 2f + offsetX
        val top = (size - drawnHeight) / 2f + offsetY

        // The square in the picture's own pixels.
        var cropLeft = (-left / effective).toInt()
        var cropTop = (-top / effective).toInt()
        var cropSide = (size / effective).toInt()

        // The framing is clamped so the picture always covers the square, but a rounding error can
        // still push an edge out by a pixel, and a crop outside the picture is not a crop.
        cropLeft = cropLeft.coerceIn(0, (width - 1).coerceAtLeast(0))
        cropTop = cropTop.coerceIn(0, (height - 1).coerceAtLeast(0))
        cropSide = cropSide.coerceIn(1, minOf(width - cropLeft, height - cropTop))

        return try {
            Bitmap.createBitmap(source, cropLeft, cropTop, cropSide, cropSide)
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "not enough memory to cut the picture", e)
            source
        }
    }
}
