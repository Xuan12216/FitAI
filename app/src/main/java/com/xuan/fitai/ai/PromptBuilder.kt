package com.xuan.fitai.ai

import com.xuan.fitai.data.model.UserProfile

object PromptBuilder {
    
    fun buildAdvicePrompt(
        profile: UserProfile,
        todayCalories: Float,
        todayProtein: Float,
        todayCarbs: Float,
        todayFat: Float,
        userMessage: String
    ): String {
        return """
            你是一位健康飲食與運動助理。
            請使用繁體中文回答。
            請回答簡短、實用、容易執行。

            使用者目標：${profile.goal}
            每日熱量目標：${profile.targetCalories.toInt()} kcal
            今日已攝取熱量：${todayCalories.toInt()} kcal
            今日蛋白質：${todayProtein.toInt()} g
            今日碳水：${todayCarbs.toInt()} g
            今日脂肪：${todayFat.toInt()} g

            使用者問題：
            $userMessage

            請注意：
            1. 不要提供醫療診斷。
            2. 如果問題涉及疾病、藥物、特殊病症，請提醒使用者詢問醫師或營養師。
            3. 如果資料不足，請給出合理估算並提醒使用者確認。
        """.trimIndent()
    }
}
