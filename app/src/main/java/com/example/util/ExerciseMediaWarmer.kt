package com.example.util

import android.content.Context
import android.util.Log
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.model.Exercise
import com.example.data.model.ExerciseMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Downloads the still image of every exercise referenced by a saved routine.
 *
 * The Exercises tab already loads images on demand: a row only requests its picture when the
 * LazyColumn composes it, so opening the tab never walks the whole catalog. Routine exercises are
 * the other category that must work offline, and most of them are never on screen when the
 * athlete is looking at their plan. This warmer fills exactly that gap.
 *
 * It deliberately does not touch the rest of the database, runs at most two requests at a time,
 * waits briefly so it cannot fight the first frame after a routine edit, and asks Coil for the
 * same URL the list rows use. Coil joins duplicate requests and serves anything already in the
 * persistent disk cache without touching the network, so repeated calls are cheap.
 */
object ExerciseMediaWarmer {

    private const val TAG = "ExerciseMediaWarmer"

    /** Enough to make progress without starving the UI thread's own image requests. */
    private const val MAX_PARALLEL_DOWNLOADS = 2

    /** Let navigation and the routine card settle before competing for decode time. */
    private const val SETTLE_DELAY_MS = 900L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(MAX_PARALLEL_DOWNLOADS)
    private val lock = Any()

    /** URLs already fetched or already present in this process, so a re-emission is a no-op. */
    private val warmed = HashSet<String>()

    private var current: Job? = null

    /**
     * Replaces any warmer work still queued for a previous routine list.
     *
     * Passing an empty list (a routine with no exercises) simply cancels the pending batch.
     */
    fun warmRoutineImages(context: Context, exercises: List<Exercise>) {
        val appContext = context.applicationContext
        val models = exercises.asSequence()
            .map { exercise ->
                ExerciseMedia.thumbnailModel(
                    exercise.customMediaUri,
                    exercise.customMediaIsVideo,
                    exercise.mediaPath
                )
            }
            .filterIsInstance<String>()
            .filter { it.startsWith("https://") || it.startsWith("http://") }
            .distinct()
            .toList()

        current?.cancel()
        if (models.isEmpty()) return

        current = scope.launch {
            delay(SETTLE_DELAY_MS)
            val loader = IronPulseApp.stillFrames(appContext)
            models.map { model ->
                async {
                    if (isWarmed(model)) return@async
                    gate.withPermit {
                        if (isWarmed(model)) return@withPermit
                        val request = ImageRequest.Builder(appContext)
                            .data(model)
                            .memoryCachePolicy(CachePolicy.DISABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .build()
                        val result = runCatching { loader.execute(request) }
                        if (result.isSuccess) {
                            markWarmed(model)
                            // Debug-level breadcrumb: proves which images the routine pass
                            // resolved, whether from the network or the persistent cache.
                            Log.d(TAG, "Routine image ready: $model")
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private fun isWarmed(model: String): Boolean = synchronized(lock) { model in warmed }

    private fun markWarmed(model: String) {
        synchronized(lock) { warmed += model }
    }
}
