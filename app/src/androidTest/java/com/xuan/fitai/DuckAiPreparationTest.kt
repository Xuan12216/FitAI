package com.xuan.fitai

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import com.xuan.fitai.nutrition.GoogleAiPageLoader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.resume

/** Runs production DOM preparation against local HTML; never queries an external service. */
class DuckAiPreparationTest {
    @Test fun ignoredMenuActionsRecoverAndSubmitOnlyOnce() = runBlocking {
        fixture { web ->
            repeat(40) { inspect(web) }
            val state = JSONObject(evaluate(web, "JSON.stringify({fast:fastSelected,search:searchOn,modeClicks,searchClicks,submits,stage:window.__fitAiDuckStage})"))
            assertTrue(state.toString(), state.getBoolean("fast"))
            assertTrue(state.toString(), state.getBoolean("search"))
            assertTrue(state.getInt("modeClicks") >= 2)
            assertTrue(state.getInt("searchClicks") >= 2)
            assertEquals("sent", state.getString("stage"))
            assertEquals(1, state.getInt("submits"))
        }
    }

    @Test fun missingFastModeNeverSubmitsAQuestion() = runBlocking {
        fixture { web ->
            evaluate(web, "window.omitFast=true; 'ok'")
            var failure: String? = null
            repeat(12) { failure = failure ?: inspect(web).failure }
            assertEquals("Duck.ai 未提供快速模式，已略過這次查詢。", failure)
            assertEquals("0", evaluate(web, "String(submits)"))
        }
    }

    private suspend fun inspect(web: WebView) = GoogleAiPageLoader.decodePage(evaluate(web,
        "window.__fitAiDuckMenuClickAt=0;" + GoogleAiPageLoader.duckScript("測試餐點每1份營養")))

    private suspend fun evaluate(web: WebView, script: String): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            web.evaluateJavascript(script) { raw -> continuation.resume(JSONArray("[$raw]").getString(0)) }
        }
    }

    private suspend fun fixture(block: suspend (WebView) -> Unit) {
        val loaded = CompletableDeferred<Unit>()
        val web = withContext(Dispatchers.Main) {
            WebView(ApplicationProvider.getApplicationContext()).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) { loaded.complete(Unit) }
                }
                loadDataWithBaseURL("https://duck.ai/", HTML, "text/html", "UTF-8", null)
            }
        }
        try { withTimeout(10_000) { loaded.await(); block(web) } }
        finally { withContext(Dispatchers.Main) { web.destroy() } }
    }

    companion object {
        private val HTML = """
            <html><body><button aria-label="推理模式" id="mode">Mode</button>
            <button id="tools">工具</button><form><textarea name="user-prompt"></textarea>
            <button type="submit" disabled id="send">問</button></form><script>
            let fastSelected=false, searchOn=false, modeClicks=0, searchClicks=0, submits=0;
            function menu(kind) {
              const old=document.querySelector('[role="menu"]'); if(old) old.remove();
              const m=document.createElement('div');m.setAttribute('role','menu');m.dataset.kind=kind;document.body.append(m);return m;
            }
            document.querySelector('#mode').onclick=()=>{
              if(++modeClicks===1) return; // visible before its first interaction becomes effective
              const m=menu('mode'), item=document.createElement('button');
              item.setAttribute('role','menuitemradio');item.setAttribute('aria-checked',String(fastSelected));
              item.innerText=window.omitFast?'推理型':'快速 立即回答';
              item.onclick=()=>{fastSelected=true;m.remove()};m.append(item);
            };
            document.querySelector('#tools').onclick=()=>{
              const old=document.querySelector('[role="menu"]');
              if(old && old.dataset.kind==='tools'){old.remove();return;}
              const m=menu('tools'), item=document.createElement('button');
              item.setAttribute('role','menuitemradio');item.setAttribute('aria-checked',String(searchOn));item.innerText='網頁搜尋';
              item.onclick=()=>{
                if(++searchClicks===1)return;
                searchOn=true;const chip=document.createElement('button');chip.setAttribute('aria-label','移除 網頁搜尋');document.body.append(chip);m.remove();
              };m.append(item);
            };
            document.querySelector('textarea').oninput=()=>document.querySelector('#send').disabled=false;
            document.querySelector('form').onsubmit=e=>{e.preventDefault();submits++};
            </script></body></html>
        """.trimIndent()
    }
}
