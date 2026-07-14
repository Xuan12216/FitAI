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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.xuan.fitai.data.model.ModelLoadState
import com.xuan.fitai.ui.components.AppLoadingIndicator
import com.xuan.fitai.ui.components.ThinkingContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToSetup: () -> Unit,
    bottomOverlayPadding: Dp = 0.dp,
    isToolsBarVisible: Boolean = false,
    onToggleToolsBar: () -> Unit = {},
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
    var selectedImages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var viewerImages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var previewImageIndex by remember { mutableStateOf<Int?>(null) }
    var audioBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var playingAudioId by remember { mutableStateOf<Int?>(null) }
    var mediaError by remember { mutableStateOf<String?>(null) }
    var hasAppliedInitialScroll by remember { mutableStateOf(false) }
    var shouldFollowMessages by remember { mutableStateOf(true) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var cameraPermissionGranted by remember {
        mutableStateOf(hasPermission(context, Manifest.permission.CAMERA))
    }
    var recordPermissionGranted by remember {
        mutableStateOf(hasPermission(context, Manifest.permission.RECORD_AUDIO))
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val bitmaps = withContext(Dispatchers.IO) {
                uris.mapNotNull { decodeChatBitmap(context, it) }
            }
            if (bitmaps.isEmpty()) {
                mediaError = "無法讀取這張圖片"
            } else {
                selectedImages = selectedImages + bitmaps
                mediaError = if (bitmaps.size == uris.size) null else "??敺蝪輸???"
            }
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.scaleForChat()?.let {
            selectedImages = selectedImages + it
            mediaError = null
        }
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
        val hasAttachment = selectedImages.isNotEmpty() || audioBytes?.isNotEmpty() == true
        if (isGenerating || isRecording || (inputQuery.isBlank() && !hasAttachment)) return
        audioPlayer.stop { playingAudioId = it }
        viewModel.sendMessage(inputQuery, selectedImages, audioBytes)
        inputQuery = ""
        selectedImages = emptyList()
        previewImageIndex = null
        viewerImages = emptyList()
        audioBytes = null
        mediaError = null
    }

    val userScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    shouldFollowMessages = false
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { !listState.canScrollForward }
            .distinctUntilChanged()
            .collect { isAtBottom ->
                if (isAtBottom) {
                    shouldFollowMessages = true
                }
            }
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isEmpty()) {
            hasAppliedInitialScroll = false
            shouldFollowMessages = true
            return@LaunchedEffect
        }

        // Keep the newest message at the bottom while following generation.
        // A zero offset puts the item at the top, which is the jump seen while
        // the streaming response grows.
        if (!hasAppliedInitialScroll || shouldFollowMessages) {
            listState.scrollToItem(messages.lastIndex, scrollOffset = Int.MAX_VALUE)
            hasAppliedInitialScroll = true
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
                        .nestedScroll(userScrollConnection)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(
                        items = messages,
                        key = { msg -> if (msg.id == 0) "streaming" else msg.id },
                    ) { msg ->
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
                                val messageImages by produceState<List<Bitmap>>(
                                    initialValue = emptyList(),
                                    msg.id,
                                    msg.imageBytes,
                                ) {
                                    value = withContext(Dispatchers.Default) {
                                        ChatImageCodec.decode(msg.imageBytes)
                                    }
                                }
                                if (isUser) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (messageImages.isNotEmpty()) {
                                            ChatImageCarousel(
                                                images = messageImages,
                                                onImageClick = {
                                                    viewerImages = messageImages
                                                    previewImageIndex = it
                                                },
                                            )
                                        }
                                    if (msg.audioBytes?.isNotEmpty() == true) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (msg.content.isNotBlank() && msg.content != "語音" && msg.content != "圖片") {
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
                                    } else if (msg.content != "圖片" || messageImages.isEmpty()) {
                                        Text(
                                            text = msg.content,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
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
                    if (selectedImages.isNotEmpty() || audioBytes?.isNotEmpty() == true) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (selectedImages.isNotEmpty()) {
                                    ChatImageCarousel(
                                        images = selectedImages,
                                        onImageClick = {
                                            viewerImages = selectedImages
                                            previewImageIndex = it
                                        },
                                        onRemoveImage = { index ->
                                            selectedImages = selectedImages.filterIndexed { i, _ -> i != index }
                                        },
                                    )
                                }
                                if (audioBytes?.isNotEmpty() == true) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
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
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                        IconButton(
                                            onClick = {
                                                audioPlayer.stop { playingAudioId = it }
                                                audioBytes = null
                                            },
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "移除語音")
                                        }
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IconButton(
                            onClick = onToggleToolsBar,
                            enabled = !isGenerating,
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = if (isToolsBarVisible) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                containerColor = if (isToolsBarVisible) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                },
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = if (isToolsBarVisible) "隱藏功能列" else "顯示功能列",
                            )
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(36.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 1.dp,
                        ) {
                        Row(
                            modifier = Modifier
                                .heightIn(min = 58.dp)
                                .padding(horizontal = 6.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box {
                                IconButton(
                                    onClick = { showAttachmentMenu = true },
                                    enabled = !isGenerating && visionReady,
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                    )
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "加入圖片或拍攝照片")
                                }
                                DropdownMenu(
                                    expanded = showAttachmentMenu,
                                    onDismissRequest = { showAttachmentMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("從相簿選取") },
                                        leadingIcon = {
                                            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                        },
                                        onClick = {
                                            showAttachmentMenu = false
                                            imagePickerLauncher.launch("image/*")
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("拍攝照片") },
                                        leadingIcon = {
                                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                                        },
                                        onClick = {
                                            showAttachmentMenu = false
                                            if (cameraPermissionGranted) {
                                                cameraLauncher.launch(null)
                                            } else {
                                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                            }
                                        },
                                    )
                                }
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
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                    containerColor = if (isRecording) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        Color.Transparent
                                    },
                                )
                            ) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = if (isRecording) "停止錄音" else "錄製語音",
                                )
                            }

                            OutlinedTextField(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                value = inputQuery,
                                onValueChange = { inputQuery = it },
                                placeholder = { Text("輸入訊息...") },
                                maxLines = 3,
                                enabled = !isGenerating,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { sendCurrentMessage() }),
                                trailingIcon = {
                                    IconButton(
                                        onClick = { sendCurrentMessage() },
                                        enabled = !isGenerating && (
                                            inputQuery.isNotBlank() ||
                                                selectedImages.isNotEmpty() ||
                                                audioBytes?.isNotEmpty() == true
                                            ),
                                        colors = IconButtonDefaults.iconButtonColors(
                                            contentColor = if (
                                                inputQuery.isNotBlank() ||
                                                    selectedImages.isNotEmpty() ||
                                                    audioBytes?.isNotEmpty() == true
                                            ) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                        ),
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "送出訊息",
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    focusedTrailingIconColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurface,
                                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
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

    previewImageIndex?.let { index ->
        if (viewerImages.isNotEmpty()) {
            ChatImageViewer(
                images = viewerImages,
                initialIndex = index,
                onDismiss = {
                    previewImageIndex = null
                    viewerImages = emptyList()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatImageCarousel(
    images: List<Bitmap>,
    onImageClick: (Int) -> Unit,
    onRemoveImage: ((Int) -> Unit)? = null,
) {
    val carouselState = rememberCarouselState { images.size }

    HorizontalMultiBrowseCarousel(
        state = carouselState,
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp),
        preferredItemWidth = 112.dp,
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) { index ->
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                bitmap = images[index].asImageBitmap(),
                contentDescription = "?? ${index + 1}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onImageClick(index) },
            )
            if (onRemoveImage != null) {
                Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(28.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = 0.55f),
            ) {
                IconButton(
                    onClick = { onRemoveImage(index) },
                    modifier = Modifier.size(28.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "蝘駁??",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun ChatImageViewer(
    images: List<Bitmap>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, images.lastIndex),
        pageCount = { images.size },
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(0.dp),
            color = Color.Black,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    ZoomableChatImage(
                        bitmap = images[page],
                        contentDescription = "?? ${page + 1}",
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = Color.White,
                        containerColor = Color.Black.copy(alpha = 0.55f),
                    ),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "蝘駁")
                }
                if (images.size > 1) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${images.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomableChatImage(
    bitmap: Bitmap,
    contentDescription: String,
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = newScale

        if (newScale <= 1f) {
            offset = Offset.Zero
        } else {
            val maxX = containerSize.width * (newScale - 1f) / 2f
            val maxY = containerSize.height * (newScale - 1f) / 2f
            offset = Offset(
                x = (offset.x + panChange.x).coerceIn(-maxX, maxX),
                y = (offset.y + panChange.y).coerceIn(-maxY, maxY),
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.5f
                        if (scale == 1f) offset = Offset.Zero
                    },
                )
            }
            .transformable(
                state = transformState,
                canPan = { scale > 1f },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
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
