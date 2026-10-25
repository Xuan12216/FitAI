package com.xuan.fitai.ui.scanner

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xuan.fitai.camera.CameraController
import com.xuan.fitai.camera.CameraScreen
import com.xuan.fitai.ai.GemmaOutputParser
import com.xuan.fitai.data.model.ModelLoadState
import com.xuan.fitai.ui.components.LoadingDialog
import com.xuan.fitai.ui.components.ThinkingContent
import com.xuan.fitai.util.PermissionUtil
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodScannerScreen(
    viewModel: FoodScannerViewModel,
    onNavigateBack: () -> Unit,
    onSaveComplete: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val saving by viewModel.isSaving.collectAsState()
    val photo by viewModel.capturedImage.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()

    val classifierLoaded by viewModel.classifierLoadState.collectAsState()
    val gemmaLoaded by viewModel.gemmaLoadState.collectAsState()
    val gemmaVisionReady by viewModel.gemmaVisionReady.collectAsState()
    val scannerModelReady = com.xuan.fitai.ai.canScanFood(classifierLoaded, gemmaLoaded, gemmaVisionReady)
    val isRecognizingFood = (uiState as? ScannerUiState.EditDetails)?.isRecognizing == true

    LaunchedEffect(classifierLoaded, gemmaLoaded, gemmaVisionReady, scannerModelReady) {
        android.util.Log.d(
            "FitAI_Scanner",
            "FoodScannerScreen model gate: classifier=$classifierLoaded, gemma=$gemmaLoaded, visionReady=$gemmaVisionReady, scannerReady=$scannerModelReady"
        )
    }

    var hasCameraPermission by remember { mutableStateOf(PermissionUtil.hasCameraPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val bitmap = loadBitmapFromUri(context, uri)
            if (bitmap != null) {
                viewModel.classifyImage(bitmap)
            } else {
                Toast.makeText(context, "無法讀取相簿圖片", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Camera settings
    val cameraController = remember { CameraController() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(Unit) {
        val classifierFile = java.io.File(context.filesDir, "models/food_classifier.tflite")
        android.util.Log.d(
            "FitAI_Scanner",
            "Scanner entered: classifierFileExists=${classifierFile.exists()}, classifierFileSize=${classifierFile.length()}"
        )
        if (classifierFile.exists() && classifierFile.length() > 0) {
            viewModel.loadFoodClassifierIfNeeded(classifierFile.absolutePath)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("AI 食物掃描鏡頭", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (!hasCameraPermission) {
                // Permission Denied View
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "此功能需要相機權限才能掃描食物，請在裝置設定中開啟相機權限。",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("授予權限")
                    }
                    OutlinedButton(enabled = scannerModelReady, onClick = { galleryLauncher.launch("image/*") }) { Text("從相簿選取") }
                }
            } else if (!scannerModelReady) {
                // Models not loaded warning
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (gemmaLoaded == ModelLoadState.Loading || classifierLoaded == ModelLoadState.Loading) "背景暖機中，準備完成後可開始辨識" else "請載入食物分類器或支援圖片的 Gemma 模型",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "你可以先返回使用其他功能；圖片辨識需要食物分類器或 Gemma 視覺能力。",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onNavigateBack) {
                        Text("返回首頁")
                    }
                }
            } else {
                // Main Camera Scanner view based on state
                when (val state = uiState) {
                    is ScannerUiState.CameraPreview -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CameraScreen(controller = cameraController)
                            
                            // Target Scan Reticle Overlay
                            Box(
                                modifier = Modifier
                                    .size(260.dp)
                                    .border(2.dp, Color.White.copy(alpha = 0.8f))
                                    .align(Alignment.Center)
                            )
                            
                            Text(
                                text = "請將食物放入框中並拍照",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(top = 300.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), shape = MaterialTheme.shapes.small)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )

                            ExtendedFloatingActionButton(
                                onClick = { galleryLauncher.launch("image/*") },
                                icon = {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                },
                                text = { Text("相簿") },
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 32.dp, bottom = 56.dp),
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary
                            )

                            // Capture Button
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 48.dp)
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    onClick = {
                                        cameraController.takePhoto(
                                            cameraExecutor,
                                            onPhotoTaken = { bitmap ->
                                                viewModel.classifyImage(bitmap)
                                            },
                                            onError = {
                                                // Handle error
                                            }
                                        )
                                    },
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "拍照",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                    }

                    is ScannerUiState.EditDetails -> {
                        var foodName by rememberSaveable { mutableStateOf("") }
                        var portion by rememberSaveable { mutableStateOf("1份") }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            photo?.let { Image(it.asImageBitmap(), "待確認的食物照片", Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop) }
                            // Title with Gemma badge if identified by vision
                            if (state.gemmaIdentified) {
                                val detectedFoodName = GemmaOutputParser.extractVisionFoodName(state.detectedLabel)
                                val thinkingText = GemmaOutputParser.extractVisionThinking(state.detectedLabel)

                                if (!thinkingText.isNullOrBlank()) {
                                    ThinkingContent(
                                        rawText = GemmaOutputParser.withThinkingContent(thinkingText, ""),
                                        contentTextStyle = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.tertiary
                                        ),
                                        isGenerating = state.isRecognizing
                                    )
                                }

                                if (detectedFoodName.isNotBlank()) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable(enabled = !state.isRecognizing) {
                                                foodName = detectedFoodName
                                            },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                        ),
                                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "✨ AI 視覺辨識結果",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = detectedFoodName,
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                                )
                                            }
                                            Text(
                                                text = "點擊套用",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onTertiary,
                                                modifier = Modifier
                                                    .background(
                                                        color = MaterialTheme.colorScheme.tertiary,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "影像辨識結果",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "辨識結果：${state.detectedLabel}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (state.candidates.isNotEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Text(
                                            text = "📸 其他可能餐點（點擊套用）",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.align(Alignment.Start)
                                        )
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            state.candidates.forEach { candidate ->
                                                SuggestionChip(
                                                    onClick = { foodName = candidate.label },
                                                    label = {
                                                        Text(candidate.label)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = foodName,
                                onValueChange = { foodName = it },
                                label = { Text("確認食物名稱") },
                                modifier = Modifier.fillMaxWidth(),
                                isError = !state.isRecognizing && foodName.isBlank(),
                                supportingText = {
                                    if (!state.isRecognizing && foodName.isBlank()) {
                                        Text("請輸入食物名稱或點選候選結果")
                                    }
                                }
                            )

                            OutlinedTextField(
                                value = portion,
                                onValueChange = { portion = it },
                                label = { Text("份量 (例如: 1份, 100g, 1碗)") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            var useReference by rememberSaveable { mutableStateOf(true) }
                            com.xuan.fitai.ui.components.NutritionReferenceToggle(useReference, { useReference = it }, !state.isRecognizing)
                            val canAnalyze = !state.isRecognizing && foodName.isNotBlank() && portion.isNotBlank()
                            Button(
                                onClick = { viewModel.analyzeWithGemma(foodName.trim(), portion, useReference) },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                enabled = canAnalyze
                            ) {
                                Text("✨ 開始 AI 營養評估")
                            }

                            OutlinedButton(
                                onClick = { viewModel.resetScanner() },
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("重新拍照")
                            }
                        }
                    }
                    is ScannerUiState.GemmaAnalysisStreaming -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "AI 營養分析中",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            Text(state.status, style = MaterialTheme.typography.bodyMedium)
                            if (state.lookupMessage.isNotBlank()) Text(state.lookupMessage, style = MaterialTheme.typography.bodySmall)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    ThinkingContent(
                                        rawText = state.rawText.ifBlank { "正在產生營養分析..." },
                                        isGenerating = true
                                    )
                                }
                            }
                        }
                    }

                    is ScannerUiState.GemmaAnalysisResult -> {
                        val analysis = state.analysis
                        var calories by rememberSaveable(state.foodName, state.portion) { mutableStateOf(analysis.calories.toString()) }
                        var protein by rememberSaveable(state.foodName, state.portion) { mutableStateOf(analysis.protein.toString()) }
                        var carbs by rememberSaveable(state.foodName, state.portion) { mutableStateOf(analysis.carbs.toString()) }
                        var fat by rememberSaveable(state.foodName, state.portion) { mutableStateOf(analysis.fat.toString()) }
                        var lastAnalysis by remember { mutableStateOf(analysis) }
                        LaunchedEffect(analysis) {
                            if (lastAnalysis != analysis) {
                            calories = analysis.calories.toString(); protein = analysis.protein.toString()
                            carbs = analysis.carbs.toString(); fat = analysis.fat.toString()
                            lastAnalysis = analysis
                            }
                        }
                        val validNumbers = listOf(calories, protein, carbs, fat).all { it.toFloatOrNull()?.let { n -> n.isFinite() && n >= 0 } == true }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "AI 營養與卡路里估算",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )

                            Text("估算 AI：${com.xuan.fitai.nutrition.nutritionEstimatorLabel(state.referenceJson, true)}", style = MaterialTheme.typography.labelMedium)
                            com.xuan.fitai.ui.components.NutritionReferenceBadge(state.referenceJson)
                            if (state.lookupMessage.isNotBlank() && (state.canRetryGoogle || state.referenceJson == null)) {
                                Text(state.lookupMessage, style = MaterialTheme.typography.bodySmall)
                            }
                            if (state.canRetryGoogle) {
                                OutlinedButton(
                                    onClick = { viewModel.analyzeWithGemma(state.foodName, state.portion, googleRetry = true) },
                                    enabled = !state.retryingGoogle && !saving,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text(if (state.retryingGoogle) "網路 AI 再查一次…" else "再查一次網路 AI") }
                            }
                            photo?.let { Image(it.asImageBitmap(), "待儲存的食物照片", Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${state.foodName} (${state.portion})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("儲存前可微調數值", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = calories,
                                    onValueChange = { calories = it },
                                    label = { Text("校正熱量 kcal") },
                                    singleLine = true,
                                    enabled = !state.retryingGoogle,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = protein,
                                    onValueChange = { protein = it },
                                    label = { Text("校正蛋白質 g") },
                                    singleLine = true,
                                    enabled = !state.retryingGoogle,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = carbs,
                                    onValueChange = { carbs = it },
                                    label = { Text("校正碳水 g") },
                                    singleLine = true,
                                    enabled = !state.retryingGoogle,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = fat,
                                    onValueChange = { fat = it },
                                    label = { Text("校正脂肪 g") },
                                    singleLine = true,
                                    enabled = !state.retryingGoogle,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            val fromGoogle = com.xuan.fitai.nutrition.NutritionReferenceJson.decodeOrNull(state.referenceJson)?.foods?.any { com.xuan.fitai.nutrition.WebAiProvider.entries.any { provider -> provider.foodId == it.id } } == true
                            if (!fromGoogle) {
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val suitabilityText = if (analysis.isSuitable) "適合" else "較不適合"
                                        val suitabilityColor = if (analysis.isSuitable) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                                        Text(
                                            text = "適合您的目標「${userProfile.goal}」嗎？",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = suitabilityText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = suitabilityColor
                                        )
                                    }
                                }
                            }

                            // Advice Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("飲食參考：", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    ThinkingContent(rawText = analysis.advice)
                                }
                            }

                            if (!analysis.reasoning.isNullOrBlank()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("💡 AI 估算依據：", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        ThinkingContent(
                                            rawText = GemmaOutputParser.withThinkingContent(
                                                thinkingText = analysis.thinking,
                                                contentText = analysis.reasoning
                                            )
                                        )
                                    }
                                }
                            }


                            // Important Warning Banner (Required by Spec)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "此為 AI 估算值，食物重量與實際料理方式會影響熱量，請自行確認。",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                enabled = validNumbers && !saving && !state.retryingGoogle,
                                onClick = {
                                    viewModel.saveMeal(state.foodName, state.portion, analysis.copy(calories = calories.toFloat(), protein = protein.toFloat(), carbs = carbs.toFloat(), fat = fat.toFloat()), onSaveComplete)
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("加入餐點紀錄")
                            }

                            OutlinedButton(
                                enabled = !state.retryingGoogle,
                                onClick = { viewModel.resetScanner() },
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("取消並重新拍照")
                            }
                        }
                    }
                    is ScannerUiState.SavedSuccess -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color(0xFF4CAF50))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("已成功儲存餐點！")
                        }
                    }
                    is ScannerUiState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(text = state.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(24.dp))
                            if (state.canRetryGoogle && state.retryFood != null && state.retryPortion != null) {
                                OutlinedButton(onClick = { viewModel.analyzeWithGemma(state.retryFood, state.retryPortion, googleRetry = true) }) {
                                    Text("再查一次網路 AI")
                                }
                            }
                            Button(onClick = { viewModel.resetScanner() }) {
                                Text("重新拍照")
                            }
                        }
                    }
                }
            }
        }
    }

    if (isRecognizingFood) {
        LoadingDialog(
            title = "辨識食物中",
            message = "正在分析照片，請稍候..."
        )
    }
}

private fun loadBitmapFromUri(context: Context, uri: android.net.Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        }
    } catch (e: Exception) {
        null
    }
}
