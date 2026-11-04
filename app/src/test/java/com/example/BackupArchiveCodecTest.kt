/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import com.example.data.backup.BackupArchiveLimits
import com.example.data.backup.BackupArchiveReader
import com.example.data.backup.BackupException
import com.example.data.backup.BackupFormat
import com.example.data.backup.BackupJson
import com.example.data.backup.BackupManifest
import com.example.data.backup.BackupErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Corrupt, truncated, unsafe and unsupported archives must fail before anything is restored. */
class BackupArchiveCodecTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun validArchive(name: String = "valid.ironpulse"): File {
        val file = temp.newFile(name)
        BackupTestFixtures.writeArchive(BackupTestFixtures.sampleSnapshot(), file)
        return file
    }

    @Test
    fun `valid archive verifies and reports its manifest`() {
        val file = validArchive()
        val payload = BackupArchiveReader(file).read()
        assertEquals(BackupFormat.MAGIC, payload.manifest.format)
        assertEquals(BackupFormat.CURRENT_FORMAT_VERSION, payload.manifest.formatVersion)
        assertEquals(BackupFormat.CURRENT_DATA_SCHEMA_VERSION, payload.manifest.dataSchemaVersion)
        assertEquals(BackupFormat.REQUIRED_SECTIONS.size, payload.manifest.sections.size)
        assertEquals(8, payload.sections.size)
        assertEquals(1, payload.files.size)
        BackupArchiveReader(file).verify()
    }

    @Test
    fun `a checksum mismatch is rejected`() {
        val file = validArchive("checksum.ironpulse")
        val corrupt = temp.newFile("checksum-corrupt.ironpulse")
        rewriteEntry(file, corrupt, "payload/profile.json", "{}".toByteArray())
        assertFailure(corrupt, BackupErrorCode.CHECKSUM_MISMATCH)
    }

    @Test
    fun `a missing required section is rejected`() {
        val file = validArchive("missing.ironpulse")
        val manifest = readManifest(file)
        val changed = manifest.copy(sections = manifest.sections - BackupFormat.SECTION_WORKOUTS)
        val corrupt = temp.newFile("missing-corrupt.ironpulse")
        rewriteEntry(
            file,
            corrupt,
            BackupFormat.MANIFEST_PATH,
            BackupJson.toBytes(changed, BackupManifest::class.java)
        )
        assertFailure(corrupt, BackupErrorCode.MISSING_SECTION)
    }

    @Test
    fun `a newer format version is rejected`() {
        val file = validArchive("newer-format.ironpulse")
        val manifest = readManifest(file).copy(formatVersion = 2)
        val future = temp.newFile("newer-format-corrupt.ironpulse")
        rewriteEntry(
            file,
            future,
            BackupFormat.MANIFEST_PATH,
            BackupJson.toBytes(manifest, BackupManifest::class.java)
        )
        assertFailure(future, BackupErrorCode.UNSUPPORTED_FORMAT)
    }

    @Test
    fun `a newer data schema is rejected`() {
        val file = validArchive("newer-schema.ironpulse")
        val manifest = readManifest(file).copy(dataSchemaVersion = 3)
        val future = temp.newFile("newer-schema-corrupt.ironpulse")
        rewriteEntry(
            file,
            future,
            BackupFormat.MANIFEST_PATH,
            BackupJson.toBytes(manifest, BackupManifest::class.java)
        )
        assertFailure(future, BackupErrorCode.UNSUPPORTED_SCHEMA)
    }

    @Test
    fun `invalid manifest JSON is rejected`() {
        val file = validArchive("bad-manifest.ironpulse")
        val corrupt = temp.newFile("bad-manifest-corrupt.ironpulse")
        rewriteEntry(file, corrupt, BackupFormat.MANIFEST_PATH, "{not json".toByteArray())
        assertFailure(corrupt, BackupErrorCode.INVALID_MANIFEST)
    }

    @Test
    fun `a truncated archive is rejected`() {
        val file = validArchive("truncated-source.ironpulse")
        val bytes = file.readBytes()
        val truncated = temp.newFile("truncated.ironpulse")
        truncated.writeBytes(bytes.copyOf(bytes.size / 2))
        assertFailure(truncated, BackupErrorCode.MALFORMED_ARCHIVE)
    }

    @Test
    fun `a non zip file is rejected`() {
        val notZip = temp.newFile("notes.ironpulse")
        notZip.writeText("this is not an archive")
        assertFailure(notZip, BackupErrorCode.NOT_AN_ARCHIVE)
    }

    @Test
    fun `path traversal entries are rejected`() {
        val file = temp.newFile("traversal.ironpulse")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("payload/../escape.json"))
            zip.write("{}".toByteArray())
            zip.closeEntry()
        }
        assertFailure(file, BackupErrorCode.MALFORMED_ARCHIVE)
    }

    @Test
    fun `archive limits reject an otherwise valid file`() {
        val file = validArchive("large-limit.ironpulse")
        try {
            BackupArchiveReader(
                file,
                BackupArchiveLimits(maxArchiveBytes = 16L)
            ).read()
            fail("expected a size rejection")
        } catch (e: BackupException) {
            assertEquals(BackupErrorCode.TOO_LARGE, e.code)
        }
    }

    @Test
    fun `the archive is byte for byte deterministic for the same snapshot`() {
        val a = validArchive("deterministic-a.ironpulse")
        val b = validArchive("deterministic-b.ironpulse")
        assertEquals(
            BackupArchiveReader.sha256(a.readBytes()),
            BackupArchiveReader.sha256(b.readBytes())
        )
    }

    private fun readManifest(file: File): BackupManifest =
        readEntry(file, BackupFormat.MANIFEST_PATH).let {
            BackupJson.parse(it, BackupManifest::class.java)
        }

    private fun readEntry(file: File, name: String): ByteArray = ZipFile(file).use { zip ->
        zip.getInputStream(zip.getEntry(name)).use { it.readBytes() }
    }

    private fun rewriteEntry(source: File, target: File, name: String, replacement: ByteArray) {
        ZipInputStream(source.inputStream()).use { input ->
            ZipOutputStream(target.outputStream()).use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    val entry = input.nextEntry ?: break
                    output.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == name) {
                        output.write(replacement)
                    } else {
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                    output.closeEntry()
                }
            }
        }
    }

    private fun assertFailure(file: File, code: BackupErrorCode) {
        try {
            BackupArchiveReader(file).read()
            fail("expected ${code.name}")
        } catch (e: BackupException) {
            assertEquals(code, e.code)
            assertTrue(e.message!!.isNotBlank())
        }
    }
}
