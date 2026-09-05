package com.xuan.fitai.ai

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiResponseFlowTest {
    @Test fun chatHistoryIsBoundedAndKeepsRecentTurns() {
        val history = PromptBuilder.recentConversation(
            listOf("user" to "old".repeat(500), "assistant" to "recent".repeat(500)), 512
        )
        assertTrue(history.startsWith("助理：recent"))
        assertTrue(history.length <= 70)
        assertEquals("", PromptBuilder.recentConversation(listOf("user" to "old"), 256))
    }

    @Test fun emptyAndControlOnlyResponsesFailClearly() = runTest {
        for (source in listOf(emptyFlow(), flowOf(" ", "\n"), flowOf("<|endoftext|>"))) {
            val failure = runCatching { source.requireAiResponse().toList() }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
            assertTrue(failure!!.message!!.contains("上下文容量"))
        }
    }

    @Test fun forwardsValidTokensUnchanged() = runTest {
        assertEquals(listOf("你好", "！"), flowOf("你好", "！").requireAiResponse().toList())
    }

    @Test fun cancellationPropagatesAndNextRequestStillWorks() = runTest {
        var cleanedUp = false
        val first = launch {
            flow<String> { try { awaitCancellation() } finally { cleanedUp = true } }.requireAiResponse().collect()
        }
        runCurrent()
        first.cancelAndJoin()
        assertTrue(cleanedUp)
        assertEquals("新的回覆", flowOf("新的回覆").requireAiResponse().single())
    }

    @Test fun incompleteNutritionNeverBecomesInventedDefaults() {
        assertNull(GemmaOutputParser.extractPartialFoodAnalysis("模型沒有回覆", null))
        assertNull(GemmaOutputParser.extractPartialFoodAnalysis("{\"calories\":150}", null))
        val recovered = GemmaOutputParser.extractPartialFoodAnalysis("{\"calories\":150,\"protein\":10,\"carbs\":20,\"fat\":3", null)
        assertEquals(150f, recovered!!.calories)
    }
}
