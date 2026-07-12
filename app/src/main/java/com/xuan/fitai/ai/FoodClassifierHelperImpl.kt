package com.xuan.fitai.ai

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import com.xuan.fitai.data.model.ModelLoadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FoodClassifierHelperImpl(private val context: Context) : FoodClassifierHelper {

    private val _loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.NotFound)
    override val loadState: StateFlow<ModelLoadState> = _loadState.asStateFlow()

    private var interpreter: Interpreter? = null
    private var labelMap: List<String> = emptyList()
    
    private var inputWidth = 224
    private var inputHeight = 224
    private var isInputFloat = true
    private var isOutputFloat = true
    private var numClasses = 0

    private fun loadModelFile(modelPath: String): ByteBuffer {
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
        android.util.Log.d("FitAI_Scanner", "FoodClassifierHelper.loadModel path=$modelPath")
        val file = File(modelPath)
        if (!file.exists()) {
            android.util.Log.d("FitAI_Scanner", "FoodClassifierHelper.loadModel file not found")
            _loadState.value = ModelLoadState.NotFound
            return
        }

        _loadState.value = ModelLoadState.Loading
        try {
            loadLabelMap()

            val byteBuffer = loadModelFile(modelPath)
            val newInterpreter = Interpreter(byteBuffer)
            
            val inputTensor = newInterpreter.getInputTensor(0)
            val inputShape = inputTensor.shape() // e.g. [1, 224, 224, 3]
            inputHeight = inputShape[1]
            inputWidth = inputShape[2]
            
            val inputDataType = inputTensor.dataType()
            isInputFloat = inputDataType.toString().contains("FLOAT", ignoreCase = true)
            
            val outputTensor = newInterpreter.getOutputTensor(0)
            val outputShape = outputTensor.shape() // e.g. [1, 2024]
            numClasses = outputShape[1]
            val outputDataType = outputTensor.dataType()
            isOutputFloat = outputDataType.toString().contains("FLOAT", ignoreCase = true)

            interpreter = newInterpreter
            _loadState.value = ModelLoadState.Loaded
            android.util.Log.d(
                "FitAI_Scanner",
                "FoodClassifierHelper loaded: input=${inputWidth}x${inputHeight}, float=$isInputFloat, classes=$numClasses, labels=${labelMap.size}"
            )
        } catch (e: Exception) {
            android.util.Log.e("FitAI_Scanner", "FoodClassifierHelper load failed", e)
            _loadState.value = ModelLoadState.Failed(e.localizedMessage ?: "載入失敗")
        }
    }

    override suspend fun classifyImage(bitmap: Bitmap): List<FoodClassificationResult> = withContext(Dispatchers.IO) {
        val currentInterpreter = interpreter ?: return@withContext emptyList()
        try {
            val bytesPerChannel = if (isInputFloat) 4 else 1
            val inputBuffer = ByteBuffer.allocateDirect(1 * inputWidth * inputHeight * 3 * bytesPerChannel).apply {
                order(ByteOrder.nativeOrder())
            }

            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
            val intValues = IntArray(inputWidth * inputHeight)
            resizedBitmap.getPixels(intValues, 0, resizedBitmap.width, 0, 0, resizedBitmap.width, resizedBitmap.height)

            inputBuffer.rewind()
            for (pixelValue in intValues) {
                val r = (pixelValue shr 16) and 0xFF
                val g = (pixelValue shr 8) and 0xFF
                val b = pixelValue and 0xFF
                
                if (isInputFloat) {
                    inputBuffer.putFloat(r / 255.0f)
                    inputBuffer.putFloat(g / 255.0f)
                    inputBuffer.putFloat(b / 255.0f)
                } else {
                    inputBuffer.put(r.toByte())
                    inputBuffer.put(g.toByte())
                    inputBuffer.put(b.toByte())
                }
            }

            val bytesPerOutput = if (isOutputFloat) 4 else 1
            val outputBuffer = ByteBuffer.allocateDirect(1 * numClasses * bytesPerOutput).apply {
                order(ByteOrder.nativeOrder())
            }
            
            currentInterpreter.run(inputBuffer, outputBuffer)

            outputBuffer.rewind()
            val probabilities = FloatArray(numClasses)
            if (isOutputFloat) {
                outputBuffer.asFloatBuffer().get(probabilities)
            } else {
                val outputTensor = currentInterpreter.getOutputTensor(0)
                val dataType = outputTensor.dataType()
                val scale = outputTensor.quantizationParams().scale
                val zeroPoint = outputTensor.quantizationParams().zeroPoint
                
                val byteValues = ByteArray(numClasses)
                outputBuffer.get(byteValues)
                for (i in 0 until numClasses) {
                    val value = if (dataType.toString().contains("UINT8", ignoreCase = true)) {
                        byteValues[i].toInt() and 0xFF
                    } else {
                        byteValues[i].toInt() // INT8
                    }
                    probabilities[i] = (value - zeroPoint) * scale
                }
            }

            val results = mutableListOf<FoodClassificationResult>()
            for (i in 0 until numClasses) {
                val confidence = probabilities[i]
                val rawName = if (labelMap.isNotEmpty() && i < labelMap.size) {
                    labelMap[i]
                } else {
                    "Class $i"
                }
                results.add(
                    FoodClassificationResult(
                        label = translateFoodLabel(rawName),
                        confidence = confidence
                    )
                )
            }
            results.sortByDescending { it.confidence }
            val topResults = results.take(5)
            android.util.Log.d(
                "FitAI_Scanner",
                "FoodClassifierHelper classify top=${topResults.joinToString { "${it.label}:${it.confidence}" }}"
            )
            topResults
        } catch (e: Exception) {
            android.util.Log.e("FitAI_Scanner", "FoodClassifierHelper classify failed", e)
            emptyList()
        }
    }

    private fun translateFoodLabel(label: String): String {
        val lower = label.lowercase()
        return when {
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
