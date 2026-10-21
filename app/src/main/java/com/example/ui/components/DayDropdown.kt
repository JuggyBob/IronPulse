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
import com.example.data.model.Weekday
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MintGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

/**
 * Chooses the weekday new exercises are assigned to.
 *
 * The seven days of the week are always offered: a routine is a week, and a day without an
 * exercise in it is a rest day rather than a day that does not exist. The days are listed in the
 * order the athlete's week runs in.
 *
 * @param exerciseCount how many exercises each weekday holds, so the menu can say which days are
 *        training days and which are rest days.
 */
@Composable
fun DayDropdown(
    selectedDay: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    weekStartsOnMonday: Boolean = true,
    exerciseCount: Map<Int, Int> = emptyMap()
) {
    val safeSelected = selectedDay.coerceIn(1, Weekday.COUNT)

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
                text = Weekday.name(safeSelected),
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
                .heightIn(max = 320.dp)
                .testTag("assign_day_dropdown_menu")
        ) {
            Weekday.ordered(weekStartsOnMonday).forEach { day ->
                val isSelected = day == safeSelected
                val count = exerciseCount[day] ?: 0
                DropdownMenuItem(
                    text = {
                        Text(
                            text = Weekday.name(day),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSelected) ElectricCyan else TextWhite
                        )
                    },
                    trailingIcon = {
                        Text(
                            text = if (count > 0) "$count ex" else "Rest",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (count > 0) MintGreen else TextMuted
                        )
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
