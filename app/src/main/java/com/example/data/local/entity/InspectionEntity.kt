package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inspections",
    indices = [Index(value = ["shiftId"], unique = true)]
)
data class InspectionEntity(
    @PrimaryKey val id: String,
    val shiftId: String,
    val declarationAccepted: Boolean,
    val validationState: String?,
    val completedAt: Long?
)
