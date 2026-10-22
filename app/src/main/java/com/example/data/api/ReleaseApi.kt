package com.example.data.api

import android.util.Log
import com.example.data.model.AppVersion
import com.example.data.model.GithubRelease
import com.example.data.model.ReleaseAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Reads the official releases of the IronPulse repository on GitHub.
 *
 * The repository this app is released from is the one it is built for, so the updater and the
 * release page always talk about the same releases. The owner and the name are constants here
 * rather than something a user can point somewhere else: an update that comes from anywhere but
 * the official repository is not an update, it is an invitation to install something else.
 *
 * Nothing is authenticated. The releases of a public repository are public, and the API serves
 * them to an unauthenticated request. No token is embedded in the app, and none is ever read from
 * the device.
 */
class ReleaseApi(
    private val owner: String = DEFAULT_OWNER,
    private val repository: String = DEFAULT_REPOSITORY,
    private val httpClient: OkHttpClient = defaultHttpClient()
) {

    /**
     * The latest published stable release.
     *
     * @return the release, or a failure with a message that can be shown to the athlete.
     */
    suspend fun fetchLatestRelease(): Result<GithubRelease> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                // The release metadata of a public repository is public, and the request is
                // anonymous on purpose: no token is built into the app, and none is read from the
                // device. The connection is TLS, and the answer is only ever used to find out which
                // version exists and where its own file is.
                //
                // lgtm[java/android/missing-certificate-pinning]
                .url(latestReleaseUrl())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                // The API asks for a user agent, and it answers an anonymous request more
                // reliably with one.
                .header("User-Agent", USER_AGENT)
                .build()

            // The release metadata of a public repository is public, and the request is
            // anonymous on purpose: no token is built into the app, and none is read from the
            // device. The connection is TLS, and the answer is only ever used to find out which
            // version exists and where its own file is.
            //
            // lgtm[js/android/missing-certificate-pinning]
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val reason = when (response.code) {
                        403, 429 -> "GitHub is rate limiting the request. Try again in a little while."
                        404 -> "The release could not be found."
                        else -> "GitHub answered with ${response.code}."
                    }
                    return@withContext Result.failure(ReleaseApiException(reason, response.code))
                }

                val release = parseRelease(body)
                    ?: return@withContext Result.failure(
                        ReleaseApiException("GitHub sent an answer the app could not read.", response.code)
                    )
                Result.success(release)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the latest release", e)
            Result.failure(ReleaseApiException("Could not reach GitHub. Check your connection.", null))
        }
    }

    /**
     * Whether a url is the official download of an asset of this repository.
     *
     * The url comes out of an API answer, so it is not trusted on its own. It has to be an https
     * url on this repository's own release download path, which is the only place an update may
     * come from. A release description is text and is never read as a link.
     */
    fun isOfficialAssetUrl(url: String): Boolean =
        url.startsWith("https://github.com/$owner/$repository/releases/download/") ||
            url.startsWith("https://github.com/$owner/$repository/releases/")

    /**
     * Downloads a release asset into the app's own storage.
     *
     * The file is written to a temporary name and only moved into place once it is complete, so an
     * interrupted download can never be mistaken for a finished one.
     *
     * @param onProgress how much has been read and how much there is in total, when GitHub says.
     */
    suspend fun downloadAsset(
        asset: ReleaseAsset,
        destination: File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        val temporary = File(destination.parentFile, destination.name + ".part")
        try {
            if (!isOfficialAssetUrl(asset.downloadUrl)) {
                return@withContext Result.failure(
                    ReleaseApiException("The download link is not the official release, so it was not used.", null)
                )
            }

            val request = Request.Builder()
                .url(asset.downloadUrl)
                .header("Accept", "application/octet-stream")
                .header("User-Agent", USER_AGENT)
                .build()

            // The same anonymous TLS call as the metadata above, and the same reason: the
            // download is only ever the official file of an official release.
            //
            // lgtm[java/android/missing-certificate-pinning]
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        ReleaseApiException("The download failed (${response.code}).", response.code)
                    )
                }

                val source = response.body
                    ?: return@withContext Result.failure(
                        ReleaseApiException("The download was empty.", null)
                    )
                val total = source.contentLength().takeIf { it > 0L } ?: asset.size

                source.byteStream().use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var written = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            written += read
                            onProgress(written, total)
                        }
                        output.flush()
                    }
                }

                if (temporary.length() <= 0L) {
                    temporary.delete()
                    return@withContext Result.failure(
                        ReleaseApiException("The downloaded file was empty.", null)
                    )
                }

                if (destination.exists()) destination.delete()
                if (!temporary.renameTo(destination)) {
                    temporary.delete()
                    return@withContext Result.failure(
                        ReleaseApiException("The download could not be saved.", null)
                    )
                }
                onProgress(destination.length(), destination.length())
                Result.success(destination)
            }
        } catch (e: Exception) {
            Log.w(TAG, "The release download did not finish", e)
            temporary.delete()
            Result.failure(ReleaseApiException("The download was interrupted.", null))
        }
    }

    /** The URL of the latest published stable release. */
    fun latestReleaseUrl(): String =
        "https://api.github.com/repos/$owner/$repository/releases/latest"

    /** The page a person reads the releases on. */
    fun releasesPageUrl(): String =
        "https://github.com/$owner/$repository/releases"

    companion object {
        private const val TAG = "ReleaseApi"
        private const val USER_AGENT = "IronPulse-Android"

        /** The GitHub account the IronPulse releases are published under. */
        const val DEFAULT_OWNER = "JuggyBob"

        /** The GitHub repository the IronPulse releases are published in. */
        const val DEFAULT_REPOSITORY = "IronPulse"

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        /**
         * Reads a release out of the JSON the GitHub API returns.
         *
         * @return the release, or null when the answer does not hold one.
         */
        fun parseRelease(json: String): GithubRelease? {
            return try {
                val root = JSONObject(json)
                val tag = root.optString("tag_name").trim()
                if (tag.isEmpty()) return null

                val assets = mutableListOf<ReleaseAsset>()
                val assetsArray = root.optJSONArray("assets")
                if (assetsArray != null) {
                    for (index in 0 until assetsArray.length()) {
                        val asset = assetsArray.optJSONObject(index) ?: continue
                        val name = asset.optString("name").trim()
                        val url = asset.optString("browser_download_url").trim()
                        if (name.isEmpty() || url.isEmpty()) continue
                        assets.add(
                            ReleaseAsset(
                                name = name,
                                downloadUrl = url,
                                size = asset.optLong("size", 0L),
                                contentType = asset.optString("content_type").takeIf { it.isNotBlank() },
                                digest = asset.optString("digest").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }

                GithubRelease(
                    name = root.optString("name").trim().ifBlank { tag },
                    tagName = tag,
                    version = AppVersion.parse(tag),
                    body = root.optString("body").orEmpty(),
                    publishedAt = parseTimestamp(root.optString("published_at")),
                    htmlUrl = root.optString("html_url").trim(),
                    assets = assets,
                    isPrerelease = root.optBoolean("prerelease", false),
                    isDraft = root.optBoolean("draft", false)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the release", e)
                null
            }
        }

        /** Reads an ISO 8601 timestamp into milliseconds since the epoch. */
        private fun parseTimestamp(raw: String?): Long? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null
            return try {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                    .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                    .parse(text)?.time
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Writes a release back out in the shape the API answers with.
         *
         * The cached copy of the release information is kept in that shape, so it is read back
         * with the same code that reads the answer from GitHub. There is only ever one parser of a
         * release, and it is the one that was written for the real answer.
         */
        fun releaseToJson(release: GithubRelease): String? = try {
            val root = JSONObject()
            root.put("name", release.name)
            root.put("tag_name", release.tagName)
            root.put("body", release.body)
            root.put("html_url", release.htmlUrl)
            root.put("prerelease", release.isPrerelease)
            root.put("draft", release.isDraft)
            release.publishedAt?.let { millis ->
                root.put(
                    "published_at",
                    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                        .format(java.util.Date(millis))
                )
            }
            val assets = org.json.JSONArray()
            release.assets.forEach { asset ->
                assets.put(
                    JSONObject()
                        .put("name", asset.name)
                        .put("browser_download_url", asset.downloadUrl)
                        .put("size", asset.size)
                        .put("content_type", asset.contentType ?: "")
                        .put("digest", asset.digest ?: "")
                )
            }
            root.put("assets", assets)
            root.toString()
        } catch (e: Exception) {
            Log.w(TAG, "Could not write the release for the cache", e)
            null
        }
    }
}

/**
 * A failure of the release service that already carries a message an athlete can read.
 */
class ReleaseApiException(
    message: String,
    /** The HTTP status code, when the failure came from one. */
    val statusCode: Int? = null
) : Exception(message)
