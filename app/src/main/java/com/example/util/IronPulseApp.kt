package com.example.util

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Application class that owns the app wide Coil image loader.
 *
 * This exists because animated GIFs were silently rendering as a single still frame. Coil's default
 * loader, built by `coil-compose` from the service loader, has no GIF decoder registered: it decodes
 * the first frame of the file and stops, so an exercise animation looked like a static photo. The
 * decoder has to be added explicitly, and that requires owning the ImageLoader.
 *
 * Registering it here rather than per composable means every animated surface in the app (exercise
 * list, detail dialog, in-workout preview, and any media the athlete adds themselves) animates
 * through one loader and one cache.
 */
class IronPulseApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                // ImageDecoderDecoder covers API 28+ and delegates to the platform's animated decoder;
                // GifDecoder covers 24 to 27, which the app still supports.
                add(ImageDecoderDecoder.Factory())
                add(GifDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    // Exercise animations are small, but a list the athlete scrolls through can hold
                    // dozens at once. 25% of the available heap is the usual recommendation.
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("exercise_media"))
                    // The dataset's GIFs never change, and the ones the athlete adds from their
                    // gallery never change either, so a long lived cache is the right default here.
                    .maxSizeBytes(80L * 1024 * 1024)
                    .build()
            }
            // Crossfade is off on purpose: it would fade a looping exercise animation on every
            // recomposition-driven reload, which reads as a stutter rather than a smooth loop.
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()

    companion object {

        @Volatile
        private var stillFrameImageLoader: ImageLoader? = null

        /**
         * An image loader that decodes only the first frame of an animation.
         *
         * This is the fix for "the demo still plays on the exercise list even with autoplay off".
         * The main loader decodes a GIF into an [android.graphics.drawable.AnimatedImageDrawable]
         * and Coil starts it, so every visible row in the list loops at once, which is both the
         * reported bug and a real cost: a screen of simultaneously animating GIFs. Loading a URL
         * through a loader with no animated decoder registered falls through to the built-in
         * `BitmapFactoryDecoder`, which returns a static bitmap of frame one.
         *
         * The network and disk cache are shared with the main loader (same urls, different decode),
         * so the frame still arrives without re downloading.
         */
        fun stillFrames(context: Context): ImageLoader =
            stillFrameImageLoader ?: synchronized(this) {
                stillFrameImageLoader ?: buildStillFrameLoader(context.applicationContext)
                    .also { stillFrameImageLoader = it }
            }

        private fun buildStillFrameLoader(context: Context): ImageLoader =
            ImageLoader.Builder(context)
                // Deliberately empty: no ImageDecoderDecoder and no GifDecoder. The defaults Coil
                // appends to every registry (including BitmapFactoryDecoder) then handle everything,
                // and they never produce an animated drawable.
                .components { }
                .memoryCache {
                    MemoryCache.Builder(context)
                        .maxSizePercent(0.10)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        // The same directory as the main loader on purpose: the bytes are identical,
                        // so the animation and the still frame of one exercise are one download.
                        // Two loaders over one cache is exactly what Coil's shared disk cache is for.
                        .directory(context.cacheDir.resolve("exercise_media"))
                        .maxSizeBytes(80L * 1024 * 1024)
                        .build()
                }
                .crossfade(false)
                .respectCacheHeaders(false)
                .build()
    }
}
