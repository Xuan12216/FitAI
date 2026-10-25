package com.xuan.fitai

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import com.xuan.fitai.nutrition.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.resume

/** Production extraction scripts against local HTML, without contacting any AI provider. */
class WebAiPageExtractionTest {
    @Test fun absentCitationsAreNeverReplacedWithAnOfficialSource() = runBlocking {
        for (provider in WebAiProvider.entries) {
            fixture(provider) { web ->
                val page = inspect(web, provider)
                assertTrue(provider.label, page.text.contains("200kcal"))
                assertTrue(provider.label, page.citations.isEmpty())
                assertNull(GoogleAiNutritionParser.parse("火腿蛋餅", "1份", page,
                    GoogleAiNutritionSource.providerUrl(provider, "火腿蛋餅", "1份", 1), provider))
            }
        }
    }

    @Test fun missingModelNameRemainsUnknownWhileRealCitationsArePreserved() = runBlocking {
        for (provider in listOf(WebAiProvider.DUCK, WebAiProvider.GEMINI, WebAiProvider.CHATGPT)) {
            fixture(provider, "<a href='$NTU_BREAKFAST_URL'>公開餐點資料</a>") { web ->
                val page = inspect(web, provider)
                assertNull(provider.label, page.modelLabel)
                assertEquals(NTU_BREAKFAST_URL, page.citations.single().url)
                val food = GoogleAiNutritionParser.parse("火腿蛋餅", "1份", page,
                    GoogleAiNutritionSource.providerUrl(provider, "火腿蛋餅", "1份", 1), provider)!!
                val raw = NutritionReferenceJson.encode(NutritionReference(1000, false, listOf(food)))
                assertEquals("${provider.label} / 模型未知", nutritionEstimatorLabel(raw, true))
            }
        }
    }

    @Test fun verificationIsBlockedForEveryProvider() = runBlocking {
        for (provider in WebAiProvider.entries) {
            fixture(provider, "Verify you are human") { web ->
                assertTrue(provider.label, inspect(web, provider).blocked)
            }
        }
    }

    private suspend fun inspect(web: WebView, provider: WebAiProvider): GoogleAiPage = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val script = "window.__fitAiDuckStage='sent';window.__fitAiGeminiStage='sent';window.__fitAiGptStage='sent';" +
                GoogleAiPageLoader.extractionScript(provider, "火腿蛋餅每1份")
            web.evaluateJavascript(script) { raw ->
                continuation.resume(GoogleAiPageLoader.decodePage(JSONArray("[$raw]").getString(0)))
            }
        }
    }

    private suspend fun fixture(provider: WebAiProvider, extra: String = "", block: suspend (WebView) -> Unit) {
        val loaded = CompletableDeferred<Unit>()
        val contents = "火腿蛋餅每1份，熱量200kcal、蛋白質10g、碳水15g、脂肪9g。$extra"
        val element = when (provider) {
            WebAiProvider.GOOGLE -> "<div data-container-id='main-col'>$contents</div>"
            WebAiProvider.BRAVE -> "<div class='message assistant llm-output'>$contents</div>"
            WebAiProvider.DUCK -> "<div data-activeresponse='true' id='test-assistant-message-1'>$contents</div>"
            WebAiProvider.GEMINI -> "<message-content>$contents</message-content>"
            WebAiProvider.CHATGPT -> "<div data-message-author-role='assistant'>$contents</div>"
        }
        val web = withContext(Dispatchers.Main) {
            WebView(ApplicationProvider.getApplicationContext()).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) { loaded.complete(Unit) }
                }
                loadDataWithBaseURL("https://fixture.invalid/", "<html><body>$element</body></html>", "text/html", "UTF-8", null)
            }
        }
        try { withTimeout(15_000) { loaded.await(); block(web) } }
        finally { withContext(Dispatchers.Main) { web.destroy() } }
    }
}
