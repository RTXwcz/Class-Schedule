package com.kebiao.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DatasetMetadataDao {
    @Query("SELECT * FROM dataset_metadata WHERE id = 1")
    fun observe(): Flow<DatasetMetadataEntity?>

    @Query("SELECT * FROM dataset_metadata WHERE id = 1")
    suspend fun get(): DatasetMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metadata: DatasetMetadataEntity)
}
