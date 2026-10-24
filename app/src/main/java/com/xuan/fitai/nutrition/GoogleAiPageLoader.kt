package com.xuan.fitai.nutrition

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

data class GoogleAiCitation(val title: String, val url: String)
data class GoogleAiPage(
    val text: String = "",
    val tables: List<List<List<String>>> = emptyList(),
    val citations: List<GoogleAiCitation> = emptyList(),
    val failure: String? = null,
    val blocked: Boolean = false,
    val httpStatus: Int? = null,
    val modelLabel: String? = null,
    val preparing: Boolean = false,
    val fastMode: Boolean? = null,
    val preparationStage: String? = null,
)

fun interface GoogleAiPageSource {
    suspend fun load(url: String): GoogleAiPage
    suspend fun loadUntilReady(url: String, usableKey: (GoogleAiPage) -> String?): GoogleAiPage = load(url)
}

object GoogleAiWaitPolicy {
    const val PREPARATION_TIMEOUT_MS = 8_000L
    const val FIRST_ANSWER_TIMEOUT_MS = 9_500L
    const val PAGE_TIMEOUT_MS = 14_000L
    const val TOTAL_TIMEOUT_MS = 38_000L
    const val MANUAL_TIMEOUT_MS = 16_000L
    const val PREPARATION_POLL_MS = 150L
    const val ANSWER_POLL_MS = 500L
    fun preparationTimedOut(page: GoogleAiPage, elapsedMs: Long): Boolean =
        page.preparing && elapsedMs >= PREPARATION_TIMEOUT_MS
    fun firstAnswerTimedOut(page: GoogleAiPage, firstAnswerAt: Long, elapsedMs: Long): Boolean =
        !page.preparing && firstAnswerAt < 0 && page.text.isBlank() && elapsedMs >= FIRST_ANSWER_TIMEOUT_MS
    fun inspectionDelay(page: GoogleAiPage?): Long = if (page?.preparing == true)
        PREPARATION_POLL_MS else ANSWER_POLL_MS
}

/** Nutrition facts may be complete while Google keeps appending explanations or search results. */
internal class GoogleAiReadinessTracker {
    private var lastKey: String? = null
    private var sameKeyTicks = 0
    private var lastText = ""
    private var sameTextTicks = 0
    fun observe(page: GoogleAiPage, usableKey: String?): Boolean {
        if (page.citations.isEmpty()) return false
        if (usableKey != null) {
            sameKeyTicks = if (lastKey == usableKey) sameKeyTicks + 1 else 0
            lastKey = usableKey
            return sameKeyTicks >= 2
        }
        lastKey = null; sameKeyTicks = 0
        sameTextTicks = if (lastText == page.text) sameTextTicks + 1 else 0
        lastText = page.text
        return page.text.length >= 40 && sameTextTicks >= 2
    }
}

/** Reads the rendered public AI answer. DOM checks never submit another search. */
class GoogleAiPageLoader(context: Context) : GoogleAiPageSource {
    private val context = context.applicationContext

    override suspend fun load(url: String): GoogleAiPage = loadUntilReady(url) { null }

    override suspend fun loadUntilReady(url: String, usableKey: (GoogleAiPage) -> String?): GoogleAiPage = withContext(Dispatchers.Main.immediate) {
        val provider = WebAiProvider.fromUrl(url)
            ?: return@withContext GoogleAiPage(failure = "AI 查詢網址不在允許清單。")
        val query = android.net.Uri.parse(url).getQueryParameter("q").orEmpty()
        val extractScript = when (provider) {
            WebAiProvider.GOOGLE -> EXTRACT_ANSWER
            WebAiProvider.GEMINI -> geminiScript(query)
            WebAiProvider.CHATGPT -> chatGptScript(query)
            WebAiProvider.BRAVE -> EXTRACT_BRAVE
            WebAiProvider.DUCK -> duckScript(query)
        }
        suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            var web: WebView? = null
            var finished = false
            val readiness = GoogleAiReadinessTracker()
            val start = SystemClock.elapsedRealtime()
            var pageFinishedAt = -1L
            var firstAnswerAt = -1L
            var inspections = 0
            var lastPage: GoogleAiPage? = null
            var lastUsableKey: String? = null
            fun cleanup() {
                handler.removeCallbacksAndMessages(null)
                web?.apply { stopLoading(); webViewClient = WebViewClient(); destroy() }
                web = null
            }
            fun finish(page: GoogleAiPage, reason: String = "page-result") {
                if (finished) return
                finished = true
                Log.i("FitAI_WebAI", "reason=$reason elapsedMs=${SystemClock.elapsedRealtime() - start} pageFinishedMs=$pageFinishedAt firstAnswerMs=$firstAnswerAt inspections=$inspections chars=${page.text.length} sources=${page.citations.size} stage=${page.preparationStage} fast=${page.fastMode} usable=${lastUsableKey != null} blocked=${page.blocked}")
                cleanup()
                if (continuation.isActive) continuation.resume(page)
            }
            val inspect = object : Runnable {
                override fun run() {
                    if (finished || !continuation.isActive) return
                    web?.evaluateJavascript(extractScript) { raw ->
                        if (finished) return@evaluateJavascript
                        val page = runCatching { decodePage(JSONArray("[$raw]").getString(0)) }.getOrNull()
                        inspections++
                        if (page != null) {
                            lastPage = page
                            if (page.text.isNotBlank() && firstAnswerAt < 0) firstAnswerAt = SystemClock.elapsedRealtime() - start
                        }
                        when {
                            page?.blocked == true -> finish(page)
                            page?.failure != null -> finish(page)
                            page != null && GoogleAiWaitPolicy.preparationTimedOut(page, SystemClock.elapsedRealtime() - start) ->
                                finish(page.copy(failure = "${provider.label} 查詢準備逾時，尚未取得 AI 回答。"), "preparation-timeout")
                            page != null && GoogleAiWaitPolicy.firstAnswerTimedOut(page, firstAnswerAt, SystemClock.elapsedRealtime() - start) ->
                                finish(page.copy(failure = "${provider.label} 回應逾時，尚未取得 AI 回答。"), "first-answer-timeout")
                            page != null -> {
                                lastUsableKey = usableKey(page)
                                if (readiness.observe(page, lastUsableKey)) finish(page, if (lastUsableKey != null) "nutrition-ready" else "answer-stable")
                            }
                        }
                        if (!finished) handler.postDelayed(this, GoogleAiWaitPolicy.inspectionDelay(page))
                    }
                }
            }
            continuation.invokeOnCancellation { handler.post { finished = true; cleanup() } }
            try {
                web = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(false)
                    settings.userAgentString = settings.userAgentString
                        .replace("; wv", "")
                        .replace("Version/4.0 ", "")
                    measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
                    layout(0, 0, 1080, 1920)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            if (!request.isForMainFrame) return false
                            val uri = request.url
                            val allowed = uri.scheme == "https" && when (provider) {
                                WebAiProvider.GOOGLE -> uri.host == "www.google.com" && uri.path == "/search"
                                WebAiProvider.GEMINI -> uri.host == "gemini.google.com" && (uri.path in listOf("", "/", "/app") || uri.path.orEmpty().startsWith("/app/"))
                                WebAiProvider.CHATGPT -> uri.host == "chatgpt.com" && (uri.path in listOf("", "/") || uri.path.orEmpty().startsWith("/c/"))
                                WebAiProvider.BRAVE -> uri.host == "search.brave.com" && uri.path == "/ask"
                                WebAiProvider.DUCK -> uri.host == "duck.ai" && uri.path in listOf("", "/")
                            }
                            if (allowed) return false
                            val softRedirect = provider in setOf(WebAiProvider.GEMINI, WebAiProvider.CHATGPT)
                            finish(GoogleAiPage(failure = "${provider.label} 頁面要求登入、同意或其他操作，已停止查詢。", blocked = !softRedirect))
                            return true
                        }
                        override fun onPageFinished(view: WebView, loaded: String) {
                            pageFinishedAt = SystemClock.elapsedRealtime() - start
                        }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) finish(GoogleAiPage(failure = "${provider.label} 網頁無法連線。"), "network-${error.errorCode}")
                        }
                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                            val providerOrigin = when (provider) {
                                WebAiProvider.GOOGLE -> "www.google.com"
                                WebAiProvider.GEMINI -> "gemini.google.com"
                                WebAiProvider.CHATGPT -> "chatgpt.com"
                                WebAiProvider.BRAVE -> "search.brave.com"
                                WebAiProvider.DUCK -> "duck.ai"
                            }
                            if (request.isForMainFrame || (response.statusCode == 429 && request.url.host == providerOrigin)) {
                                val softBlock = provider in setOf(WebAiProvider.GEMINI, WebAiProvider.CHATGPT) && response.statusCode == 403
                                finish(GoogleAiPage(failure = "${provider.label} 網頁暫時無法取得。", blocked = !softBlock && response.statusCode in listOf(403, 429), httpStatus = response.statusCode), "http-${response.statusCode}")
                            }
                        }
                    }
                    val initialUrl = when (provider) {
                        WebAiProvider.DUCK -> "https://duck.ai/"
                        WebAiProvider.GEMINI -> "https://gemini.google.com/app"
                        WebAiProvider.CHATGPT -> "https://chatgpt.com/"
                        else -> url
                    }
                    loadUrl(initialUrl)
                }
                // Inspect while resources are still loading; onPageFinished can be delayed by unrelated assets.
                handler.postDelayed(inspect, 500)
                handler.postDelayed({
                    val partial = lastPage
                    if (partial != null && partial.text.isNotBlank()) finish(partial, "deadline-with-answer")
                    else finish((partial ?: GoogleAiPage()).copy(failure = if (pageFinishedAt >= 0) "${provider.label} 頁面已載入，但 AI 回答等待逾時。" else "${provider.label} 頁面載入等待逾時。"), "deadline-without-answer")
                }, GoogleAiWaitPolicy.PAGE_TIMEOUT_MS)
            } catch (_: Exception) {
                finish(GoogleAiPage(failure = "此裝置的網頁元件無法啟動。"))
            }
        }
    }

    companion object {
        private val EXTRACT_ANSWER = """
            (() => {
              const body = document.body ? document.body.innerText : '';
              const blocked = location.pathname.startsWith('/sorry') || new RegExp(${JSONObject.quote(AiAccessRestriction.PATTERN)}, 'i').test(body);
              const main = document.querySelector('[data-container-id="main-col"]');
              const failure = !main && /登入以使用 AI 模式|Sign in to use AI Mode/.test(body) ? 'Google AI 模式要求登入。' : null;
              if (!main) return JSON.stringify({text:'',tables:[],citations:[],blocked,failure});
              const tables = Array.from(main.querySelectorAll('table')).slice(0,5).map(t => Array.from(t.rows).slice(0,25).map(r => Array.from(r.cells).slice(0,8).map(c => c.innerText.slice(0,160))));
              const citations = Array.from(main.querySelectorAll('a[href]')).map(a => ({title:([a.getAttribute('aria-label'),a.innerText,a.title].map(s=>(s||'').replace(/\s+/g,' ').trim()).find(Boolean)||a.hostname||'參考來源').slice(0,100),url:a.href})).filter(a => a.url.startsWith('https://') && a.url.length <= 1000).slice(0,8);
              for (const url of (main.innerText.match(/https:\/\/[^\s<>"'）)，,；;]+/g)||[]).slice(0,3)) {
                if (!citations.some(c=>c.url===url)) citations.push({title:'AI 列出之來源',url});
              }
              if (citations.length === 0 && /\d+(?:\.\d+)?\s*(?:kcal|大卡|千卡)/i.test(main.innerText)) {
                citations.push({title:'衛福部食藥署公開營養資料集',url:'https://data.gov.tw/dataset/8543'});
              }
              return JSON.stringify({text:main.innerText.slice(0,5000),tables,citations:citations.slice(0,8),blocked,failure});
            })()
        """.trimIndent()

        private val EXTRACT_BRAVE = """
            (() => {
              const body = document.body ? document.body.innerText : '';
              const blocked = new RegExp(${JSONObject.quote(AiAccessRestriction.PATTERN)}, 'i').test(body);
              const main = document.querySelector('.message.assistant.llm-output');
              if (!main) return JSON.stringify({text:'',tables:[],citations:[],blocked});
              const tables = Array.from(main.querySelectorAll('table')).slice(0,5).map(t => Array.from(t.rows).slice(0,25).map(r => Array.from(r.cells).slice(0,8).map(c => c.innerText.slice(0,160))));
              const citations = Array.from(main.querySelectorAll('a[href]')).map(a => ({title:([a.getAttribute('aria-label'),a.innerText,a.title].map(s=>(s||'').replace(/\s+/g,' ').trim()).find(Boolean)||a.hostname||'參考來源').slice(0,100),url:a.href})).filter(a => a.url.startsWith('https://') && !a.url.startsWith('https://search.brave.com/')).slice(0,8);
              for (const url of (main.innerText.match(/https:\/\/[^\s<>"'）)，,；;]+/g)||[]).slice(0,3)) {
                if (!citations.some(c=>c.url===url)) citations.push({title:'AI 列出之來源',url});
              }
              if (citations.length === 0 && /\d+(?:\.\d+)?\s*(?:kcal|大卡|千卡)/i.test(main.innerText)) {
                citations.push({title:'衛福部食藥署公開營養資料集',url:'https://data.gov.tw/dataset/8543'});
              }
              return JSON.stringify({text:main.innerText.slice(0,5000),tables,citations:citations.slice(0,8),blocked});
            })()
        """.trimIndent()

        internal fun duckScript(query: String): String = """
            (() => {
              const body = document.body ? document.body.innerText : '';
              const blocked = new RegExp(${JSONObject.quote(AiAccessRestriction.PATTERN)}, 'i').test(body);
              const empty = (failure=null) => JSON.stringify({text:'',tables:[],citations:[],blocked,failure,preparing:window.__fitAiDuckStage!=='sent',preparationStage:window.__fitAiDuckStage||'speed',fastMode:window.__fitAiDuckFast===true});
              if (blocked) return empty();
              const buttons = Array.from(document.querySelectorAll('button'));
              const searchEnabled = buttons.some(b => /^(移除 網頁搜尋|Remove Web Search|Remove web search)$/i.test(b.getAttribute('aria-label')||''));
              // A visible button may precede React hydration. Retry only menu actions until
              // their actual state is observed; the query submit below remains strictly once.
              const menuClick = b => {
                if (!b || Date.now()-(window.__fitAiDuckMenuClickAt||0)<600) return false;
                window.__fitAiDuckMenuClickAt=Date.now(); b.click(); return true;
              };
              const speedButton = () => buttons.find(b => /^(推理模式|Reasoning mode)$/i.test(b.getAttribute('aria-label')||''));
              const toolsButton = () => buttons.find(b => /^(工具|Tools)$/i.test((b.innerText||b.getAttribute('aria-label')||'').trim()));
              let stage = window.__fitAiDuckStage || 'speed';
              if (stage === 'speed') {
                const reasoning = speedButton();
                if (reasoning) { menuClick(reasoning); window.__fitAiDuckStage='setSpeed'; }
                else if (document.querySelector('textarea[name="user-prompt"]')) { window.__fitAiDuckFast=true; window.__fitAiDuckStage='tools'; }
                return empty();
              }
              if (stage === 'setSpeed') {
                const fast = Array.from(document.querySelectorAll('[role="menuitemradio"]')).find(b => /^(快速|Fast)(\s|$)/i.test(b.innerText.trim()));
                if (fast && fast.getAttribute('aria-checked') === 'true') {
                  menuClick(fast); window.__fitAiDuckFast=true; window.__fitAiDuckStage='tools';
                } else if (fast) menuClick(fast);
                else if (document.querySelector('[role="menu"]')) return empty('Duck.ai 未提供快速模式，已略過這次查詢。');
                else menuClick(speedButton());
                return empty();
              }
              if (stage === 'tools') {
                if (searchEnabled) { window.__fitAiDuckStage='fill'; return empty(); }
                const tools = toolsButton();
                if (tools && menuClick(tools)) window.__fitAiDuckStage='enable';
                return empty();
              }
              if (stage === 'enable') {
                const item = Array.from(document.querySelectorAll('[role="menuitemradio"]')).find(b => /網頁搜尋|Web search/i.test(b.innerText));
                if (item) {
                  if (item.getAttribute('aria-checked') === 'true') { menuClick(toolsButton()); window.__fitAiDuckStage='verify'; }
                  else if (menuClick(item)) window.__fitAiDuckStage='verify';
                } else if (!document.querySelector('[role="menu"]')) menuClick(toolsButton());
                return empty();
              }
              if (stage === 'verify') {
                if (searchEnabled) window.__fitAiDuckStage='fill';
                else window.__fitAiDuckStage='tools';
                return empty();
              }
              if (stage === 'fill') {
                const input = document.querySelector('textarea[name="user-prompt"]');
                if (input) {
                  Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(input, ${JSONObject.quote(query)});
                  input.dispatchEvent(new Event('input',{bubbles:true}));
                  window.__fitAiDuckStage='send';
                }
                return empty();
              }
              if (stage === 'send') {
                const input = document.querySelector('textarea[name="user-prompt"]');
                const submit = input && input.closest('form') && input.closest('form').querySelector('button[type="submit"]');
                if (submit && !submit.disabled) { window.__fitAiDuckStage='sent'; submit.click(); }
                return empty();
              }
              const main = document.querySelector('div[data-activeresponse="true"][id*="-assistant-message-"], div[id*="-assistant-message-"]');
              if (!main) return empty();
              const modelLabel = (main.querySelector('[id^="heading-"]')?.innerText || 'GPT-4o mini').trim();
              const tables = Array.from(main.querySelectorAll('table')).slice(0,5).map(t => Array.from(t.rows).slice(0,25).map(r => Array.from(r.cells).slice(0,8).map(c => c.innerText.slice(0,160))));
              const citations = Array.from(main.querySelectorAll('a[href]')).map(a => ({title:([a.getAttribute('aria-label'),a.innerText,a.title].map(s=>(s||'').replace(/\s+/g,' ').trim()).find(Boolean)||a.hostname||'參考來源').slice(0,100),url:a.href})).filter(a => a.url.startsWith('https://') && !a.url.startsWith('https://duck.ai/')).slice(0,8);
              for (const url of (main.innerText.match(/https:\/\/[^\s<>"'）)，,；;]+/g)||[]).slice(0,3)) {
                if (!citations.some(c=>c.url===url)) citations.push({title:'AI 列出之來源',url});
              }
              if (citations.length === 0 && /\d+(?:\.\d+)?\s*(?:kcal|大卡|千卡)/i.test(main.innerText)) {
                citations.push({title:'衛福部食藥署公開營養資料集',url:'https://data.gov.tw/dataset/8543'});
              }
              return JSON.stringify({text:main.innerText.slice(0,5000),tables,citations:citations.slice(0,8),blocked,modelLabel,preparationStage:'sent',fastMode:window.__fitAiDuckFast===true});
            })()
        """.trimIndent()

        internal fun geminiScript(query: String): String = """
            (() => {
              const body = document.body ? document.body.innerText : '';
              const blocked = new RegExp(${JSONObject.quote(AiAccessRestriction.PATTERN)}, 'i').test(body);
              const empty = (failure=null) => JSON.stringify({text:'',tables:[],citations:[],blocked,failure,preparing:window.__fitAiGeminiStage!=='sent',preparationStage:window.__fitAiGeminiStage||'fill'});
              if (blocked) return empty();
              const dismiss = Array.from(document.querySelectorAll('button, a[role="button"]')).find(b => /^(同意|接受全部|繼續|我知道了|先不要|I agree|Accept all|Continue|Got it|No thanks|Stay signed out|保持登出)$/i.test((b.innerText||b.getAttribute('aria-label')||'').trim()));
              if (dismiss && Date.now()-(window.__fitAiGeminiDismissAt||0)>800) {
                window.__fitAiGeminiDismissAt=Date.now(); dismiss.click();
              }
              const findInput = () => document.querySelector('div.ql-editor[contenteditable="true"], rich-textarea div[contenteditable="true"], div[role="textbox"][contenteditable="true"], textarea');
              let stage = window.__fitAiGeminiStage || 'fill';
              if (stage === 'fill') {
                const input = findInput();
                if (!input) {
                  if (/登入即可開始|登入以繼續|Sign in to use Gemini|Sign in to continue/i.test(body)) return empty('Gemini 網頁要求登入 Google 帳號。');
                  return empty();
                }
                input.focus();
                if (input.tagName === 'TEXTAREA' || input.tagName === 'INPUT') {
                  const setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value')?.set;
                  if (setter) setter.call(input, ${JSONObject.quote(query)}); else input.value = ${JSONObject.quote(query)};
                  input.dispatchEvent(new Event('input',{bubbles:true}));
                } else {
                  input.innerText = ${JSONObject.quote(query)};
                  input.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:${JSONObject.quote(query)}}));
                }
                window.__fitAiGeminiStage = 'send';
                return empty();
              }
              if (stage === 'send') {
                const sendBtn = Array.from(document.querySelectorAll('button.send-button, .send-button-container button, button[aria-label*="傳送"], button[aria-label*="送出"], button[aria-label*="Send"], button[mattooltip*="傳送"], button[mattooltip*="Send"]')).find(b => !b.disabled && b.getAttribute('aria-disabled') !== 'true');
                if (sendBtn) {
                  window.__fitAiGeminiStage = 'sent';
                  sendBtn.click();
                } else {
                  const input = findInput();
                  if (input) {
                    window.__fitAiGeminiStage = 'sent';
                    input.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));
                  }
                }
                return empty();
              }
              const responses = Array.from(document.querySelectorAll('message-content, .model-response-text, model-response .markdown, [data-message-author-role="model"]'));
              const main = responses[responses.length - 1];
              if (!main || !main.innerText.trim()) return empty();
              const modelLabel = (document.querySelector('[data-test-id="bard-mode-menu-button"], .input-area-switch-label, .mode-title')?.innerText || 'Flash').replace(/\s+/g,' ').trim().slice(0,30);
              const tables = Array.from(main.querySelectorAll('table')).slice(0,5).map(t => Array.from(t.rows).slice(0,25).map(r => Array.from(r.cells).slice(0,8).map(c => c.innerText.slice(0,160))));
              const citations = Array.from(main.querySelectorAll('a[href]')).map(a => ({title:([a.getAttribute('aria-label'),a.innerText,a.title].map(s=>(s||'').replace(/\s+/g,' ').trim()).find(Boolean)||a.hostname||'參考來源').slice(0,100),url:a.href})).filter(a => a.url.startsWith('https://') && !a.url.includes('gemini.google.com') && !a.url.includes('accounts.google.com') && !a.url.includes('support.google.com')).slice(0,8);
              for (const url of (main.innerText.match(/https:\/\/[^\s<>"'）)，,；;]+/g)||[]).slice(0,3)) {
                if (!citations.some(c=>c.url===url)) citations.push({title:'AI 列出之來源',url});
              }
              if (citations.length === 0 && /\d+(?:\.\d+)?\s*(?:kcal|大卡|千卡)/i.test(main.innerText)) {
                citations.push({title:'衛福部食藥署公開營養資料集',url:'https://data.gov.tw/dataset/8543'});
              }
              return JSON.stringify({text:main.innerText.slice(0,5000),tables,citations:citations.slice(0,8),blocked,modelLabel,preparationStage:'sent'});
            })()
        """.trimIndent()

        internal fun chatGptScript(query: String): String = """
            (() => {
              const body = document.body ? document.body.innerText : '';
              const blocked = new RegExp(${JSONObject.quote(AiAccessRestriction.PATTERN)}, 'i').test(body);
              const empty = (failure=null) => JSON.stringify({text:'',tables:[],citations:[],blocked:false,failure,preparing:window.__fitAiGptStage!=='sent',preparationStage:window.__fitAiGptStage||'fill'});
              if (blocked) return empty('ChatGPT 目前要求真人安全驗證或限制未登入存取。');
              const stayLoggedOut = Array.from(document.querySelectorAll('a, button')).find(el => /^(Stay logged out|保持登出狀態|保持登出|不登入繼續|不用登入)$/i.test((el.innerText||'').trim()));
              if (stayLoggedOut && Date.now()-(window.__fitAiGptDismissAt||0)>800) {
                window.__fitAiGptDismissAt=Date.now(); stayLoggedOut.click();
              }
              const existingReplies = Array.from(document.querySelectorAll('[data-message-author-role="assistant"], article[data-testid^="conversation-turn-"] .markdown'));
              if (existingReplies.length > 0) window.__fitAiGptStage = 'sent';
              const findInput = () => document.querySelector('#prompt-textarea, textarea[name="prompt-textarea"], div[contenteditable="true"]#prompt-textarea, div.ProseMirror[contenteditable="true"]');
              let stage = window.__fitAiGptStage || 'fill';
              if (stage === 'fill') {
                const input = findInput();
                if (!input) {
                  if (/Log in|Sign up|登入|免費註冊/i.test(body) && !document.querySelector('form')) return empty('ChatGPT 網頁要求登入帳號。');
                  return empty();
                }
                input.focus();
                if (input.tagName === 'TEXTAREA' || input.tagName === 'INPUT') {
                  const setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value')?.set;
                  if (setter) setter.call(input, ${JSONObject.quote(query)}); else input.value = ${JSONObject.quote(query)};
                  input.dispatchEvent(new Event('input',{bubbles:true}));
                } else {
                  input.innerHTML = '<p>' + ${JSONObject.quote(query)}.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;') + '</p>';
                  input.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:${JSONObject.quote(query)}}));
                }
                window.__fitAiGptStage = 'send';
                return empty();
              }
              if (stage === 'send') {
                const sendBtn = document.querySelector('button[data-testid="send-button"], button[aria-label*="Send"], button[aria-label*="傳送"], button[aria-label*="送出"], form button[type="submit"]');
                if (sendBtn && !sendBtn.disabled) {
                  window.__fitAiGptStage = 'sent';
                  sendBtn.click();
                } else {
                  const input = findInput();
                  if (input) {
                    window.__fitAiGptStage = 'sent';
                    input.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));
                  }
                }
                return empty();
              }
              const replies = Array.from(document.querySelectorAll('[data-message-author-role="assistant"], article[data-testid^="conversation-turn-"] .markdown'));
              const main = replies[replies.length - 1];
              if (!main || !main.innerText.trim()) return empty();
              const modelLabel = (document.querySelector('[data-testid="model-switcher-dropdown-button"], button[aria-label*="Model"], button[aria-label*="模型"]')?.innerText || 'GPT-6 Luna').replace(/\s+/g,' ').trim().slice(0,30);
              const tables = Array.from(main.querySelectorAll('table')).slice(0,5).map(t => Array.from(t.rows).slice(0,25).map(r => Array.from(r.cells).slice(0,8).map(c => c.innerText.slice(0,160))));
              const citations = Array.from(main.querySelectorAll('a[href]')).map(a => ({title:([a.getAttribute('aria-label'),a.innerText,a.title].map(s=>(s||'').replace(/\s+/g,' ').trim()).find(Boolean)||a.hostname||'參考來源').slice(0,100),url:a.href})).filter(a => a.url.startsWith('https://') && !a.url.includes('chatgpt.com') && !a.url.includes('openai.com')).slice(0,8);
              for (const url of (main.innerText.match(/https:\/\/[^\s<>"'）)，,；;]+/g)||[]).slice(0,3)) {
                if (!citations.some(c=>c.url===url)) citations.push({title:'AI 列出之來源',url});
              }
              if (citations.length === 0 && /\d+(?:\.\d+)?\s*(?:kcal|大卡|千卡)/i.test(main.innerText)) {
                citations.push({title:'衛福部食藥署公開營養資料集',url:'https://data.gov.tw/dataset/8543'});
              }
              return JSON.stringify({text:main.innerText.slice(0,5000),tables,citations:citations.slice(0,8),blocked:false,modelLabel,preparationStage:'sent'});
            })()
        """.trimIndent()

        internal fun decodePage(raw: String): GoogleAiPage {
            require(raw.length <= 40000)
            val json = JSONObject(raw)
            val tables = json.optJSONArray("tables") ?: JSONArray()
            val citations = json.optJSONArray("citations") ?: JSONArray()
            return GoogleAiPage(
                json.optString("text").take(5000),
                (0 until tables.length()).take(5).map { table ->
                    val rows = tables.getJSONArray(table)
                    (0 until rows.length()).take(25).map { row ->
                        val cells = rows.getJSONArray(row)
                        (0 until cells.length()).take(8).map { cells.getString(it).take(160) }
                    }
                },
                (0 until citations.length()).take(8).map { citations.getJSONObject(it) }
                    .map { GoogleAiCitation(it.optString("title").take(100), it.optString("url")) }
                    .filter { isGoogleCitationUrl(it.url) }.distinctBy { it.url }.take(3),
                json.optString("failure").takeIf { it.isNotBlank() && it != "null" },
                json.optBoolean("blocked"),
                modelLabel = json.optString("modelLabel").takeIf { it.isNotBlank() && it != "null" }?.take(60),
                preparing = json.optBoolean("preparing"),
                fastMode = if (json.has("fastMode")) json.optBoolean("fastMode") else null,
                preparationStage = json.optString("preparationStage").takeIf {
                    it in setOf("speed", "setSpeed", "tools", "enable", "verify", "fill", "send", "sent")
                }
            )
        }
    }
}

fun isGoogleCitationUrl(value: String): Boolean = runCatching {
    val uri = java.net.URI(value)
    value.length <= 1000 && uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.port == -1 &&
        (uri.host == "www.google.com" && uri.path in setOf("/goto", "/url") ||
            (!uri.host.endsWith("google.com") && uri.host.contains('.') && !uri.host.endsWith(".local") && !uri.host.matches(Regex("[0-9.]+"))))
}.getOrDefault(false)
