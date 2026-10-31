package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.util.WidgetPhotoStore
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Home photo widget keeps its own copy of the picked picture.
 *
 * These tests pin the parts that protect the athlete's data: the copy is cropped to the card shape,
 * a write is only committed through a staging file, and cleanup can only ever remove files inside
 * the widget photo directory.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class WidgetPhotoStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clean() {
        WidgetPhotoStore.deleteAll(context)
    }

    @After
    fun tearDown() {
        WidgetPhotoStore.deleteAll(context)
    }

    private fun bitmap(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }

    @Test
    fun `a wide picture is centre cropped to sixteen by nine`() {
        val source = bitmap(400, 100)
        val cropped = WidgetPhotoStore.cropToAspect(source, 16f / 9f)
        assertEquals(177, cropped.width)
        assertEquals(100, cropped.height)
        assertNotEquals(source, cropped)
        source.recycle()
        if (cropped !== source) cropped.recycle()
    }

    @Test
    fun `a tall picture is centre cropped to sixteen by nine`() {
        val source = bitmap(100, 400)
        val cropped = WidgetPhotoStore.cropToAspect(source, 16f / 9f)
        assertEquals(100, cropped.width)
        assertEquals(56, cropped.height)
        source.recycle()
        if (cropped !== source) cropped.recycle()
    }

    @Test
    fun `a picture that already has the card shape is returned unchanged`() {
        val source = bitmap(160, 90)
        assertSame(source, WidgetPhotoStore.cropToAspect(source, 16f / 9f))
        source.recycle()
    }

    @Test
    fun `a written photo is usable and lives in the widget directory`() {
        val source = bitmap(160, 90)
        val path = WidgetPhotoStore.write(context, source)
        source.recycle()
        assertTrue(path != null)
        assertTrue(WidgetPhotoStore.isUsable(path!!))
        val file = File(path)
        assertTrue(file.isFile)
        assertTrue(file.length() > 0)
        assertEquals("home_widget_media", file.parentFile?.name)
    }

    @Test
    fun `a blank or missing path is not usable`() {
        assertFalse(WidgetPhotoStore.isUsable(""))
        assertFalse(WidgetPhotoStore.isUsable(File(context.cacheDir, "absent.jpg").absolutePath))
    }

    @Test
    fun `cleanup keeps the referenced photo and removes everything else`() {
        val keepSource = bitmap(160, 90)
        val dropSource = bitmap(160, 90)
        val keep = WidgetPhotoStore.write(context, keepSource)!!
        val drop = WidgetPhotoStore.write(context, dropSource)!!
        keepSource.recycle()
        dropSource.recycle()

        WidgetPhotoStore.deleteUnreferenced(context, setOf(keep))

        assertTrue(File(keep).isFile)
        assertFalse(File(drop).exists())
    }

    @Test
    fun `cleanup with an empty reference set removes every widget photo`() {
        val source = bitmap(160, 90)
        val path = WidgetPhotoStore.write(context, source)!!
        source.recycle()

        WidgetPhotoStore.deleteUnreferenced(context, emptySet())

        assertFalse(File(path).exists())
    }

    @Test
    fun `deleting a path outside the widget directory is refused`() {
        val outside = File(context.cacheDir, "not_a_widget_photo.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        WidgetPhotoStore.delete(context, outside.absolutePath)
        assertTrue(outside.isFile)
        outside.delete()
    }

    @Test
    fun `a successful write commits through the staging file and leaves no temp file`() {
        val source = bitmap(1, 1)
        val path = WidgetPhotoStore.write(context, source)
        source.recycle()
        assertTrue(path != null)
        val folder = File(context.filesDir, "home_widget_media")
        assertFalse(folder.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
    }
}
