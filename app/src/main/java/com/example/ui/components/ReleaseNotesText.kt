package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

/**
 * Renders the release notes of a GitHub release.
 *
 * The notes are written in Markdown by the maintainer, and they are shown as they were written.
 * Only a small, deliberate part of Markdown is understood: headings, bullet points and bold text.
 *
 * Nothing else is interpreted. There is no HTML, no link that is followed, and nothing in the text
 * can become anything but text, so a release description can never do anything but be read.
 */
@Composable
fun ReleaseNotesText(
    notes: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val lines = notes.lines()
        var firstHeadingSkipped = false
        // The notes are written as a list, and a point that is too long for one line is wrapped
        // onto the next one. The wrapped part belongs to the point above it, not to a new
        // paragraph, so the two are put back together.
        val blocks = mutableListOf<Pair<Boolean, String>>()
        var currentBullet: String? = null
        for (rawLine in lines) {
            val line = rawLine.trim()
            when {
                line.startsWith("- ") -> {
                    currentBullet?.let { blocks.add(true to it) }
                    currentBullet = line.removePrefix("- ").trim()
                }
                line.isNotEmpty() && currentBullet != null && !line.startsWith("#") -> {
                    currentBullet = "$currentBullet $line"
                }
                else -> {
                    currentBullet?.let { blocks.add(true to it) }
                    currentBullet = null
                    blocks.add(false to line)
                }
            }
        }
        currentBullet?.let { blocks.add(true to it) }

        for ((isBullet, text) in blocks) {
            when {
                text.isBlank() -> Spacer(modifier = Modifier.padding(vertical = 4.dp))

                text.startsWith("#") -> {
                    val level = text.takeWhile { it == '#' }.length
                    val heading = text.dropWhile { it == '#' }.trim()
                    // The first heading is the release title, which the dialog already shows.
                    if (!firstHeadingSkipped && heading.isNotEmpty()) {
                        firstHeadingSkipped = true
                    } else {
                        NotesHeading(text = heading, level = level)
                    }
                }

                isBullet -> {
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = ElectricCyan,
                            modifier = Modifier.width(12.dp)
                        )
                        Text(
                            text = inlineBold(text),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextWhite
                        )
                    }
                }

                else -> {
                    Text(
                        text = inlineBold(text),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * A heading in the release notes.
 */
@Composable
private fun NotesHeading(text: String, level: Int) {
    val style = when (level) {
        1 -> MaterialTheme.typography.titleSmall
        2 -> MaterialTheme.typography.bodyMedium
        else -> MaterialTheme.typography.labelLarge
    }
    Text(
        text = inlineBold(text),
        style = style.copy(fontWeight = FontWeight.Bold),
        color = if (level == 1) ElectricCyan else TextWhite,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}
/**
 * Reads the bold parts of a line.
 *
 * `**this**` becomes bold text and everything else stays plain. The markers themselves are never
 * shown, and nothing else in the line is interpreted.
 */
private fun inlineBold(text: String): AnnotatedString = buildAnnotatedString {
    var rest = text
    while (true) {
        val start = rest.indexOf("**")
        if (start < 0) break
        val end = rest.indexOf("**", start + 2)
        if (end < 0) break
        append(rest.substring(0, start))
        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        append(rest.substring(start + 2, end))
        pop()
        rest = rest.substring(end + 2)
    }
    append(rest)
}
