package com.example.data.model

/**
 * Resolves an exercise's animation GIF to a remote URL.
 *
 * The GIFs are © Gym visual and live in the hasaneyldrm/exercises-dataset repository. They are
 * deliberately not bundled into the apk: 1,300+ GIFs would add tens of megabytes that every
 * athlete downloads and most never look at. Instead the path stored on the exercise
 * ("videos/0025-EIeI8Vf.gif") is turned into a URL on demand and cached by the image loader,
 * which keeps the animation only for the exercises that were actually opened.
 *
 * Attribution travels with every URL: see [ATTRIBUTION].
 */
object ExerciseMedia {

    private const val RAW_BASE = "https://raw.githubusercontent.com/hasaneyldrm/exercises-dataset/main/"

    /** Required by the dataset's media terms whenever a dataset GIF is shown. */
    const val ATTRIBUTION = "© Gym visual — https://gymvisual.com/"

    /**
     * @return the absolute GIF URL for [mediaPath], or null when the exercise has no dataset media
     * (custom exercises, and the curated weighted-calisthenics additions, which the dataset
     * simply does not cover).
     */
    fun gifUrl(mediaPath: String): String? {
        val path = mediaPath.trim()
        if (path.isEmpty()) return null
        if (!path.endsWith(".gif", ignoreCase = true)) return null
        return RAW_BASE + path.trimStart('/')
    }

    /** Whether this exercise has a dataset animation available to show. */
    fun hasDatasetMedia(mediaPath: String): Boolean = gifUrl(mediaPath) != null

    /**
     * Whether a media slot may be pointed at something the athlete picked from their gallery.
     *
     * Only for exercises they own. The shipped library is locked, so attaching a photo of the
     * athlete's own barbell setup to "Barbell Bench Press" would edit a dataset row, and would then
     * be silently overwritten by the next catalog reseed.
     */
    fun isUserOwnedMedia(isCustom: Boolean): Boolean = isCustom

    /**
     * Whether this stored uri looks like something we can load.
     *
     * Gallery picks come back as `content://` on modern Android and occasionally `file://` from
     * older pickers. Anything else (an http url someone pasted, a stray path) is rejected rather
     * than handed to the image loader, which would fail silently and leave a blank box.
     */
    fun isUsableLocalUri(uri: String): Boolean {
        val value = uri.trim()
        return value.startsWith("content://") || value.startsWith("file://")
    }

    /**
     * What to actually show for an exercise.
     *
     * A local pick always wins over the dataset animation: if the athlete recorded a video of the
     * exact variation they perform, that is the one they want to see, not the generic dataset loop.
     */
    enum class Source { LOCAL_VIDEO, LOCAL_IMAGE, DATASET_GIF, NONE }

    /**
     * @param localUri the athlete's own media, or "" when they have not attached any.
     * @param localIsVideo whether that pick is a video rather than a still image.
     */
    fun resolve(
        localUri: String,
        localIsVideo: Boolean,
        mediaPath: String,
        hasLocal: Boolean = isUsableLocalUri(localUri)
    ): Source = when {
        hasLocal && localIsVideo -> Source.LOCAL_VIDEO
        hasLocal -> Source.LOCAL_IMAGE
        hasDatasetMedia(mediaPath) -> Source.DATASET_GIF
        else -> Source.NONE
    }

    /** The value to hand the image loader, which accepts content uris and http urls alike. */
    fun model(localUri: String, localIsVideo: Boolean, mediaPath: String): Any? =
        when (resolve(localUri, localIsVideo, mediaPath)) {
            Source.LOCAL_VIDEO, Source.LOCAL_IMAGE -> localUri.trim()
            Source.DATASET_GIF -> gifUrl(mediaPath)
            Source.NONE -> null
        }

    /**
     * Whether the dataset credit has to be shown alongside this media.
     *
     * Only for dataset media. The athlete's own photo or video is theirs, and putting a Gym visual
     * credit on it would be both wrong and confusing.
     */
    fun needsAttribution(
        localUri: String,
        localIsVideo: Boolean,
        mediaPath: String
    ): Boolean = resolve(localUri, localIsVideo, mediaPath) == Source.DATASET_GIF
}
