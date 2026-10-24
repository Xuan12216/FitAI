package com.xuan.fitai.nutrition

/** Explicit access/usage restrictions only; a generic slow page or network error is not a ban. */
object AiAccessRestriction {
    const val PATTERN = "unusual traffic|verify you are human|not a robot|too many requests|" +
        "(?:you have|you've|you’ve) reached.{0,60}(?:limit|quota)|daily limit reached|" +
        "系統偵測到.{0,40}異常流量|系统检测到.{0,40}异常流量|不是自動程式|不是机器人|確認您是真人|安全驗證|" +
        "已(?:達到|達|达到|超過|超过).{0,30}(?:使用上限|每日上限|查詢上限|查询上限|訊息上限|消息上限|使用限制|額度|额度)|" +
        "(?:使用|查詢|查询|每日|訊息|消息).{0,15}(?:上限|額度|额度).{0,15}(?:已達|已用完|已達到|已达到|已耗盡|已耗尽)"
    fun matches(text: String): Boolean = Regex(PATTERN, RegexOption.IGNORE_CASE).containsMatchIn(text)
}
