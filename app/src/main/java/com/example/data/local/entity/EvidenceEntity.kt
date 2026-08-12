package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "evidence",
    indices = [Index("jobId"), Index("status")]
)
data class EvidenceEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val type: String,
    val localUri: String?,
    val status: String,
    val createdAt: Long
)
