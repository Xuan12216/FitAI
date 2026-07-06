package com.xuan.fitai.ai

import android.graphics.Bitmap
import com.xuan.fitai.data.model.ModelLoadState
import kotlinx.coroutines.flow.StateFlow

interface GemmaLocalHelper {
    val loadState: StateFlow<ModelLoadState>
    val loadedModelName: StateFlow<String?>
    val visionReady: StateFlow<Boolean>
    fun loadModel(modelPath: String, modelName: String)
    suspend fun loadModelSync(modelPath: String, modelName: String): Boolean
    suspend fun generateReply(prompt: String): String
    fun generateReplyFlow(prompt: String): kotlinx.coroutines.flow.Flow<String>
    suspend fun analyzeFood(foodName: String, portion: String, goal: String): GemmaFoodAnalysis
    fun analyzeFoodFlow(foodName: String, portion: String, goal: String): kotlinx.coroutines.flow.Flow<String>
    suspend fun identifyFoodFromImage(bitmap: Bitmap): String
    fun identifyFoodFromImageFlow(bitmap: Bitmap): kotlinx.coroutines.flow.Flow<String>
}

data class GemmaFoodAnalysis(
    val calories: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val isSuitable: Boolean,
    val advice: String,
    val reasoning: String = "",
    val thinking: String? = null
)
