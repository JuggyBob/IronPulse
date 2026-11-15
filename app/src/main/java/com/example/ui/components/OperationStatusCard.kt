/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * This is new code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.FireRed
import com.example.ui.theme.GoldenAmber
import com.example.ui.theme.MintGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

/**
 * One compact success/failure notice for imports, exports and cloud backups.
 *
 * The dismiss action belongs to the card itself: it sits at the end of the first row rather than
 * under the message where it used to leave a detached, uneven block. The card grows with long
 * messages and keeps the same visual language as the rest of IronPulse.
 */
@Composable
fun OperationStatusCard(
    title: String,
    detail: String? = null,
    label: String? = null,
    isError: Boolean = false,
    neutral: Boolean = false,
    onDismiss: (() -> Unit)? = null,
    dismissTestTag: String = "operation_status_dismiss",
    modifier: Modifier = Modifier,
    /**
     * Optional one-action slot under the description. Used by the settings warning that has to
     * take the athlete to the provider setup without leaving the card.
     */
    action: (@Composable () -> Unit)? = null
) {
    val accent = when {
        isError -> FireRed
        neutral -> GoldenAmber
        else -> MintGreen
    }
    val icon = when {
        isError -> Icons.Default.ErrorOutline
        neutral -> Icons.Default.Info
        else -> Icons.Default.CheckCircle
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(tween(180)),
        color = DarkSurface,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.42f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 11.dp, bottom = 11.dp, end = if (onDismiss != null) 4.dp else 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(accent.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                label?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextWhite
                )
                detail?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        lineHeight = MaterialTheme.typography.labelSmall.lineHeight
                    )
                }
                action?.let {
                    Spacer(Modifier.height(4.dp))
                    it()
                }
            }
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag(dismissTestTag)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = TextMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}
