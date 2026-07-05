package com.xuan.fitai.ai

/**
 * Utility for parsing raw text output from the Gemma model.
 *
 * When thinking mode is enabled the model wraps its chain-of-thought in
 * channel tags before emitting the actual answer:
 *
 *   <|channel>thought
 *   ... reasoning ...
 *   <channel|>actual answer here
 *
 * These helpers extract the relevant section from the raw output so that
 * callers receive clean, usable text rather than raw model tokens.
 */
object GemmaOutputParser {

    private val CHANNEL_REGEX = Regex("<\\|?channel\\|?>([a-zA-Z0-9_]*)")

    /**
     * Returns only the actual answer portion of [rawText], stripping any
     * thinking/reasoning preamble. If no channel tags are found the original
     * text is returned unchanged.
     */
    fun extractContent(rawText: String): String {
        val matches = CHANNEL_REGEX.findAll(rawText).toList()
        if (matches.isEmpty()) return rawText

        for (i in matches.indices) {
            val channelName = matches[i].groupValues[1]
            // Empty channel name or anything that isn't "thought"/"thinking" is the answer
            if (channelName != "thought" && channelName != "thinking") {
                val startIdx = matches[i].range.last + 1
                val endIdx = if (i + 1 < matches.size) matches[i + 1].range.first else rawText.length
                val section = rawText.substring(startIdx, endIdx).trim()
                if (section.isNotBlank()) return section
            }
        }

        // Fallback: if only a thinking section exists, return the text after the last tag
        val last = matches.last()
        return rawText.substring(last.range.last + 1).trim().ifBlank { rawText }
    }

    /**
     * Returns only the thinking/reasoning portion of [rawText], or null if
     * no thinking channel tag is present.
     */
    fun extractThinking(rawText: String): String? {
        val matches = CHANNEL_REGEX.findAll(rawText).toList()
        if (matches.isEmpty()) return null

        for (i in matches.indices) {
            val channelName = matches[i].groupValues[1]
            if (channelName == "thought" || channelName == "thinking") {
                val startIdx = matches[i].range.last + 1
                val endIdx = if (i + 1 < matches.size) matches[i + 1].range.first else rawText.length
                val section = rawText.substring(startIdx, endIdx).trim()
                return section.ifBlank { null }
            }
        }
        return null
    }

    /**
     * Extracts a JSON object string from [rawText]. Thinking content is
     * stripped first so that stray `{` / `}` inside the reasoning section do
     * not confuse the search for the actual JSON payload.
     *
     * Returns an empty string if no JSON object delimiters are found.
     */
    fun extractJson(rawText: String): String {
        val content = extractContent(rawText)
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        return if (start != -1 && end > start) content.substring(start, end + 1) else ""
    }
}
