/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.LauncherIconOption
import com.example.util.LauncherIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LauncherIconsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `every icon key maps to a stable option and an unknown key falls back to default`() {
        assertEquals(LauncherIconOption.CYAN, LauncherIconOption.fromKey("cyan"))
        assertEquals(LauncherIconOption.AMBER, LauncherIconOption.fromKey(" AMBER "))
        assertEquals(LauncherIconOption.DEFAULT, LauncherIconOption.fromKey("does_not_exist"))
        assertEquals(LauncherIconOption.DEFAULT, LauncherIconOption.fromKey(null))
    }

    @Test
    fun `applying an option leaves exactly one enabled launcher alias`() {
        LauncherIconOption.all.forEach { option ->
            LauncherIcons.apply(context, option)
            assertEquals(
                "only ${option.aliasName} should be an active launcher component",
                listOf(option.aliasName),
                LauncherIcons.enabledAliases(context)
            )
            assertFalse(
                "the bootstrap launcher must disappear after the app starts",
                LauncherIcons.bootstrapEnabled(context)
            )
        }
    }
}
