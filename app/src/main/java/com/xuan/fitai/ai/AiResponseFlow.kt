package com.xuan.fitai.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal fun Flow<String>.requireAiResponse(): Flow<String> = flow {
    var hasContent = false
    collect { token ->
        val visible = token.replace(Regex("<\\|[^>]*\\|>|</?think>"), "")
        if (visible.isNotBlank()) hasContent = true
        emit(token)
    }
    check(hasContent) { "模型沒有產生回覆。請重試，或到模型參數增加上下文容量。" }
}
