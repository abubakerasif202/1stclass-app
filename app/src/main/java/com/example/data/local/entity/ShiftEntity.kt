package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shifts",
    indices = [Index("driverId"), Index("phase")]
)
data class ShiftEntity(
    @PrimaryKey val id: String,
    val driverId: String,
    val vehicleId: String,
    val trailerId: String?,
    val startOdometer: Long,
    val endOdometer: Long?,
    val phase: String,
    val createdAt: Long,
    val startedAt: Long?,
    val endedAt: Long?
)
