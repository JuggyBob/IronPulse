package com.example.data.model

/**
 * One file attached to a GitHub release.
 */
data class ReleaseAsset(
    /** The file name, e.g. `IronPulse2.0.0-release.apk`. */
    val name: String,
    /** Where the file is downloaded from. Always an https GitHub URL. */
    val downloadUrl: String,
    /** The size of the file in bytes, when GitHub reports it. */
    val size: Long = 0L,
    /** The content type GitHub reports, e.g. `application/vnd.android.package-archive`. */
    val contentType: String? = null,
    /**
     * The digest GitHub publishes for the asset, e.g. `sha256:1a2b...`.
     *
     * Null when GitHub does not publish one. The download is then checked with the measures that
     * are always available, and the app never claims an integrity check it did not make.
     */
    val digest: String? = null
) {
    /** The digest as a plain hex string, when there is a sha256 one. */
    val sha256: String?
        get() = digest?.takeIf { it.startsWith("sha256:", ignoreCase = true) }?.substringAfter(':')?.lowercase()
}

/**
 * A published GitHub release.
 */
data class GithubRelease(
    /** The name of the release, e.g. `IronPulse 2.0.0`. */
    val name: String,
    /** The tag the release was cut from, e.g. `v2.0.0`. */
    val tagName: String,
    /** The version, read from the tag. Null when the tag is not a version. */
    val version: AppVersion?,
    /** The release notes, as the maintainer wrote them. */
    val body: String,
    /** When the release was published, in milliseconds since the epoch. */
    val publishedAt: Long?,
    /** The page a person can read the release on. */
    val htmlUrl: String,
    /** The files attached to the release. */
    val assets: List<ReleaseAsset> = emptyList(),
    val isPrerelease: Boolean = false,
    val isDraft: Boolean = false
) {
    /** The version as it is written, e.g. `2.0.0`. */
    val versionText: String
        get() = version?.text ?: tagName.removePrefix("v").removePrefix("V")

    /**
     * The installable release APK, or null when the release does not carry one.
     *
     * The rule is the project's own release convention: the file is called
     * `IronPulse<version>-release.apk`. A debug build, a checksum file, a source archive and an
     * Android App Bundle are never installable updates, so they are never picked.
     *
     * When the convention does not match, any other release APK is accepted rather than nothing,
     * but only after the exact name has been looked for first: a release can carry more than one
     * APK and the right one is the one that belongs to this version.
     */
    fun installableApk(): ReleaseAsset? {
        val apks = assets.filter { isReleaseApk(it.name) }
        if (apks.isEmpty()) return null

        val expected = versionText
        apks.firstOrNull { asset ->
            val name = asset.name.removePrefix("IronPulse").removeSuffix("-release.apk")
            name.equals(expected, ignoreCase = true) || name.equals("v$expected", ignoreCase = true)
        }?.let { return it }

        // More than one release APK and none of them matches the version: take the first that is
        // not a debug build, which the filter above already guarantees.
        return apks.first()
    }

    companion object {
        /** Whether the file name is an installable release APK. */
        fun isReleaseApk(name: String): Boolean {
            val lower = name.lowercase()
            if (!lower.endsWith(".apk")) return false
            // A debug build is not an update of the published app.
            if (lower.contains("debug")) return false
            // A checksum file or a source archive is not an APK to install.
            if (lower.endsWith(".apk.sha256") || lower.endsWith(".apk.txt")) return false
            return true
        }
    }
}
