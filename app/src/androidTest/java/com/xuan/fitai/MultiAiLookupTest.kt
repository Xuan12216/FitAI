package com.xuan.fitai

import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xuan.fitai.nutrition.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MultiAiLookupTest {
    @Test fun optInLiveAutomaticLookupCanUseOtherProvidersAfterNormalFailure() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("runMultiAi") == "true")
        assertEquals("com.xuan.fitai.probe", instrumentation.targetContext.packageName)
        val loader = GoogleAiPageLoader(ApplicationProvider.getApplicationContext())
        val selected = mutableListOf<WebAiProvider>()
        val source = GoogleAiNutritionSource(object : GoogleAiPageSource {
            override suspend fun load(url: String) = loadUntilReady(url) { null }
            override suspend fun loadUntilReady(url: String, usableKey: (GoogleAiPage) -> String?): GoogleAiPage {
                val provider = WebAiProvider.fromUrl(url)!!
                selected += provider
                val page = loader.loadUntilReady(url, usableKey)
                instrumentation.sendStatus(0, Bundle().apply {
                    putString("stream", "automatic ${provider.name}: blocked=${page.blocked} failure=${page.failure} sources=${page.citations.size}\n")
                })
                return page
            }
        }, rotation = WebAiRotation(random = object : kotlin.random.Random() {
            override fun nextBits(bitCount: Int): Int = 0
        }))
        val result = source.lookup("火腿蛋餅", "1份")
        assertEquals(WebAiProvider.GOOGLE, selected.first())
        assertEquals(selected.size, selected.distinct().size)
        assertTrue(selected.size in 1..3)
        assertNotNull(result.message, result.assessment)
        instrumentation.sendStatus(0, Bundle().apply {
            putString("stream", "automatic result=${result.assessment!!.reference.foods.single().category} attempts=${result.attempts}\n")
        })
        Unit
    }

    @Test fun optInLiveProvidersRetainActualIdentityAndCitations() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("runMultiAi") == "true")
        assertEquals("com.xuan.fitai.probe", instrumentation.targetContext.packageName)
        val providers = InstrumentationRegistry.getArguments().getString("providers", "BRAVE,DUCK")!!
            .split(',').map { WebAiProvider.valueOf(it) }
        val foodName = InstrumentationRegistry.getArguments().getString("foodName", "火腿蛋餅")!!
        val failures = mutableListOf<String>()
        for (provider in providers) {
            val pages = mutableListOf<GoogleAiPage>()
            val loader = GoogleAiPageLoader(ApplicationProvider.getApplicationContext())
            val source = GoogleAiNutritionSource(object : GoogleAiPageSource {
                override suspend fun load(url: String) = loadUntilReady(url) { null }
                override suspend fun loadUntilReady(url: String, usableKey: (GoogleAiPage) -> String?) =
                    loader.loadUntilReady(url, usableKey).also { pages += it }
            }, rotation = WebAiRotation(listOf(provider)))
            val result = source.retry(foodName, "1份")
            val report = "${provider.name}: usable=${result.assessment != null} attempts=${result.attempts} " +
                "outcome=${source.monitor.snapshot().lastOutcome} model=${pages.firstOrNull()?.modelLabel} fast=${pages.firstOrNull()?.fastMode} stage=${pages.firstOrNull()?.preparationStage} " +
                "sources=${pages.firstOrNull()?.citations?.size} recipe=${result.assessment?.reference?.recipeEstimate} assumption=${result.assessment?.reference?.servingAssumption} message=${result.message}"
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "$report\n") })
            val assessment = result.assessment
            if (assessment == null) {
                failures += report + " text=" + pages.firstOrNull()?.text.orEmpty().take(900)
                continue
            }
            assertEquals(1, result.attempts)
            if (provider == WebAiProvider.DUCK) assertEquals(true, pages.single().fastMode)
            assertEquals(provider.foodId, assessment.analysis.referenceFoodId)
            val encoded = NutritionReferenceJson.encode(assessment.reference)
            assertEquals(assessment.reference, NutritionReferenceJson.decode(encoded))
            assertTrue(nutritionEstimatorLabel(encoded, true)!!.startsWith(provider.label))
            assertTrue(assessment.reference.googleAiCitations.isNotEmpty())
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
        Unit
    }

    @Test fun savedProviderIdentityAndLegacyGoogleRecordsRoundTrip() {
        for (provider in WebAiProvider.entries) {
            val citation = GoogleAiCitation("來源", "https://meals.ntu.edu.tw/CampusDining/kcal/breakfast")
            val page = GoogleAiPage("火腿蛋餅每1份，熱量300kcal、蛋白質15g、碳水37g、脂肪10g", citations = listOf(citation), modelLabel = "GPT-6 Luna")
            val food = GoogleAiNutritionParser.parse("火腿蛋餅", "1份", page,
                GoogleAiNutritionSource.providerUrl(provider, "火腿蛋餅", "1份", 1), provider)!!
            val reference = NutritionReference(1000L, false, listOf(food), food.id, page.text, listOf(citation))
            val json = NutritionReferenceJson.encode(reference)
            assertEquals(reference, NutritionReferenceJson.decode(json))
            assertTrue(nutritionEstimatorLabel(json, true)!!.startsWith(provider.label))
        }
        assertEquals("本地 Gemma", nutritionEstimatorLabel(null, true))
        assertNull(nutritionEstimatorLabel(null, false))
    }
}
