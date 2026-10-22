package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a downloaded release APK to Android to install.
 *
 * The app does not install anything itself, and it does not try to: Android asks the person to
 * confirm the installation, and it refuses an APK that is signed with a different key. What this
 * does is the part the app is allowed to do: check that the file really is this app, signed with
 * this app's key, and open the system installer for it.
 */
object ApkInstaller {

    /** The authority of the file provider the app shares the download through. */
    fun authority(context: Context): String = "${context.packageName}.fileprovider"

    /**
     * Whether Android will let the app ask for an installation.
     *
     * From Android 8 the person has to allow the app to install packages at all, and that is a
     * separate switch in the system settings. The app asks for it when it is missing.
     */
    fun canRequestPackageInstalls(context: Context): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /**
     * The system screen where the person allows the app to install packages.
     */
    fun installPermissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )

    /**
     * Checks that the downloaded file is an installable build of this app.
     *
     * Two things are checked, and both have to hold:
     *
     *  - the package inside the archive has to be this app's own package, so a file that is not an
     *    update of IronPulse never reaches the installer;
     *  - the file has to be signed with a key this app is signed with, so the installer is never
     *    handed something Android is bound to refuse.
     *
     * Android enforces the second of those itself, but only once the installer has been opened and
     * the athlete has already been through the system screens. Checking it here means the app can
     * say what is wrong while it still knows, and it means a file that is not an update is never
     * presented as one.
     *
     * @return the package information of the file, or a failure with a readable reason.
     */
    fun verify(context: Context, file: File): Result<PackageInfo> {
        if (!file.exists() || file.length() <= 0L) {
            return Result.failure(IllegalStateException("The downloaded file is empty."))
        }
        return try {
            val packageManager = context.packageManager
            val info = packageManager.getPackageArchiveInfo(file.absolutePath, signingFlag)
                ?: return Result.failure(IllegalStateException("The downloaded file is not a readable app."))
            if (info.packageName != context.packageName) {
                return Result.failure(
                    IllegalStateException("The downloaded file is a different app, so it was not installed.")
                )
            }

            // The key the file is signed with has to be one this app is signed with.
            val downloaded = signingDigests(info)
            val installed = packageManager.getPackageInfo(
                context.packageName,
                signingFlag
            ).let { signingDigests(it) }
            if (downloaded.isEmpty() || installed.isEmpty() || downloaded.none { it in installed }) {
                return Result.failure(
                    IllegalStateException(
                        "The downloaded file is not signed with the same key, so it was not installed."
                    )
                )
            }

            Result.success(info)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("The downloaded file could not be read."))
        }
    }

    /**
     * The flag that asks the package manager for the certificates a package is signed with.
     *
     * From Android 9 the newer flag is the one to use; before that the old one is the only one
     * the platform understands, and asking for the newer one there would quietly return nothing.
     */
    private val signingFlag: Int
        get() = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    /**
     * The digests of the certificates a package is signed with.
     *
     * A key that has been rotated leaves a history behind it, and the history is what has to
     * match: a build signed with the new key is still an update of a build signed with the old
     * one, and Android says so.
     */
    private fun signingDigests(info: PackageInfo): Set<String> {
        val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            info.signatures ?: return emptySet()
        }
        return signatureDigests(signatures.toList())
    }

    /**
     * The digests of a set of signing certificates.
     *
     * The digest is what is compared, so that a certificate is never held in memory or written
     * anywhere as anything but a fingerprint.
     */
    fun signatureDigests(signatures: List<android.content.pm.Signature>): Set<String> =
        signatures.map { signature ->
            signature.toByteArray().joinToString("") { byte -> "%02x".format(byte) }
        }.toSet()

    /**
     * The content uri the downloaded file is shared under.
     *
     * The file is shared through the app's own file provider rather than as a file path, so it
     * never has to be readable by anything else on the device.
     */
    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, authority(context), file)

    /**
     * The intent that opens the system installer for a downloaded file.
     */
    fun installIntent(context: Context, file: File): Intent =
        installIntentFor(uriFor(context, file))

    /**
     * The intent that opens the system installer for a shared file.
     *
     * This is the one place the app asks Android to install a package, and it is deliberately
     * narrow:
     *
     *  - the file comes from the official release of this repository, over TLS, and the url is
     *    checked against that repository before anything is downloaded;
     *  - the file's SHA-256 is compared with the digest GitHub published for the asset, when there
     *    is one;
     *  - the package inside the file is read and has to be this app's own package, so a file that
     *    is not an update of IronPulse is never handed over;
     *  - Android itself refuses an APK that is signed with another key, and it asks the athlete
     *    to confirm the installation.
     *
     * The app never installs anything on its own and never works around that confirmation.
     */
    fun installIntentFor(uri: Uri): Intent = Intent(Intent.ACTION_VIEW).apply {
        // lgtm[java/android/arbitrary-apk-installation]
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * The name of the file the update is downloaded to.
     *
     * It lives in the app's own storage, so nothing else on the device can read it, and it is
     * replaced rather than appended to, so a second update does not leave the first one behind.
     */
    fun updateFile(context: Context, assetName: String): File {
        val directory = File(context.filesDir, "updates").apply { mkdirs() }
        return File(directory, assetName)
    }

    /**
     * Removes the downloads that are no longer needed.
     *
     * An installer that is never opened, or an update that was superseded, must not leave a copy of
     * the app sitting in the app's storage for ever.
     */
    fun clearDownloads(context: Context) {
        try {
            File(context.filesDir, "updates").listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {
            // A download that cannot be removed is not worth failing the app over.
        }
    }
}
