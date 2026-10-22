package com.example

import com.example.data.model.UpdateVerification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that decide whether a downloaded update may be installed.
 *
 * The answer is null when there is no problem, and the reason when there is. These tests pin that
 * convention, because a caller that reads it the other way round would offer a file that is not
 * an update of the app.
 */
class UpdateVerificationTest {

    @Test
    fun `a file that matches everything may be installed`() {
        assertNull(
            UpdateVerification.problem(
                expectedDigest = "abc123",
                actualDigest = "abc123",
                packageProblem = null
            )
        )
    }

    @Test
    fun `a file with no published digest is judged on its package alone`() {
        assertNull(
            UpdateVerification.problem(
                expectedDigest = null,
                actualDigest = null,
                packageProblem = null
            )
        )
    }

    @Test
    fun `a file that does not match the published checksum may not be installed`() {
        val problem = UpdateVerification.problem(
            expectedDigest = "abc123",
            actualDigest = "def456",
            packageProblem = null
        )
        assertNotNull(problem)
        assertTrue(problem!!.contains("does not match the checksum"))
    }

    @Test
    fun `the checksum is compared without caring about the case`() {
        assertNull(
            UpdateVerification.problem(
                expectedDigest = "ABC123",
                actualDigest = "abc123",
                packageProblem = null
            )
        )
    }

    @Test
    fun `a file whose checksum could not be calculated may not be installed`() {
        val problem = UpdateVerification.problem(
            expectedDigest = "abc123",
            actualDigest = null,
            packageProblem = null
        )
        assertNotNull(problem)
        assertTrue(problem!!.contains("could not be checked"))
    }

    @Test
    fun `a file that is not an update of the app may not be installed`() {
        val problem = UpdateVerification.problem(
            expectedDigest = "abc123",
            actualDigest = "abc123",
            packageProblem = "The downloaded file is a different app, so it was not installed."
        )
        assertEquals("The downloaded file is a different app, so it was not installed.", problem)
    }

    @Test
    fun `a file that is signed with another key may not be installed`() {
        val problem = UpdateVerification.problem(
            expectedDigest = null,
            actualDigest = null,
            packageProblem = "The downloaded file is not signed with the same key, so it was not installed."
        )
        assertNotNull(problem)
        assertTrue(problem!!.contains("not signed with the same key"))
    }

    @Test
    fun `a checksum that does not match is reported before the package is looked at`() {
        // The checksum is the cheaper check, and a file that does not match it is not the file
        // that was published, whatever it contains.
        val problem = UpdateVerification.problem(
            expectedDigest = "abc123",
            actualDigest = "def456",
            packageProblem = "The downloaded file is a different app, so it was not installed."
        )
        assertTrue(problem!!.contains("does not match the checksum"))
    }
}
