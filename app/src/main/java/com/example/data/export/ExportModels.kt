package com.example.data.export

import android.graphics.Bitmap

/** Immutable content shared by PDF, CSV, XLSX, preview and the saved file. */
data class ExportDocument(
    val title: String,
    val subtitle: String = "",
    val generatedAt: Long = System.currentTimeMillis(),
    val sections: List<ExportSection> = emptyList(),
    val tables: List<ExportTable> = emptyList()
)

sealed interface ExportSection {
    data class Heading(val text: String) : ExportSection
    data class Paragraph(val text: String) : ExportSection
    data class Bullets(val items: List<String>) : ExportSection
    data class TableSection(val table: ExportTable) : ExportSection
    data class ImageSection(val image: ExportImage) : ExportSection
    data class ExerciseBlocks(val blocks: List<ExportExerciseBlock>) : ExportSection
    data object PageBreak : ExportSection
}

/** One exercise card in the PDF's two-column body. */
data class ExportExerciseBlock(
    val name: String,
    val image: ExportImage?,
    val instructions: List<String>,
    val sets: List<ExportSetBullet>,
    val supersetLabel: String? = null,
    val supersetGroupId: String? = null
)

data class ExportSetBullet(
    val text: String,
    val isDrop: Boolean = false
)

data class ExportTable(
    val name: String,
    val columns: List<String>,
    val rows: List<List<String?>>
)

data class ExportImage(
    val bitmap: Bitmap?,
    val attribution: String? = null
)
