package com.xuan.fitai.data.model

sealed class ModelDownloadState {
    data object Idle : ModelDownloadState()
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : ModelDownloadState()
    data object Completed : ModelDownloadState()
    data class Failed(val message: String) : ModelDownloadState()
    data object Cancelled : ModelDownloadState()
}

sealed class ModelLoadState {
    data object NotFound : ModelLoadState()
    data object Loading : ModelLoadState()
    data object Loaded : ModelLoadState()
    data class Failed(val message: String) : ModelLoadState()
}
