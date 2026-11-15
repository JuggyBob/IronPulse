package com.example

import com.example.data.model.ExerciseMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The list must use the dataset's small still image, never the animation. */
class ExerciseMediaThumbnailTest {

    @Test
    fun `dataset thumbnail is derived from the animation path`() {
        assertEquals(
            "https://raw.githubusercontent.com/hasaneyldrm/exercises-dataset/main/images/0025-EIeI8Vf.jpg",
            ExerciseMedia.thumbnailUrl("videos/0025-EIeI8Vf.gif")
        )
        assertEquals(
            "https://raw.githubusercontent.com/hasaneyldrm/exercises-dataset/main/images/0025-EIeI8Vf.jpg",
            ExerciseMedia.thumbnailUrl("/videos/0025-EIeI8Vf.gif")
        )
    }

    @Test
    fun `unknown or empty paths do not produce a thumbnail`() {
        assertNull(ExerciseMedia.thumbnailUrl(""))
        assertNull(ExerciseMedia.thumbnailUrl("images/0025-EIeI8Vf.jpg"))
        assertNull(ExerciseMedia.thumbnailUrl("custom/0025.gif"))
        assertNull(ExerciseMedia.thumbnailUrl("videos/0025-EIeI8Vf.mp4"))
    }

    @Test
    fun `local still wins, local video has no poster, dataset falls back to the still`() {
        assertEquals(
            "content://media/external/images/1",
            ExerciseMedia.thumbnailModel("content://media/external/images/1", false, "videos/0025-EIeI8Vf.gif")
        )
        assertNull(ExerciseMedia.thumbnailModel("content://media/external/video/1", true, "videos/0025-EIeI8Vf.gif"))
        assertEquals(
            "https://raw.githubusercontent.com/hasaneyldrm/exercises-dataset/main/images/0025-EIeI8Vf.jpg",
            ExerciseMedia.thumbnailModel("", false, "videos/0025-EIeI8Vf.gif")
        )
        assertNull(ExerciseMedia.thumbnailModel("", false, ""))
    }
}
