package com.xuan.fitai.ui.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.xuan.fitai.data.model.ModelLoadState
import com.xuan.fitai.ui.components.AppLoadingIndicator
import com.xuan.fitai.ui.components.ThinkingContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToSetup: () -> Unit,
    bottomOverlayPadding: Dp = 0.dp,
) {
    val messages by viewModel.chatMessages.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val isThinking by viewModel.isThinking.collectAsState()
    val errorMsg by viewModel.errorMsg.collectAsState()
    val gemmaLoadState by viewModel.gemmaLoadState.collectAsState()
    val visionReady by viewModel.visionReady.collectAsState()
    val audioReady by viewModel.audioReady.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var inputQuery by remember { mutableStateOf("") }
    var selectedImage by remember { mutableStateOf<Bitmap?>(null) }
    var audioBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var playingAudioId by remember { mutableStateOf<Int?>(null) }
    var mediaError by remember { mutableStateOf<String?>(null) }
    var cameraPermissionGranted by remember {
        mutableStateOf(hasPermission(context, Manifest.permission.CAMERA))
    }
    var recordPermissionGranted by remember {
        mutableStateOf(hasPermission(context, Manifest.permission.RECORD_AUDIO))
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { decodeChatBitmap(context, uri) }
            if (bitmap == null) {
                mediaError = "無法讀取這張圖片"
            } else {
                selectedImage = bitmap
                mediaError = null
            }
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        selectedImage = bitmap?.scaleForChat()
        if (bitmap != null) mediaError = null
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionGranted = granted
        if (granted) cameraLauncher.launch(null)
    }

    val audioRecorder = remember { ChatAudioRecorder() }
    val audioPlayer = remember { ChatAudioPlayer(context) }
    DisposableEffect(audioRecorder) {
        onDispose { audioRecorder.release() }
    }
    DisposableEffect(audioPlayer) {
        onDispose { audioPlayer.release() }
    }
    val startRecording: () -> Unit = {
        try {
            audioRecorder.start()
            isRecording = true
            mediaError = null
        } catch (e: Exception) {
            mediaError = e.localizedMessage ?: "無法啟用麥克風"
        }
    }
    val recordPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        recordPermissionGranted = granted
        if (granted) startRecording()
    }

    fun sendCurrentMessage() {
        val hasAttachment = selectedImage != null || audioBytes?.isNotEmpty() == true
        if (isGenerating || isRecording || (inputQuery.isBlank() && !hasAttachment)) return
        audioPlayer.stop { playingAudioId = it }
        viewModel.sendMessage(inputQuery, selectedImage, audioBytes)
        inputQuery = ""
        selectedImage = null
        audioBytes = null
        mediaError = null
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("本地 Gemma AI 助理", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.clearChat() }) {
                        Icon(Icons.Default.Delete, contentDescription = "清除聊天紀錄")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (gemmaLoadState != ModelLoadState.Loaded) {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Gemma 模型尚未載入",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.heightIn(min = 8.dp))
                Text(
                    text = "請先前往模型設定頁面下載或匯入 Gemma 模型。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.heightIn(min = 24.dp))
                androidx.compose.material3.Button(onClick = onNavigateToSetup) {
                    Text("前往模型設定")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                if (!errorMsg.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = errorMsg!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(messages) { msg ->
                        val isUser = msg.role == "user"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(
                                        MaterialTheme.shapes.medium.copy(
                                            bottomEnd = if (isUser) CornerSize(0.dp) else CornerSize(12.dp),
                                            bottomStart = if (isUser) CornerSize(12.dp) else CornerSize(0.dp)
                                        )
                                    )
                                    .background(
                                        if (isUser) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .padding(12.dp)
                                    .widthIn(max = 280.dp)
                            ) {
                                if (isUser) {
                                    if (msg.audioBytes?.isNotEmpty() == true) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (msg.content.isNotBlank() && msg.content != "語音") {
                                                Text(
                                                    text = msg.content,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                            AudioMessageButton(
                                                isPlaying = playingAudioId == msg.id,
                                                onClick = {
                                                    audioPlayer.toggle(
                                                        id = msg.id,
                                                        audioBytes = msg.audioBytes,
                                                        onPlayingChanged = { playingAudioId = it },
                                                    )
                                                },
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = msg.content,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                } else {
                                    ThinkingContent(
                                        rawText = msg.content,
                                        isGenerating = isGenerating && msg.id == 0
                                    )
                                }
                            }
                        }
                    }

                    if (isThinking) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.medium.copy(bottomStart = CornerSize(0.dp)))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        AppLoadingIndicator(modifier = Modifier.size(16.dp))
                                        Text(
                                            text = "Gemma 正在思考中...",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Divider()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp + bottomOverlayPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedImage != null || audioBytes?.isNotEmpty() == true) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                selectedImage?.let { bitmap ->
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "已選取的圖片",
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(MaterialTheme.shapes.small)
                                    )
                                    Text("圖片", style = MaterialTheme.typography.labelLarge)
                                    IconButton(onClick = { selectedImage = null }) {
                                        Icon(Icons.Default.Close, contentDescription = "移除圖片")
                                    }
                                }
                                if (audioBytes?.isNotEmpty() == true) {
                                    IconButton(
                                        onClick = {
                                            audioPlayer.toggle(
                                                id = PREVIEW_AUDIO_ID,
                                                audioBytes = audioBytes!!,
                                                onPlayingChanged = { playingAudioId = it },
                                            )
                                        },
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (playingAudioId == PREVIEW_AUDIO_ID) {
                                                Icons.Default.Stop
                                            } else {
                                                Icons.Default.PlayArrow
                                            },
                                            contentDescription = if (playingAudioId == PREVIEW_AUDIO_ID) {
                                                "停止播放語音"
                                            } else {
                                                "播放語音"
                                            },
                                        )
                                    }
                                    Icon(Icons.Default.GraphicEq, contentDescription = null)
                                    Text(
                                        "語音 (${audioBytes!!.size / 1024} KB)",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    IconButton(onClick = {
                                        audioPlayer.stop { playingAudioId = it }
                                        audioBytes = null
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "移除語音")
                                    }
                                }
                            }
                        }
                    }

                    mediaError?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(34.dp),
                        color = MaterialTheme.colorScheme.primary,
                        tonalElevation = 3.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                enabled = !isGenerating && visionReady,
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f),
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "從相簿加入圖片")
                            }
                            IconButton(
                                onClick = {
                                    if (cameraPermissionGranted) {
                                        cameraLauncher.launch(null)
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                enabled = !isGenerating && visionReady,
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f),
                                )
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "拍攝圖片")
                            }
                            IconButton(
                                onClick = {
                                    if (isRecording) {
                                        scope.launch {
                                            try {
                                                val wavBytes = audioRecorder.stop()
                                                isRecording = false
                                                if (ChatAudioRecorder.hasAudioSignal(wavBytes)) {
                                                    audioBytes = wavBytes
                                                    mediaError = null
                                                } else {
                                                    mediaError = "沒有錄到有效語音，請確認麥克風權限後再試一次"
                                                }
                                            } catch (e: Exception) {
                                                isRecording = false
                                                mediaError = e.localizedMessage ?: "錄音失敗"
                                            }
                                        }
                                    } else if (recordPermissionGranted) {
                                        startRecording()
                                    } else {
                                        recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                enabled = !isGenerating && (audioReady || isRecording),
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = if (isRecording) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onPrimary
                                    },
                                    containerColor = if (isRecording) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
                                    },
                                )
                            ) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.GraphicEq,
                                    contentDescription = if (isRecording) "停止錄音" else "錄製語音",
                                )
                            }

                            val hasAttachment = selectedImage != null || audioBytes?.isNotEmpty() == true
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 54.dp),
                                shape = RoundedCornerShape(28.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                OutlinedTextField(
                                    value = inputQuery,
                                    onValueChange = { inputQuery = it },
                                    placeholder = { Text("輸入訊息...") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 3,
                                    enabled = !isGenerating,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = { sendCurrentMessage() }),
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { sendCurrentMessage() },
                                            enabled = !isGenerating &&
                                                (inputQuery.isNotBlank() || hasAttachment),
                                        ) {
                                            Icon(
                                                imageVector = if (inputQuery.isNotBlank() || hasAttachment) {
                                                    Icons.Default.Send
                                                } else {
                                                    Icons.Default.EmojiEmotions
                                                },
                                                contentDescription = if (inputQuery.isNotBlank() || hasAttachment) {
                                                    "送出訊息"
                                                } else {
                                                    "表情符號"
                                                },
                                            )
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        errorContainerColor = Color.Transparent,
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        disabledBorderColor = Color.Transparent,
                                        errorBorderColor = Color.Transparent,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun decodeChatBitmap(context: Context, uri: Uri): Bitmap? =
    context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)?.scaleForChat()

private fun Bitmap.scaleForChat(maxDimension: Int = 1024): Bitmap {
    val largestDimension = maxOf(width, height)
    if (largestDimension <= maxDimension) return this
    val scale = maxDimension.toFloat() / largestDimension.toFloat()
    return Bitmap.createScaledBitmap(
        this,
        (width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1),
        true,
    )
}

private const val PREVIEW_AUDIO_ID = -1

@Composable
private fun AudioMessageButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onClick,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "停止播放語音" else "播放語音",
                )
            }
            Text(
                text = "語音",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
    }
}
