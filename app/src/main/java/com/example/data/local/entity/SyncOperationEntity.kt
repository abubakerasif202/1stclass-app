package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_operations",
    indices = [Index("status"), Index(value = ["entityType", "entityId", "operationType"])]
)
data class SyncOperationEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val operationType: String,
    val payloadJson: String,
    val createdAt: Long,
    val retryCount: Int,
    val lastError: String?,
    val status: String
)
