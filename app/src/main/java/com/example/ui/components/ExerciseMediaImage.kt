package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import com.example.data.model.ExerciseMedia
import com.example.util.IronPulseApp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

/**
 * The animation for an exercise.
 *
 * Three sources, in order of preference (see [ExerciseMedia.resolve]):
 *  - a video the athlete picked from their own gallery, which is the exact variation they perform,
 *  - a still image they picked,
 *  - the dataset GIF, streamed and cached (see [com.example.util.IronPulseApp]).
 *
 * Nothing is bundled in the apk.
 *
 * @param autoplay when false the media stays on a play affordance until tapped, which is what the
 * "Autoplay exercise demos" setting drives. Dataset GIFs can be paused by Coil alone; a picked video
 * has to be stopped and rebuilt, because ExoPlayer does not idle mid playback on its own.
 */
@Composable
fun ExerciseGif(
    localUri: String,
    localIsVideo: Boolean,
    mediaPath: String,
    autoplay: Boolean,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    showAttribution: Boolean = true,
    cornerRadius: Int = 12
) {
    val source = ExerciseMedia.resolve(localUri, localIsVideo, mediaPath)
    var started by remember(source) { mutableStateOf(autoplay) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(Color.Black.copy(alpha = 0.25f))
            .testTag("exercise_gif"),
        contentAlignment = Alignment.Center
    ) {
        val model = ExerciseMedia.model(localUri, localIsVideo, mediaPath)

        when {
            model == null -> ExerciseGifPlaceholder(modifier = Modifier.fillMaxSize())

            // A picked video has to be played by a real video player: Coil will decode its first
            // frame but has no way to advance through it.
            source == ExerciseMedia.Source.LOCAL_VIDEO && started -> {
                val uri = localUri.trim()
                AndroidView(
                    factory = { context ->
                        val player = ExoPlayer.Builder(context).build().apply {
                            setMediaItem(MediaItem.fromUri(uri))
                            repeatMode = ExoPlayer.REPEAT_MODE_ONE
                            playWhenReady = true
                            prepare()
                        }
                        PlayerView(context).apply {
                            this.player = player
                            useController = true
                            // The app draws its own controls and chrome around this block.
                            controllerAutoShow = false
                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        }
                    },
                    update = { view ->
                        view.player?.playWhenReady = true
                    },
                    onRelease = { view ->
                        // Released with the composable, otherwise the decoder keeps the file handle
                        // and the audio focus after the dialog is dismissed.
                        view.player?.release()
                        view.player = null
                    },
                    modifier = Modifier.fillMaxSize().testTag("exercise_local_video")
                )
            }

            source == ExerciseMedia.Source.LOCAL_VIDEO -> PlayOverlay(
                label = "Play your video",
                modifier = Modifier.clickableSurface { started = true }
            )

            started -> {
                // Coil renders animated GIFs and WebP through the decoder registered in
                // IronPulseApp; without it these would show a single still frame.
                SubcomposeAsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(model)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = "Animation of this exercise",
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                    filterQuality = FilterQuality.Low,
                    loading = { ExerciseGifLoading(modifier = Modifier.fillMaxSize()) },
                    error = { ExerciseGifUnavailable(modifier = Modifier.fillMaxSize()) }
                )
            }

            else -> PlayOverlay(
                label = "Play",
                modifier = Modifier.clickableSurface { started = true }
            )
        }

        if (showAttribution && ExerciseMedia.needsAttribution(localUri, localIsVideo, mediaPath)) {
            // The credit sits on a full width scrim rather than floating over the animation: the
            // dataset's frames are mostly light, so bare light text disappears into them.
            Text(
                text = ExerciseMedia.ATTRIBUTION,
                style = MaterialTheme.typography.labelSmall,
                color = TextWhite,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("exercise_gif_attribution")
            )
        }
    }
}

/**
 * The tap target that starts a paused demo. It covers the whole media block rather than being a small
 * button, because the whole point is "tap the animation to make it move".
 */
@Composable
private fun PlayOverlay(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.PlayCircleFilled,
                contentDescription = null,
                tint = TextWhite.copy(alpha = 0.92f),
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = TextWhite,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

private fun Modifier.clickableSurface(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

/**
 * A compact media slot for an exercise list row.
 *
 * Square by default so rows stay uniform whether or not an exercise has media, and it takes the
 * caller's size when one is given. It deliberately carries no attribution and no play affordance:
 * a 56dp row has no room for either, and the row's own tap opens the detail, which does have both.
 */
@Composable
fun ExerciseGifThumbnail(
    localUri: String,
    localIsVideo: Boolean,
    mediaPath: String,
    modifier: Modifier = Modifier
) {
    val source = ExerciseMedia.resolve(localUri, localIsVideo, mediaPath)
    val hasMedia = source != ExerciseMedia.Source.NONE
    // A video has no useful poster frame here: fetching one through an image loader would try to
    // decode the video file as a bitmap and show nothing. The play badge already says what it is.
    val skipFetch = source == ExerciseMedia.Source.LOCAL_VIDEO

    Box(
        modifier = modifier
            .size(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ElectricCyan.copy(alpha = 0.05f))
            .testTag("exercise_gif_thumbnail"),
        contentAlignment = Alignment.Center
    ) {
        if (hasMedia && !skipFetch) {
            // Still frames only, always, regardless of the autoplay setting.
            //
            // The still-frame loader is the whole point: the animation belongs to the detail view,
            // where the athlete asked to see the exercise. A list of hundreds of rows would otherwise
            // decode and run an animated drawable per visible row, which is the reported bug and a
            // cost the autoplay setting cannot express, because that setting governs the detail view.
            //
            // See IronPulseApp.stillFrames for why a different loader achieves a static first frame.
            SubcomposeAsyncImage(
                model = ExerciseMedia.model(localUri, localIsVideo, mediaPath),
                imageLoader = IronPulseApp.stillFrames(LocalContext.current.applicationContext),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                filterQuality = FilterQuality.Low,
                loading = { ExerciseGifLoading(modifier = Modifier.fillMaxSize()) },
                error = { ExerciseGifLoading(modifier = Modifier.fillMaxSize()) }
            )
        }

        if (source == ExerciseMedia.Source.LOCAL_VIDEO) {
            // The one badge this block has room for: a video cannot be previewed as a still frame.
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = null,
                tint = TextWhite,
                modifier = Modifier
                    .size(22.dp)
                    .align(Alignment.Center)
            )
        } else if (!hasMedia) {
            // Never fetch anything: the path is blank, so there is no request to make.
            ExerciseGifLoading(modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * Shown for exercises with no media at all: the user's own exercises with nothing attached, and the
 * curated weighted-calisthenics additions the dataset does not cover.
 */
@Composable
private fun ExerciseGifPlaceholder(modifier: Modifier = Modifier) {
    val pulse = pulseAlpha()

    Box(
        modifier = modifier.background(ElectricCyan.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.FitnessCenter,
                contentDescription = null,
                tint = ElectricCyan.copy(alpha = pulse),
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = "No animation",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * While media downloads. A static shimmer rather than a spinner, because the block is already
 * sized: the point is only to say something is arriving, not to imply progress.
 */
@Composable
private fun ExerciseGifLoading(modifier: Modifier = Modifier) {
    val pulse = pulseAlpha()
    Box(
        modifier = modifier.background(ElectricCyan.copy(alpha = 0.04f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.FitnessCenter,
            contentDescription = "Loading animation",
            tint = ElectricCyan.copy(alpha = pulse * 0.6f),
            modifier = Modifier.size(24.dp)
        )
    }
}

/** Shown when a media download failed, so the athlete is told rather than left with a blank block. */
@Composable
private fun ExerciseGifUnavailable(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(ElectricCyan.copy(alpha = 0.05f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "Could not load animation",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** Shared slow pulse for the placeholder and loading states. */
@Composable
private fun pulseAlpha(min: Float = 0.45f, max: Float = 0.8f): Float {
    val transition = rememberInfiniteTransition(label = "mediaPulse")
    val value by transition.animateFloat(
        initialValue = min,
        targetValue = max,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mediaAlpha"
    )
    return value
}
