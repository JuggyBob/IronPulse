/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.data.backup

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Limits that turn a malicious or broken archive into a safe validation failure.
 *
 * The defaults are deliberately much larger than a real IronPulse backup but small enough that a
 * zip bomb cannot exhaust the process. Tests pass smaller limits.
 */
data class BackupArchiveLimits(
    val maxArchiveBytes: Long = 256L * 1024 * 1024,
    val maxTotalUncompressedBytes: Long = 512L * 1024 * 1024,
    val maxEntryBytes: Long = 128L * 1024 * 1024,
    val maxEntries: Int = 64,
    val maxCompressionRatio: Long = 500L
)

/** Builds one `.ironpulse` archive and records the checksums the importer will verify. */
class BackupArchiveWriter(
    output: OutputStream,
    private val limits: BackupArchiveLimits = BackupArchiveLimits()
) {
    private val zip = ZipOutputStream(BufferedOutputStream(output))
    private val checksums = LinkedHashMap<String, String>()
    private val sections = LinkedHashMap<String, BackupSectionEntry>()
    private val files = mutableListOf<BackupFileEntry>()
    private var finished = false

    fun addSection(name: String, bytes: ByteArray, recordCount: Int) {
        check(!finished) { "archive already finished" }
        val expectedPath = BackupFormat.REQUIRED_SECTIONS[name]
            ?: throw IllegalArgumentException("unknown backup section: $name")
        if (sections.containsKey(name)) throw IllegalArgumentException("duplicate section: $name")
        if (bytes.size > limits.maxEntryBytes) {
            throw BackupException(
                "A backup section is larger than this app can safely write.",
                BackupErrorCode.TOO_LARGE
            )
        }
        writeEntry(expectedPath, bytes, store = false)
        checksums[expectedPath] = BackupArchiveReader.sha256(bytes)
        sections[name] = BackupSectionEntry(path = expectedPath, recordCount = recordCount)
    }

    fun addFile(
        role: String,
        archivePath: String,
        bytes: ByteArray,
        mediaType: String
    ) {
        check(!finished) { "archive already finished" }
        if (files.any { it.path == archivePath }) {
            throw IllegalArgumentException("duplicate archive file: $archivePath")
        }
        if (bytes.size > limits.maxEntryBytes) {
            throw BackupException(
                "A backup file is larger than this app can safely write.",
                BackupErrorCode.TOO_LARGE
            )
        }
        writeEntry(archivePath, bytes, store = true)
        val digest = BackupArchiveReader.sha256(bytes)
        checksums[archivePath] = digest
        files += BackupFileEntry(
            path = archivePath,
            role = role,
            mediaType = mediaType,
            size = bytes.size.toLong(),
            sha256 = digest
        )
    }

    fun finish(
        appVersionName: String,
        appVersionCode: Int,
        exportedAt: String,
        exportedAtEpochMillis: Long
    ) {
        check(!finished) { "archive already finished" }
        finished = true

        if (sections.keys != BackupFormat.REQUIRED_SECTIONS.keys) {
            throw IllegalStateException("every required backup section must be written")
        }

        val checksumsBytes = BackupJson.toBytes(
            BackupChecksums(entries = checksums.toMap()),
            BackupChecksums::class.java
        )
        writeEntry(BackupFormat.CHECKSUMS_PATH, checksumsBytes, store = false)

        val manifest = BackupManifest(
            appVersionName = appVersionName,
            appVersionCode = appVersionCode,
            exportedAt = exportedAt,
            exportedAtEpochMillis = exportedAtEpochMillis,
            sections = sections.toMap(),
            files = files.toList()
        )
        writeEntry(
            BackupFormat.MANIFEST_PATH,
            BackupJson.toBytes(manifest, BackupManifest::class.java),
            store = false
        )

        zip.finish()
        zip.flush()
        zip.close()
    }

    private fun writeEntry(path: String, bytes: ByteArray, store: Boolean) {
        validateArchivePath(path)
        val entry = ZipEntry(path)
        // A fixed timestamp makes the archive deterministic. The real export time is in the manifest.
        entry.time = 0L
        if (store) {
            val crc = CRC32()
            crc.update(bytes)
            entry.method = ZipEntry.STORED
            entry.size = bytes.size.toLong()
            entry.compressedSize = bytes.size.toLong()
            entry.crc = crc.value
        } else {
            entry.method = ZipEntry.DEFLATED
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    companion object {
        fun validateArchivePath(path: String) {
            if (path.length !in 1..220) {
                throw BackupException("The archive contains an invalid path.", BackupErrorCode.MALFORMED_ARCHIVE)
            }
            if (path.startsWith('/') || path.startsWith('\\') ||
                path.contains('\\') || path.contains(':') ||
                path.contains("//") || path.contains("../") ||
                path == ".." || path.split('/').any { it.isEmpty() || it == "." || it == ".." }
            ) {
                throw BackupException("The archive contains an unsafe path.", BackupErrorCode.MALFORMED_ARCHIVE)
            }
            if (path != BackupFormat.MANIFEST_PATH &&
                path != BackupFormat.CHECKSUMS_PATH &&
                !path.startsWith("payload/") &&
                !path.startsWith("files/")
            ) {
                throw BackupException("The archive contains an unexpected entry.", BackupErrorCode.MALFORMED_ARCHIVE)
            }
            if (!path.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._/-]*$"))) {
                throw BackupException("The archive contains an invalid file name.", BackupErrorCode.MALFORMED_ARCHIVE)
            }
        }
    }
}

/**
 * Reads and verifies one archive. Nothing outside the archive is touched until this class returns a
 * complete, checksum-validated payload.
 */
class BackupArchiveReader(
    private val file: File,
    private val limits: BackupArchiveLimits = BackupArchiveLimits()
) {

    fun read(): ArchivePayload = scan(collect = true)!!

    /** Full structural and checksum verification without retaining the payload in memory. */
    fun verify() {
        scan(collect = false)
    }

    private fun scan(collect: Boolean): ArchivePayload? {
        if (!file.isFile) {
            throw BackupException("The selected backup file could not be opened.", BackupErrorCode.IO_ERROR)
        }
        if (file.length() <= 0L) {
            throw BackupException("The selected backup file is empty.", BackupErrorCode.NOT_AN_ARCHIVE)
        }
        if (file.length() > limits.maxArchiveBytes) {
            throw BackupException(
                "The selected backup file is too large for this app to import safely.",
                BackupErrorCode.TOO_LARGE
            )
        }
        verifyZipSignature(file)

        try {
            ZipFile(file).use { zip ->
                val entries = indexEntries(zip)
                val manifestBytes = readEntry(zip, entries.getValue(BackupFormat.MANIFEST_PATH))
                val manifest = parseManifest(manifestBytes)
                validateVersion(manifest)

                val checksumsEntry = entries[manifest.checksumsFile]
                    ?: throw BackupException(
                        "The backup is missing its checksum file.",
                        BackupErrorCode.MISSING_SECTION
                    )
                val checksums = parseChecksums(readEntry(zip, checksumsEntry))

                validateManifestAndChecksums(manifest, checksums, entries)

                val sectionBytes = LinkedHashMap<String, ByteArray>()
                val fileBytes = LinkedHashMap<String, ByteArray>()
                var totalRead = 0L

                for ((name, section) in manifest.sections) {
                    val entry = entries[section.path]
                        ?: throw BackupException(
                            "The backup is missing the $name section.",
                            BackupErrorCode.MISSING_SECTION
                        )
                    val bytes = readEntry(zip, entry)
                    totalRead += bytes.size
                    if (totalRead > limits.maxTotalUncompressedBytes) {
                        throw BackupException(
                            "The backup expands to more data than this app can import safely.",
                            BackupErrorCode.TOO_LARGE
                        )
                    }
                    verifyChecksum(section.path, bytes, checksums)
                    if (collect) sectionBytes[name] = bytes
                }

                val manifestFiles = LinkedHashMap<String, BackupFileEntry>()
                for (entry in manifest.files) {
                    if (manifestFiles.put(entry.path, entry) != null) {
                        throw BackupException(
                            "The backup manifest lists the same file twice.",
                            BackupErrorCode.INVALID_MANIFEST
                        )
                    }
                    val archiveEntry = entries[entry.path]
                        ?: throw BackupException(
                            "The backup is missing a file its manifest lists.",
                            BackupErrorCode.MISSING_SECTION
                        )
                    val bytes = readEntry(zip, archiveEntry)
                    totalRead += bytes.size
                    if (totalRead > limits.maxTotalUncompressedBytes) {
                        throw BackupException(
                            "The backup expands to more data than this app can import safely.",
                            BackupErrorCode.TOO_LARGE
                        )
                    }
                    verifyChecksum(entry.path, bytes, checksums)
                    if (bytes.size.toLong() != entry.size) {
                        throw BackupException(
                            "A file in the backup does not match the size in its manifest.",
                            BackupErrorCode.INVALID_MANIFEST
                        )
                    }
                    if (collect) fileBytes[entry.path] = bytes
                }

                return if (collect) {
                    ArchivePayload(manifest, sectionBytes, fileBytes)
                } else {
                    null
                }
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException(
                "The selected file is not a readable IronPulse backup, or it is corrupted.",
                BackupErrorCode.MALFORMED_ARCHIVE,
                e
            )
        }
    }

    private fun indexEntries(zip: ZipFile): Map<String, ZipEntry> {
        val entries = LinkedHashMap<String, ZipEntry>()
        var totalUncompressed = 0L
        var count = 0
        val enumeration = zip.entries()
        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement()
            if (entry.isDirectory) {
                throw BackupException(
                    "The backup contains directory entries, which IronPulse backups never use.",
                    BackupErrorCode.MALFORMED_ARCHIVE
                )
            }
            count++
            if (count > limits.maxEntries) {
                throw BackupException("The backup contains too many entries.", BackupErrorCode.TOO_LARGE)
            }
            BackupArchiveWriter.validateArchivePath(entry.name)
            if (entries.put(entry.name, entry) != null) {
                throw BackupException(
                    "The backup contains duplicate entries.",
                    BackupErrorCode.MALFORMED_ARCHIVE
                )
            }
            if (entry.size < 0L || entry.compressedSize < 0L) {
                throw BackupException(
                    "The backup contains an entry with an invalid size.",
                    BackupErrorCode.MALFORMED_ARCHIVE
                )
            }
            if (entry.size > limits.maxEntryBytes) {
                throw BackupException(
                    "An entry in the backup is too large for this app to import safely.",
                    BackupErrorCode.TOO_LARGE
                )
            }
            totalUncompressed += entry.size
            if (totalUncompressed > limits.maxTotalUncompressedBytes) {
                throw BackupException(
                    "The backup expands to more data than this app can import safely.",
                    BackupErrorCode.TOO_LARGE
                )
            }
            if (entry.size > 1024 * 1024 && entry.compressedSize > 0L) {
                if ((entry.size / entry.compressedSize) > limits.maxCompressionRatio) {
                    throw BackupException(
                        "An entry in the backup has an unsafe compression ratio.",
                        BackupErrorCode.TOO_LARGE
                    )
                }
            }
        }
        if (!entries.containsKey(BackupFormat.MANIFEST_PATH)) {
            throw BackupException(
                "The selected file is missing its backup manifest.",
                BackupErrorCode.INVALID_MANIFEST
            )
        }
        return entries
    }

    private fun readEntry(zip: ZipFile, entry: ZipEntry): ByteArray {
        val size = entry.size
        if (size < 0L || size > limits.maxEntryBytes || size > Int.MAX_VALUE) {
            throw BackupException("The backup contains an entry with an invalid size.", BackupErrorCode.TOO_LARGE)
        }
        val expected = size.toInt()
        val bytes = ByteArray(expected)
        BufferedInputStream(zip.getInputStream(entry)).use { input ->
            var offset = 0
            while (offset < expected) {
                val read = input.read(bytes, offset, expected - offset)
                if (read < 0) {
                    throw BackupException(
                        "The backup is truncated.",
                        BackupErrorCode.MALFORMED_ARCHIVE
                    )
                }
                offset += read
            }
            if (input.read() != -1) {
                throw BackupException(
                    "A backup entry is larger than its archive index says.",
                    BackupErrorCode.MALFORMED_ARCHIVE
                )
            }
        }
        return bytes
    }

    private fun parseManifest(bytes: ByteArray): BackupManifest {
        val manifest = try {
            BackupJson.parse(bytes, BackupManifest::class.java)
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("The backup manifest is not valid JSON.", BackupErrorCode.INVALID_MANIFEST, e)
        }
        if (manifest.format != BackupFormat.MAGIC) {
            throw BackupException("The selected file is not an IronPulse backup.", BackupErrorCode.INVALID_MANIFEST)
        }
        if (manifest.appVersionName.length !in 1..64 ||
            manifest.exportedAt.length !in 1..64 ||
            manifest.exportedAtEpochMillis < 0L
        ) {
            throw BackupException("The backup manifest contains invalid values.", BackupErrorCode.INVALID_MANIFEST)
        }
        if (manifest.serialization != BackupFormat.SERIALIZATION_JSON ||
            manifest.archive != BackupFormat.ARCHIVE_ZIP
        ) {
            throw BackupException(
                "The backup uses a serialization this app does not support.",
                BackupErrorCode.UNSUPPORTED_FORMAT
            )
        }
        return manifest
    }

    private fun parseChecksums(bytes: ByteArray): BackupChecksums {
        val checksums = try {
            BackupJson.parse(bytes, BackupChecksums::class.java)
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("The backup checksum file is not valid JSON.", BackupErrorCode.INVALID_MANIFEST, e)
        }
        if (!checksums.algorithm.equals("SHA-256", ignoreCase = true)) {
            throw BackupException(
                "The backup uses an unsupported checksum algorithm.",
                BackupErrorCode.UNSUPPORTED_FORMAT
            )
        }
        for ((path, digest) in checksums.entries) {
            if (!digest.matches(Regex("^[0-9a-fA-F]{64}$"))) {
                throw BackupException("The backup contains an invalid checksum.", BackupErrorCode.INVALID_MANIFEST)
            }
            if (path == BackupFormat.MANIFEST_PATH || path == BackupFormat.CHECKSUMS_PATH) {
                throw BackupException("The backup checksum file is malformed.", BackupErrorCode.INVALID_MANIFEST)
            }
        }
        return checksums
    }

    private fun validateVersion(manifest: BackupManifest) {
        if (manifest.formatVersion > BackupFormat.CURRENT_FORMAT_VERSION) {
            throw BackupException(
                "This backup was created by a newer IronPulse (format v${manifest.formatVersion}). Update IronPulse and try again.",
                BackupErrorCode.UNSUPPORTED_FORMAT
            )
        }
        if (manifest.formatVersion < BackupFormat.MIN_SUPPORTED_FORMAT_VERSION) {
            throw BackupException(
                "This backup uses an older format that this version of IronPulse cannot restore.",
                BackupErrorCode.UNSUPPORTED_FORMAT
            )
        }
        if (manifest.dataSchemaVersion > BackupFormat.CURRENT_DATA_SCHEMA_VERSION) {
            throw BackupException(
                "This backup contains newer user data (schema v${manifest.dataSchemaVersion}). Update IronPulse and try again.",
                BackupErrorCode.UNSUPPORTED_SCHEMA
            )
        }
        if (manifest.dataSchemaVersion < BackupFormat.MIN_SUPPORTED_DATA_SCHEMA_VERSION) {
            throw BackupException(
                "This backup uses an older data schema that this version of IronPulse cannot restore.",
                BackupErrorCode.UNSUPPORTED_SCHEMA
            )
        }
    }

    private fun validateManifestAndChecksums(
        manifest: BackupManifest,
        checksums: BackupChecksums,
        entries: Map<String, ZipEntry>
    ) {
        if (manifest.checksumsFile != BackupFormat.CHECKSUMS_PATH) {
            throw BackupException("The backup manifest names an unexpected checksum file.", BackupErrorCode.INVALID_MANIFEST)
        }
        val expectedPaths = LinkedHashSet<String>()

        for ((name, expectedPath) in BackupFormat.REQUIRED_SECTIONS) {
            val section = manifest.sections[name]
                ?: throw BackupException(
                    "The backup manifest is missing the $name section.",
                    BackupErrorCode.MISSING_SECTION
                )
            if (!section.required) {
                throw BackupException(
                    "A required backup section is marked optional.",
                    BackupErrorCode.INVALID_MANIFEST
                )
            }
            if (section.path != expectedPath) {
                throw BackupException(
                    "The backup manifest names an unexpected path for the $name section.",
                    BackupErrorCode.INVALID_MANIFEST
                )
            }
            if (!entries.containsKey(section.path)) {
                throw BackupException("The backup is missing the $name section.", BackupErrorCode.MISSING_SECTION)
            }
            expectedPaths += section.path
        }

        for (name in manifest.sections.keys) {
            if (name !in BackupFormat.REQUIRED_SECTIONS) {
                throw BackupException(
                    "The backup contains an unknown section this app cannot restore safely.",
                    BackupErrorCode.UNSUPPORTED_FORMAT
                )
            }
        }

        if (manifest.files.size > 1) {
            throw BackupException(
                "The backup contains more files than this app knows how to restore.",
                BackupErrorCode.UNSUPPORTED_FORMAT
            )
        }
        for (entry in manifest.files) {
            if (!entry.path.startsWith("files/") || entry.path == "files/") {
                throw BackupException("The backup manifest contains an unsafe file path.", BackupErrorCode.INVALID_MANIFEST)
            }
            if (entry.role != BackupFormat.ROLE_WIDGET_PHOTO) {
                throw BackupException(
                    "The backup contains a file this app does not know how to restore.",
                    BackupErrorCode.UNSUPPORTED_FORMAT
                )
            }
            if (entry.mediaType.length !in 1..100) {
                throw BackupException("The backup manifest contains an invalid file type.", BackupErrorCode.INVALID_MANIFEST)
            }
            if (!entry.path.startsWith(BackupFormat.FILE_WIDGET_PHOTO)) {
                throw BackupException("The backup manifest contains an unexpected file.", BackupErrorCode.INVALID_MANIFEST)
            }
            if (!entries.containsKey(entry.path)) {
                throw BackupException("The backup is missing a file its manifest lists.", BackupErrorCode.MISSING_SECTION)
            }
            expectedPaths += entry.path
        }

        val checksumPaths = checksums.entries.keys
        if (checksumPaths != expectedPaths) {
            throw BackupException(
                "The backup checksum list does not match the files in its manifest.",
                BackupErrorCode.INVALID_MANIFEST
            )
        }

        val listed = expectedPaths + BackupFormat.MANIFEST_PATH + BackupFormat.CHECKSUMS_PATH
        if (entries.keys != listed) {
            throw BackupException(
                "The backup contains entries its manifest does not describe.",
                BackupErrorCode.INVALID_MANIFEST
            )
        }
    }

    private fun verifyChecksum(path: String, bytes: ByteArray, checksums: BackupChecksums) {
        val expected = checksums.entries[path]
            ?: throw BackupException("The backup has no checksum for an entry.", BackupErrorCode.CHECKSUM_MISMATCH)
        val actual = sha256(bytes)
        if (!actual.equals(expected, ignoreCase = true)) {
            throw BackupException(
                "The backup is corrupted: a checksum did not match. Nothing was changed.",
                BackupErrorCode.CHECKSUM_MISMATCH
            )
        }
    }

    private fun verifyZipSignature(file: File) {
        try {
            BufferedInputStream(file.inputStream()).use { input ->
                val first = input.read()
                val second = input.read()
                if (first != 'P'.code || second != 'K'.code) {
                    throw BackupException(
                        "The selected file is not an IronPulse backup.",
                        BackupErrorCode.NOT_AN_ARCHIVE
                    )
                }
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("The selected backup could not be read.", BackupErrorCode.IO_ERROR, e)
        }
    }

    companion object {
        fun sha256(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { "%02x".format(Locale.US, it) }
        }

        fun writeToFile(file: File, payload: ArchivePayload, limits: BackupArchiveLimits = BackupArchiveLimits()) {
            val partial = File(file.parentFile, "${file.name}.part")
            try {
                FileOutputStream(partial).use { out ->
                    val writer = BackupArchiveWriter(out, limits)
                    for ((name, bytes) in payload.sections) {
                        val recordCount = payload.manifest.sections[name]?.recordCount ?: 0
                        writer.addSection(name, bytes, recordCount)
                    }
                    for (entry in payload.manifest.files) {
                        val bytes = payload.files[entry.path]
                            ?: throw BackupException("A payload file is missing.", BackupErrorCode.MISSING_SECTION)
                        writer.addFile(entry.role, entry.path, bytes, entry.mediaType)
                    }
                    writer.finish(
                        appVersionName = payload.manifest.appVersionName,
                        appVersionCode = payload.manifest.appVersionCode,
                        exportedAt = payload.manifest.exportedAt,
                        exportedAtEpochMillis = payload.manifest.exportedAtEpochMillis
                    )
                }
                BackupArchiveReader(partial, limits).verify()
                if (file.exists() && !file.delete()) {
                    throw BackupException("The destination backup could not be replaced.", BackupErrorCode.IO_ERROR)
                }
                if (!partial.renameTo(file)) {
                    throw BackupException("The completed backup could not be moved into place.", BackupErrorCode.IO_ERROR)
                }
            } catch (e: Exception) {
                partial.delete()
                throw e
            }
        }
    }
}

/** Shared low-level helper for copying a provider stream with a hard byte ceiling. */
internal fun copyWithLimit(input: InputStream, output: OutputStream, maxBytes: Long): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) {
            throw BackupException(
                "The selected file is larger than this app can import safely.",
                BackupErrorCode.TOO_LARGE
            )
        }
        output.write(buffer, 0, read)
    }
    return total
}
