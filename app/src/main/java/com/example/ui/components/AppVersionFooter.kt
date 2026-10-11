package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.ui.theme.TextMuted

/**
 * The build stamp shown at the bottom of every tab and in the chat history sidebar.
 *
 * The version is read from [BuildConfig], which the Gradle script generates from `versionName` in
 * module :app, so this can never drift from the shipped build. Typed out by hand instead, the
 * footer was the one place in the app guaranteed to be wrong after a release.
 *
 * @param compact skips the vertical padding for the sidebar, which supplies its own spacing.
 */
@Composable
fun AppVersionFooter(
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
    compact: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (compact) Modifier else Modifier.padding(vertical = 18.dp))
            .testTag("app_version_footer"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "IronPulse ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted.copy(alpha = 0.7f),
            textAlign = textAlign
        )
    }
}
