package com.xuan.fitai.data.local

import androidx.room.*
import com.xuan.fitai.data.model.LocalModelInfo
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    @Query("SELECT * FROM local_models ORDER BY createdAt DESC")
    fun getAllModels(): Flow<List<LocalModelInfo>>

    @Query("SELECT * FROM local_models WHERE id = :id LIMIT 1")
    suspend fun getModelById(id: String): LocalModelInfo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: LocalModelInfo)

    @Update
    suspend fun updateModel(model: LocalModelInfo)

    @Query("DELETE FROM local_models WHERE id = :id")
    suspend fun deleteModelById(id: String)
}
