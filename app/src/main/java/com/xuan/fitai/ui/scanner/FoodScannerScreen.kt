package com.xuan.fitai.ui.scanner

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xuan.fitai.camera.CameraController
import com.xuan.fitai.camera.CameraScreen
import com.xuan.fitai.data.model.ModelLoadState
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
    val userProfile by viewModel.userProfile.collectAsState()

    val classifierLoaded by viewModel.classifierLoadState.collectAsState()
    val gemmaLoaded by viewModel.gemmaLoadState.collectAsState()

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

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
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
                }
            } else if (classifierLoaded != ModelLoadState.Loaded || gemmaLoaded != ModelLoadState.Loaded) {
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
                        text = "本地辨識或 Gemma AI 模型尚未載入！",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "請先前往模型設定頁面下載並載入對應的模型，才能進行本地 AI 辨識與分析。",
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
                    is ScannerUiState.Classifying -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("本地影像分類模型分析中...")
                        }
                    }
                    is ScannerUiState.GemmaVisionIdentifying -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "✨ Gemma 4 視覚辨識中...",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "正在使用 Gemma 4 多模態視覺 AI 辨識食物",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is ScannerUiState.EditDetails -> {
                        var foodName by remember { mutableStateOf(state.detectedLabel) }
                        var portion by remember { mutableStateOf("1份") }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Title with Gemma badge if identified by vision
                            if (state.gemmaIdentified) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "影像辨識結果",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text("✨ Gemma 4 視覺", style = MaterialTheme.typography.labelSmall) },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                        )
                                    )
                                }
                                Text(
                                    text = "預測結果: ${state.detectedLabel}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            } else {
                                Text(
                                    text = "影像辨識結果",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "預測結果: ${state.detectedLabel} (信心度: ${(state.confidence * 100).toInt()}%)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (state.candidates.isNotEmpty()) {
                                Text(
                                    text = "候選結果 (點選直接修改名稱)：",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.Start)
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    state.candidates.forEach { candidate ->
                                        SuggestionChip(
                                            onClick = { foodName = candidate.label },
                                            label = { Text("${candidate.label} (${(candidate.confidence * 100).toInt()}%)") }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Common food quick-select card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(
                                        text = "📌 快速選擇食物（辨識不準時點選）",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val commonFoods = listOf(
                                            "雞蛋", "水煮蛋", "荷包蛋",
                                            "雞胸肉", "地瓜", "香蕉",
                                            "蘋果", "牛奶", "燕麥",
                                            "沙拉", "白飯", "豆腐"
                                        )
                                        commonFoods.forEach { food ->
                                            SuggestionChip(
                                                onClick = { foodName = food },
                                                label = { Text(food) }
                                            )
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = foodName,
                                onValueChange = { foodName = it },
                                label = { Text("確認食物名稱") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = portion,
                                onValueChange = { portion = it },
                                label = { Text("份量 (例如: 1份, 100g, 1碗)") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            Button(
                                onClick = { viewModel.analyzeWithGemma(foodName, portion) },
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("送出給本地 Gemma 分析")
                            }

                            OutlinedButton(
                                onClick = { viewModel.resetScanner() },
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("重新拍照")
                            }
                        }
                    }
                    is ScannerUiState.GemmaAnalysing -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("本地 Gemma AI 計算與分析中...")
                        }
                    }
                    is ScannerUiState.GemmaAnalysisResult -> {
                        val analysis = state.analysis
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "AI 營養與卡路里估算",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )

                            // Stats Grid Card
                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "${state.foodName} (${state.portion})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("熱量", style = MaterialTheme.typography.bodySmall)
                                            Text("${analysis.calories.toInt()} kcal", fontWeight = FontWeight.Bold)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("蛋白質", style = MaterialTheme.typography.bodySmall)
                                            Text("${analysis.protein.toInt()} g", fontWeight = FontWeight.Bold)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("碳水", style = MaterialTheme.typography.bodySmall)
                                            Text("${analysis.carbs.toInt()} g", fontWeight = FontWeight.Bold)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("脂肪", style = MaterialTheme.typography.bodySmall)
                                            Text("${analysis.fat.toInt()} g", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    val suitabilityText = if (analysis.isSuitable) "適合" else "較不適合"
                                    val suitabilityColor = if (analysis.isSuitable) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                                    Text(
                                        text = "適合您的目標「${userProfile.goal}」嗎？",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = suitabilityText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = suitabilityColor
                                    )
                                }
                            }

                            // Advice Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Gemma 健康飲食建議：", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = analysis.advice, style = MaterialTheme.typography.bodyMedium)
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

                            Spacer(modifier = Modifier.weight(1f))

                            Button(
                                onClick = {
                                    viewModel.saveMeal(state.foodName, state.portion, analysis, onSaveComplete)
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("加入餐點紀錄")
                            }

                            OutlinedButton(
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
                            Button(onClick = { viewModel.resetScanner() }) {
                                Text("重試")
                            }
                        }
                    }
                }
            }
        }
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
