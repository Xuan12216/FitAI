package com.xuan.fitai

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.xuan.fitai.data.model.Meal
import com.xuan.fitai.ui.dashboard.mealGroupExpanded
import com.xuan.fitai.ui.dashboard.mealGroupItems
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class MealGroupTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fourthRecordCollapsesGroupAndUserCanExpandAndCollapseWithoutChangingTotals() {
        val count = mutableIntStateOf(3)
        compose.setContent {
            var open by remember { mutableStateOf(false) }
            var closed by remember { mutableStateOf(false) }
            val records = (1..count.intValue).map { Meal(it, "測試餐$it", 100f, 5f, 10f, 2f, mealType = "早餐") }
            val expanded = mealGroupExpanded(records.size, open, closed)
            MaterialTheme {
                LazyColumn {
                    mealGroupItems("早餐", records, expanded, {
                        open = !expanded; closed = expanded
                    }, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("測試餐1").assertExists()
        compose.runOnIdle { count.intValue = 4 }
        compose.onNodeWithText("測試餐1").assertDoesNotExist()
        compose.onNodeWithText("4 筆餐點").assertExists()
        compose.onNodeWithText("400 kcal").assertExists()
        compose.onNodeWithContentDescription("展開早餐").performClick()
        compose.onNodeWithText("測試餐4").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("收合早餐").performScrollTo().performClick()
        compose.onNodeWithText("測試餐1").assertDoesNotExist()
        compose.onNodeWithText("4 筆餐點").assertExists()
        compose.onNodeWithText("400 kcal").assertExists()
        assertTrue(mealGroupExpanded(4, true, false))
        assertFalse(mealGroupExpanded(3, false, true))
    }

    @Test fun mealCardsDistinguishLocalEstimatesFromManualRecords() {
        val breakfast = listOf(
            Meal(1, "火腿蛋餅", 330f, 14f, 36f, 14f, mealType = "早餐", isAiEstimated = true, portionSize = "1份"),
            Meal(2, "無糖豆漿", 100f, 9f, 4f, 5f, mealType = "早餐", portionSize = "1杯")
        )
        val lunch = listOf(Meal(3, "雞胸肉便當", 650f, 40f, 80f, 19f, mealType = "午餐", portionSize = "1份"))
        compose.setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("今日餐點", style = MaterialTheme.typography.headlineSmall) }
                mealGroupItems("早餐", breakfast, true, {}, {}, {}, {})
                mealGroupItems("午餐", lunch, false, {}, {}, {}, {})
            }
        } } }
        compose.onNodeWithText("本地 Gemma 估算").assertIsDisplayed()
        compose.onNodeWithText("手動記錄").assertIsDisplayed()
        compose.onNodeWithText("2 筆餐點").assertIsDisplayed()
        compose.onNodeWithText("430 kcal").assertIsDisplayed()
        if (InstrumentationRegistry.getArguments().getString("captureMealGroups") == "true") {
            val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
            File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "meal-groups.png")
                .outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
