package com.xuan.fitai.nutrition

import com.xuan.fitai.ai.GemmaLocalHelper
import com.xuan.fitai.ai.PromptBuilder
import com.xuan.fitai.data.model.ModelLoadState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

sealed interface FoodAssessmentEvent {
    data class GoogleFinished(val canRetry: Boolean) : FoodAssessmentEvent
    data class Status(val text: String) : FoodAssessmentEvent
    data class Lookup(val result: NutritionLookup) : FoodAssessmentEvent
    data class Token(val text: String) : FoodAssessmentEvent
}

class FoodAssessment(private val gemma: GemmaLocalHelper,
    private val googleAi: GoogleAiLookupSource = GoogleAiLookupSource { _, _ -> GoogleAiLookup(message = "") },
    private val nutrition: NutritionLookupSource) {
    fun analyze(name: String, portion: String, goal: String, useReference: Boolean, googleRetry: Boolean = false): Flow<FoodAssessmentEvent> = flow {
        var googleMessage = ""
        if (useReference) {
            val failureFallback = if (googleRetry) "本次未更新估算，原有數值已保留。" else "改查台灣資料。"
            emit(FoodAssessmentEvent.Status("網路 AI 查詢中，失敗自動切換（最多 3 家，每家回答最多等 30 秒）…"))
            val google = try {
                withTimeoutOrNull(if (googleRetry) GoogleAiWaitPolicy.MANUAL_TIMEOUT_MS else GoogleAiWaitPolicy.TOTAL_TIMEOUT_MS) {
                    googleAi.lookupWithProgress(name, portion, googleRetry) { emit(FoodAssessmentEvent.Status(it)) }
                }
                    ?: GoogleAiLookup(message = "網路 AI 查詢逾時，$failureFallback", canRetry = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { GoogleAiLookup(message = "網路 AI 暫時無法使用，$failureFallback", canRetry = true) }
            emit(FoodAssessmentEvent.GoogleFinished(google.canRetry))
            googleMessage = google.message
            val result = google.assessment
            if (result != null) {
                emit(FoodAssessmentEvent.Lookup(NutritionLookup(result.reference, google.message)))
                emit(FoodAssessmentEvent.Status("已取得 網路 AI 估算"))
                emit(FoodAssessmentEvent.Token(result.json()))
                return@flow
            }
            if (googleMessage.isNotBlank()) emit(FoodAssessmentEvent.Status(googleMessage))
            if (googleRetry) throw IllegalStateException(googleMessage.ifBlank { "網路 AI 未提供可用估算，原有數值已保留。" })
        }
        check(gemma.loadState.value == ModelLoadState.Loaded) { if (useReference)
            "網路 AI 未提供可用估算，本地 AI 也尚未準備就緒；可先手動輸入或稍後重試。" else "本地 AI 尚未準備就緒，請稍後再試。" }
        val contextLimit = gemma.contextTokenLimit.first()
        val lookup = if (useReference && contextLimit < 1024) {
            NutritionLookup(message = "目前模型的上下文長度較小，使用本地估算。可在 AI 模型設定調整至 1024 以上以加入資料參考。")
        } else if (useReference) {
            emit(FoodAssessmentEvent.Status("正在查詢營養參考資料…"))
            try {
                withTimeoutOrNull(20_000) { nutrition.lookup(name) }
                    ?: NutritionLookup(message = "營養資料查詢逾時，已改用本地估算。")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { NutritionLookup(message = "營養資料暫時無法取得，已改用本地估算。") }
        } else NutritionLookup(message = "聯網輔助已關閉，使用本地估算。")
        val selected = lookup.reference?.let { fitReference(it, name, portion, goal, contextLimit) }
        val localLookup = if (lookup.reference != null && selected == null) NutritionLookup(message = "參考資料超過目前模型的可用上下文，已改用本地估算。")
            else lookup.copy(reference = selected, message = if (selected != null)
                "${lookup.message}已依上下文容量選入 ${selected.foods.size} 筆候選樣品。" else lookup.message)
        val finalLookup = localLookup.copy(message = listOf(googleMessage, localLookup.message).filter { it.isNotBlank() }.joinToString(" "))
        emit(FoodAssessmentEvent.Lookup(finalLookup))
        emit(FoodAssessmentEvent.Status("本地 AI 估算中…"))
        gemma.analyzeFoodFlow(name, portion, goal, finalLookup.reference?.promptText().orEmpty()).collect {
            emit(FoodAssessmentEvent.Token(it))
        }
    }

    companion object {
        /** Reserve room for the answer; badge metadata contains only rows actually sent. */
        fun fitReference(reference: NutritionReference, name: String, portion: String, goal: String, limit: Int): NutritionReference? {
            for (count in reference.foods.size.coerceAtMost(5) downTo 1) {
                val candidate = reference.copy(foods = reference.foods.take(count))
                val text = candidate.promptText()
                if (text.length > 1400) continue
                val prompt = PromptBuilder.buildFoodAnalysisPrompt(name, portion, goal, limit, text)
                // Approximate tokenizer plus a 50% margin and system overhead; keep >= 1/4 for output.
                val estimatedInput = kotlin.math.ceil(PromptBuilder.estimateTokenCount(prompt) * 1.5).toInt() + 60
                if (estimatedInput + maxOf(256, limit / 4) <= limit) return candidate
            }
            return null
        }
    }
}
