package com.xuan.fitai.ai

import com.xuan.fitai.data.model.UserProfile
import org.junit.Assert.*
import org.junit.Test

class PromptBuilderTokenLimitTest {

    @Test
    fun estimateTokenCountHandlesDifferentLanguages() {
        assertEquals(0, PromptBuilder.estimateTokenCount(""))
        assertEquals(0, PromptBuilder.estimateTokenCount("   "))

        val englishTokens = PromptBuilder.estimateTokenCount("You are a fitness coach")
        assertTrue("Expected ~5 tokens, got $englishTokens", englishTokens in 4..6)

        val cjkTokens = PromptBuilder.estimateTokenCount("你是一位健康飲食與運動助理。")
        assertTrue("Expected ~14 tokens, got $cjkTokens", cjkTokens in 13..16)
    }

    @Test
    fun calculateAvailableOutputTokensBoundsGracefully() {
        val prompt = "測試提示詞"
        // When maxTokens is 256, available tokens should be constrained but >= 32
        val available256 = PromptBuilder.calculateAvailableOutputTokens(prompt, 256)
        assertTrue(available256 in 32..256)

        // When maxTokens is 1024, available tokens should be around 900+
        val available1024 = PromptBuilder.calculateAvailableOutputTokens(prompt, 1024)
        assertTrue(available1024 in 800..1024)

        // When maxTokens is 2000
        val available2000 = PromptBuilder.calculateAvailableOutputTokens(prompt, 2000)
        assertTrue(available2000 in 1800..2000)

        // Extreme edge cases (small or invalid numbers should never crash)
        val available10 = PromptBuilder.calculateAvailableOutputTokens(prompt, 10)
        assertEquals(10, available10)
        val available0 = PromptBuilder.calculateAvailableOutputTokens(prompt, 0)
        assertEquals(1, available0)
    }

    @Test
    fun buildAdvicePromptIncludesMaxTokenAndWarning() {
        val profile = UserProfile(goal = "增肌")

        // Low token budget (256)
        val prompt256 = PromptBuilder.buildAdvicePrompt(
            profile = profile,
            todayCalories = 1500f,
            todayProtein = 90f,
            todayCarbs = 180f,
            todayFat = 50f,
            userMessage = "晚餐吃什麼？",
            maxTokens = 256,
        )
        assertTrue(prompt256.contains("256"))
        assertTrue(prompt256.contains("Max Token"))
        assertTrue(prompt256.contains("截斷"))

        // Standard token budget (1024)
        val prompt1024 = PromptBuilder.buildAdvicePrompt(
            profile = profile,
            todayCalories = 1500f,
            todayProtein = 90f,
            todayCarbs = 180f,
            todayFat = 50f,
            userMessage = "晚餐吃什麼？",
            maxTokens = 1024,
        )
        assertTrue(prompt1024.contains("1024"))
        assertTrue(prompt1024.contains("Max Token"))
        assertTrue(prompt1024.contains("截斷"))
    }

    @Test
    fun buildFoodAnalysisPromptIncludesLimitAndKeywords() {
        val prompt = PromptBuilder.buildFoodAnalysisPrompt(
            foodName = "雞胸肉",
            portion = "200g",
            goal = "增肌",
            maxTokens = 512,
        )
        assertTrue(prompt.contains("512"))
        assertTrue(prompt.contains("Token Limit Reminder"))
        assertTrue(prompt.contains("professional nutritionist"))
        assertTrue(prompt.contains("calorie count"))
    }

    @Test
    fun buildDashboardAdvicePromptIncludesLimitAndKeywords() {
        val profile = UserProfile(goal = "維持健康")
        val prompt = PromptBuilder.buildDashboardAdvicePrompt(
            profile = profile,
            todayCalories = 1800f,
            todayProtein = 80f,
            todayCarbs = 200f,
            todayFat = 60f,
            maxTokens = 1024,
        )
        assertTrue(prompt.contains("1024"))
        assertTrue(prompt.contains("今日熱量目標"))
        assertTrue(prompt.contains("今日三大營養素"))
        assertTrue(prompt.contains("100 個中文字以內"))
        assertTrue(prompt.contains("Max Token"))
    }

    @Test
    fun buildWorkoutPlanAndSummaryPromptsIncludeLimitAndKeywords() {
        val profile = UserProfile(goal = "減脂", age = 25, gender = "男", height = 175f, weight = 70f)
        val planPrompt = PromptBuilder.buildWorkoutPlanPrompt(
            profile = profile,
            daysPerWeek = 3,
            preference = "居家無器材",
            maxTokens = 2000,
        )
        assertTrue(planPrompt.contains("2000"))
        assertTrue(planPrompt.contains("professional fitness coach"))
        assertTrue(planPrompt.contains("weekly workout plan"))
        assertTrue(planPrompt.contains("Token Limit Reminder"))

        val summaryPrompt = PromptBuilder.buildWorkoutSummaryPrompt(
            goal = "減脂",
            planJson = "{}",
            maxTokens = 512,
        )
        assertTrue(summaryPrompt.contains("512"))
        assertTrue(summaryPrompt.contains("workout plan JSON"))
        assertTrue(summaryPrompt.contains("motivating summary"))
        assertTrue(summaryPrompt.contains("Token Limit Reminder"))
    }

    @Test
    fun repairJsonHandlesTruncatedWorkoutPlanWithoutThrowing() {
        // The exact truncated output snippet from the device
        val truncatedDeviceOutput = """
            {
              "summary": "您好！這是一個為您量身打造的增肌計畫。",
              "weeklyPlan": [
                {
                  "dayOfWeek": "星期一",
                  "exerciseName": "深蹲 (Squats)",
                  "sets": 4,
                  "reps": "8-12次",
                  "targetMuscleGroup": "腿部"
                }
        """.trimIndent()

        val repaired = GemmaOutputParser.repairJson(truncatedDeviceOutput)
        assertTrue("Repaired JSON must close weeklyPlan array and root object", repaired.endsWith("]}"))
        assertTrue("Must contain summary", repaired.contains("您好！這是一個為您量身打造的增肌計畫。"))
        assertTrue("Must contain exercise name", repaired.contains("深蹲 (Squats)"))
    }

    @Test
    fun extractWorkoutPlansFromTextFallbackWorks() {
        val rawText = """
            Here is the plan:
            {"dayOfWeek":"星期二","exerciseName":"伏地挺身","sets":3,"reps":"15次","targetMuscleGroup":"胸肌"}
            {"dayOfWeek":"星期四","exerciseName":"棒式","sets":3,"reps":"60秒","targetMuscleGroup":"核心"}
        """.trimIndent()

        val plans = GemmaOutputParser.extractWorkoutPlansFromText(rawText)
        assertEquals(2, plans.size)
        assertEquals("伏地挺身", plans[0].exerciseName)
        assertEquals("星期二", plans[0].dayOfWeek)
        assertEquals("棒式", plans[1].exerciseName)
        assertEquals("星期四", plans[1].dayOfWeek)
    }

    @Test
    fun detectRepetitionLoopIdentifiesLoopsCorrectly() {
        // Multi-char loop (2 chars, 4 repeats)
        val loop1 = GemmaOutputParser.detectRepetitionLoop("您好！為了維持健康，建議您以**均衡均衡均衡均衡")
        assertNotNull(loop1)
        assertEquals("均衡", loop1?.unit)
        assertEquals(4, loop1?.count)

        // 4-char phrase loop (3 repeats)
        val loop2 = GemmaOutputParser.detectRepetitionLoop("建議您三餐飲用三餐飲用三餐飲用")
        assertNotNull(loop2)
        assertEquals("三餐飲用", loop2?.unit)
        assertEquals(3, loop2?.count)

        // Single-char repeat >= 5
        val loop3 = GemmaOutputParser.detectRepetitionLoop("多喝水啊啊啊啊啊")
        assertNotNull(loop3)
        assertEquals("啊", loop3?.unit)
        assertEquals(5, loop3?.count)

        // Single-char repeat < 5 is allowed (normal emphasis)
        val normalShortRepeat = GemmaOutputParser.detectRepetitionLoop("哈哈哈哈")
        assertNull(normalShortRepeat)

        // Completely healthy text
        val healthy = GemmaOutputParser.detectRepetitionLoop("您好！健康飲食的原則包括均衡攝取各類營養素、多喝水並維持充足睡眠。")
        assertNull(healthy)
    }

    @Test
    fun pruneRepetitionLoopCleansRepeatsAndBalancesMarkdown() {
        val raw1 = "您好！為了維持健康，建議您以**均衡均衡均衡均衡"
        val pruned1 = GemmaOutputParser.pruneRepetitionLoop(raw1)
        assertEquals("您好！為了維持健康，建議您以**均衡**。", pruned1)

        val raw2 = "建議您三餐飲用三餐飲用三餐飲用"
        val pruned2 = GemmaOutputParser.pruneRepetitionLoop(raw2)
        assertEquals("建議您三餐飲用。", pruned2)

        val raw3 = "<|channel>thought\nUser wants diet advice.\n<channel|>\n建議您三餐飲用三餐飲用三餐飲用"
        val pruned3 = GemmaOutputParser.pruneRepetitionLoop(raw3)
        assertTrue(pruned3.contains("<|channel>thought\nUser wants diet advice.\n<channel|>"))
        assertTrue(pruned3.endsWith("建議您三餐飲用。"))

        // Unchanged when healthy
        val healthy = "多喝水，多運動。"
        val prunedHealthy = GemmaOutputParser.pruneRepetitionLoop(healthy)
        assertEquals(healthy, prunedHealthy)
    }
}
