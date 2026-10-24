package com.xuan.fitai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xuan.fitai.nutrition.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NutritionReferenceTest {
    @Test fun liveHamPancakeLookupPreservesSourceServingAndMissingMacros() = runBlocking {
        val foods = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.withTimeout(6000) { WebNutritionScraper.searchOnce("火腿蛋餅") }
        }
        val food = foods.single()
        assertEquals("火腿蛋餅", food.name)
        assertEquals(NTU_BREAKFAST_URL, food.sourceUrl)
        assertEquals("1份", food.servingLabel); assertFalse(food.isPer100g); assertFalse(food.hasMacros)
        assertTrue(food.calories > 0f)
        val reference = NutritionReference(System.currentTimeMillis(), false, foods)
        assertEquals(reference, NutritionReferenceJson.decode(NutritionReferenceJson.encode(reference)))
        val oldJson = NutritionReferenceJson.foodToJson(food.copy(servingLabel = "100g", hasMacros = true))
        oldJson.remove("servingLabel"); oldJson.remove("hasMacros")
        assertTrue(NutritionReferenceJson.foodFromJson(oldJson).isPer100g)
        assertTrue(NutritionReferenceJson.foodFromJson(oldJson).hasMacros)
    }

    @Test fun slowWebLookupStillUsesBuiltInReference() = runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val dir = java.io.File(context.cacheDir, "nutrition-slow-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : android.content.ContextWrapper(context) { override fun getCacheDir() = dir }
        try {
            val start = android.os.SystemClock.elapsedRealtime()
            val result = TfdaNutritionRepository(isolated, webSearch = { kotlinx.coroutines.delay(10_000); emptyList() }).lookup("起司蛋餅")
            assertTrue(result.reference!!.foods.any { it.name == "起司蛋餅" })
            assertTrue(android.os.SystemClock.elapsedRealtime() - start < 8000)
            assertTrue(result.reference!!.fromCache)
        } finally { dir.deleteRecursively() }
    }

    @Test fun cachedWebKeepsAcquisitionTimeAndOriginalPage() = runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val dir = java.io.File(context.cacheDir, "nutrition-web-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : android.content.ContextWrapper(context) { override fun getCacheDir() = dir }
        var queries = 0
        val source = "https://www.fatsecret.cn/food/original"
        try {
            val repository = TfdaNutritionRepository(isolated, webSearch = {
                queries++; listOf(NutritionFood("WEB-1", "雞蛋", "", "每100g", 150f, 13f, 1f, 11f, sourceUrl = source))
            })
            val fresh = repository.lookup("雞蛋").reference!!
            val cached = repository.lookup("雞蛋").reference!!
            assertEquals(1, queries); assertFalse(fresh.fromCache); assertTrue(cached.fromCache)
            assertEquals(fresh.fetchedAt, cached.fetchedAt)
            assertEquals(source, NutritionReferenceJson.decode(NutritionReferenceJson.encode(cached)).foods.single().sourceUrl)
            assertFalse(cached.foods.single().copy(sourceUrl = "javascript:alert(1)").isValid())
        } finally { dir.deleteRecursively() }
    }

    @Test fun liveOfficialDownloadAndCacheWorkWithinBudget() = runBlocking {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val dir = java.io.File(context.cacheDir, "nutrition-network-test-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : android.content.ContextWrapper(context) { override fun getCacheDir() = dir }
        try {
            val first = kotlinx.coroutines.withTimeout(20_000) { TfdaNutritionRepository(isolated, webSearch = { emptyList() }).lookup("白飯") }
            assertNotNull(first.message, first.reference)
            assertFalse(first.reference!!.fromCache)
            assertEquals(183f, first.reference!!.foods.single().calories)
            val cached = TfdaNutritionRepository(isolated, webSearch = { emptyList() }).lookup("牛肉")
            assertTrue(cached.reference!!.fromCache)
            assertEquals(5, cached.reference!!.foods.size)
        } finally { dir.deleteRecursively() }
    }

    @Test fun tfdaParserHandlesNestedRowsAndNeverTreatsMissingValuesAsZero() = runBlocking {
        fun row(id: String, nutrient: String, value: String?) = """[{"整合編號":"$id"},{"樣品名稱":"白飯"},{"俗名":null},{"內容物描述":"熟"},{"分析項":"$nutrient"},{"含量單位":"${if (nutrient == "熱量") "kcal" else "g"}"},{"每100克含量":${value?.let { "\" $it \"" } ?: "null"}}]"""
        val complete = listOf(row("A", "熱量", "183"), row("A", "粗蛋白", "3.1"), row("A", "粗脂肪", "0.3"), row("A", "總碳水化合物", "41"))
        val missing = listOf(row("B", "熱量", "100"), row("B", "粗蛋白", null), row("B", "粗脂肪", "0.1"), row("B", "總碳水化合物", "20"))
        val foods = TfdaDatasetParser.parse((complete + missing).joinToString(",", "[", "]").byteInputStream())
        assertEquals(1, foods.size); assertEquals(183f, foods.single().calories); assertEquals("", foods.single().aliases)
    }

    @Test fun sourceRoundTripAndMalformedSourceValidation() {
        val reference = NutritionReference(1000, true, listOf(NutritionFood("A", "白飯", "", "熟", 183f, 3.1f, 41f, .3f)))
        assertEquals(reference, NutritionReferenceJson.decode(NutritionReferenceJson.encode(reference)))
        assertNull(NutritionReferenceJson.decodeOrNull("{}"))
        assertTrue(runCatching { NutritionReferenceJson.decode(NutritionReferenceJson.encode(reference).replace("tfda", "other")) }.isFailure)
        assertEquals("A", NutritionReferenceJson.decode(NutritionReferenceJson.withSelection(NutritionReferenceJson.encode(reference), "A")!!).selectedFoodId)
        val parsed = com.xuan.fitai.ai.GemmaOutputParser.parseFoodAnalysis("""{"calories":999,"protein":1,"carbs":1,"fat":1,"referenceFoodId":"A"}""")
        assertEquals("A", parsed.referenceFoodId)
    }

    @Test fun realPublishedDatasetParsesWithinLookupBudget() = runBlocking {
        // The host downloads this public fixture to the isolated emulator, never to the user's app data.
        val file = java.io.File("/data/local/tmp/fitai-tfda.zip")
        org.junit.Assume.assumeTrue(file.exists())
        val start = android.os.SystemClock.elapsedRealtime()
        val foods = java.util.zip.ZipInputStream(file.inputStream()).use { it.nextEntry; TfdaDatasetParser.parse(it) }
        val elapsed = android.os.SystemClock.elapsedRealtime() - start
        assertTrue("found ${foods.size} foods", foods.size >= 2000)
        assertEquals(183f, NutritionMatcher.find("白飯", foods).single().calories)
        assertTrue(NutritionMatcher.find("香蕉", foods).single().name.endsWith("平均值"))
        assertTrue("parse took $elapsed ms", elapsed < 15000)
        android.util.Log.i("FitAI_NutritionTest", "Parsed ${foods.size} complete foods in $elapsed ms")
        val beef = NutritionMatcher.find("牛肉", foods)
        assertEquals(5, beef.size)
        assertTrue(beef.all { it.category == "肉類" })
        assertFalse(beef.any { it.name.contains("醬") || it.name.contains("餡餅") })
        Unit
    }
}
