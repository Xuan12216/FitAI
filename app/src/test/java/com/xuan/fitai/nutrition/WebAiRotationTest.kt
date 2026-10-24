package com.xuan.fitai.nutrition

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class WebAiRotationTest {
    @Test fun selectionExcludesPreviousProviderIncludingAfterRecreation() {
        var saved = WebAiProvider.GOOGLE
        val seen = mutableSetOf<WebAiProvider>()
        var rotation = WebAiRotation(initial = saved, random = Random(17), save = { saved = it })
        repeat(100) {
            val previous = saved
            val next = rotation.next()!!
            assertNotEquals(previous, next)
            seen += next
            if (it == 50) rotation = WebAiRotation(initial = saved, random = Random(21), save = { saved = it })
        }
        assertEquals(WebAiProvider.entries.toSet(), seen)
    }

    @Test fun allowlistRejectsOtherHostsCredentialsAndNonHttps() {
        for (provider in WebAiProvider.entries) {
            val url = GoogleAiNutritionSource.providerUrl(provider, "火腿蛋餅", "1份", 1)
            assertEquals(provider, WebAiProvider.fromUrl(url))
            assertTrue(isNutritionSourceUrl(url))
            assertNull(WebAiProvider.fromUrl(url.replace("https://", "http://")))
        }
        assertNull(WebAiProvider.fromUrl("https://duck.ai.attacker.example/"))
        assertNull(WebAiProvider.fromUrl("https://user:pass@duck.ai/"))
        assertNull(WebAiProvider.fromUrl("https://search.brave.com/other"))
        assertNull(WebAiRotation(listOf(WebAiProvider.GOOGLE), initial = WebAiProvider.GOOGLE).next())
    }

    @Test fun explicitVerificationAndUsageLimitsAreDistinctFromOrdinaryTimeouts() {
        for (text in listOf("Our systems detected unusual traffic", "Verify you are human", "Too many requests",
            "You've reached your daily limit", "已達到每日上限", "本日訊息上限已用完", "正在執行安全驗證")) {
            assertTrue(text, AiAccessRestriction.matches(text))
        }
        for (text in listOf("Google AI 回答等待逾時", "網頁無法連線", "熱量 300 kcal", "最多查詢 3 次", "AI 可能會出錯")) {
            assertFalse(text, AiAccessRestriction.matches(text))
        }
    }

    @Test fun expiredRollingWindowDoesNotRestoreAnAppCooldown() {
        var now = 0L
        val monitor = GoogleAiTrafficMonitor { now }
        repeat(6) { monitor.started("Brave AI"); monitor.finished(GoogleAiTrafficMonitor.Outcome.INCOMPLETE, 100) }
        assertTrue(monitor.snapshot().dense)
        monitor.cacheHit(); monitor.suppressed()
        now = 60_000
        val state = monitor.snapshot()
        assertFalse(state.dense)
        assertEquals(6, state.requests); assertEquals(1, state.cacheHits); assertEquals(1, state.suppressed)
        assertEquals("Brave AI", state.lastProvider)
        assertFalse(state.inFlight)
    }
}
