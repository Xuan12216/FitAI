package com.xuan.fitai.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardAdviceParserTest {
    @Test fun separatesGeneratedTitleFromAdvice() {
        assertEquals(DashboardAdviceContent("先記錄今天第一餐", "記下餐點與份量，再安排下一餐。"),
            DashboardAdviceParser.parse("標題：先記錄今天第一餐\n建議：記下餐點與份量，再安排下一餐。"))
    }

    @Test fun handlesIncompleteStreamingFields() {
        assertEquals(DashboardAdviceContent(), DashboardAdviceParser.parse("標題"))
        assertEquals(DashboardAdviceContent("補足蛋白質"), DashboardAdviceParser.parse("標題：補足蛋白質\n建議"))
        assertEquals(DashboardAdviceContent("補足蛋白質", "下一餐"),
            DashboardAdviceParser.parse("標題：補足蛋白質\n建議：下一餐"))
    }

    @Test fun preservesUnformattedOrInterruptedAdvice() {
        assertEquals(DashboardAdviceContent(body = "先記錄一餐。"), DashboardAdviceParser.parse("先記錄一餐。"))
        assertEquals(DashboardAdviceContent("下一餐補足蛋白質", "搭配蔬菜。"),
            DashboardAdviceParser.parse("標題: 下一餐補足蛋白質\n搭配蔬菜。"))
    }

    @Test fun hidesThinkingAndParsesOnlyAnswerChannel() {
        assertEquals(DashboardAdviceContent(), DashboardAdviceParser.parse("<|channel>thought標題：內部文字"))
        assertEquals(DashboardAdviceContent("先記錄再調整", "記錄下一餐。"),
            DashboardAdviceParser.parse("<|channel>thought內部文字<channel|>標題：先記錄再調整\n建議：記錄下一餐。"))
    }
}
