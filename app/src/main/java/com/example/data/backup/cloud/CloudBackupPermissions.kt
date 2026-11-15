/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.backup.cloud

import android.content.Context
import android.content.Intent
import android.net.Uri

object CloudBackupPermissions {

    /** Takes the read/write permission that must survive an app restart. */
    fun takePersistableWrite(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }

    fun hasPersistedWrite(context: Context, uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any {
            it.isWritePermission &&
                (it.uri == uri || it.uri.toString() == uri.toString())
        }

    fun release(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }
}
