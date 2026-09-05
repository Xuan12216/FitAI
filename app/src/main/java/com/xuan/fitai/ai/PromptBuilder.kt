package com.xuan.fitai.ai

import com.xuan.fitai.data.model.ContextTokenLimits
import com.xuan.fitai.data.model.UserProfile

object PromptBuilder {

    /**
     * Estimates the token count of a given text.
     * CJK characters and full-width punctuation typically consume 1-1.5 tokens each in Gemma.
     * Latin words/numbers consume ~1-1.3 tokens each.
     */
    fun estimateTokenCount(text: String): Int {
        if (text.isBlank()) return 0
        var tokens = 0
        var inAsciiWord = false
        for (ch in text) {
            if (ch.isWhitespace()) {
                inAsciiWord = false
            } else if (ch.code in 0x4E00..0x9FFF || ch.code in 0x3400..0x4DBF || ch.code in 0x3000..0x303F || ch.code in 0xFF00..0xFFEF) {
                tokens += 1
                inAsciiWord = false
            } else {
                if (!inAsciiWord) {
                    tokens += 1
                    inAsciiWord = true
                }
            }
        }
        return tokens.coerceAtLeast(1)
    }

    /**
     * Calculates available output tokens from total max context tokens,
     * accounting for prompt tokens, system instruction overhead, and safety margin.
     */
    fun calculateAvailableOutputTokens(
        promptBaseText: String,
        maxTokens: Int,
        systemPromptTokens: Int = 40,
        safetyBufferTokens: Int = 20,
    ): Int {
        val safeMax = maxTokens.coerceAtLeast(1)
        val estimatedPrompt = estimateTokenCount(promptBaseText)
        val overhead = estimatedPrompt + systemPromptTokens + safetyBufferTokens
        val rawAvailable = maxTokens - overhead
        val minBudget = minOf(32, safeMax)
        return rawAvailable.coerceIn(minBudget, safeMax)
    }

    fun formatTokenBudgetNotice(maxTokens: Int, availableTokens: Int, maxChars: Int): String {
        return if (availableTokens <= 128) {
            "【長度限制】當前上下文容量（Max Token）為 $maxTokens tokens（預估最大可用輸出約 $availableTokens tokens）。請在 $maxChars 個中文字以內極簡回答（約 1-2 句話），切勿使用長篇列點，請在限制內完整收尾，切勿半途截斷。"
        } else {
            "【長度限制】當前上下文容量（Max Token）為 $maxTokens tokens（預估最大可用輸出約 $availableTokens tokens）。請在 $maxChars 個中文字以內簡明扼要完整回答，並在限制內自然收尾，切勿半途截斷。"
        }
    }

    fun formatTokenBudgetNoticeEn(maxTokens: Int, availableTokens: Int, maxChars: Int): String {
        return if (availableTokens <= 128) {
            "[Token Limit Reminder] Current max context limit is $maxTokens tokens (estimated max output allowance ~$availableTokens tokens). Keep response extremely brief (strictly within $maxChars Traditional Chinese characters or 1-2 sentences) and ensure it completes cleanly without truncation."
        } else {
            "[Token Limit Reminder] Current max context limit is $maxTokens tokens (estimated max output allowance ~$availableTokens tokens). Keep response strictly within $maxChars Traditional Chinese characters and ensure your output (including any JSON) is fully completed before reaching the limit."
        }
    }

    // Keep chat continuity explicitly without sharing a native session with food
    // analysis or dashboard advice. This is a conservative character allowance,
    // not a claim of exact tokenizer accounting.
    fun recentConversation(messages: List<Pair<String, String>>, contextTokens: Int): String {
        var remaining = ((contextTokens - 384).coerceAtLeast(0) / 2).coerceAtMost(1200)
        val lines = mutableListOf<String>()
        for ((role, content) in messages.takeLast(4).asReversed()) {
            if (remaining == 0) break
            val excerpt = content.take(minOf(remaining, 300))
            if (excerpt.isBlank()) continue
            remaining -= excerpt.length
            lines.add("${if (role == "user") "使用者" else "助理"}：$excerpt")
        }
        return lines.asReversed().joinToString("\n")
    }

    fun buildFoodAnalysisPrompt(
        foodName: String,
        portion: String,
        goal: String,
        maxTokens: Int = ContextTokenLimits.DEFAULT,
    ): String {
        val base = """
            You are a professional nutritionist. Estimate calorie count and nutrition for $foodName ($portion). Goal: $goal.
            Use realistic serving sizes; calories should approximate protein*4+carbs*4+fat*9.
            Return JSON only with numeric calories, protein, carbs, fat; boolean suitable;
            and short Traditional Chinese advice and reasoning (include serving assumptions).
            Schema: {"calories":0,"protein":0,"carbs":0,"fat":0,"suitable":true,"advice":"","reasoning":""}
        """.trimIndent()
        val availableTokens = calculateAvailableOutputTokens(base, maxTokens)
        val maxChars = when {
            maxTokens <= 256 -> 30
            maxTokens <= 512 -> 50
            maxTokens <= 1024 -> 80
            else -> 120
        }
        return """
            $base
            ${formatTokenBudgetNoticeEn(maxTokens, availableTokens, maxChars)}
            Advice and reasoning must each be under $maxChars Traditional Chinese characters so that the JSON object is completely closed before hitting the limit.
        """.trimIndent()
    }

    fun buildAdvicePrompt(
        profile: UserProfile,
        todayCalories: Float,
        todayProtein: Float,
        todayCarbs: Float,
        todayFat: Float,
        userMessage: String,
        recentConversation: String = "",
        maxTokens: Int = ContextTokenLimits.DEFAULT,
    ): String {
        val basePrompt = """
            你是一位健康飲食與運動助理。
            請使用繁體中文回答。
            請回答簡短、實用、容易執行。

            使用者目標：${profile.goal}
            每日熱量目標：${profile.targetCalories.toInt()} kcal
            今日已攝取熱量：${todayCalories.toInt()} kcal
            今日蛋白質：${todayProtein.toInt()} g
            今日碳水：${todayCarbs.toInt()} g
            今日脂肪：${todayFat.toInt()} g

            ${if (recentConversation.isNotBlank()) "近期對話（節錄）：\n$recentConversation" else ""}

            使用者問題：
            $userMessage

            請注意：
            1. 不要提供醫療診斷。
            2. 如果問題涉及疾病、藥物、特殊病症，請提醒使用者詢問醫師或營養師。
            3. 如果資料不足，請給出合理估算並提醒使用者確認。
        """.trimIndent()

        val availableTokens = calculateAvailableOutputTokens(basePrompt, maxTokens)
        val maxChineseChars = when {
            maxTokens <= 256 -> 35
            maxTokens <= 512 -> 75
            maxTokens <= 1024 -> 160
            maxTokens <= 2000 -> 300
            else -> 600
        }.coerceAtMost(maxOf(25, (availableTokens * 0.7).toInt()))

        val tokenNotice = formatTokenBudgetNotice(maxTokens, availableTokens, maxChineseChars)

        return """
            $basePrompt
            4. $tokenNotice
        """.trimIndent()
    }

    fun buildDashboardAdvicePrompt(
        profile: UserProfile,
        todayCalories: Float,
        todayProtein: Float,
        todayCarbs: Float,
        todayFat: Float,
        maxTokens: Int = ContextTokenLimits.DEFAULT,
    ): String {
        val wordLimit = if (maxTokens <= 256) 50 else 100
        val basePrompt = """
            你是一位專業健康顧問。使用者目標是「${profile.goal}」。
            今日熱量目標為 ${profile.targetCalories.toInt()} kcal，已攝取 $todayCalories kcal。
            今日三大營養素已攝取：蛋白質 ${todayProtein.toInt()}g，碳水 ${todayCarbs.toInt()}g，脂肪 ${todayFat.toInt()}g。
            請根據以上數據，只輸出一段 $wordLimit 個中文字以內的繁體中文實用建議。
        """.trimIndent()

        val availableTokens = calculateAvailableOutputTokens(basePrompt, maxTokens)
        val tokenNotice = formatTokenBudgetNotice(maxTokens, availableTokens, wordLimit)

        return """
            $basePrompt
            $tokenNotice
        """.trimIndent()
    }

    fun buildWorkoutPlanPrompt(
        profile: UserProfile,
        daysPerWeek: Int,
        preference: String,
        maxTokens: Int = ContextTokenLimits.DEFAULT,
    ): String {
        val maxExercisesPerDay = when {
            maxTokens <= 256 -> 1
            maxTokens <= 512 -> 2
            maxTokens <= 1024 -> 3
            else -> 4
        }
        val summaryRule = when {
            maxTokens <= 256 -> "1 short sentence"
            maxTokens <= 512 -> "1-2 short sentences"
            else -> "3-5 short sentences"
        }
        val basePrompt = """
            You are a professional fitness coach. Generate a customized weekly workout plan.
            Profile: Goal=${profile.goal}, Age=${profile.age}, ${profile.gender}, ${profile.height.toInt()}cm/${profile.weight.toInt()}kg, Level=${profile.workoutExperience}, Days=$daysPerWeek, Pref=$preference.

            Output ONLY one raw JSON object. Do not include markdown, code fences, or extra prose.
            Schema:
            {"weeklyPlan":[{"dayOfWeek":"星期一","exerciseName":"深蹲 (Squats)","sets":4,"reps":"8-12次","targetMuscleGroup":"腿部"}],"summary":"$summaryRule in Traditional Chinese"}
            Rules:
            1. Only include days they train ($daysPerWeek days).
            2. At most $maxExercisesPerDay exercises per day.
            3. Make summary $summaryRule in Traditional Chinese.
        """.trimIndent()

        val availableTokens = calculateAvailableOutputTokens(basePrompt, maxTokens)
        val maxChars = if (maxTokens <= 512) 50 else 160
        val tokenNotice = formatTokenBudgetNoticeEn(maxTokens, availableTokens, maxChars)

        return """
            $basePrompt
            $tokenNotice
        """.trimIndent()
    }

    fun buildWorkoutSummaryPrompt(
        goal: String,
        planJson: String,
        maxTokens: Int = ContextTokenLimits.DEFAULT,
    ): String {
        val maxChars = when {
            maxTokens <= 256 -> 40
            maxTokens <= 512 -> 80
            maxTokens <= 1024 -> 120
            else -> 200
        }
        val sentenceRange = if (maxTokens <= 256) "1-2" else if (maxTokens <= 512) "2-3" else "3-5"
        val compactedPlan = if (maxTokens <= 512 && planJson.length > 300) planJson.take(300) else planJson
        val basePrompt = """
            Based on the following workout plan JSON, write a concise and motivating summary in Traditional Chinese (繁體中文) for the user.
            The summary should:
            - Be $sentenceRange sentences (strictly within $maxChars Traditional Chinese characters)
            - Highlight the key training focus and muscle groups targeted
            - Mention the training frequency and volume
            - Include a motivational closing statement
            - Use a friendly, encouraging tone
            - Do NOT include any JSON or code, only plain prose text

            User Goal: $goal
            Workout Plan JSON:
            $compactedPlan

            Write the summary now:
        """.trimIndent()

        val availableTokens = calculateAvailableOutputTokens(basePrompt, maxTokens)
        val tokenNotice = formatTokenBudgetNoticeEn(maxTokens, availableTokens, maxChars)

        return """
            $basePrompt
            $tokenNotice
        """.trimIndent()
    }
}
