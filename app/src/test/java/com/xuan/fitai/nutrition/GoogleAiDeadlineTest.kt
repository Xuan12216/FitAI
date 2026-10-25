package com.xuan.fitai.nutrition

import org.junit.Assert.*
import org.junit.Test

class GoogleAiDeadlineTest {
    @Test fun formPreparationDoesNotConsumeTheThirtySecondAnswerBudget() {
        val deadline = GoogleAiDeadlineTracker(preparesQuery = true)
        deadline.observe(GoogleAiPage(preparing = true, preparationStage = "send"), 7_900)
        assertNull(deadline.answerStartedAt)
        assertFalse(deadline.preparationTimedOut(7_999))
        deadline.observe(GoogleAiPage(preparationStage = "sent"), 7_900)
        assertFalse(deadline.preparationTimedOut(8_000))
        assertFalse(deadline.answerTimedOut(37_899))
        assertTrue(deadline.answerTimedOut(37_900))
        deadline.observe(GoogleAiPage(text = "回答中", preparationStage = "sent"), 20_000)
        assertEquals(7_900L, deadline.answerStartedAt)
    }

    @Test fun preparationHasItsOwnBoundAndGetQueriesWaitThirtySeconds() {
        assertTrue(GoogleAiDeadlineTracker(true).preparationTimedOut(8_000))
        val deadline = GoogleAiDeadlineTracker(preparesQuery = false)
        assertFalse(deadline.answerTimedOut(29_999))
        assertTrue(deadline.answerTimedOut(30_000))
        assertTrue(GoogleAiWaitPolicy.PAGE_TIMEOUT_MS >= 8_000 + 30_000)
        assertTrue(GoogleAiWaitPolicy.TOTAL_TIMEOUT_MS > GoogleAiWaitPolicy.PAGE_TIMEOUT_MS * 3)
        assertEquals(GoogleAiWaitPolicy.TOTAL_TIMEOUT_MS, GoogleAiWaitPolicy.MANUAL_TIMEOUT_MS)
    }
}
