/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.viewmodel

import android.app.Application
import com.example.BuildConfig
import com.example.data.api.ReleaseApi
import com.example.data.model.AboutState
import com.example.data.model.AppVersion
import com.example.data.model.GithubRelease
import com.example.data.model.ReleaseAsset
import com.example.data.model.UpdateState
import com.example.data.model.UpdateVerification
import com.example.data.preferences.UserPreferencesRepository
import com.example.util.ApkInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * Owns the GitHub release lookup, download/verification flow and About-page release metadata.
 *
 * The old public surface remains on [MainViewModel]; this component replaces the central
 * ViewModel's implementation without changing state types or timing.
 */
class AppUpdateController(
    private val application: Application,
    private val preferences: UserPreferencesRepository,
    private val scope: CoroutineScope
) {
    private val releaseApi = ReleaseApi()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _startupUpdate = MutableStateFlow<GithubRelease?>(null)
    val startupUpdate: StateFlow<GithubRelease?> = _startupUpdate.asStateFlow()

    private val _aboutState = MutableStateFlow(
        AboutState(installedVersion = BuildConfig.VERSION_NAME)
    )
    val aboutState: StateFlow<AboutState> = _aboutState.asStateFlow()

    private var updateJob: Job? = null

    /**
     * Looks for a newer release when the app starts, unless the athlete turned that off or already
     * asked not to be reminded for this installed version.
     */
    suspend fun maybeCheckForUpdatesOnStart() {
        if (!preferences.autoCheckUpdatesOnStartFlow.first()) return
        val dismissedFor = preferences.dismissedUpdatePromptVersionFlow.first()
        if (dismissedFor == BuildConfig.VERSION_NAME) return

        releaseApi.fetchLatestRelease()
            .onSuccess { release ->
                cacheRelease(release)
                val state = evaluateRelease(release)
                if (state is UpdateState.Available) {
                    if (_updateState.value is UpdateState.Idle) {
                        _updateState.value = state
                    }
                    _startupUpdate.value = release
                }
            }
    }

    /**
     * Closes the startup update prompt. Closing without [dontShowAgain] is not remembered.
     */
    fun closeStartupUpdatePrompt(dontShowAgain: Boolean = false) {
        if (dontShowAgain) {
            scope.launch(Dispatchers.IO) {
                preferences.dismissUpdatePromptForVersion(BuildConfig.VERSION_NAME)
            }
        }
        _startupUpdate.value = null
    }

    fun checkForUpdates() {
        updateJob?.cancel()
        updateJob = scope.launch {
            _updateState.value = UpdateState.Checking
            releaseApi.fetchLatestRelease()
                .onSuccess { release ->
                    cacheRelease(release)
                    _updateState.value = evaluateRelease(release)
                }
                .onFailure { error ->
                    _updateState.value = UpdateState.Failed(
                        error.message ?: "The update could not be checked."
                    )
                }
        }
    }

    fun cancelUpdate() {
        updateJob?.cancel()
        updateJob = null
        _updateState.value = UpdateState.Cancelled
    }

    fun dismissUpdate() {
        updateJob?.cancel()
        updateJob = null
        _updateState.value = UpdateState.Idle
    }

    fun downloadUpdate() {
        val available = _updateState.value as? UpdateState.Available
            ?: _startupUpdate.value?.let { release ->
                release.installableApk()?.let { UpdateState.Available(release, it) }
            }
            ?: return
        val asset = available.asset ?: return
        updateJob?.cancel()
        updateJob = scope.launch {
            _updateState.value = UpdateState.Downloading(available.release, asset, 0L, asset.size)
            val target = ApkInstaller.updateFile(application, asset.name)
            val lastPercent = intArrayOf(-1)
            releaseApi.downloadAsset(asset, target) { read, total ->
                val percent = if (total > 0L) ((read * 100L) / total).toInt() else -1
                if (percent != lastPercent[0]) {
                    lastPercent[0] = percent
                    _updateState.value = UpdateState.Downloading(available.release, asset, read, total)
                }
            }.onSuccess { file ->
                val problem = verifyDownload(asset, file)
                if (problem == null) {
                    _updateState.value = UpdateState.ReadyToInstall(available.release, file)
                } else {
                    file.delete()
                    _updateState.value = UpdateState.Failed(problem)
                }
            }.onFailure { error ->
                _updateState.value = UpdateState.Failed(
                    error.message ?: "The download did not finish."
                )
            }
        }
    }

    private fun verifyDownload(asset: ReleaseAsset, file: File): String? {
        val packageProblem = ApkInstaller.verify(application, file)
            .exceptionOrNull()
            ?.message
        return UpdateVerification.problem(
            expectedDigest = asset.sha256,
            actualDigest = sha256Of(file),
            packageProblem = packageProblem
        )
    }

    private fun sha256Of(file: File): String? = try {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    } catch (_: Exception) {
        null
    }

    private fun evaluateRelease(release: GithubRelease): UpdateState {
        val latest = release.version
        if (latest == null) {
            return UpdateState.Failed(
                "The release ${release.tagName} does not carry a version the app can read.",
                canRetry = false
            )
        }
        val installed = AppVersion.parse(BuildConfig.VERSION_NAME)
        if (installed == null) {
            return UpdateState.Failed(
                "The installed version could not be read, so no update was offered.",
                canRetry = false
            )
        }
        if (!latest.isNewerThan(installed)) {
            return UpdateState.UpToDate(release)
        }
        return UpdateState.Available(release, release.installableApk())
    }

    fun loadAboutInfo() {
        scope.launch {
            preferences.cachedReleaseFlow.first()?.let { cached ->
                val release = ReleaseApi.parseRelease(cached)
                if (release != null) {
                    _aboutState.value = _aboutState.value.copy(
                        latestRelease = release,
                        isFromCache = true
                    )
                }
            }
            _aboutState.value = _aboutState.value.copy(isLoading = true, error = null)
            releaseApi.fetchLatestRelease()
                .onSuccess { release ->
                    cacheRelease(release)
                    _aboutState.value = _aboutState.value.copy(
                        latestRelease = release,
                        isLoading = false,
                        error = null,
                        isFromCache = false
                    )
                }
                .onFailure { error ->
                    _aboutState.value = _aboutState.value.copy(
                        isLoading = false,
                        error = error.message ?: "The release information could not be read."
                    )
                }
        }
    }

    private fun cacheRelease(release: GithubRelease) {
        scope.launch(Dispatchers.IO) {
            preferences.saveCachedRelease(release)
        }
    }

    fun releasesPageUrl(): String = releaseApi.releasesPageUrl()
}
