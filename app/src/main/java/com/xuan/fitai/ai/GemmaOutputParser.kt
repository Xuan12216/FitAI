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
     * Splits Gemma Vision raw output into a Pair containing (thinkingText, foodName).
     * It filters out any conversational, list format, or thinking preamble if the model
     * did not use channel tags.
     */
    fun splitVisionOutput(rawText: String): Pair<String?, String> {
        val trimmed = rawText.trim()

        val matches = CHANNEL_REGEX.findAll(trimmed).toList()
        if (matches.isNotEmpty()) {
            var thinkingText: String? = null
            var contentText = ""

            for (i in matches.indices) {
                val match = matches[i]
                val channelName = match.groupValues[1]
                val startIdx = match.range.last + 1
                val endIdx = if (i + 1 < matches.size) matches[i + 1].range.first else trimmed.length
                val sectionText = trimmed.substring(startIdx, endIdx).trim()

                if (channelName == "thought" || channelName == "thinking") {
                    thinkingText = sectionText
                } else {
                    contentText = sectionText
                }
            }
            return Pair(thinkingText, contentText)
        }

        if (trimmed.length < 15 && !trimmed.contains("\n")) {
            return Pair(null, trimmed.trimEnd('.', '。', '!', '！'))
        }

        val startsWithThinking = trimmed.take(100).contains("thinking", ignoreCase = true) ||
                trimmed.take(100).contains("process", ignoreCase = true) ||
                trimmed.take(100).contains("analyze", ignoreCase = true) ||
                trimmed.take(100).contains("分析", ignoreCase = true) ||
                trimmed.take(100).contains("思考", ignoreCase = true) ||
                trimmed.startsWith("1.") || trimmed.startsWith("1. ") || trimmed.startsWith("1. **")

        if (startsWithThinking) {
            val lines = trimmed.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (lines.isNotEmpty()) {
                for (i in lines.indices.reversed()) {
                    val line = lines[i]
                    if (line.startsWith("*") || line.startsWith("-") || line.startsWith("•") || line.startsWith("#")) {
                        continue
                    }
                    if (line.contains("thinking", ignoreCase = true) || line.contains("process", ignoreCase = true) || 
                        line.contains("image", ignoreCase = true) || line.contains("analyze", ignoreCase = true) ||
                        line.contains("request", ignoreCase = true)) {
                        continue
                    }
                    if (line.length in 1..15) {
                        var cleanLine = line
                        if (cleanLine.startsWith("Answer:", ignoreCase = true)) {
                            cleanLine = cleanLine.substring(7).trim()
                        }
                        val numPrefixMatch = Regex("^\\d+\\.\\s*").find(cleanLine)
                        if (numPrefixMatch != null) {
                            cleanLine = cleanLine.substring(numPrefixMatch.range.last + 1).trim()
                        }

                        val cleanName = cleanLine.trimEnd('.', '。', '!', '！')

                        val lineIndex = trimmed.lastIndexOf(line)
                        val thinking = if (lineIndex > 0) trimmed.substring(0, lineIndex).trim() else null

                        return Pair(thinking, cleanName)
                    }
                }
            }

            return Pair(trimmed, "")
        }

        return Pair(null, trimmed)
    }

    /**
     * Extracts only the final concise food name from Gemma Vision raw output.
     */
    fun extractVisionFoodName(rawText: String): String {
        return splitVisionOutput(rawText).second
    }

    /**
     * Extracts the thinking process from Gemma Vision raw output.
     */
    fun extractVisionThinking(rawText: String): String? {
        return splitVisionOutput(rawText).first
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
    /**
     * Extracts the primary JSON object from [rawText]. If the JSON was truncated
     * before closing delimiters could be written, it automatically repairs and closes
     * any unclosed braces/brackets so it can still be parsed.
     *
     * Returns an empty string if no JSON object delimiters are found.
     */
    fun extractJson(rawText: String): String {
        val content = extractContent(rawText)
        val start = content.indexOf('{')
        if (start == -1) return ""
        val end = content.lastIndexOf('}')
        if (end > start) {
            val candidate = sanitizeJsonObject(content.substring(start, end + 1))
            try {
                org.json.JSONObject(candidate)
                return candidate
            } catch (_: Exception) {
                // Bracket mismatch or truncated interior, fall through to repair
            }
        }
        return repairJson(content.substring(start))
    }

    /**
     * Extracts a JSON array string from [rawText]. If the JSON was truncated
     * before closing brackets, it automatically repairs and closes them.
     *
     * Returns an empty string if no JSON array delimiters are found.
     */
    fun extractJsonArray(rawText: String): String {
        val content = extractContent(rawText)
        val start = content.indexOf('[')
        if (start == -1) return ""
        val end = content.lastIndexOf(']')
        if (end > start) {
            val candidate = content.substring(start, end + 1)
            try {
                org.json.JSONArray(candidate)
                return candidate
            } catch (_: Exception) {
                // fall through to repair
            }
        }
        return repairJson(content.substring(start))
    }

    /**
     * Repairs truncated JSON text by balancing unclosed braces and brackets
     * in LIFO order and ensuring open string literals are closed.
     */
    fun repairJson(raw: String): String {
        if (raw.isBlank()) return raw
        val content = extractContent(raw).trim()
        val start = content.indexOfAny(charArrayOf('{', '['))
        if (start == -1) return ""
        val candidate = content.substring(start)

        val sb = StringBuilder(candidate.length + 16)
        var inString = false
        var escaping = false
        val stack = ArrayDeque<Char>()

        for (ch in candidate) {
            if (inString) {
                sb.append(ch)
                if (escaping) {
                    escaping = false
                } else if (ch == '\\') {
                    escaping = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }

            when (ch) {
                '"' -> {
                    inString = true
                    sb.append(ch)
                }
                '{' -> {
                    stack.addLast('}')
                    sb.append(ch)
                }
                '[' -> {
                    stack.addLast(']')
                    sb.append(ch)
                }
                '}', ']' -> {
                    if (stack.isNotEmpty() && stack.last() == ch) {
                        stack.removeLast()
                    }
                    sb.append(ch)
                }
                else -> sb.append(ch)
            }
        }

        if (inString) {
            sb.append('"')
        }

        var text = sb.toString().trimEnd()
        while (text.endsWith(",") || text.endsWith(":") || text.endsWith(" ")) {
            text = text.dropLast(1).trimEnd()
        }

        val repaired = StringBuilder(text)
        while (stack.isNotEmpty()) {
            repaired.append(stack.removeLast())
        }

        return sanitizeJsonObject(repaired.toString())
    }

    /**
     * Resiliently extracts workout plans from raw model text using regex patterns
     * even if JSON delimiters or surrounding wrappers were cut off.
     */
    fun extractWorkoutPlansFromText(rawText: String): List<com.xuan.fitai.data.model.WorkoutPlan> {
        val content = extractContent(rawText)
        val list = mutableListOf<com.xuan.fitai.data.model.WorkoutPlan>()
        val itemPattern = Regex("""\{[^{}]*"exerciseName"\s*:\s*"([^"]+)"[^{}]*\}""")
        val matches = itemPattern.findAll(content).toList()
        for (m in matches) {
            val block = m.value
            val exerciseName = extractString(block, "exerciseName") ?: continue
            val dayOfWeek = extractString(block, "dayOfWeek") ?: "星期一"
            val sets = extractNumber(block, "sets")?.toInt() ?: 3
            val reps = extractString(block, "reps") ?: "8-12次"
            val targetMuscle = extractString(block, "targetMuscleGroup") ?: "全身"
            list.add(
                com.xuan.fitai.data.model.WorkoutPlan(
                    dayOfWeek = dayOfWeek,
                    exerciseName = exerciseName,
                    sets = sets,
                    reps = reps,
                    targetMuscleGroup = targetMuscle,
                )
            )
        }
        return list
    }

    data class RepetitionLoop(
        val startIndex: Int,
        val unitLength: Int,
        val count: Int,
        val unit: String
    )

    /**
     * Detects if the trailing portion of [rawText] has fallen into a degenerative
     * repetition loop (e.g. "均衡均衡均衡均衡", "三餐飲用三餐飲用三餐飲用").
     *
     * Returns a [RepetitionLoop] describing where the loop began if detected,
     * or null if the text is healthy.
     */
    fun detectRepetitionLoop(rawText: String): RepetitionLoop? {
        val content = extractContent(rawText).trimEnd()
        if (content.length < 6) return null

        for (unitLen in 1..16) {
            val threshold = if (unitLen == 1) 5 else 3
            val neededLen = unitLen * threshold
            if (content.length < neededLen) continue

            val unit = content.takeLast(unitLen)
            if (unit.isBlank()) continue

            var count = 0
            var idx = content.length
            while (idx >= unitLen && content.regionMatches(idx - unitLen, unit, 0, unitLen)) {
                count += 1
                idx -= unitLen
            }
            if (count >= threshold) {
                return RepetitionLoop(
                    startIndex = idx,
                    unitLength = unitLen,
                    count = count,
                    unit = unit
                )
            }
        }
        return null
    }

    /**
     * Prunes a repetitive loop from [rawText], retaining only one occurrence of the unit
     * and closing the sentence cleanly with punctuation.
     */
    fun pruneRepetitionLoop(rawText: String): String {
        val loop = detectRepetitionLoop(rawText) ?: return rawText
        val content = extractContent(rawText).trimEnd()
        val rawPrefix = content.substring(0, (loop.startIndex + loop.unitLength).coerceAtMost(content.length)).trimEnd()
        var cleanPrefix = rawPrefix.trimEnd(',', '，', '、', '：', ':', ' ', ';', '；', '-')
        val boldCount = Regex("\\*\\*").findAll(cleanPrefix).count()
        if (boldCount % 2 != 0) {
            cleanPrefix += "**"
        }
        val suffix = if (cleanPrefix.endsWith("。") || cleanPrefix.endsWith("！") || cleanPrefix.endsWith("？") || cleanPrefix.endsWith(".")) "" else "。"
        val thinking = extractThinking(rawText)
        return withThinkingContent(thinkingText = thinking, contentText = cleanPrefix + suffix)
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

        if (calories == null || protein == null || carbs == null || fat == null ||
            listOf(calories, protein, carbs, fat).any { !it.isFinite() || it < 0f }) {
            return null
        }

        return GemmaFoodAnalysis(
            calories = calories,
            protein = protein,
            carbs = carbs,
            fat = fat,
            isSuitable = extractBoolean(content, "suitable") ?: true,
            advice = extractString(content, "advice")
                ?: "AI 已估算營養數值，但建議文字輸出不完整。",
            reasoning = extractString(content, "reasoning")
                ?: "AI 回覆的 JSON 尚未完整結束，已使用可讀取的欄位估算。",
            thinking = thinkingText
        )
    }

    fun parseFoodAnalysis(rawText: String): GemmaFoodAnalysis {
        val thinkingText = extractThinking(rawText)
        return try {
            val cleanReply = extractJson(rawText)
            val json = org.json.JSONObject(cleanReply)
            GemmaFoodAnalysis(
                calories = json.getDouble("calories").toFloat().also { require(it.isFinite() && it >= 0f) },
                protein = json.getDouble("protein").toFloat().also { require(it.isFinite() && it >= 0f) },
                carbs = json.getDouble("carbs").toFloat().also { require(it.isFinite() && it >= 0f) },
                fat = json.getDouble("fat").toFloat().also { require(it.isFinite() && it >= 0f) },
                isSuitable = json.optBoolean("suitable", true),
                advice = json.optString("advice", "AI 已完成營養估算，請依實際份量微調。"),
                reasoning = json.optString("reasoning", "依常見食物份量與營養比例估算。"),
                thinking = thinkingText
            )
        } catch (e: Exception) {
            extractPartialFoodAnalysis(rawText, thinkingText)
                ?: throw IllegalStateException("AI 回覆未包含完整營養數值，請重試或增加上下文容量。", e)
        }
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
