# FitAI 1.1 實作與 Code Review

日期：2026-10-23。檢查範圍為本次工作目錄的功能整合與先前首頁／暖機修改；由同一實作者進行程式碼檢查及回歸測試，沒有外部審查者。

## 完成內容

- 餐點：修改、份量、日期時間補登、餐別、常用標記、最近餐點帶入、確認後再記一次，近七日點選明細及任意日期瀏覽。
- 首頁：記錄按鈕優先、AI 標題與可展開摘要、更新時間與快取、替代食物提問及餐點草稿入口，熱量超標回饋。
- 體重：日期紀錄、最近 30 次趨勢、目標距離；明確確認才套用個人資料與營養目標。
- 訓練：按週計算完成、逐日逐組重量與次數、上次紀錄、30／60／90 秒休息計時。清除本週組數須確認。
- 聊天／掃描：提問捷徑、聊天轉餐點確認表單、清除聊天確認、附件跨尺寸保留；掃描縮圖與可修改的營養數值，修正模型可用性判斷。
- 個人與飲水：即時 BMR／TDEE／BMI／營養預覽、有效輸入檢查、自訂飲水目標與杯量。
- 備份：版本化 JSON，備份餐點、常用標記、體重、課表與訓練組數；先驗證、顯示筆數、確認取代，交易內還原。介面明示不含聊天、個人設定與模型檔。
- 保留背景暖機，暖機期間顯示非阻擋提示，載入後隱藏；thinking 預設關閉。

## 摺疊設計依據

參考 Google 官方 [Window size classes](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes)、[Canonical layouts](https://developer.android.com/develop/ui/compose/layouts/adaptive/canonical-layouts) 與 [Fold-aware layouts](https://developer.android.com/develop/adaptive-apps/guides/foldables/make-your-app-fold-aware)。

- 依目前 App 視窗寬高調整，非依裝置型號。600dp 起側邊導覽；840dp 起首頁支援雙欄；高度不足 480dp 時維持緊湊配置。
- 使用 WindowInfoTracker 接收 FoldingFeature，將視窗座標轉換至內容座標。
- 分隔式垂直鉸鏈採左右支援面板、水平鉸鏈採上下支援面板；單面板頁面選擇可用區域，主要輸入彈窗移到一側。
- 表單使用可保存狀態，聊天媒體與計時截止時間由 ViewModel 保存，支援尺寸切換與 Activity 重建；不承諾程序被系統終止後仍保存記憶體附件。

## Review 發現與修正

| 等級 | 問題 | 修正及驗證 |
| --- | --- | --- |
| P1 | 還原中途失敗可能造成部分資料被取代 | 整份備份先解析驗證，Room transaction 包住刪除與寫入；觸發器注入失敗，確認原餐點回滾保留 |
| P1 | DB 升級不可清除使用者資料 | 移除 destructive fallback，新增 3→4 migration；舊餐點遷移測試及實機原有紀錄確認 |
| P1 | 舊 Android 不支援 InputStream.readNBytes | 改為有大小上限的串流讀取，Release lint 通過 |
| P2 | 跨日而餐點相同可能沿用前日 AI 建議 | 生成事件與內容簽章納入當日日期，更新時間在重新產生時清除 |
| P2 | 連點快速完成可能重複寫入或部分完成 | 每動作防重入，缺少組數在 Room transaction 中補齊，保留既有重量；錯誤顯示於 UI |
| P2 | 摺疊會重設部分訓練編輯欄位 | 用可保存的動作 ID 與表單欄位，資料尚未載入時不初始化空白編輯表單 |
| P2 | 無效課表組數影響完成計算及備份 | 手動表單限制 1–100 組，AI 課表組數限制範圍 |
| P2 | 掃描重複儲存／取消被視為一般錯誤 | 儲存防重入，CancellationException 正確向上傳遞 |
| P2 | 錄音初始化遇權限撤銷或失敗 | 明確處理 SecurityException，失敗時釋放 AudioRecord |
| P2 | 編輯餐點會丟失原有 AI 來源資訊 | 編輯與複製保留原有估算標記與建議內容 |

## 自動驗證

- Debug 與 Release 編譯通過。
- 42 項 JVM 單元測試通過。
- 11 項 Android 裝置測試通過：5 項自適應 Compose 測試、6 項資料完整性測試。
- 自適應測試涵蓋窄版、寬版雙欄、水平鉸鏈、垂直鉸鏈與單面板避讓、尺寸切換輸入保留。
- 資料測試涵蓋編輯／複製身份與日期、備份往返、無效備份、還原失敗回滾、每組更新與跨週保留、3→4 遷移。
- Release lint：0 errors、38 warnings、7 hints。保留原有 target SDK、依賴升級、提醒電池最佳化設定等非阻擋提示；未停用檢查規則。
- git diff --check 通過。

## 畫面與實機驗收

- 模擬器 1600×1200 / 160 dpi：側邊導覽與首頁雙欄正常，見 expanded-dashboard.png。
- 餐點表單輸入後，從 1600dp 切到 420dp，文字保留，見 compact-form-retained.png；未儲存測試餐點。
- 訓練課表輸入後從 1600dp 切到 420dp，動作名稱保留；取消表單，沒有新增測試課表。
- 手機 SM-F9710：原 1.0 覆蓋安裝為 1.1，使用相同本機簽章，Release 非 debuggable，未清除 App 資料。
- 冷啟動有背景暖機提示；暖機期間成功開啟餐點表單並取消，沒有儲存測試資料。
- 實機本輪載入模型約 20 秒；實際耗時依模型、裝置與快取狀態而變。

## 驗收邊界

鉸鏈位置與桌面姿態使用受控 FoldingFeature 測試；實機在目前展開狀態驗收，沒有替使用者進行實體開合。未在真實手機執行覆蓋還原測試，以免改動使用者資料；還原、回滾與無效檔案以隔離資料庫測試。聊天／掃描的所有模型、相機及語音輸入組合未逐一實測。最後的手機畫面已切到其他 App，未再次接管前景檢查暖機完成畫面；完成後隱藏由載入狀態控制，已確認實機日誌達到 Loaded。

APK：app/build/outputs/apk/release/FitAI-release.apk（沿用本機 debug.keystore 簽署的 Release variant，供本機覆蓋安裝）。

## 側邊導覽狀態列背景修正

ADB 實機截圖確認：NavigationRail 外側的 statusBarsPadding 使背景從狀態列下方才開始。移除外側 padding，保留元件預設 windowInsets，讓背景繪製至視窗頂端、導覽項目仍避開系統列。Release 建置與 lint 通過，同簽章覆蓋安裝手機後，以 rail-statusbar-after.png 確認背景已延伸到狀態列。未新增測試，這次為單一低影響視覺修正，採實機前後畫面驗證。

## Material 3 日期選擇器

餐點歷史日期、餐點補登／編輯日期與體重日期統一使用共用 PastDatePickerDialog（Material 3 DatePicker 與 DatePickerDialog），取代 android.app.DatePickerDialog。沿用 App 主題、支援文字輸入與摺疊彈窗避讓、停用未來日期，UTC 午夜值轉換為 LocalDate，避免裝置時區造成日期偏移。Release 建置與 lint 通過；同簽章覆蓋安裝實機。ADB 實測未來日期停用、取消維持 10/23、確認可正確套用 10/22，最後還原為今天，沒有修改餐點紀錄。新畫面見 date-picker-material3.png。
