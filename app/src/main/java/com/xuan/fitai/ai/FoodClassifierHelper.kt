package com.xuan.fitai.ai

import android.graphics.Bitmap
import com.xuan.fitai.data.model.ModelLoadState
import kotlinx.coroutines.flow.StateFlow

interface FoodClassifierHelper {
    val loadState: StateFlow<ModelLoadState>
    fun loadModel(modelPath: String)
    suspend fun classifyImage(bitmap: Bitmap): List<FoodClassificationResult>
}

data class FoodClassificationResult(
    val label: String,
    val confidence: Float
)
