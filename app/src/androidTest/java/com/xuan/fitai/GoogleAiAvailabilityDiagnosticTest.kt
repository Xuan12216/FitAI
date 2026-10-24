package com.xuan.fitai

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xuan.fitai.nutrition.GoogleAiNutritionParser
import com.xuan.fitai.nutrition.GoogleAiNutritionSource
import com.xuan.fitai.nutrition.GoogleAiPageLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicitly opted-in, bounded availability sample; this does not discover a blocking threshold. */
@RunWith(AndroidJUnit4::class)
class GoogleAiAvailabilityDiagnosticTest {
    @Test fun boundedSequentialQueriesReportAvailability() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("Requires -e runGoogleAvailabilitySample true",
            InstrumentationRegistry.getArguments().getString("runGoogleAvailabilitySample") == "true")
        // An isolated install prevents touching the user's records, settings, or WebView session.
        assertTrue("Install only as the disposable probe app",
            instrumentation.targetContext.packageName == "com.xuan.fitai.probe")
        val loader = GoogleAiPageLoader(ApplicationProvider.getApplicationContext())
        val foods = listOf("火腿蛋餅", "鮪魚三明治", "牛肉麵", "雞肉飯", "茶葉蛋")
        var completed = 0
        var consecutiveFailures = 0
        for ((index, food) in foods.withIndex()) {
            if (index > 0) delay(5_000)
            val url = GoogleAiNutritionSource.searchUrl(food, "1份", 1)
            val started = SystemClock.elapsedRealtime()
            val page = loader.loadUntilReady(url) { candidate ->
                GoogleAiNutritionParser.parse(food, "1份", candidate, url)?.let {
                    "${it.servingLabel}|${it.calories}|${it.protein}|${it.carbs}|${it.fat}"
                }
            }
            completed++
            val usable = GoogleAiNutritionParser.parse(food, "1份", page, url) != null
            val outcome = when {
                page.blocked -> "blocked-or-verification"
                page.failure != null -> "page-failure"
                usable -> "usable-answer"
                page.text.isNotBlank() -> "answer-unusable"
                else -> "no-answer"
            }
            val report = "query=$completed elapsedMs=${SystemClock.elapsedRealtime() - started} " +
                "outcome=$outcome sources=${page.citations.size} chars=${page.text.length} " +
                "failure=${page.failure.orEmpty()}"
            Log.i("FitAI_GoogleSample", report)
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "$report\n") })
            if (page.blocked) break
            consecutiveFailures = if (page.failure != null) consecutiveFailures + 1 else 0
            if (consecutiveFailures >= 2) break
        }
        assertTrue(completed in 1..5)
        Unit
    }
}
