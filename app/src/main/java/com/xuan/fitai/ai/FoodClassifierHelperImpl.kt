package com.xuan.fitai.ai

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier
import com.xuan.fitai.data.model.ModelLoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class FoodClassifierHelperImpl(private val context: Context) : FoodClassifierHelper {

    private val _loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.NotFound)
    override val loadState: StateFlow<ModelLoadState> = _loadState.asStateFlow()

    private var imageClassifier: ImageClassifier? = null
    // Label map loaded from assets: index -> human-readable food name
    private var labelMap: List<String> = emptyList()

    private fun loadModelFile(modelPath: String): java.nio.ByteBuffer {
        val file = File(modelPath)
        val inputStream = java.io.FileInputStream(file)
        val fileChannel = inputStream.channel
        return fileChannel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0L, fileChannel.size())
    }

    private fun loadLabelMap() {
        try {
            val lines = context.assets.open("aiy_food_V1_labelmap.txt")
                .bufferedReader(Charsets.UTF_8)
                .readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            labelMap = lines
        } catch (e: Exception) {
            labelMap = emptyList()
        }
    }

    override fun loadModel(modelPath: String) {
        val file = File(modelPath)
        if (!file.exists()) {
            _loadState.value = ModelLoadState.NotFound
            return
        }

        _loadState.value = ModelLoadState.Loading
        try {
            // Load label map from assets
            loadLabelMap()

            val byteBuffer = loadModelFile(modelPath)
            val baseOptions = BaseOptions.builder()
                .setModelAssetBuffer(byteBuffer)
                .build()

            val options = ImageClassifier.ImageClassifierOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setMaxResults(5)
                .build()

            imageClassifier = ImageClassifier.createFromOptions(context, options)
            _loadState.value = ModelLoadState.Loaded
        } catch (e: Exception) {
            _loadState.value = ModelLoadState.Failed(e.localizedMessage ?: "載入失敗")
        }
    }

    override suspend fun classifyImage(bitmap: Bitmap): List<FoodClassificationResult> = withContext(Dispatchers.IO) {
        val classifier = imageClassifier ?: return@withContext emptyList()
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = classifier.classify(mpImage)
            result.classificationResult().classifications().flatMap { classification ->
                classification.categories().map { category ->
                    // Use index to look up human-readable label from our label map
                    val rawName = if (labelMap.isNotEmpty() && category.index() >= 0 && category.index() < labelMap.size) {
                        labelMap[category.index()]
                    } else {
                        category.categoryName()
                    }
                    FoodClassificationResult(
                        label = translateFoodLabel(rawName),
                        confidence = category.score()
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun translateFoodLabel(label: String): String {
        val lower = label.lowercase()
        return when {
            // Pass through Freebase IDs that have no translation as-is (fallback)
            lower.startsWith("/m/") || lower.startsWith("/g/") -> label
            lower.contains("french fries") || lower.contains("french fry") || lower.contains("potato chips") || lower.contains("chips") -> "薯條"
            lower.contains("hotdog") || lower.contains("hot dog") -> "熱狗"
            lower.contains("hamburger") || lower.contains("burger") || lower.contains("cheeseburger") -> "漢堡"
            lower.contains("sandwich") -> "三明治"
            lower.contains("steak") -> "牛排"
            lower.contains("sushi") -> "壽司"
            lower.contains("pizza") -> "披薩"
            lower.contains("salad") -> "沙拉"
            lower.contains("chicken") || lower.contains("poultry") -> "雞肉"
            lower.contains("apple") -> "蘋果"
            lower.contains("banana") -> "香蕉"
            lower.contains("orange") -> "橘子"
            lower.contains("bread") -> "麵包"
            lower.contains("rice") -> "米飯"
            lower.contains("spaghetti") || lower.contains("pasta") -> "義大利麵"
            lower.contains("egg") -> "雞蛋"
            lower.contains("soup") -> "湯"
            lower.contains("noodle") -> "麵"
            lower.contains("tofu") -> "豆腐"
            lower.contains("pork") -> "豬肉"
            lower.contains("beef") -> "牛肉"
            lower.contains("fish") -> "魚"
            lower.contains("shrimp") || lower.contains("prawn") -> "蝦"
            lower.contains("cake") -> "蛋糕"
            lower.contains("milk") -> "牛奶"
            lower.contains("coffee") -> "咖啡"
            lower.contains("tea") -> "茶"
            lower.contains("juice") -> "果汁"
            lower.contains("sweet potato") || lower.contains("yam") -> "地瓜"
            lower.contains("broccoli") -> "花椰菜"
            lower.contains("tomato") -> "番茄"
            else -> label
        }
    }
}
