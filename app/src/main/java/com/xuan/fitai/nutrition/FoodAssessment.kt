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
    data class Status(val text: String) : FoodAssessmentEvent
    data class Lookup(val result: NutritionLookup) : FoodAssessmentEvent
    data class Token(val text: String) : FoodAssessmentEvent
}

class FoodAssessment(private val gemma: GemmaLocalHelper, private val nutrition: NutritionLookupSource) {
    fun analyze(name: String, portion: String, goal: String, useReference: Boolean): Flow<FoodAssessmentEvent> = flow {
        check(gemma.loadState.value == ModelLoadState.Loaded) { "本地 AI 尚未準備就緒，請稍後再試。" }
        val contextLimit = gemma.contextTokenLimit.first()
        val lookup = if (useReference && contextLimit < 1024) {
            NutritionLookup(message = "目前模型的上下文長度較小，使用本地估算。可在 AI 模型設定調整至 1024 以上以加入資料參考。")
        } else if (useReference) {
            emit(FoodAssessmentEvent.Status("查詢營養資料中…首次下載約 5 MB"))
            try {
                withTimeoutOrNull(20_000) { nutrition.lookup(name) }
                    ?: NutritionLookup(message = "營養資料查詢逾時，已改用本地估算。")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { NutritionLookup(message = "營養資料暫時無法取得，已改用本地估算。") }
        } else NutritionLookup(message = "聯網輔助已關閉，使用本地估算。")
        val selected = lookup.reference?.let { fitReference(it, name, portion, goal, contextLimit) }
        val finalLookup = if (lookup.reference != null && selected == null) NutritionLookup(message = "參考資料超過目前模型的可用上下文，已改用本地估算。")
            else lookup.copy(reference = selected, message = if (selected != null)
                "${lookup.message}已依上下文容量選入 ${selected.foods.size} 筆候選樣品。" else lookup.message)
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
