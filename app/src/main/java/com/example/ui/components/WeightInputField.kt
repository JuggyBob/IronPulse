package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.example.data.model.WeightConversion
import com.example.data.model.WeightUnit
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

/**
 * Weight input that speaks the athlete's unit.
 *
 * The value shown and typed is pounds or kilograms, whatever the athlete chose; what leaves this
 * field is always the stored kilograms. The text is kept locally so a number can be typed freely
 * ("2" on the way to "225") without the field reformatting itself mid keystroke.
 *
 * A number typed with an explicit unit ("225 lb") is honoured, so a weight copied from a program
 * written in the other system is never silently stored wrong.
 */
@Composable
fun WeightInputField(
    label: String,
    kgValue: Double,
    unit: WeightUnit,
    onKgChanged: (Double) -> Unit,
    modifier: Modifier = Modifier,
    labelColor: Color = TextMuted,
    placeholder: String = "0",
    testTag: String? = null,
    allowEmptyAsZero: Boolean = true
) {
    var text by remember(kgValue, unit) {
        mutableStateOf(
            if (kgValue == 0.0 && allowEmptyAsZero) {
                ""
            } else {
                WeightConversion.format(kgValue, unit, decimals = if (unit.isPounds) 1 else 1, withUnit = false)
            }
        )
    }

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = labelColor
        )
        OutlinedTextField(
            value = text,
            onValueChange = { newValue ->
                text = newValue
                val parsed = WeightConversion.parseToKg(newValue, unit)
                if (parsed != null) {
                    onKgChanged(parsed)
                } else if (newValue.isBlank() && allowEmptyAsZero) {
                    onKgChanged(0.0)
                }
            },
            placeholder = { Text(placeholder, color = TextMuted) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkBg,
                unfocusedContainerColor = DarkBg,
                focusedBorderColor = ElectricCyan,
                unfocusedBorderColor = DarkBorder,
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
        )
    }
}
