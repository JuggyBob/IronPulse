package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.export.ExportFormat
import com.example.data.export.ExportState
import com.example.data.export.ExportTarget
import com.example.ui.theme.CoralOrange
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.OnAccent
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(viewModel: MainViewModel) {
    val state by viewModel.exportState.collectAsStateWithLifecycle()
    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.PDF.mimeType)
    ) { uri -> if (uri != null) viewModel.saveExport(uri) }
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.CSV.mimeType)
    ) { uri -> if (uri != null) viewModel.saveExport(uri) }
    val xlsxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.XLSX.mimeType)
    ) { uri -> if (uri != null) viewModel.saveExport(uri) }
    if (state is ExportState.Idle) return
    ModalBottomSheet(
        onDismissRequest = { viewModel.closeExport() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DarkSurface,
        contentColor = TextWhite,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .background(DarkBorder, RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 20.dp)
        ) {
            when (val current = state) {
                is ExportState.Choosing -> {
                    Text(
                        text = "Export ${targetLabel(current.target)}",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                    Text(
                        text = "Choose a format. The preview uses the same data as the saved file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ExportFormat.entries.forEach { format ->
                            FormatRow(
                                format = format,
                                onClick = { viewModel.chooseExportFormat(format) }
                            )
                        }
                    }
                }

                is ExportState.Building -> {
                    Row(
                        modifier = Modifier.padding(vertical = 28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = ElectricCyan, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Preparing ${current.format.label}…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextWhite
                        )
                    }
                }

                is ExportState.Ready -> {
                    Text(
                        text = current.document.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${current.format.label} preview",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (current.format == ExportFormat.PDF && current.pdfPages.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 480.dp)
                                .background(DarkBg, RoundedCornerShape(10.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(current.pdfPages.size) { index ->
                                Image(
                                    bitmap = current.pdfPages[index].asImageBitmap(),
                                    contentDescription = "PDF page ${index + 1}",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White, RoundedCornerShape(4.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    } else {
                        DataPreview(current)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ElectricCyan)
                                .clickable {
                                    when (current.format) {
                                        ExportFormat.PDF -> pdfLauncher.launch(current.suggestedName)
                                        ExportFormat.CSV -> csvLauncher.launch(current.suggestedName)
                                        ExportFormat.XLSX -> xlsxLauncher.launch(current.suggestedName)
                                    }
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Export",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = OnAccent
                            )
                        }
                        ExportTextAction("Cancel", TextMuted, modifier = Modifier.weight(1f)) {
                            viewModel.closeExport()
                        }
                    }
                }

                is ExportState.Error -> {
                    Text(
                        text = "Export failed",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = CoralOrange
                    )
                    Text(current.message, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    Spacer(modifier = Modifier.height(12.dp))
                    ExportTextAction("Close", ElectricCyan) { viewModel.closeExport() }
                }

                ExportState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun FormatRow(
    format: ExportFormat,
    onClick: () -> Unit
) {
    val icon = when (format) {
        ExportFormat.PDF -> Icons.Outlined.PictureAsPdf
        ExportFormat.CSV -> Icons.Outlined.TableChart
        ExportFormat.XLSX -> Icons.Outlined.GridOn
    }
    val description = when (format) {
        ExportFormat.PDF -> "Fixed-page document"
        ExportFormat.CSV -> "Comma-separated values"
        ExportFormat.XLSX -> "Excel workbook"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkBg)
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("export_format_${format.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(ElectricCyan.copy(alpha = 0.13f))
                .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ElectricCyan,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = format.label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = TextWhite
            )
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun DataPreview(state: ExportState.Ready) {
    val table = state.document.tables.firstOrNull()
    if (table == null) {
        Text("No rows to preview.", color = TextMuted)
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .background(DarkBg, RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                table.columns.forEach { column ->
                    Text(
                        text = column,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ElectricCyan,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        items(table.rows.size) { index ->
            val row = table.rows[index]
            Row(modifier = Modifier.fillMaxWidth()) {
                table.columns.indices.forEach { columnIndex ->
                    Text(
                        text = row.getOrNull(columnIndex).orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextWhite,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportTextAction(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

private fun targetLabel(target: ExportTarget): String = when (target) {
    is ExportTarget.Routine -> "routine"
    is ExportTarget.Schedule -> "schedule"
    is ExportTarget.Workout -> "workout"
    is ExportTarget.ExerciseHistory -> target.exerciseName
}
