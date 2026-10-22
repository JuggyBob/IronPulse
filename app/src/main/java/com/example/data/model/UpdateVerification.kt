package com.example.data.model

/**
 * The rules that decide whether a downloaded update may be installed.
 *
 * The rules live here, away from the screen and away from the view model, because they are rules
 * and not plumbing: a file that was downloaded is either an update of this app or it is not, and
 * there is one answer to that question.
 *
 * The convention is the one the name says: **null means there is no problem**. A caller that
 * reads the answer the other way round would offer a file that is not an update, so the answer is
 * named after what it holds and never after what it means.
 */
object UpdateVerification {

    /**
     * Why a downloaded file may not be installed, or null when it may.
     *
     * @param expectedDigest the digest GitHub published for the asset, or null when it published
     *        none. Without one there is nothing to compare the file against, and the file is
     *        judged on the package and the key it carries.
     * @param actualDigest the digest of the file that was downloaded, or null when it could not
     *        be calculated.
     * @param packageProblem why the file is not an installable build of this app, or null when it
     *        is. This covers the package name and the signing key.
     * @return null when the file may be installed, or the reason it may not.
     */
    fun problem(
        expectedDigest: String?,
        actualDigest: String?,
        packageProblem: String?
    ): String? {
        if (expectedDigest != null) {
            if (actualDigest == null) {
                return "The downloaded file could not be checked."
            }
            if (!actualDigest.equals(expectedDigest, ignoreCase = true)) {
                return "The downloaded file does not match the checksum GitHub published, so it was not used."
            }
        }
        if (packageProblem != null) {
            return packageProblem
        }
        return null
    }
}
