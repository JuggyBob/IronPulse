package com.example.data.export

/** What the export sheet is allowed to read. The target is chosen by the screen that opened it. */
sealed interface ExportTarget {
    data class Routine(val id: Long) : ExportTarget
    data class Schedule(val id: Long) : ExportTarget
    data class Workout(val id: Long) : ExportTarget
    data class ExerciseHistory(val exerciseName: String) : ExportTarget
}

enum class ExportFormat(val label: String, val mimeType: String, val extension: String) {
    PDF(
        "PDF",
        "application/pdf",
        "pdf"
    ),
    CSV(
        "CSV",
        "text/csv",
        "csv"
    ),
    XLSX(
        "Excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "xlsx"
    )
}
