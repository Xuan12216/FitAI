package com.xuan.fitai.data.model

data class UserProfile(
    val goal: String = "維持健康", // 增肌, 減肥, 維持健康
    val gender: String = "男", // 男, 女
    val age: Int = 25,
    val height: Float = 170f, // cm
    val weight: Float = 60f, // kg
    val activityLevel: String = "中度", // 久坐, 輕度, 中度, 重度
    val dietPreference: String = "無特殊偏好",
    val workoutExperience: String = "無經驗"
) {
    val bmr: Float
        get() {
            return if (gender == "男") {
                10f * weight + 6.25f * height - 5f * age + 5f
            } else {
                10f * weight + 6.25f * height - 5f * age - 161f
            }
        }

    val tdee: Float
        get() {
            val factor = when (activityLevel) {
                "久坐" -> 1.2f
                "輕度" -> 1.375f
                "中度" -> 1.55f
                "重度" -> 1.725f
                else -> 1.2f
            }
            return bmr * factor
        }

    val targetCalories: Float
        get() {
            return when (goal) {
                "增肌" -> tdee + 300f
                "減肥" -> tdee - 400f
                "維持健康" -> tdee
                else -> tdee
            }
        }

    // Macros in grams: Protein (4 kcal/g), Carbs (4 kcal/g), Fat (9 kcal/g)
    val targetProteinGrams: Float
        get() {
            return when (goal) {
                "增肌" -> weight * 2.0f
                "減肥" -> weight * 1.8f
                "維持健康" -> weight * 1.5f
                else -> weight * 1.5f
            }
        }

    val targetFatGrams: Float
        get() {
            val fatRatio = when (goal) {
                "增肌" -> 0.25f
                "減肥" -> 0.20f
                "維持健康" -> 0.25f
                else -> 0.25f
            }
            return (targetCalories * fatRatio) / 9f
        }

    val targetCarbsGrams: Float
        get() {
            val proteinKcal = targetProteinGrams * 4f
            val fatKcal = targetFatGrams * 9f
            val remainingKcal = targetCalories - proteinKcal - fatKcal
            return if (remainingKcal > 0) remainingKcal / 4f else 0f
        }
}
