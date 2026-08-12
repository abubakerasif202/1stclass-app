package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.DriverCredentialEntity

@Dao
interface DriverCredentialDao {
    @Query("SELECT COUNT(*) FROM driver_credentials")
    suspend fun count(): Int

    @Query("SELECT * FROM driver_credentials WHERE loginId = :loginId OR email = :loginId LIMIT 1")
    suspend fun findByLogin(loginId: String): DriverCredentialEntity?

    @Query("SELECT * FROM driver_credentials WHERE driverId = :driverId LIMIT 1")
    suspend fun findByDriverId(driverId: String): DriverCredentialEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(credential: DriverCredentialEntity)
}
