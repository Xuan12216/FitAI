package com.xuan.fitai

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xuan.fitai.nutrition.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoogleAiLookupTest {
    @Test fun liveGoogleAiLookupUsesAtMostThreeQueriesAndPersistsSource() = runBlocking {
        val foodName = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("foodName") ?: "火腿蛋餅"
        val loader = GoogleAiPageLoader(ApplicationProvider.getApplicationContext())
        var searches = 0
        val observed = mutableListOf<GoogleAiPage>()
        val source = GoogleAiNutritionSource(object : GoogleAiPageSource {
            override suspend fun load(url: String): GoogleAiPage = loadUntilReady(url) { null }
            override suspend fun loadUntilReady(url: String, usableKey: (GoogleAiPage) -> String?): GoogleAiPage {
                searches++
                return loader.loadUntilReady(url, usableKey).also { observed.add(it) }
            }
        })
        val result = source.lookup(foodName, "1份")
        assertTrue(searches in 1..3)
        assertNotNull(result.message + observed.joinToString { " [failure=${it.failure}, blocked=${it.blocked}, sources=${it.citations.size}, text=${it.text.take(1400)}]" }, result.assessment)
        val assessment = result.assessment!!
        assertTrue(assessment.analysis.calories > 0f)
        assertEquals("GOOGLE-AI-1", assessment.analysis.referenceFoodId)
        assertEquals("1份", assessment.reference.foods.single().servingLabel)
        assertTrue(assessment.reference.googleAiCitations.isNotEmpty())
        assertFalse(assessment.reference.googleAiAnswer.isNullOrBlank())
        val json = NutritionReferenceJson.encode(assessment.reference)
        assertTrue(json.length <= 8000)
        assertEquals(assessment.reference, NutritionReferenceJson.decode(json))
        val cached = source.lookup(foodName, "2份")
        assertEquals(searches, result.attempts)
        assertEquals(0, cached.attempts)
        assertTrue(cached.assessment!!.reference.fromCache)
        android.util.Log.i("FitAI_GoogleTest", "Google AI success: searches=$searches, calories=${assessment.analysis.calories}, protein=${assessment.analysis.protein}, carbs=${assessment.analysis.carbs}, fat=${assessment.analysis.fat}; cached without another search")
        Unit
    }
}
