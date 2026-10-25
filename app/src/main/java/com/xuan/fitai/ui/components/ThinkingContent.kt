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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    isGenerating: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {}
) {
    val regex = Regex("<\\|?channel\\|?>([a-zA-Z0-9_]*)")
    val matches = regex.findAll(rawText).toList()
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val accentColor = MaterialTheme.colorScheme.primary

    // No channel tags — render as formatted Markdown text
    if (matches.isEmpty()) {
        val annotated = remember(rawText, codeBackground, accentColor) {
            parseMarkdownToAnnotatedString(rawText, codeBackground, accentColor)
        }
        Text(
            text = annotated,
            style = contentTextStyle,
            modifier = modifier,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = onTextLayout
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
                            text = "💭 查看 AI 思考過程",
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
                            val annotatedThinking = remember(thinkingText, codeBackground, accentColor) {
                                parseMarkdownToAnnotatedString(thinkingText.orEmpty(), codeBackground, accentColor)
                            }
                            Text(
                                text = annotatedThinking,
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
            val annotatedContent = remember(contentText, codeBackground, accentColor) {
                parseMarkdownToAnnotatedString(contentText, codeBackground, accentColor)
            }
            Text(
                text = annotatedContent,
                style = contentTextStyle,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = onTextLayout
            )
        }
    }
}

/**
 * Extracts only the visible answer portion from raw model text (stripping `<|channel>thought...` sections).
 */
fun extractAnswerWithoutThinking(rawText: String): String {
    val regex = Regex("<\\|?channel\\|?>([a-zA-Z0-9_]*)")
    val matches = regex.findAll(rawText).toList()
    if (matches.isEmpty()) return rawText.trim()
    var contentText = ""
    for (i in matches.indices) {
        val match = matches[i]
        val channelName = match.groupValues[1]
        val startIdx = match.range.last + 1
        val endIdx = if (i + 1 < matches.size) matches[i + 1].range.first else rawText.length
        val sectionText = rawText.substring(startIdx, endIdx).trim()
        if (channelName != "thought" && channelName != "thinking") {
            contentText = sectionText
        }
    }
    return contentText.ifBlank { rawText }.trim()
}

internal fun parseMarkdownToAnnotatedString(
    markdown: String,
    codeBackground: Color = Color.Transparent,
    accentColor: Color = Color.Unspecified
): AnnotatedString = buildAnnotatedString {
    val normalized = markdown.replace("\r\n", "\n")
    val lines = normalized.split("\n")
    var inCodeBlock = false
    val headingRegex = Regex("^\\s*(#{1,6})\\s+(.*)$")
    val hrRegex = Regex("^\\s*([-*_]){3,}\\s*$")
    val bulletRegex = Regex("^(\\s*)[-*+•]\\s+(.*)$")
    val numberedRegex = Regex("^(\\s*)(\\d+\\.)\\s+(.*)$")
    val quoteRegex = Regex("^\\s*>\\s?(.*)$")
    val tableDividerRegex = Regex("^\\s*\\|?[\\s:|-]+\\|?\\s*$")

    var emittedLines = 0
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            inCodeBlock = !inCodeBlock
            continue
        }
        if (inCodeBlock) {
            if (emittedLines > 0) append("\n")
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                append(line)
            }
            emittedLines++
            continue
        }
        if (hrRegex.matches(trimmed)) {
            continue
        }
        if (trimmed.startsWith("|") && tableDividerRegex.matches(trimmed) && trimmed.contains("-")) {
            continue
        }

        if (emittedLines > 0) append("\n")
        emittedLines++

        val headingMatch = headingRegex.matchEntire(line)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            val titleText = headingMatch.groupValues[2].trim()
            val fontSize = when (level) {
                1 -> 18.sp
                2 -> 17.sp
                3 -> 16.sp
                else -> 15.sp
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = fontSize)) {
                appendInlineMarkdown(titleText, codeBackground, accentColor)
            }
            continue
        }

        val bulletMatch = bulletRegex.matchEntire(line)
        if (bulletMatch != null) {
            val indent = bulletMatch.groupValues[1].length
            val itemText = bulletMatch.groupValues[2]
            val prefix = if (indent >= 2) "  ◦ " else "• "
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accentColor)) {
                append(prefix)
            }
            appendInlineMarkdown(itemText, codeBackground, accentColor)
            continue
        }

        val numberedMatch = numberedRegex.matchEntire(line)
        if (numberedMatch != null) {
            val indent = if (numberedMatch.groupValues[1].length >= 2) "  " else ""
            val numPrefix = numberedMatch.groupValues[2]
            val itemText = numberedMatch.groupValues[3]
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accentColor)) {
                append("$indent$numPrefix ")
            }
            appendInlineMarkdown(itemText, codeBackground, accentColor)
            continue
        }

        val quoteMatch = quoteRegex.matchEntire(line)
        if (quoteMatch != null) {
            val quoteText = quoteMatch.groupValues[1]
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accentColor)) {
                append("▎ ")
            }
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendInlineMarkdown(quoteText, codeBackground, accentColor)
            }
            continue
        }

        // Table row formatting: | A | B | C | -> A · B · C
        if (trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.count { it == '|' } >= 3) {
            val cells = trimmed.removePrefix("|").removeSuffix("|").split("|").map { it.trim() }
            cells.forEachIndexed { idx, cell ->
                if (idx > 0) append("  ｜  ")
                appendInlineMarkdown(cell, codeBackground, accentColor)
            }
            continue
        }

        appendInlineMarkdown(line, codeBackground, accentColor)
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(
    text: String,
    codeBackground: Color,
    accentColor: Color
) {
    var i = 0
    val n = text.length
    while (i < n) {
        when {
            // Bold + Italic: ***text***
            text.startsWith("***", i) -> {
                val end = text.indexOf("***", i + 3)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                        appendInlineMarkdown(text.substring(i + 3, end), codeBackground, accentColor)
                    }
                    i = end + 3
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 3))
                    }
                    break
                }
            }
            // Bold: **text** or __text__
            text.startsWith("**", i) || text.startsWith("__", i) -> {
                val marker = text.substring(i, i + 2)
                val end = text.indexOf(marker, i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        appendInlineMarkdown(text.substring(i + 2, end), codeBackground, accentColor)
                    }
                    i = end + 2
                } else {
                    // Unclosed bold during streaming: bold the remainder cleanly without showing raw **
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2))
                    }
                    break
                }
            }
            // Strikethrough: ~~text~~
            text.startsWith("~~", i) -> {
                val end = text.indexOf("~~", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        appendInlineMarkdown(text.substring(i + 2, end), codeBackground, accentColor)
                    }
                    i = end + 2
                } else {
                    append("~~")
                    i += 2
                }
            }
            // Inline code: `code`
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append('`')
                    i++
                }
            }
            // Link: [title](url)
            text[i] == '[' -> {
                val closeBracket = text.indexOf(']', i + 1)
                val openParen = if (closeBracket != -1 && closeBracket + 1 < n && text[closeBracket + 1] == '(') closeBracket + 1 else -1
                val closeParen = if (openParen != -1) text.indexOf(')', openParen + 1) else -1
                if (closeParen != -1) {
                    val label = text.substring(i + 1, closeBracket)
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = accentColor, textDecoration = TextDecoration.Underline)) {
                        appendInlineMarkdown(label, codeBackground, accentColor)
                    }
                    i = closeParen + 1
                } else {
                    append('[')
                    i++
                }
            }
            // Italic: *text* (only when not followed by space)
            text[i] == '*' && i + 1 < n && text[i + 1] != ' ' && text[i + 1] != '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end != -1 && text[end - 1] != ' ') {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendInlineMarkdown(text.substring(i + 1, end), codeBackground, accentColor)
                    }
                    i = end + 1
                } else {
                    append('*')
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
