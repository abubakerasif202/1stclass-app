package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.DriverEntity
import com.example.data.local.entity.VehicleEntity

@Dao
interface ReferenceDataDao {
    @Query("SELECT COUNT(*) FROM drivers")
    suspend fun driverCount(): Int

    @Query("SELECT COUNT(*) FROM vehicles")
    suspend fun vehicleCount(): Int

    @Query("SELECT * FROM drivers ORDER BY id LIMIT 1")
    suspend fun firstDriver(): DriverEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDriver(driver: DriverEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVehicles(vehicles: List<VehicleEntity>)
}
