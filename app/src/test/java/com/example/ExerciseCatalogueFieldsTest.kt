/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example

import com.example.data.model.ExerciseField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONArray
import java.io.File

/**
 * The shipped catalogue, audited as a whole.
 *
 * A sample is not enough here: every one of the 1,334 records has to declare a valid, non-empty
 * field list, and that list has to agree with the audit matrix in `docs/`. If someone edits the
 * asset without rerunning `tools/audit_exercise_fields.py`, this test fails instead of shipping a
 * silently incomplete exercise.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExerciseCatalogueFieldsTest {

    private fun locate(vararg candidates: String): File =
        candidates.map(::File).firstOrNull { it.exists() }
            ?: error("None of ${candidates.toList()} exists")

    private fun records(): JSONArray {
        val asset = locate(
            "src/main/assets/exercises.json",
            "app/src/main/assets/exercises.json"
        )
        return JSONArray(asset.readText(Charsets.UTF_8))
    }

    /** Minimal RFC 4180 reader for the audit matrix (quoted commas and doubled quotes). */
    private fun readCsv(file: File): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        val text = file.readText(Charsets.UTF_8)
        while (index < text.length) {
            val ch = text[index]
            when {
                quoted && ch == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                    cell.append('"')
                    index++
                }
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> {
                    row.add(cell.toString())
                    cell.clear()
                }
                (ch == '\n' || ch == '\r') && !quoted -> {
                    if (ch == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                    if (cell.isNotEmpty() || row.isNotEmpty()) {
                        row.add(cell.toString())
                        rows.add(row.toList())
                        row.clear()
                        cell.clear()
                    }
                }
                else -> cell.append(ch)
            }
            index++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            rows.add(row.toList())
        }
        return rows
    }

    @Test
    fun `every catalogue record declares a valid complete field list`() {
        val records = records()
        assertEquals("the bundled catalogue size changed", 1334, records.length())

        val validIds = ExerciseField.entries.map { it.id }.toSet()
        val ids = mutableSetOf<String>()
        val failures = mutableListOf<String>()

        for (index in 0 until records.length()) {
            val record = records.getJSONObject(index)
            val id = record.optString("id")
            val name = record.optString("name")
            if (id.isBlank()) failures += "record $index has no stable id"
            if (!ids.add(id)) failures += "$id ($name) duplicates another stable id"
            if (name.isBlank()) failures += "$id has no name"

            val raw = record.optJSONArray("fields")
            if (raw == null || raw.length() == 0) {
                failures += "$id ($name) has no explicit fields"
                continue
            }
            val fields = (0 until raw.length()).map { raw.optString(it).trim() }
            if (fields.size != fields.distinct().size) {
                failures += "$id ($name) lists a field twice: $fields"
            }
            fields.filterNot(validIds::contains).forEach { unknown ->
                failures += "$id ($name) uses unknown field '$unknown'"
            }
            if (ExerciseField.WEIGHT.id in fields && ExerciseField.ADDED_WEIGHT.id in fields) {
                failures += "$id ($name) combines weight and added weight"
            }
            if (ExerciseField.HOLD_SECONDS.id in fields && ExerciseField.TIME_SECONDS.id in fields) {
                failures += "$id ($name) combines hold and time"
            }
        }

        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `the audit matrix accounts for every record and matches the asset`() {
        val asset = locate(
            "src/main/assets/exercises.json",
            "app/src/main/assets/exercises.json"
        )
        val audit = locate(
            "../docs/exercise_field_audit.csv",
            "docs/exercise_field_audit.csv"
        )
        val records = JSONArray(asset.readText(Charsets.UTF_8))
        val byId = (0 until records.length()).associate { index ->
            val record = records.getJSONObject(index)
            record.getString("id") to record
        }

        val rows = readCsv(audit)
        assertEquals("audit header", listOf(
            "Exercise ID", "Exercise name", "Category", "Body part", "Target",
            "Exercise type", "Equipment", "Existing fields", "Recommended fields",
            "Added", "Removed", "Reason", "Validation"
        ), rows.first())
        assertEquals("audited records", 1334, rows.size - 1)

        val failures = mutableListOf<String>()
        rows.drop(1).forEach { row ->
            val id = row[0]
            val record = byId[id] ?: run {
                failures += "audit row $id has no catalogue record"
                return@forEach
            }
            val recommended = row[8]
            val actual = record.getJSONArray("fields").let { array ->
                (0 until array.length()).joinToString(",") { array.getString(it) }
            }
            if (recommended != actual) {
                failures += "$id ${record.optString("name")}: audit=$recommended asset=$actual"
            }
            if (row[11].isBlank()) failures += "$id has no audit reason"
            if (row[12].isBlank()) failures += "$id has no audit validation"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `representative corrections survive future audits`() {
        val records = records()
        val byId = (0 until records.length()).associate { index ->
            val record = records.getJSONObject(index)
            record.getString("id") to record
        }

        fun fields(id: String): List<String> =
            byId.getValue(id).getJSONArray("fields").let { array ->
                (0 until array.length()).map { array.getString(it) }
            }

        // Plank family: duration plus optional added load, regardless of the bodyweight type.
        assertEquals(
            listOf("holdSeconds", "addedWeight"),
            fields("2135") // Weighted Front Plank
        )
        assertEquals(
            listOf("holdSeconds", "addedWeight"),
            fields("3544") // Bodyweight Incline Side Plank
        )
        // Weighted Plank shipped as STANDARD_WEIGHT and still needs added weight, not a barbell.
        assertEquals(listOf("holdSeconds", "addedWeight"), fields("5216"))
        // Carries need load plus time and distance.
        assertEquals(listOf("weight", "timeSeconds", "distanceKm"), fields("2133"))
        // Conditioning is duration work; the sprint variant adds distance.
        assertEquals(listOf("timeSeconds"), fields("0128"))
        assertEquals(listOf("timeSeconds", "distanceKm"), fields("0858"))
        // A plain bodyweight repetition movement does not inherit an irrelevant hold.
        assertEquals(listOf("reps"), fields("0001"))
    }
}
