package com.example

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.util.ApkInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Handing a downloaded update to Android.
 *
 * The app does not install anything itself: it checks that the file is an installable build of
 * this app, and opens the system installer. These tests cover the part the app is responsible for.
 */
@RunWith(RobolectricTestRunner::class)
class ApkInstallerTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `the file provider authority belongs to this app`() {
        assertEquals("${context.packageName}.fileprovider", ApkInstaller.authority(context))
    }

    @Test
    fun `an update is written into the app's own storage`() {
        val file = ApkInstaller.updateFile(context, "IronPulse2.0.1-release.apk")
        assertTrue(file.absolutePath.startsWith(context.filesDir.absolutePath))
        assertTrue(file.name.endsWith(".apk"))
    }

    @Test
    fun `a file that is not there is not an update`() {
        val missing = File(context.filesDir, "updates/does-not-exist.apk")
        val result = ApkInstaller.verify(context, missing)
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull()?.message)
    }

    @Test
    fun `a file that is empty is not an update`() {
        val empty = ApkInstaller.updateFile(context, "empty.apk")
        empty.parentFile?.mkdirs()
        empty.writeText("")
        val result = ApkInstaller.verify(context, empty)
        assertTrue(result.isFailure)
    }

    @Test
    fun `a file that is not a package is not an update`() {
        val notAnApk = ApkInstaller.updateFile(context, "not-an-apk.apk")
        notAnApk.parentFile?.mkdirs()
        notAnApk.writeText("this is not an android package")
        val result = ApkInstaller.verify(context, notAnApk)
        assertTrue(result.isFailure)
    }

    @Test
    fun `the install intent asks the system installer`() {
        // The content uri is built by the platform's file provider, which matches its roots with
        // the platform separator and therefore only works where that separator is a slash. The
        // intent is the part the app owns, so that is what is checked here, and the provider
        // declaration is checked separately.
        val intent = ApkInstaller.installIntentFor(
            Uri.parse("content://com.aistudio.fitness.ironpulse.fileprovider/updates/app.apk")
        )
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("application/vnd.android.package-archive", intent.type)
        // The file is shared as a content uri, never as a path.
        assertEquals("content", intent.data?.scheme)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `the file provider is declared for this app`() {
        // Without this declaration the installer would be handed a uri nothing can open.
        val info = context.packageManager.resolveContentProvider(
            ApkInstaller.authority(context),
            0
        )
        assertNotNull(info)
        assertNotNull(info!!.metaData?.getInt("android.support.FILE_PROVIDER_PATHS"))
    }

    @Test
    fun `the downloads are cleaned up`() {
        val file = ApkInstaller.updateFile(context, "IronPulse2.0.1-release.apk")
        file.parentFile?.mkdirs()
        file.writeText("placeholder")

        ApkInstaller.clearDownloads(context)
        assertFalse(file.exists())
    }
}
