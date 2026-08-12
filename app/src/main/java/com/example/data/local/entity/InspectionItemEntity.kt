package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inspection_items",
    indices = [Index("inspectionId"), Index("shiftId")]
)
data class InspectionItemEntity(
    @PrimaryKey val id: String,
    val inspectionId: String,
    val shiftId: String,
    val code: String,
    val label: String,
    val category: String,
    val mandatory: Boolean,
    val status: String,
    val defectDescription: String?,
    val defectSeverity: String?
)
