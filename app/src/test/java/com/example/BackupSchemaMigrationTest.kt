/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import com.example.data.backup.BackupErrorCode
import com.example.data.backup.BackupException
import com.example.data.backup.BackupSchemaMigration
import com.example.data.backup.BackupSchemaMigrations
import com.example.data.backup.JsonNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupSchemaMigrationTest {

    private class AddField(
        override val fromVersion: Int,
        override val toVersion: Int,
        private val field: String,
        private val value: String
    ) : BackupSchemaMigration {
        override fun migrate(sections: MutableMap<String, JsonNode.Obj>) {
            sections.getValue("profile")[field] = JsonNode.Str(value)
        }
    }

    @Test
    fun `migrations run in contiguous order and preserve existing fields`() {
        val sections = mutableMapOf<String, JsonNode.Obj>(
            "profile" to JsonNode.Obj(
                linkedMapOf(
                    "name" to JsonNode.Str("Athlete"),
                    "weight" to JsonNode.Num("81.25"),
                    "timestamp" to JsonNode.Num("1760000000000")
                )
            )
        )
        val migrations = listOf(
            AddField(1, 2, "first", "one"),
            AddField(2, 3, "second", "two")
        )

        BackupSchemaMigrations.migrate(sections, fromVersion = 1, toVersion = 3, migrations = migrations)

        val profile = sections.getValue("profile")
        assertEquals("Athlete", (profile["name"] as JsonNode.Str).value)
        assertEquals("81.25", (profile["weight"] as JsonNode.Num).literal)
        assertEquals("1760000000000", (profile["timestamp"] as JsonNode.Num).literal)
        assertEquals("one", (profile["first"] as JsonNode.Str).value)
        assertEquals("two", (profile["second"] as JsonNode.Str).value)
    }

    @Test
    fun `a missing migration step is rejected instead of guessed`() {
        val sections = mutableMapOf<String, JsonNode.Obj>("profile" to JsonNode.Obj())
        try {
            BackupSchemaMigrations.migrate(
                sections,
                fromVersion = 1,
                toVersion = 3,
                migrations = listOf(AddField(1, 2, "first", "one"))
            )
            fail("expected an unsupported-schema failure")
        } catch (e: BackupException) {
            assertEquals(BackupErrorCode.UNSUPPORTED_SCHEMA, e.code)
        }
    }

    @Test
    fun `migration refuses to go backwards or start below the supported version`() {
        val sections = mutableMapOf<String, JsonNode.Obj>("profile" to JsonNode.Obj())
        try {
            BackupSchemaMigrations.migrate(sections, fromVersion = 2, toVersion = 1, migrations = emptyList())
            fail("expected a failure")
        } catch (e: BackupException) {
            assertEquals(BackupErrorCode.UNSUPPORTED_SCHEMA, e.code)
        }
        try {
            BackupSchemaMigrations.migrate(sections, fromVersion = 0, toVersion = 1, migrations = emptyList())
            fail("expected a failure")
        } catch (e: BackupException) {
            assertEquals(BackupErrorCode.UNSUPPORTED_SCHEMA, e.code)
        }
    }

    @Test
    fun `json tree keeps numeric literals exactly`() {
        val source = """{"weight":81.250,"timestamp":1760000000000,"nested":{"distance":1.2300}}"""
        val node = JsonNode.parse(source.toByteArray())
        assertTrue(node is JsonNode.Obj)
        val rendered = node.render()
        assertTrue(rendered.contains("81.250"))
        assertTrue(rendered.contains("1760000000000"))
        assertTrue(rendered.contains("1.2300"))
    }
}
