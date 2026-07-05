package com.xuan.fitai.ai

import android.graphics.Bitmap
import com.xuan.fitai.data.model.ModelLoadState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

class MockFoodClassifierHelper : FoodClassifierHelper {

    private val _loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.NotFound)
    override val loadState: StateFlow<ModelLoadState> = _loadState.asStateFlow()

    private val mockFoods = listOf(
        "雞胸肉",
        "蘋果",
        "披薩",
        "沙拉",
        "香蕉",
        "薯條",
        "熱狗",
        "漢堡"
    )

    override fun loadModel(modelPath: String) {
        if (modelPath.isBlank()) {
            _loadState.value = ModelLoadState.NotFound
            return
        }
        _loadState.value = ModelLoadState.Loading
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.postDelayed({
            _loadState.value = ModelLoadState.Loaded
        }, 1000)
    }

    override suspend fun classifyImage(bitmap: Bitmap): List<FoodClassificationResult> {
        delay(800) // Simulate image processing delay
        if (_loadState.value != ModelLoadState.Loaded) {
            throw IllegalStateException("Food classifier model is not loaded")
        }

        // Return 1-3 random food suggestions
        val random = Random.nextInt(1, 3)
        val shuffled = mockFoods.shuffled()
        
        return List(random) { index ->
            val label = shuffled[index]
            val confidence = 0.70f + Random.nextFloat() * 0.25f
            FoodClassificationResult(label, confidence)
        }.sortedByDescending { it.confidence }
    }
}
