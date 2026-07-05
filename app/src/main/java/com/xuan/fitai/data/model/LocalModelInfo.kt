package com.xuan.fitai.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_models")
data class LocalModelInfo(
    @PrimaryKey val id: String,
    val name: String,
    val type: ModelType,
    val fileName: String,
    val localPath: String,
    val sourceUrl: String? = null,
    val requiresToken: Boolean = false,
    val fileSizeBytes: Long = 0L,
    val isDownloaded: Boolean = false,
    val isLoaded: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

enum class ModelType {
    LLM,
    FOOD_CLASSIFIER,
    EMBEDDING,
    UNKNOWN
}
