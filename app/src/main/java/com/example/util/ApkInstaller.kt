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
 * does is the part the app is allowed to do: check that the file really is this app, and open the
 * system installer for it.
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
     * The check reads the package out of the archive, so a file that is not an APK of this app
     * never reaches the installer. Android refuses an APK signed with another key anyway, but the
     * app does not hand it something that is plainly not an update in the first place.
     *
     * @return the package information of the file, or a failure with a readable reason.
     */
    fun verify(context: Context, file: File): Result<PackageInfo> {
        if (!file.exists() || file.length() <= 0L) {
            return Result.failure(IllegalStateException("The downloaded file is empty."))
        }
        return try {
            val packageManager = context.packageManager
            val info = packageManager.getPackageArchiveInfo(file.absolutePath, 0)
                ?: return Result.failure(IllegalStateException("The downloaded file is not a readable app."))
            if (info.packageName != context.packageName) {
                return Result.failure(
                    IllegalStateException("The downloaded file is a different app, so it was not installed.")
                )
            }
            Result.success(info)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("The downloaded file could not be read."))
        }
    }

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
     */
    fun installIntentFor(uri: Uri): Intent = Intent(Intent.ACTION_VIEW).apply {
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
