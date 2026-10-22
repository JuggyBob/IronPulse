package com.example

import com.example.data.model.AppVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading and comparing versions.
 *
 * The version of the running build and the version of a published release are compared here, so a
 * string comparison can never offer a downgrade or hide a real update.
 */
class AppVersionTest {

    @Test
    fun `a plain version is read as its numbers`() {
        val version = AppVersion.parse("2.0.1")!!
        assertEquals(listOf(2, 0, 1), version.numbers)
        assertNull(version.prerelease)
        assertEquals("2.0.1", version.text)
    }

    @Test
    fun `a leading v is not part of the version`() {
        val version = AppVersion.parse("v2.0.1")!!
        assertEquals(listOf(2, 0, 1), version.numbers)
        assertEquals("2.0.1", version.text)
    }

    @Test
    fun `a capital v is not part of the version either`() {
        assertEquals(listOf(2, 0, 1), AppVersion.parse("V2.0.1")!!.numbers)
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals(listOf(2, 0, 1), AppVersion.parse("  2.0.1  ")!!.numbers)
    }

    @Test
    fun `a version with fewer numbers is padded with zeros`() {
        // 2.0 and 2.0.0 are the same version.
        assertEquals(0, AppVersion.parse("2.0")!!.compareTo(AppVersion.parse("2.0.0")!!))
        assertEquals(0, AppVersion.parse("2")!!.compareTo(AppVersion.parse("2.0.0")!!))
    }

    @Test
    fun `a multi digit number is larger than the one before it`() {
        // The comparison that a string comparison gets wrong.
        assertTrue(AppVersion.parse("2.0.10")!!.isNewerThan(AppVersion.parse("2.0.9")!!))
        assertTrue(AppVersion.parse("0.0.13")!!.isNewerThan(AppVersion.parse("0.0.12")!!))
        assertTrue(AppVersion.parse("10.0.0")!!.isNewerThan(AppVersion.parse("9.9.9")!!))
    }

    @Test
    fun `the same version is not newer than itself`() {
        assertFalse(AppVersion.parse("2.0.1")!!.isNewerThan(AppVersion.parse("2.0.1")!!))
        assertFalse(AppVersion.parse("2.0.1")!!.isNewerThan(AppVersion.parse("v2.0.1")!!))
    }

    @Test
    fun `an older version is not newer`() {
        assertFalse(AppVersion.parse("2.0.0")!!.isNewerThan(AppVersion.parse("2.0.1")!!))
        assertFalse(AppVersion.parse("1.9.9")!!.isNewerThan(AppVersion.parse("2.0.0")!!))
    }

    @Test
    fun `a prerelease is older than the finished version`() {
        val beta = AppVersion.parse("2.1.0-beta.1")!!
        val finished = AppVersion.parse("2.1.0")!!
        assertEquals("beta.1", beta.prerelease)
        assertTrue(finished.isNewerThan(beta))
        assertFalse(beta.isNewerThan(finished))
    }

    @Test
    fun `two prereleases of the same version are ordered`() {
        val first = AppVersion.parse("2.1.0-beta.1")!!
        val second = AppVersion.parse("2.1.0-beta.2")!!
        assertTrue(second.isNewerThan(first))
        assertTrue(first.isNewerThan(AppVersion.parse("2.1.0-alpha.9")!!))
    }

    @Test
    fun `a build number after a plus is a prerelease marker too`() {
        val version = AppVersion.parse("2.0.1+build.7")!!
        assertEquals(listOf(2, 0, 1), version.numbers)
        assertEquals("build.7", version.prerelease)
    }

    @Test
    fun `a version that is not a version is not read`() {
        assertNull(AppVersion.parse(null))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("   "))
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse("abc"))
        assertNull(AppVersion.parse("v"))
    }

    @Test
    fun `a version with an empty component is not read`() {
        assertNull(AppVersion.parse("2..1"))
        assertNull(AppVersion.parse("2.0."))
    }

    @Test
    fun `the versions are ordered the way a version is ordered`() {
        val ordered = listOf("0.0.9", "0.0.10", "0.1.0", "1.0.0", "2.0.0", "2.0.1", "10.0.0")
        val parsed = ordered.map { AppVersion.parse(it)!! }
        for (index in 0 until parsed.size - 1) {
            assertTrue(
                "${parsed[index].text} should be older than ${parsed[index + 1].text}",
                parsed[index + 1].isNewerThan(parsed[index])
            )
        }
    }
}
