package com.xuan.fitai.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onNavigateToDashboard: () -> Unit
) {
    val isLoaded by viewModel.isLoaded.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val scrollState = rememberScrollState()

    if (!isLoaded) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    var ageText by remember { mutableStateOf(profile.age.toString()) }
    var heightText by remember { mutableStateOf(profile.height.toString()) }
    var weightText by remember { mutableStateOf(profile.weight.toString()) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("個人健康設定", fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "請輸入您的基本資料，FitAI 將為您計算專屬的熱量與營養目標。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Goal Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("您的目標", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val goals = listOf("增肌", "減肥", "維持健康")
                    goals.forEach { goal ->
                        val selected = profile.goal == goal
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.updateGoal(goal) },
                            label = { Text(goal, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), textAlign = TextAlign.Center) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Gender Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("性別", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val genders = listOf("男", "女")
                    genders.forEach { gender ->
                        val selected = profile.gender == gender
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.updateGender(gender) },
                            label = { Text(gender, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), textAlign = TextAlign.Center) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Age, Height, Weight Fields
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = ageText,
                    onValueChange = {
                        ageText = it
                        it.toIntOrNull()?.let { age -> viewModel.updateAge(age) }
                    },
                    label = { Text("年齡") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = heightText,
                    onValueChange = {
                        heightText = it
                        it.toFloatOrNull()?.let { h -> viewModel.updateHeight(h) }
                    },
                    label = { Text("身高 (cm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = weightText,
                    onValueChange = {
                        weightText = it
                        it.toFloatOrNull()?.let { w -> viewModel.updateWeight(w) }
                    },
                    label = { Text("體重 (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                    modifier = Modifier.weight(1f)
                )
            }

            // Activity Level
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("日常活動量", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val levels = listOf("久坐", "輕度", "中度", "重度")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    levels.forEach { level ->
                        val selected = profile.activityLevel == level
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.updateActivityLevel(level) },
                            label = { Text(level, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), textAlign = TextAlign.Center) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Diet Preference
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("飲食偏好", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val prefs = listOf("無特殊偏好", "素食", "高蛋白", "低脂")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    prefs.forEach { pref ->
                        val selected = profile.dietPreference == pref
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.updateDietPreference(pref) },
                            label = { Text(pref, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), textAlign = TextAlign.Center) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Workout Experience
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("運動經驗", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val exps = listOf("無經驗", "新手", "中階", "高階")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    exps.forEach { exp ->
                        val selected = profile.workoutExperience == exp
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.updateWorkoutExperience(exp) },
                            label = { Text(exp, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), textAlign = TextAlign.Center) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save Button
            Button(
                onClick = {
                    viewModel.saveProfile {
                        onNavigateToDashboard()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("完成設定，進入首頁", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}
