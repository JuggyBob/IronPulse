package com.example

import android.content.Context
import android.graphics.Bitmap
import coil.size.Size
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import coil.decode.BitmapFactoryDecoder
import coil.decode.Decoder
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.SourceResult
import coil.request.ImageRequest
import coil.request.Options
import com.example.util.IronPulseApp
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The exercise list must show a still frame, never a looping animation.
 *
 * This is the reported bug: with "Autoplay exercise demos" turned off, the detail dialog correctly
 * waited for a tap, but the exercise list kept animating. The list cannot honour a setting that
 * governs the detail view, because the setting is about what happens when you open an exercise; a
 * scrolling list has to be still by design instead.
 *
 * The mechanism pinned down here is decoder selection, because that is what decides whether an
 * animated drawable exists at all. An image loader with the animated decoders registered produces an
 * AnimatedImageDrawable for a GIF and Coil starts it; one without them falls through to Coil's
 * built-in BitmapFactoryDecoder, which decodes frame one and stops.
 *
 * Decoder selection is asserted rather than the rendered frame, because a screenshot cannot tell
 * frame one of a still from frame one of an animation: both look identical, which is exactly how this
 * bug survived a visual check.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StillFrameMediaTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * The animated loader, built the way the app builds it.
     *
     * `ImageLoaderFactory.newImageLoader()` is an instance method over `this`, and it calls
     * `applicationContext`, so it needs a real Application behind it. Robolectric's
     * `ApplicationProvider` returns one, so the app object is attached to it before use.
     */
    private fun decoderNames(loader: ImageLoader) = loader.components.decoderFactories.map { it::class.qualifiedName.orEmpty() }

    private fun animatedLoader(): ImageLoader =
        (context.applicationContext as IronPulseApp).newImageLoader()

    /** The name of the decoder a GIF would actually be handed to. */
    private fun decoderForGif(loader: ImageLoader): Decoder? {
        val request = ImageRequest.Builder(context).data("https://example.com/exercise.gif").build()
        val options = Options(
            context = request.context,
            config = Bitmap.Config.ARGB_8888,
            colorSpace = null,
            size = coil.size.Size(64, 64),
            scale = coil.size.Scale.FIT
        )
        return loader.components.newDecoder(
            SourceResult(ImageSource(GIF_BYTES, context), "image/gif", DataSource.MEMORY),
            options,
            loader
        )?.first
    }

    @Test
    fun `the main loader registers the animated decoders`() {
        val names = decoderNames(animatedLoader())

        assertTrue(
            "the exercise detail view has to animate, so ImageDecoderDecoder must be registered",
            names.any { it.startsWith(ImageDecoderDecoder.Factory::class.qualifiedName!!) }
        )
        assertTrue(
            "API 24 to 27 needs the Coil gif decoder instead",
            names.any { it.startsWith(GifDecoder.Factory::class.qualifiedName!!) }
        )
    }

    @Test
    fun `the main loader hands a gif to an animated decoder`() {
        val decoder = decoderForGif(animatedLoader())

        assertNotNull("a gif must be animated in the detail view", decoder)
        assertTrue(
            "it must go to a decoder that produces an animatable drawable",
            decoder is ImageDecoderDecoder || decoder is GifDecoder
        )
    }

    @Test
    fun `the still frame loader registers no animated decoder`() {
        val names = decoderNames(IronPulseApp.stillFrames(context))

        assertFalse(
            "an ImageDecoderDecoder would decode a GIF into an animatable drawable and Coil would " +
                "start playing it, which is the bug being fixed",
            names.any { it.startsWith(ImageDecoderDecoder.Factory::class.qualifiedName!!) }
        )
        assertFalse(
            "the Coil gif decoder animates on the same API range",
            names.any { it.startsWith(GifDecoder.Factory::class.qualifiedName!!) }
        )
    }

    @Test
    fun `the still frame loader falls through to the static bitmap decoder for a gif`() {
        val decoder = decoderForGif(IronPulseApp.stillFrames(context))

        assertNotNull("the still frame loader still has to decode the file", decoder)
        assertEquals(
            "BitmapFactoryDecoder returns frame one and stops, which is what makes it a still",
            BitmapFactoryDecoder::class.java,
            decoder!!::class.java
        )
    }

    @Test
    fun `the two loaders are separate instances so a still is never cached as an animation`() {
        val still = IronPulseApp.stillFrames(context)
        val animated = animatedLoader()

        // Separate registries is the fix; if the still loader were the animated one the request would
        // animate again, and if the animated loader were the still one nothing in the app would move.
        assertFalse(still === animated)
        assertFalse(decoderNames(still) == decoderNames(animated))
        // A second look must remember the choice rather than build a second animated loader: the
        // thumbnail path builds one loader per scrolling row, and a fresh one each time would leak.
        assertTrue(IronPulseApp.stillFrames(context) === still)
    }

    private companion object {
        /**
         * The six bytes that identify a GIF. Decoder selection only inspects the magic bytes, so a
         * real animation is not needed here and none has to be carried in the repository.
         */
        val GIF_BYTES: okio.Buffer = Buffer().write("GIF89a".toByteArray(Charsets.US_ASCII))
    }
}
