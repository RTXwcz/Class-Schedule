package com.kebiao.app.data.local

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "dataset_metadata")
data class DatasetMetadataEntity(
    @PrimaryKey val id: Int = 1,
    val schemaVersion: Int,
    val datasetId: String,
    val updatedAt: String,
    val source: String,
    val extraFieldsJson: String,
    @ColumnInfo(defaultValue = "0") val rulesInitialized: Boolean = false,
)
