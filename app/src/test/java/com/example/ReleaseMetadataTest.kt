package com.example

import com.example.data.api.ReleaseApi
import com.example.data.model.AboutState
import com.example.data.model.GithubRelease
import com.example.data.model.ReleaseAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Reading the official releases, and picking the file to install.
 *
 * The answers used here are the shape the GitHub Releases API actually answers with, so a change
 * in the API shows up as a failing test rather than as an empty screen in the app.
 *
 * The runner is Robolectric because the release is read with the platform's JSON parser, which is
 * not a real implementation in a plain JVM test.
 */
@RunWith(RobolectricTestRunner::class)
class ReleaseMetadataTest {

    private val releaseJson = """
        {
          "name": "IronPulse 2.0.1",
          "tag_name": "v2.0.1",
          "body": "## What's new in 2.0.1`n`n- A check for updates.`n- An About sheet.",
          "html_url": "https://github.com/JuggyBob/IronPulse/releases/tag/v2.0.1",
          "published_at": "2026-10-22T09:00:00Z",
          "draft": false,
          "prerelease": false,
          "assets": [
            {
              "name": "IronPulse2.0.1-release.apk",
              "browser_download_url": "https://github.com/JuggyBob/IronPulse/releases/download/v2.0.1/IronPulse2.0.1-release.apk",
              "size": 20037403,
              "content_type": "application/vnd.android.package-archive",
              "digest": "sha256:cd6562d8c2c84837e582c88b7ea50be0d341d98449c572ee6fba0aeb2ff52792"
            },
            {
              "name": "IronPulse2.0.1-release.apk.sha256",
              "browser_download_url": "https://github.com/JuggyBob/IronPulse/releases/download/v2.0.1/IronPulse2.0.1-release.apk.sha256",
              "size": 92,
              "content_type": "application/octet-stream"
            }
          ]
        }
    """.trimIndent()

    // === Reading a release ===

    @Test
    fun `a release is read out of the api answer`() {
        val release = ReleaseApi.parseRelease(releaseJson)!!
        assertEquals("IronPulse 2.0.1", release.name)
        assertEquals("v2.0.1", release.tagName)
        assertEquals("2.0.1", release.versionText)
        assertEquals("https://github.com/JuggyBob/IronPulse/releases/tag/v2.0.1", release.htmlUrl)
        assertEquals(2, release.assets.size)
        assertFalse(release.isDraft)
        assertFalse(release.isPrerelease)
    }

    @Test
    fun `the release notes are read`() {
        val release = ReleaseApi.parseRelease(releaseJson)!!
        assertTrue(release.body.contains("A check for updates"))
    }

    @Test
    fun `the publication date is read`() {
        val release = ReleaseApi.parseRelease(releaseJson)!!
        assertNotNull(release.publishedAt)
        assertTrue(release.publishedAt!! > 1_700_000_000_000L)
    }

    @Test
    fun `the asset digest is read`() {
        val release = ReleaseApi.parseRelease(releaseJson)!!
        val apk = release.assets.first { it.name.endsWith(".apk") }
        assertEquals(
            "cd6562d8c2c84837e582c88b7ea50be0d341d98449c572ee6fba0aeb2ff52792",
            apk.sha256
        )
    }

    @Test
    fun `an answer that is not json is not read`() {
        assertNull(ReleaseApi.parseRelease("not json at all"))
        assertNull(ReleaseApi.parseRelease(""))
    }

    @Test
    fun `an answer without a tag is not read`() {
        assertNull(ReleaseApi.parseRelease("""{"name": "no tag"}"""))
    }

    @Test
    fun `a release with no notes is still a release`() {
        val release = ReleaseApi.parseRelease(
            """{"name": "IronPulse 2.0.1", "tag_name": "v2.0.1", "body": "", "html_url": "https://example.com"}"""
        )!!
        assertEquals("", release.body)
        assertTrue(release.assets.isEmpty())
    }

    @Test
    fun `a release is written and read back the same way`() {
        val release = ReleaseApi.parseRelease(releaseJson)!!
        val json = ReleaseApi.releaseToJson(release)!!
        val readBack = ReleaseApi.parseRelease(json)!!
        assertEquals(release.tagName, readBack.tagName)
        assertEquals(release.versionText, readBack.versionText)
        assertEquals(release.body, readBack.body)
        assertEquals(release.assets.size, readBack.assets.size)
        assertEquals(release.assets.first().sha256, readBack.assets.first().sha256)
    }

    // === Picking the file to install ===

    @Test
    fun `the release apk is the file that is installed`() {
        val release = ReleaseApi.parseRelease(releaseJson)!!
        val apk = release.installableApk()!!
        assertEquals("IronPulse2.0.1-release.apk", apk.name)
    }

    @Test
    fun `a checksum file is never the file that is installed`() {
        val release = ReleaseApi.parseRelease(releaseJson)!!
        assertFalse(release.installableApk()!!.name.endsWith(".sha256"))
    }

    @Test
    fun `a debug build is never the file that is installed`() {
        val release = releaseWith(
            "IronPulse2.0.1-debug.apk",
            "IronPulse2.0.1-release.apk"
        )
        assertEquals("IronPulse2.0.1-release.apk", release.installableApk()!!.name)
    }

    @Test
    fun `a source archive is never the file that is installed`() {
        val release = releaseWith("IronPulse-2.0.1-source.zip", "IronPulse2.0.1-release.apk")
        assertEquals("IronPulse2.0.1-release.apk", release.installableApk()!!.name)
    }

    @Test
    fun `the apk of the release version wins when several are published`() {
        val release = releaseWith(
            "IronPulse2.0.0-release.apk",
            "IronPulse2.0.1-release.apk"
        )
        assertEquals("IronPulse2.0.1-release.apk", release.installableApk()!!.name)
    }

    @Test
    fun `a release with no installable file has none`() {
        val release = releaseWith("IronPulse2.0.1-release.apk.sha256")
        assertNull(release.installableApk())
    }

    @Test
    fun `a release with no assets at all has none`() {
        val release = ReleaseApi.parseRelease(
            """{"name": "IronPulse 2.0.1", "tag_name": "v2.0.1", "body": "", "html_url": "https://example.com"}"""
        )!!
        assertNull(release.installableApk())
    }

    // === What the About screen says ===

    @Test
    fun `the about screen knows when there is an update`() {
        val state = AboutState(
            installedVersion = "2.0.0",
            latestRelease = ReleaseApi.parseRelease(releaseJson)!!
        )
        assertTrue(state.hasUpdate)
        assertEquals("2.0.1", state.latestVersion)
    }

    @Test
    fun `the about screen knows when there is not an update`() {
        val state = AboutState(
            installedVersion = "2.0.1",
            latestRelease = ReleaseApi.parseRelease(releaseJson)!!
        )
        assertFalse(state.hasUpdate)
    }

    @Test
    fun `an installed build that is ahead of the release is not offered a downgrade`() {
        val state = AboutState(
            installedVersion = "2.0.2",
            latestRelease = ReleaseApi.parseRelease(releaseJson)!!
        )
        assertFalse(state.hasUpdate)
    }

    @Test
    fun `an unreadable release version is never an update`() {
        val state = AboutState(
            installedVersion = "2.0.1",
            latestRelease = ReleaseApi.parseRelease("""{"tag_name": "nightly", "body": ""}""")!!
        )
        assertFalse(state.hasUpdate)
    }

    @Test
    fun `without a release there is nothing to offer`() {
        val state = AboutState(installedVersion = "2.0.1", latestRelease = null)
        assertFalse(state.hasUpdate)
        assertNull(state.latestVersion)
    }

    // === The official source ===

    @Test
    fun `the release endpoint is the official repository`() {
        val api = ReleaseApi()
        assertEquals(
            "https://api.github.com/repos/JuggyBob/IronPulse/releases/latest",
            api.latestReleaseUrl()
        )
        assertEquals(
            "https://github.com/JuggyBob/IronPulse/releases",
            api.releasesPageUrl()
        )
    }

    @Test
    fun `only the official release download is accepted`() {
        val api = ReleaseApi()
        assertTrue(
            api.isOfficialAssetUrl(
                "https://github.com/JuggyBob/IronPulse/releases/download/v2.0.1/IronPulse2.0.1-release.apk"
            )
        )
        // A link that is not the official release is never downloaded, whatever it says.
        assertFalse(api.isOfficialAssetUrl("http://github.com/JuggyBob/IronPulse/releases/download/v2.0.1/app.apk"))
        assertFalse(api.isOfficialAssetUrl("https://github.com/someone/else/releases/download/v2.0.1/app.apk"))
        assertFalse(api.isOfficialAssetUrl("https://example.com/IronPulse2.0.1-release.apk"))
        assertFalse(api.isOfficialAssetUrl("https://github.com/JuggyBob/IronPulse/issues/1"))
    }

    private fun releaseWith(vararg names: String): GithubRelease = ReleaseApi.parseRelease(
        """
        {
          "name": "IronPulse 2.0.1",
          "tag_name": "v2.0.1",
          "body": "",
          "html_url": "https://github.com/JuggyBob/IronPulse/releases/tag/v2.0.1",
          "assets": [
            ${names.joinToString(",") { name ->
                """{"name": "$name", "browser_download_url": "https://github.com/JuggyBob/IronPulse/releases/download/v2.0.1/$name", "size": 1}"""
            }}
          ]
        }
        """.trimIndent()
    )!!
}
