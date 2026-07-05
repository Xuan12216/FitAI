package com.xuan.fitai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Parses and renders an AI response that may contain thinking/reasoning sections.
 *
 * The model wraps its internal chain-of-thought in channel tags:
 *   <|channel>thought ... <channel|>actual answer
 *
 * When thinking content is detected, it is rendered as a collapsible card above
 * the main answer with smooth expand/collapse animations. When no channel tags
 * are present the text is displayed as-is.
 *
 * @param rawText           Raw text returned by the model (may or may not contain channel tags).
 * @param modifier          Modifier applied to the root Column.
 * @param contentTextStyle  Typography style for the main answer text.
 * @param cardCornerRadius  Corner radius of the collapsible thinking card.
 */
@Composable
fun ThinkingContent(
    rawText: String,
    modifier: Modifier = Modifier,
    contentTextStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    cardCornerRadius: Dp = 8.dp,
    isGenerating: Boolean = false
) {
    val regex = Regex("<\\|?channel\\|?>([a-zA-Z0-9_]*)")
    val matches = regex.findAll(rawText).toList()

    // No channel tags — render as plain text
    if (matches.isEmpty()) {
        Text(
            text = rawText,
            style = contentTextStyle,
            modifier = modifier
        )
        return
    }

    // Parse sections
    var thinkingText: String? = null
    var contentText = ""

    for (i in matches.indices) {
        val match = matches[i]
        val channelName = match.groupValues[1]
        val startIdx = match.range.last + 1
        val endIdx = if (i + 1 < matches.size) matches[i + 1].range.first else rawText.length
        val sectionText = rawText.substring(startIdx, endIdx).trim()

        if (channelName == "thought" || channelName == "thinking") {
            thinkingText = sectionText
        } else {
            contentText = sectionText
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // Collapsible thinking card
        if (!thinkingText.isNullOrBlank()) {
            var isExpanded by remember { mutableStateOf(false) }

            LaunchedEffect(isGenerating) {
                isExpanded = isGenerating
            }

            // Animate arrow rotation: 0° when collapsed, 180° when expanded
            val arrowRotation by animateFloatAsState(
                targetValue = if (isExpanded) 180f else 0f,
                animationSpec = tween(durationMillis = 300),
                label = "arrowRotation"
            )

            // Animate border colour: slightly more opaque when expanded
            val borderAlpha by animateFloatAsState(
                targetValue = if (isExpanded) 0.4f else 0.2f,
                animationSpec = tween(durationMillis = 300),
                label = "borderAlpha"
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = borderAlpha),
                        shape = RoundedCornerShape(cardCornerRadius)
                    )
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(cardCornerRadius)
                    )
            ) {
                // Header row (always visible, tappable)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(16.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(50)
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Show thinking (顯示思考過程)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Single icon that rotates instead of swapping between two icons
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "收起" else "展開",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(arrowRotation)
                    )
                }

                // Animated expand / collapse of thinking content
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(animationSpec = tween(durationMillis = 300)) +
                            fadeIn(animationSpec = tween(durationMillis = 250, delayMillis = 50)),
                    exit = shrinkVertically(animationSpec = tween(durationMillis = 300)) +
                            fadeOut(animationSpec = tween(durationMillis = 200))
                ) {
                    Column {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(IntrinsicSize.Max)
                                    .background(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(50)
                                    )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = thinkingText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Main answer text
        if (contentText.isNotBlank()) {
            Text(text = contentText, style = contentTextStyle)
        }
    }
}
