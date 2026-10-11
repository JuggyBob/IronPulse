package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MintGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

/** Longest routine the builder allows, so the list can never overflow the screen. */
private const val MAX_ROUTINE_DAYS = 7

/**
 * Chooses which training day new exercises are assigned to.
 *
 * The entry count always follows [daysPerWeek]: a two day routine offers two entries and a four day
 * routine offers four, so a day that does not exist can never be picked.
 */
@Composable
fun DayDropdown(
    daysPerWeek: Int,
    selectedDay: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // A day count can only shrink while the routine is being edited, so the selection is clamped
    // rather than kept pointing at a day that no longer exists.
    val safeDays = daysPerWeek.coerceIn(1, MAX_ROUTINE_DAYS)
    val safeSelected = selectedDay.coerceIn(1, safeDays)

    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
                .border(1.dp, ElectricCyan.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 7.dp)
                .testTag("assign_day_dropdown"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Day $safeSelected of $safeDays",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MintGreen
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = "Choose the training day",
                tint = MintGreen,
                modifier = Modifier.size(16.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(DarkBg, RoundedCornerShape(10.dp))
                .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                .heightIn(max = 260.dp)
                .testTag("assign_day_dropdown_menu")
        ) {
            (1..safeDays).forEach { day ->
                val isSelected = day == safeSelected
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Day $day",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSelected) ElectricCyan else TextWhite
                        )
                    },
                    trailingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    onClick = {
                        onSelect(day)
                        expanded = false
                    },
                    modifier = Modifier.testTag("assign_day_menu_item_$day")
                )
            }
        }
    }
}
