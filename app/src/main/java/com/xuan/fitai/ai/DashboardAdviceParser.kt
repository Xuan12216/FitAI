package com.xuan.fitai.ai

data class DashboardAdviceContent(val title: String = "", val body: String = "")

/** Separates the title and advice while tokens are still arriving. */
object DashboardAdviceParser {
    private val channels = Regex("<\\|?channel\\|?>([a-zA-Z0-9_]*)")
    private val titleLabel = Regex("^標題[：:]\\s*")
    private val bodyLabel = Regex("(?:^|\\n)\\s*建議[：:]\\s*")

    fun parse(raw: String): DashboardAdviceContent {
        val sections = channels.findAll(raw).toList()
        if (sections.isNotEmpty() && sections.all { it.groupValues[1] in listOf("thought", "thinking") }) {
            return DashboardAdviceContent()
        }
        val content = GemmaOutputParser.extractContent(raw).trim()
        if (content.isEmpty()) return DashboardAdviceContent()
        val firstLine = content.lineSequence().first().trim()
        if (!titleLabel.containsMatchIn(firstLine)) {
            // Do not flash a partly received field label as advice.
            if (!content.contains('\n') && ("標題：".startsWith(content) || "標題:".startsWith(content))) {
                return DashboardAdviceContent()
            }
            return DashboardAdviceContent(body = content.replaceFirst(bodyLabel, ""))
        }
        val title = firstLine.replaceFirst(titleLabel, "").trim().trim('*', '#', '"')
        val rest = content.substringAfter('\n', "").trimStart()
        val body = if (rest == "建" || rest == "建議") "" else rest.replaceFirst(bodyLabel, "")
        return DashboardAdviceContent(title, body)
    }
}
