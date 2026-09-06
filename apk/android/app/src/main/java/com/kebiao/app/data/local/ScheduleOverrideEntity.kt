package com.kebiao.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedule_overrides")
data class ScheduleOverrideEntity(
    @PrimaryKey val date: String,
    val replacementWeekday: Int,
    val note: String?,
)
