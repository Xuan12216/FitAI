package com.xuan.fitai.ai

import com.xuan.fitai.data.model.ModelLoadState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

class MockGemmaLocalHelper : GemmaLocalHelper {

    private val _loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.NotFound)
    override val loadState: StateFlow<ModelLoadState> = _loadState.asStateFlow()

    private val _loadedModelName = MutableStateFlow<String?>(null)
    override val loadedModelName: StateFlow<String?> = _loadedModelName.asStateFlow()

    private val _visionReady = MutableStateFlow(true)
    override val visionReady: StateFlow<Boolean> = _visionReady.asStateFlow()

    override fun loadModel(modelPath: String, modelName: String) {
        if (modelPath.isBlank()) {
            _loadState.value = ModelLoadState.NotFound
            _loadedModelName.value = null
            return
        }
        _loadState.value = ModelLoadState.Loading
        _loadedModelName.value = null
        // Simulate loading delay
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.postDelayed({
            _loadState.value = ModelLoadState.Loaded
            _loadedModelName.value = modelName
        }, 1500)
    }

    override suspend fun loadModelSync(modelPath: String, modelName: String): Boolean {
        if (modelPath.isBlank()) {
            _loadState.value = ModelLoadState.NotFound
            _loadedModelName.value = null
            return false
        }
        _loadState.value = ModelLoadState.Loading
        _loadedModelName.value = null
        delay(1500)
        _loadState.value = ModelLoadState.Loaded
        _loadedModelName.value = modelName
        return true
    }

    override suspend fun generateReply(prompt: String): String {
        delay(1000) // Simulate inference delay
        if (_loadState.value != ModelLoadState.Loaded) {
            return "錯誤：Gemma 模型尚未成功載入，請先至模型設定頁面設定。"
        }
        
        val lowercasePrompt = prompt.lowercase()
        return when {
            lowercasePrompt.contains("運動") || lowercasePrompt.contains("健身") || lowercasePrompt.contains("訓練") -> {
                "根據您的目標，建議每週進行 3-4 次運動：\n" +
                "1. 增肌者：重力訓練為主（深蹲、臥推、硬舉），每組 8-12 下。\n" +
                "2. 減肥者：阻力訓練 30 分鐘 + 有氧運動 20 分鐘，維持心率。\n" +
                "多喝水並維持充足睡眠（7-8小時）是關鍵！"
            }
            lowercasePrompt.contains("蛋白質") || lowercasePrompt.contains("豆漿") || lowercasePrompt.contains("雞胸肉") -> {
                "補充足夠的蛋白質對於增肌與維持代謝都非常重要。\n" +
                "建議來源：雞胸肉、雞蛋、豆腐、無糖豆漿及乳清蛋白。\n" +
                "增肌建議每日攝取體重 x 2.0 克的蛋白質；減肥則建議體重 x 1.8 克。"
            }
            lowercasePrompt.contains("飲食") || lowercasePrompt.contains("熱量") || lowercasePrompt.contains("卡路里") -> {
                "飲食控制是達成健康目標的核心！\n" +
                "- 減肥：維持 300-500 大卡的熱量赤字，避免加工食品與含糖飲料。\n" +
                "- 增肌：維持 250-400 大卡的熱量盈餘，多補充複雜碳水化合物（地瓜、燕麥）與優質蛋白。"
            }
            else -> {
                "您好！我是您的本地 AI 健康助理 Gemma。\n" +
                "我可以幫您分析食物卡路里、規劃運動菜單並提供日常飲食建議。\n" +
                "請問您今天想了解什麼呢？（例如：『如何安排一週運動計畫？』）"
            }
        }
    }

    override suspend fun analyzeFood(foodName: String, portion: String, goal: String): GemmaFoodAnalysis {
        delay(1200) // Simulate inference delay
        if (_loadState.value != ModelLoadState.Loaded) {
            throw IllegalStateException("Gemma model is not loaded")
        }

        val quantity = try {
            val num = portion.replace(Regex("[^0-9.]"), "")
            if (num.isEmpty()) 1f else num.toFloat()
        } catch (e: Exception) {
            1f
        }

        return when {
            foodName.contains("蘋果") || foodName.contains("apple") -> {
                val cal = 52f * quantity
                GemmaFoodAnalysis(
                    calories = cal,
                    protein = 0.3f * quantity,
                    carbs = 14f * quantity,
                    fat = 0.2f * quantity,
                    isSuitable = true,
                    advice = "蘋果富含膳食纖維與維生素C，熱量極低且飽足感強。不論您的目標是增肌、減肥或維持健康，都是非常推薦的優質水果選擇！"
                )
            }
            foodName.contains("雞胸肉") || foodName.contains("chicken") -> {
                val cal = 165f * quantity
                GemmaFoodAnalysis(
                    calories = cal,
                    protein = 31f * quantity,
                    carbs = 0f * quantity,
                    fat = 3.6f * quantity,
                    isSuitable = true,
                    advice = "雞胸肉是極佳的高蛋白、低脂肪食物。對於增肌者能高效補充蛋白質；對於減肥者能提供飽足感且不易累積脂肪，非常推薦食用。"
                )
            }
            foodName.contains("披薩") || foodName.contains("比薩") || foodName.contains("pizza") -> {
                val cal = 270f * quantity
                val isOk = goal == "增肌"
                GemmaFoodAnalysis(
                    calories = cal,
                    protein = 11f * quantity,
                    carbs = 32f * quantity,
                    fat = 11f * quantity,
                    isSuitable = isOk,
                    advice = if (isOk) {
                        "披薩能提供高熱量與大量碳水，對於增肌期補充熱量有幫助，但油脂也偏高。建議控制分量並多搭配蔬菜與蛋白質。"
                    } else {
                        "披薩屬於高脂、高鈉的加工食品，熱量密度極高。減肥期間應盡量避免，或僅當作偶爾的放縱餐（Cheat Meal）且限制在 1-2 片內。"
                    }
                )
            }
            foodName.contains("沙拉") || foodName.contains("salad") -> {
                val cal = 90f * quantity
                GemmaFoodAnalysis(
                    calories = cal,
                    protein = 2f * quantity,
                    carbs = 7f * quantity,
                    fat = 6f * quantity,
                    isSuitable = true,
                    advice = "沙拉是減肥時的聖品，富含纖維能促進腸胃蠕動。不過要注意千島醬、凱薩醬等沙拉醬熱量極高，建議改用油醋醬或和風醬。"
                )
            }
            foodName.contains("香蕉") || foodName.contains("banana") -> {
                val cal = 89f * quantity
                GemmaFoodAnalysis(
                    calories = cal,
                    protein = 1.1f * quantity,
                    carbs = 23f * quantity,
                    fat = 0.3f * quantity,
                    isSuitable = true,
                    advice = "香蕉是快速補充能量的優良來源，含有豐富的鉀離子。增肌者適合在運動前後食用以補充碳水，減肥者亦可食用但建議一天不超過一根。"
                )
            }
            else -> {
                // Generates random but realistic food stats based on name length seed
                val hash = foodName.hashCode()
                val r = Random(hash.toLong())
                val calories = (80 + r.nextInt(250)).toFloat() * quantity
                val protein = (1 + r.nextInt(20)).toFloat() * quantity
                val fat = (1 + r.nextInt(15)).toFloat() * quantity
                val carbs = (5 + r.nextInt(40)).toFloat() * quantity
                val suitable = if (goal == "減肥") calories < 180f else true

                GemmaFoodAnalysis(
                    calories = calories,
                    protein = protein,
                    carbs = carbs,
                    fat = fat,
                    isSuitable = suitable,
                    advice = "這是針對「${foodName}」的本地 AI 營養分析。${foodName} 能提供熱量與營養素，若您的目標是「${goal}」，建議" +
                            if (goal == "減肥") "控制每日總熱量，並將其納入飲食計畫中。" else "多搭配充足的蛋白質，以利肌肉生長與修復。"
                )
            }
        }
    }

    override suspend fun identifyFoodFromImage(bitmap: android.graphics.Bitmap): String {
        delay(1200)
        val mockFoods = listOf("雞蛋", "雞胸肉", "地瓜", "香蕉", "蘋果")
        return mockFoods.random()
    }
}
