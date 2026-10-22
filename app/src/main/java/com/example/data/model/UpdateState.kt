package com.example.data.model

import java.io.File

/**
 * What the update check is doing.
 *
 * The states are the ones the athlete can act on, so the interface never has to guess: every state
 * says what happened and what can be done next.
 */
sealed interface UpdateState {

    /** Nothing has been asked for yet. */
    data object Idle : UpdateState

    /** The release is being looked up. */
    data object Checking : UpdateState

    /** The installed build is the newest one there is. */
    data class UpToDate(val release: GithubRelease?) : UpdateState

    /**
     * A newer release exists.
     *
     * @param asset the installable APK, or null when the release does not carry one.
     */
    data class Available(val release: GithubRelease, val asset: ReleaseAsset?) : UpdateState

    /** The APK is being downloaded. */
    data class Downloading(
        val release: GithubRelease,
        val asset: ReleaseAsset,
        val bytesRead: Long,
        val totalBytes: Long
    ) : UpdateState

    /**
     * The APK is downloaded and checked, and is ready to be installed.
     *
     * @param file the downloaded APK, in the app's own storage.
     */
    data class ReadyToInstall(val release: GithubRelease, val file: File) : UpdateState

    /**
     * The check or the download failed.
     *
     * @param message what to tell the athlete, in words.
     */
    data class Failed(val message: String, val canRetry: Boolean = true) : UpdateState

    /** The check was cancelled before it finished. */
    data object Cancelled : UpdateState
}

/**
 * The release information the About screen shows.
 */
data class AboutState(
    /** The version of the build that is running, e.g. `2.0.1`. */
    val installedVersion: String,
    /** The newest published release, when it could be read. */
    val latestRelease: GithubRelease? = null,
    /** Whether the release information is being fetched. */
    val isLoading: Boolean = false,
    /** Why the release information could not be fetched, when it could not. */
    val error: String? = null,
    /** Whether the release information came from the cache rather than from GitHub. */
    val isFromCache: Boolean = false
) {
    /** The version of the newest release, when there is one. */
    val latestVersion: String? get() = latestRelease?.versionText

    /** Whether the newest release is newer than the installed one. */
    val hasUpdate: Boolean
        get() {
            val latest = latestRelease?.version ?: return false
            val installed = AppVersion.parse(installedVersion) ?: return false
            return latest.isNewerThan(installed)
        }
}
