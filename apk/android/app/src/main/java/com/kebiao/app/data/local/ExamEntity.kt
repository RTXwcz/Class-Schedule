package com.kebiao.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exams")
data class ExamEntity(
    @PrimaryKey val id: String,
    val subject: String,
    val date: String,
    val time: String?,
    val building: String?,
    val room: String?,
    val locationNote: String?,
    val source: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
