package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.data.local.dao.DriverCredentialDao
import com.example.data.local.dao.EvidenceDao
import com.example.data.local.dao.FreightExceptionDao
import com.example.data.local.dao.InspectionDao
import com.example.data.local.dao.JobDao
import com.example.data.local.dao.ReferenceDataDao
import com.example.data.local.dao.ShiftDao
import com.example.data.local.dao.SyncOperationDao
import com.example.data.local.entity.DriverCredentialEntity
import com.example.data.local.entity.DriverEntity
import com.example.data.local.entity.EvidenceEntity
import com.example.data.local.entity.FreightExceptionEntity
import com.example.data.local.entity.InspectionEntity
import com.example.data.local.entity.InspectionItemEntity
import com.example.data.local.entity.JobEntity
import com.example.data.local.entity.ShiftEntity
import com.example.data.local.entity.SyncOperationEntity
import com.example.data.local.entity.VehicleEntity

@Database(
    entities = [
        DriverEntity::class,
        DriverCredentialEntity::class,
        FreightExceptionEntity::class,
        VehicleEntity::class,
        ShiftEntity::class,
        InspectionEntity::class,
        InspectionItemEntity::class,
        JobEntity::class,
        EvidenceEntity::class,
        SyncOperationEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun referenceDataDao(): ReferenceDataDao
    abstract fun driverCredentialDao(): DriverCredentialDao
    abstract fun freightExceptionDao(): FreightExceptionDao
    abstract fun shiftDao(): ShiftDao
    abstract fun inspectionDao(): InspectionDao
    abstract fun jobDao(): JobDao
    abstract fun evidenceDao(): EvidenceDao
    abstract fun syncOperationDao(): SyncOperationDao
}
