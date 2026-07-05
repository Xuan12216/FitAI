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
    private val JSON_FENCE_REGEX = Regex("^```(?:json)?\\s*|\\s*```$", RegexOption.IGNORE_CASE)

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
        return if (start != -1 && end > start) {
            sanitizeJsonObject(content.substring(start, end + 1))
        } else {
            ""
        }
    }

    /**
     * Extracts a JSON array string from [rawText].
     *
     * Returns an empty string if no JSON array delimiters are found.
     */
    fun extractJsonArray(rawText: String): String {
        val content = extractContent(rawText)
        val start = content.indexOf('[')
        val end = content.lastIndexOf(']')
        return if (start != -1 && end > start) {
            content.substring(start, end + 1)
        } else {
            ""
        }
    }

    fun extractJsonStringValue(rawText: String, key: String): String? {
        return extractString(extractContent(rawText), key)
    }

    private fun sanitizeJsonObject(json: String): String {
        val result = StringBuilder(json.length)
        var inString = false
        var escaping = false
        var previousStructuralComma = false

        for (char in json) {
            if (inString) {
                result.append(char)
                if (escaping) {
                    escaping = false
                } else if (char == '\\') {
                    escaping = true
                } else if (char == '"') {
                    inString = false
                }
                continue
            }

            when (char) {
                '"' -> {
                    inString = true
                    previousStructuralComma = false
                    result.append(char)
                }
                ',' -> {
                    if (!previousStructuralComma) {
                        result.append(char)
                        previousStructuralComma = true
                    }
                }
                ' ', '\n', '\r', '\t' -> result.append(char)
                '}', ']' -> {
                    removeTrailingComma(result)
                    previousStructuralComma = false
                    result.append(char)
                }
                else -> {
                    previousStructuralComma = false
                    result.append(char)
                }
            }
        }

        return result.toString()
    }

    private fun removeTrailingComma(builder: StringBuilder) {
        var index = builder.length - 1
        while (index >= 0 && builder[index].isWhitespace()) {
            index--
        }
        if (index >= 0 && builder[index] == ',') {
            builder.deleteCharAt(index)
        }
    }

    fun extractPartialFoodAnalysis(rawText: String, thinkingText: String?): GemmaFoodAnalysis? {
        val content = extractContent(rawText).replace(JSON_FENCE_REGEX, "").trim()
        val calories = extractNumber(content, "calories")
        val protein = extractNumber(content, "protein")
        val carbs = extractNumber(content, "carbs")
        val fat = extractNumber(content, "fat")

        if (calories == null && protein == null && carbs == null && fat == null) {
            return null
        }

        return GemmaFoodAnalysis(
            calories = calories ?: 200f,
            protein = protein ?: 8f,
            carbs = carbs ?: 20f,
            fat = fat ?: 6f,
            isSuitable = extractBoolean(content, "suitable") ?: true,
            advice = extractString(content, "advice")
                ?: "AI 已估算營養數值，但建議文字輸出不完整。",
            reasoning = extractString(content, "reasoning")
                ?: "AI 回覆的 JSON 尚未完整結束，已使用可讀取的欄位估算。",
            thinking = thinkingText
        )
    }

    private fun extractNumber(text: String, key: String): Float? {
        val regex = Regex("\"$key\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
        return regex.find(text)?.groupValues?.getOrNull(1)?.toFloatOrNull()
    }

    private fun extractBoolean(text: String, key: String): Boolean? {
        val regex = Regex("\"$key\"\\s*:\\s*(true|false)", RegexOption.IGNORE_CASE)
        return regex.find(text)?.groupValues?.getOrNull(1)?.equals("true", ignoreCase = true)
    }

    private fun extractString(text: String, key: String): String? {
        val strictRegex = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", RegexOption.DOT_MATCHES_ALL)
        val strictMatch = strictRegex.find(text)
        if (strictMatch != null) {
            return unescapeJsonString(strictMatch.groupValues[1]).trim().ifBlank { null }
        }

        val startRegex = Regex("\"$key\"\\s*:\\s*\"", RegexOption.DOT_MATCHES_ALL)
        val startMatch = startRegex.find(text) ?: return null
        val valueStart = startMatch.range.last + 1
        val partial = text.substring(valueStart)
            .substringBefore("\n```")
            .trim()
            .trimEnd(',', '}', ']', '`')
        return unescapeJsonString(partial).trim().ifBlank { null }
    }

    private fun unescapeJsonString(value: String): String {
        return value
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
    }

    fun withThinkingContent(thinkingText: String?, contentText: String): String {
        val content = contentText.trim()
        val thinking = thinkingText?.trim().orEmpty()
        return if (thinking.isBlank()) {
            content
        } else {
            "<|channel>thought\n$thinking\n<channel|>\n$content"
        }
    }
}
