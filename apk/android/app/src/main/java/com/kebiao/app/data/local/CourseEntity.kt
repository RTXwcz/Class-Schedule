package com.kebiao.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val weekday: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weekRule: String,
    val building: String?,
    val room: String?,
    val locationNote: String?,
    val source: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val teacher: String? = null,
    @ColumnInfo(defaultValue = "'[]'") val weeksJson: String = "[]",
    val courseNote: String? = null,
)
