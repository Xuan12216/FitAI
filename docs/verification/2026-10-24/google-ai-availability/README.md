# Google AI 查詢可用性樣本

## 範圍與限制

日期：2026-10-24（Asia/Taipei）。目的為小量正常頻率的可用性取樣，不是刻意觸發封鎖或推導安全查詢上限。

正式 App 的「最多 2 次」是每次評估的自訂搜尋／重試上限，並非每天上限，也不是 Google 公布的配額。本次沒有修改正式 App 的查詢策略或安裝新的正式版本。

Google 官方將自動化查詢、共用網路上的流量列為異常流量訊息的可能原因，未在該說明提供可套用的固定次數：
<https://support.google.com/websearch/answer/86640?hl=en>

## 測試方式

- Samsung SM-F9710，已配對的無線 ADB。
- 使用相同 production loader/parser，但安裝成隔離套件 `com.xuan.fitai.probe`，不操作正式 App 的餐點、模型或備份設定。
- 最多 5 筆，各送出一次全新的搜尋，完成後間隔 5 秒。餐點依序為火腿蛋餅、鮪魚三明治、牛肉麵、雞肉飯及茶葉蛋。
- 單頁仍為 25 秒上限；無平行搜尋、無自動重試。遇到驗證／限流立刻停止，連續兩次頁面錯誤也停止。
- `GoogleAiAvailabilityDiagnosticTest` 預設跳過，需明確傳入 `-e runGoogleAvailabilitySample true`，且只允許獨立 probe 套件執行。測試斷言只確保送出筆數介於 1～5，不能把 JUnit 成功當成所有回答可用，需閱讀逐筆輸出。
- 桌面 IAB 先完成兩筆不同餐點查詢，兩筆都有回答及來源，未見驗證；與手機的環境及對話方式不同，不合併成手機的連續查詢數。
- 手機測試時亦觀察到正式 App 另有查詢耗時紀錄，因此這不是完全隔離的網路負載實驗，不能推導 IP 的限流門檻。

## 手機實測結果

| 次序 | 餐點 | 秒數 | 結果 |
| --- | --- | ---: | --- |
| 1 | 火腿蛋餅 | 3.997 | 可用回答、1 個來源 |
| 2 | 鮪魚三明治 | 5.150 | 可用回答、1 個來源 |
| 3 | 牛肉麵 | 3.824 | 可用回答、1 個來源 |
| 4 | 雞肉飯 | 5.093 | 可用回答、1 個來源 |
| 5 | 茶葉蛋 | 19.448 | 可用回答、2 個來源 |

5/5 回答通過目前 parser 的品項、份量、四項營養素及來源檢查；這不等於人工核實營養數字正確。未觀察到 blocked／驗證／403／429。查詢耗時平均約 7.50 秒、中位數約 5.09 秒，整輪約 57.53 秒（含筆間等待）。

這只能說明本次條件下五筆不同餐點的逐次查詢可用，不能推導第六筆是否限流、每分鐘／每日安全額度、同一餐點反覆重試的效果，或整個 IP 的封鎖門檻。原始測試輸出見 `phone-test.txt`，只保留 probe 程序的耗時見 `phone-timings.txt`。JUnit 診斷 1 項完成，獨立測試 App 與套件已移除。

## 正式串接替代選項

- [Gemini + Google Search grounding](https://ai.google.dev/gemini-api/docs/google-search)：官方搜尋與引用，需 API key；[額度與費用依模型及方案](https://ai.google.dev/gemini-api/docs/pricing)。
- [Perplexity Agent API](https://docs.perplexity.ai/docs/agent-api/quickstart)：提供聯網回答及引用，需 API key，[依用量計費](https://docs.perplexity.ai/docs/getting-started/pricing)。Search API 本身則提供搜尋結果，與 AI 統整回答不同。
- [Microsoft Foundry Web Search](https://learn.microsoft.com/en-us/azure/foundry/agents/how-to/tools/web-search)：使用 Bing grounding 並提供引用，需要 Azure 專案、模型及憑證，會產生費用。

API 的結構與額度較容易管理，但仍須處理配額／限流。替代服務沒有在本次建立帳號、付費或實際串接；免費網頁版自動讀取仍需另行驗證。

## 準備檢查

隔離 Debug App 與 Android test APK 編譯成功，`git diff --check` 通過。沒有重跑正式 App 的整套單元與裝置測試；本次沒有 production 行為變更。
