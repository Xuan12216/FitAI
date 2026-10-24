package com.xuan.fitai

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.xuan.fitai.data.model.Meal
import com.xuan.fitai.ui.dashboard.mealGroupExpanded
import com.xuan.fitai.ui.dashboard.mealGroupItems
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

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
        compose.onNodeWithText("4 筆 · 400 kcal").assertExists()
        compose.onNodeWithContentDescription("展開早餐").performClick()
        compose.onNodeWithText("測試餐4").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("收合早餐").performScrollTo().performClick()
        compose.onNodeWithText("測試餐1").assertDoesNotExist()
        compose.onNodeWithText("4 筆 · 400 kcal").assertExists()
        assertTrue(mealGroupExpanded(4, true, false))
        assertFalse(mealGroupExpanded(3, false, true))
    }
}
